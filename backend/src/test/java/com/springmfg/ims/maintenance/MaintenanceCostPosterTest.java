package com.springmfg.ims.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.finance.gl.service.AccountingFailureReporter;
import com.springmfg.ims.maintenance.domain.MaintenanceRequest;
import com.springmfg.ims.maintenance.event.MaintenanceCompletedEvent;
import com.springmfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.springmfg.ims.maintenance.service.MaintenanceCostPoster;
import com.springmfg.ims.maintenance.service.MaintenanceCostService;

/** The after-commit accounting step must never undo a completion: failures become alerts and can be retried. */
class MaintenanceCostPosterTest {

    private MaintenanceCostService costService;
    private MaintenanceRequestRepository requestRepo;
    private AccountingFailureReporter reporter;
    private MaintenanceCostPoster poster;

    @BeforeEach
    void setUp() {
        costService = mock(MaintenanceCostService.class);
        requestRepo = mock(MaintenanceRequestRepository.class);
        reporter = mock(AccountingFailureReporter.class);
        poster = new MaintenanceCostPoster(costService, requestRepo, reporter);
    }

    private MaintenanceRequest request(long id, String number) {
        MaintenanceRequest r = mock(MaintenanceRequest.class);
        when(r.getId()).thenReturn(id);
        when(r.getRequestNumber()).thenReturn(number);
        when(requestRepo.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void completion_posts_the_cost_and_clears_any_earlier_alert() {
        request(1L, "MR/26/00001");

        poster.onCompleted(new MaintenanceCompletedEvent(1L));

        verify(costService).postLabourCost(1L);
        verify(reporter).maintenanceCostPosted(1L, "MR/26/00001");
        verify(reporter, never()).maintenanceCostUnposted(any(), any(), any());
    }

    @Test
    void a_failure_raises_the_alert_and_does_not_propagate() {
        request(2L, "MR/26/00002");
        doThrow(new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No open fiscal period"))
                .when(costService).postLabourCost(2L);

        boolean posted = poster.postAndReport(2L);

        assertThat(posted).isFalse();
        verify(reporter).maintenanceCostUnposted(eq(2L), eq("MR/26/00002"),
                org.mockito.ArgumentMatchers.contains("No open fiscal period"));
        verify(reporter, never()).maintenanceCostPosted(any(), any());
    }

    @Test
    void a_failing_alert_write_does_not_hide_the_original_problem_or_throw() {
        request(3L, "MR/26/00003");
        doThrow(new IllegalStateException("db down")).when(costService).postLabourCost(3L);
        doThrow(new IllegalStateException("alerts down")).when(reporter).maintenanceCostUnposted(any(), any(), any());

        assertThat(poster.postAndReport(3L)).isFalse();
    }

    @Test
    void repost_retries_every_unposted_request_and_counts_only_the_ones_that_succeed() {
        MaintenanceRequest good = request(4L, "MR/26/00004");
        MaintenanceRequest bad = request(5L, "MR/26/00005");
        when(requestRepo.findCompletedWithUnpostedCost()).thenReturn(List.of(good, bad));
        doThrow(new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "no cost centre"))
                .when(costService).postLabourCost(5L);

        int posted = poster.repostUnposted();

        assertThat(posted).isEqualTo(1);
        verify(costService).postLabourCost(4L);
        verify(costService).postLabourCost(5L);
    }
}
