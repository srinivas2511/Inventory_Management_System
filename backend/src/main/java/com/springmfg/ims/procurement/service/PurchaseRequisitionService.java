package com.springmfg.ims.procurement.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.procurement.domain.PurchaseRequisition;
import com.springmfg.ims.procurement.dto.CreateRequisitionRequest;
import com.springmfg.ims.procurement.dto.PurchaseRequisitionDto;
import com.springmfg.ims.procurement.repository.PurchaseRequisitionRepository;

@Service
public class PurchaseRequisitionService {

    private final PurchaseRequisitionRepository prRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public PurchaseRequisitionService(
            PurchaseRequisitionRepository prRepo,
            DocumentNumberService numberService,
            AuditService auditService) {
        this.prRepo = prRepo;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    @Transactional
    public PurchaseRequisitionDto create(CreateRequisitionRequest req) {
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPrNumber(numberService.next("PR"));
        pr.setMaterialId(req.materialId());
        pr.setRequestedQty(req.requestedQty());
        pr.setUom(req.uom());
        pr.setNeededBy(req.neededBy());
        pr.setReason(req.reason());
        pr.setStatus("OPEN");
        pr.setRequestedBy(currentUserId());
        pr = prRepo.save(pr);
        auditService.record("PR_CREATED", "PURCHASE_REQUISITION", String.valueOf(pr.getId()),
                null, pr.getPrNumber(), null);
        return toDto(pr);
    }

    @Transactional
    public PurchaseRequisitionDto cancel(Long id) {
        PurchaseRequisition pr = requirePr(id);
        if (!"OPEN".equals(pr.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only OPEN requisitions can be cancelled");
        }
        pr.setStatus("CANCELLED");
        pr = prRepo.save(pr);
        auditService.record("PR_CANCELLED", "PURCHASE_REQUISITION", String.valueOf(id),
                "OPEN", "CANCELLED", null);
        return toDto(pr);
    }

    @Transactional(readOnly = true)
    public PurchaseRequisitionDto findById(Long id) {
        return toDto(requirePr(id));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequisitionDto> findByStatus(String status, Pageable pageable) {
        Pageable page = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
        // A null status is "no filter"; the derived finder would turn it into "status IS NULL".
        if (status == null || status.isBlank()) {
            return prRepo.findAll(page).map(this::toDto);
        }
        return prRepo.findByStatus(status.trim(), page).map(this::toDto);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private PurchaseRequisition requirePr(Long id) {
        return prRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase requisition not found: " + id));
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser u) {
                return u.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private PurchaseRequisitionDto toDto(PurchaseRequisition pr) {
        return new PurchaseRequisitionDto(
                pr.getId(), pr.getPrNumber(), pr.getMaterialId(), null, null,
                pr.getRequestedQty(), pr.getUom(), pr.getNeededBy(), pr.getReason(),
                pr.getStatus(), pr.getRequestedBy(), pr.getConvertedPoId(), null);
    }
}
