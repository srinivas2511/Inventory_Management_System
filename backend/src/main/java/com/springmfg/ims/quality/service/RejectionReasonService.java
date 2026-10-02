package com.springmfg.ims.quality.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.quality.domain.Rejection;
import com.springmfg.ims.quality.domain.RejectionReason;
import com.springmfg.ims.quality.dto.ParetoEntryDto;
import com.springmfg.ims.quality.dto.RejectionDto;
import com.springmfg.ims.quality.dto.RejectionReasonDto;
import com.springmfg.ims.quality.dto.SaveRejectionReasonRequest;
import com.springmfg.ims.quality.repository.RejectionReasonRepository;
import com.springmfg.ims.quality.repository.RejectionRepository;

@Service
public class RejectionReasonService {

    private final RejectionReasonRepository reasonRepo;
    private final RejectionRepository rejectionRepo;

    public RejectionReasonService(RejectionReasonRepository reasonRepo, RejectionRepository rejectionRepo) {
        this.reasonRepo = reasonRepo;
        this.rejectionRepo = rejectionRepo;
    }

    @Transactional(readOnly = true)
    public List<RejectionReasonDto> listActive() {
        return reasonRepo.findByActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RejectionReasonDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public RejectionReasonDto create(SaveRejectionReasonRequest req) {
        if (reasonRepo.existsByReasonCode(req.reasonCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Rejection reason code already exists: " + req.reasonCode());
        }
        RejectionReason r = new RejectionReason();
        r.setReasonCode(req.reasonCode());
        r.setName(req.name());
        r.setAppliesTo(req.appliesTo());
        r.setActive(true);
        return toDto(reasonRepo.save(r));
    }

    @Transactional
    public RejectionReasonDto update(Long id, SaveRejectionReasonRequest req) {
        RejectionReason r = require(id);
        if (!r.getReasonCode().equals(req.reasonCode()) && reasonRepo.existsByReasonCode(req.reasonCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Rejection reason code already exists: " + req.reasonCode());
        }
        r.setReasonCode(req.reasonCode());
        r.setName(req.name());
        r.setAppliesTo(req.appliesTo());
        return toDto(reasonRepo.save(r));
    }

    @Transactional
    public void deactivate(Long id) {
        RejectionReason r = require(id);
        r.setActive(false);
        reasonRepo.save(r);
    }

    // ── Rejection queries (Task 5.6) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RejectionDto> searchRejections(Long productId, Long reasonId, Pageable pageable) {
        return rejectionRepo.search(productId, reasonId, pageable).map(this::toRejectionDto);
    }

    @Transactional(readOnly = true)
    public List<ParetoEntryDto> pareto() {
        List<Object[]> rows = rejectionRepo.paretoByReason();
        long totalCount = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        double totalQty = rows.stream().mapToDouble(r -> ((Number) r[2]).doubleValue()).sum();
        return rows.stream().map(r -> {
            Long reasonId = ((Number) r[0]).longValue();
            long count = ((Number) r[1]).longValue();
            java.math.BigDecimal qty = new java.math.BigDecimal(r[2].toString());
            double pct = totalQty > 0 ? Math.round(qty.doubleValue() / totalQty * 10000d) / 100d : 0;
            RejectionReason rr = reasonRepo.findById(reasonId).orElse(null);
            return new ParetoEntryDto(
                    reasonId,
                    rr != null ? rr.getReasonCode() : null,
                    rr != null ? rr.getName() : null,
                    qty, count, pct);
        }).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RejectionReason require(Long id) {
        return reasonRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Rejection reason not found: " + id));
    }

    private RejectionReasonDto toDto(RejectionReason r) {
        return new RejectionReasonDto(r.getId(), r.getReasonCode(), r.getName(), r.getAppliesTo(), r.isActive());
    }

    private RejectionDto toRejectionDto(Rejection r) {
        RejectionReason rr = r.getRejectionReasonId() != null
                ? reasonRepo.findById(r.getRejectionReasonId()).orElse(null) : null;
        return new RejectionDto(
                r.getId(), r.getInspectionId(), r.getProductionOutputId(),
                r.getRejectionReasonId(),
                rr != null ? rr.getReasonCode() : null,
                rr != null ? rr.getName() : null,
                r.getItemType(), r.getProductId(), r.getBatchId(),
                r.getQuantity(), r.getDisposition(), null, r.getRecordedAt());
    }
}
