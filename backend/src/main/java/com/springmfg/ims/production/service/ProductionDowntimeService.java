package com.springmfg.ims.production.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.production.domain.DowntimeCategory;
import com.springmfg.ims.production.domain.ProductionDowntime;
import com.springmfg.ims.production.dto.DowntimeDto;
import com.springmfg.ims.production.dto.RecordDowntimeRequest;
import com.springmfg.ims.production.repository.ProductionDowntimeRepository;

@Service
public class ProductionDowntimeService {

    private final ProductionDowntimeRepository repo;
    private final AuditService auditService;

    public ProductionDowntimeService(ProductionDowntimeRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional
    public DowntimeDto record(RecordDowntimeRequest req) {
        ProductionDowntime dt = new ProductionDowntime();
        dt.setMachineId(req.machineId());
        dt.setProductionOrderId(req.productionOrderId());
        dt.setReason(req.reason());
        dt.setCategory(DowntimeCategory.validate(req.category()));
        dt.setStartedAt(req.startedAt() != null ? req.startedAt() : Instant.now());
        if (req.endedAt() != null) {
            if (!req.endedAt().isAfter(dt.getStartedAt())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Downtime must end after it starts");
            }
            dt.setEndedAt(req.endedAt());
        }
        dt.setReportedBy(currentUserId());
        dt = repo.save(dt);
        auditService.record("DOWNTIME_RECORD", "ProductionDowntime", String.valueOf(dt.getId()),
                null, req.reason(), null);
        return toDto(dt);
    }

    @Transactional
    public DowntimeDto endDowntime(Long id) {
        ProductionDowntime dt = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Downtime record not found: " + id));
        if (dt.getEndedAt() != null) {
            throw new IllegalStateException("Downtime already ended");
        }
        dt.setEndedAt(Instant.now());
        dt = repo.save(dt);
        auditService.record("DOWNTIME_END", "ProductionDowntime", String.valueOf(id), null, "ended", null);
        return toDto(dt);
    }

    @Transactional(readOnly = true)
    public List<DowntimeDto> list() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DowntimeDto> listByOrder(Long orderId) {
        return repo.findByProductionOrderId(orderId).stream().map(this::toDto).toList();
    }

    private DowntimeDto toDto(ProductionDowntime dt) {
        return new DowntimeDto(dt.getId(), dt.getMachineId(), dt.getProductionOrderId(),
                dt.getReason(), dt.getCategory(), dt.getStartedAt(), dt.getEndedAt(), dt.getReportedBy());
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
