package com.mfg.ims.engineering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.engineering.bom.Bom;
import com.mfg.ims.engineering.bom.BomItemRepository;
import com.mfg.ims.engineering.bom.BomRepository;
import com.mfg.ims.engineering.bom.BomService;
import com.mfg.ims.masterdata.operation.OperationRepository;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class BomServiceTest {

    @Mock BomRepository bomRepo;
    @Mock BomItemRepository itemRepo;
    @Mock ProductRepository productRepo;
    @Mock MaterialRepository materialRepo;
    @Mock OperationRepository operationRepo;
    @Mock DocumentNumberService numberService;
    @Mock SystemSettingService settingService;
    @Mock AuditService auditService;

    @InjectMocks BomService service;

    @Test
    void activate_supersedes_previous_active_bom() {
        // Arrange: a BOM in APPROVED status waiting to be activated
        Bom toBom = new Bom();
        toBom.setStatus("APPROVED");
        toBom.setProductId(10L);

        // Existing ACTIVE BOM for same product
        Bom existingActive = new Bom();
        existingActive.setStatus("ACTIVE");
        existingActive.setProductId(10L);

        when(bomRepo.findById(1L)).thenReturn(Optional.of(toBom));
        when(bomRepo.findByProductIdAndStatus(10L, "ACTIVE")).thenReturn(Optional.of(existingActive));
        when(bomRepo.save(any(Bom.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.activate(1L);

        // Assert: previous active BOM is now SUPERSEDED
        assertThat(existingActive.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(existingActive.getEffectiveTo()).isNotNull();
        // New BOM is ACTIVE
        assertThat(toBom.getStatus()).isEqualTo("ACTIVE");
        assertThat(toBom.getEffectiveFrom()).isNotNull();
    }

    @Test
    void cannot_activate_draft_bom() {
        Bom draft = new Bom();
        draft.setStatus("DRAFT");
        draft.setProductId(10L);

        when(bomRepo.findById(2L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.activate(2L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
    }

    @Test
    void submit_transitions_draft_to_engineering_review() {
        Bom bom = new Bom();
        bom.setStatus("DRAFT");
        when(bomRepo.findById(3L)).thenReturn(Optional.of(bom));
        when(bomRepo.save(any(Bom.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submitForReview(3L);

        assertThat(bom.getStatus()).isEqualTo("ENGINEERING_REVIEW");
    }

    @Test
    void cannot_update_bom_not_in_draft() {
        Bom bom = new Bom();
        bom.setStatus("APPROVED");
        when(bomRepo.findById(4L)).thenReturn(Optional.of(bom));

        assertThatThrownBy(() -> service.update(4L, new com.mfg.ims.engineering.bom.CreateBomRequest(1L, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
