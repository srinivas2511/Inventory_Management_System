package com.mfg.ims.production.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.production.domain.ProductionOrderTemplate;
import com.mfg.ims.production.dto.ProductionOrderTemplateDto;
import com.mfg.ims.production.dto.SaveTemplateRequest;
import com.mfg.ims.production.repository.ProductionOrderTemplateRepository;

@Service
public class ProductionOrderTemplateService {

    private static final Long COMPANY_ID = 1L;

    private final ProductionOrderTemplateRepository repo;

    public ProductionOrderTemplateService(ProductionOrderTemplateRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ProductionOrderTemplateDto> listActive() {
        return repo.findByCompanyIdAndActiveOrderByNameAsc(COMPANY_ID, true)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProductionOrderTemplateDto get(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public ProductionOrderTemplateDto create(SaveTemplateRequest req) {
        // Idempotent: return existing template if the same code is already in use
        var existing = repo.findByCompanyIdAndCodeIgnoreCase(COMPANY_ID, req.code());
        if (existing.isPresent()) {
            return toDto(existing.get());
        }
        var t = new ProductionOrderTemplate();
        t.setCode(req.code());
        t.setName(req.name());
        t.setProductId(req.productId());
        t.setBomId(req.bomId());
        if (req.plannedQty() != null) t.setPlannedQty(req.plannedQty());
        if (req.leadTimeDays() != null) t.setLeadTimeDays(req.leadTimeDays());
        t.setNotes(req.notes());
        return toDto(repo.save(t));
    }

    @Transactional
    public ProductionOrderTemplateDto update(Long id, SaveTemplateRequest req) {
        var t = findOrThrow(id);
        t.setCode(req.code());
        t.setName(req.name());
        t.setProductId(req.productId());
        t.setBomId(req.bomId());
        if (req.plannedQty() != null) t.setPlannedQty(req.plannedQty());
        if (req.leadTimeDays() != null) t.setLeadTimeDays(req.leadTimeDays());
        t.setNotes(req.notes());
        return toDto(repo.save(t));
    }

    @Transactional
    public void deactivate(Long id) {
        var t = findOrThrow(id);
        t.setActive(false);
        repo.save(t);
    }

    private ProductionOrderTemplate findOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Production order template not found: " + id));
    }

    private ProductionOrderTemplateDto toDto(ProductionOrderTemplate t) {
        return new ProductionOrderTemplateDto(
                t.getId(), t.getCode(), t.getName(),
                t.getProductId(), t.getBomId(),
                t.getPlannedQty(), t.getLeadTimeDays(),
                t.getNotes(), t.isActive());
    }
}
