package com.mfg.ims.mrp.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.mrp.domain.MrpPlanningParam;
import com.mfg.ims.mrp.dto.PlanningParamDto;
import com.mfg.ims.mrp.dto.SavePlanningParamRequest;
import com.mfg.ims.mrp.repository.MrpPlanningParamRepository;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.product.repository.ProductRepository;

/** Per-item MTS/MTO strategy, lot-sizing rule and lead time (MRP-003, MRP-005). */
@Service
public class PlanningParamService {

    private final MrpPlanningParamRepository repo;
    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;
    private final AuditService auditService;

    public PlanningParamService(MrpPlanningParamRepository repo, MaterialRepository materialRepo,
                                ProductRepository productRepo, AuditService auditService) {
        this.repo = repo;
        this.materialRepo = materialRepo;
        this.productRepo = productRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlanningParamDto> findAll() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public PlanningParamDto save(SavePlanningParamRequest req) {
        boolean forMaterial = req.materialId() != null;
        if (forMaterial == (req.productId() != null)) {
            throw invalid("Exactly one of materialId or productId is required");
        }
        validateLot(req);
        if (forMaterial && "MTO".equals(req.planningStrategy())) {
            throw invalid("MTO planning strategy applies to products only");
        }
        MrpPlanningParam param;
        if (forMaterial) {
            materialRepo.findById(req.materialId())
                    .orElseThrow(() -> new NotFoundException("Material not found: " + req.materialId()));
            param = repo.findByMaterialId(req.materialId()).orElseGet(MrpPlanningParam::new);
            param.setItemType("MATERIAL");
            param.setMaterialId(req.materialId());
        } else {
            productRepo.findById(req.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + req.productId()));
            param = repo.findByProductId(req.productId()).orElseGet(MrpPlanningParam::new);
            param.setItemType("PRODUCT");
            param.setProductId(req.productId());
        }
        String old = param.getId() == null ? null : describe(param);
        param.setPlanningStrategy(req.planningStrategy());
        param.setLotSizeRule(req.lotSizeRule());
        param.setFixedLotQty(req.fixedLotQty());
        param.setMinLotQty(req.minLotQty());
        param.setMaxLotQty(req.maxLotQty());
        param.setEoqAnnualDemand(req.eoqAnnualDemand());
        param.setEoqOrderCost(req.eoqOrderCost());
        param.setEoqHoldingCost(req.eoqHoldingCost());
        param.setLeadTimeDays(req.leadTimeDays());
        param = repo.save(param);
        auditService.record("MRP_PARAM_SAVE", "MrpPlanningParam", String.valueOf(param.getId()), old,
                describe(param), null);
        return toDto(param);
    }

    @Transactional
    public void delete(Long id) {
        MrpPlanningParam param = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Planning parameters not found: " + id));
        repo.delete(param);
        auditService.record("MRP_PARAM_DELETE", "MrpPlanningParam", String.valueOf(id), describe(param), null, null);
    }

    private void validateLot(SavePlanningParamRequest r) {
        switch (r.lotSizeRule()) {
            case "FIXED" -> {
                if (r.fixedLotQty() == null) throw invalid("FIXED lot sizing requires fixedLotQty");
            }
            case "MIN_MAX" -> {
                if (r.minLotQty() == null && r.maxLotQty() == null) {
                    throw invalid("MIN_MAX lot sizing requires minLotQty and/or maxLotQty");
                }
                if (r.minLotQty() != null && r.maxLotQty() != null && r.minLotQty().compareTo(r.maxLotQty()) > 0) {
                    throw invalid("minLotQty must not exceed maxLotQty");
                }
            }
            case "EOQ" -> {
                if (r.eoqAnnualDemand() == null || r.eoqOrderCost() == null || r.eoqHoldingCost() == null) {
                    throw invalid("EOQ lot sizing requires eoqAnnualDemand, eoqOrderCost and eoqHoldingCost");
                }
            }
            default -> { }
        }
    }

    private static BusinessRuleException invalid(String detail) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED, detail);
    }

    private String describe(MrpPlanningParam p) {
        return p.getPlanningStrategy() + "/" + p.getLotSizeRule() + " lead=" + p.getLeadTimeDays();
    }

    private PlanningParamDto toDto(MrpPlanningParam p) {
        String code;
        String name;
        if (p.getMaterialId() != null) {
            Material m = materialRepo.findById(p.getMaterialId()).orElse(null);
            code = m == null ? null : m.getMaterialCode();
            name = m == null ? null : m.getName();
        } else {
            Product pr = productRepo.findById(p.getProductId()).orElse(null);
            code = pr == null ? null : pr.getProductCode();
            name = pr == null ? null : pr.getName();
        }
        return new PlanningParamDto(p.getId(), p.getItemType(), p.getMaterialId(), p.getProductId(), code, name,
                p.getPlanningStrategy(), p.getLotSizeRule(), p.getFixedLotQty(), p.getMinLotQty(), p.getMaxLotQty(),
                p.getEoqAnnualDemand(), p.getEoqOrderCost(), p.getEoqHoldingCost(), p.getLeadTimeDays());
    }
}
