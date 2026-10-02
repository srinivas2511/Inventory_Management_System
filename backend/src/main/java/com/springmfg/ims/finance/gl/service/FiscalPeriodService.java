package com.springmfg.ims.finance.gl.service;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.finance.gl.domain.FiscalPeriod;
import com.springmfg.ims.finance.gl.dto.FiscalPeriodDto;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FiscalPeriodService {

    private final FiscalPeriodRepository periodRepo;
    private final AuditService auditService;

    public FiscalPeriodService(FiscalPeriodRepository periodRepo, AuditService auditService) {
        this.periodRepo = periodRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FiscalPeriodDto> findAll() {
        return periodRepo.findAllByOrderByFiscalYearAscPeriodNumberAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<FiscalPeriodDto> findOpenPeriodForDate(LocalDate date) {
        return periodRepo.findOpenPeriodForDate(date).map(this::toDto);
    }

    public FiscalPeriodDto initiatePeriodClose(Long id) {
        FiscalPeriod period = findPeriod(id);
        if (!"OPEN".equals(period.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Period " + period.getPeriodName() + " is not OPEN");
        }
        period.setStatus("CLOSING");
        auditService.record("PERIOD_CLOSE_INITIATED", "FISCAL_PERIOD", String.valueOf(id),
                "OPEN", "CLOSING", null);
        return toDto(periodRepo.save(period));
    }

    public FiscalPeriodDto finalizePeriodClose(Long id) {
        FiscalPeriod period = findPeriod(id);
        if (!"CLOSING".equals(period.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Period " + period.getPeriodName() + " is not in CLOSING state");
        }
        period.setStatus("CLOSED");
        period.setClosedBy(currentUserId());
        period.setClosedAt(Instant.now());
        auditService.record("PERIOD_CLOSED", "FISCAL_PERIOD", String.valueOf(id),
                "CLOSING", "CLOSED", "Period close finalized");
        return toDto(periodRepo.save(period));
    }

    public FiscalPeriodDto reopenPeriod(Long id) {
        FiscalPeriod period = findPeriod(id);
        if (!"CLOSED".equals(period.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Period " + period.getPeriodName() + " is not CLOSED");
        }
        period.setStatus("OPEN");
        period.setClosedBy(null);
        period.setClosedAt(null);
        auditService.record("PERIOD_REOPENED", "FISCAL_PERIOD", String.valueOf(id),
                "CLOSED", "OPEN", "Period reopened by Finance Manager");
        return toDto(periodRepo.save(period));
    }

    private FiscalPeriod findPeriod(Long id) {
        return periodRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", id));
    }

    private FiscalPeriodDto toDto(FiscalPeriod p) {
        return new FiscalPeriodDto(p.getId(), p.getPeriodName(), p.getFiscalYear(),
                p.getPeriodNumber(), p.getStartDate(), p.getEndDate(), p.getStatus());
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.iam.domain.User user) {
                return user.getId();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
