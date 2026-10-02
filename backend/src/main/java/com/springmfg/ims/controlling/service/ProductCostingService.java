package com.springmfg.ims.controlling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.controlling.domain.CostCentre;
import com.springmfg.ims.controlling.domain.CostEstimateLine;
import com.springmfg.ims.controlling.domain.ProductCostEstimate;
import com.springmfg.ims.controlling.dto.CostEstimateDto;
import com.springmfg.ims.controlling.dto.CostEstimateLineDto;
import com.springmfg.ims.controlling.repository.CostCentreRepository;
import com.springmfg.ims.controlling.repository.ProductCostEstimateRepository;
import com.springmfg.ims.engineering.bom.Bom;
import com.springmfg.ims.engineering.bom.BomItem;
import com.springmfg.ims.engineering.bom.BomRepository;
import com.springmfg.ims.engineering.routing.ProductRouting;
import com.springmfg.ims.engineering.routing.ProductRoutingRepository;
import com.springmfg.ims.masterdata.machine.Machine;
import com.springmfg.ims.masterdata.machine.MachineRepository;
import com.springmfg.ims.masterdata.operation.Operation;
import com.springmfg.ims.masterdata.operation.OperationRepository;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;

/**
 * Builds standard-cost estimates from the BOM (material) and routing (labour, machine, overhead),
 * and runs the DRAFT to RELEASED to HISTORICAL release workflow (DESIGN section 16.2).
 */
@Service
public class ProductCostingService {

    public static final String SETTING_PRODUCTION_COST_CENTRE = "controlling.production_cost_centre_id";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private final ProductCostEstimateRepository estimateRepo;
    private final BomRepository bomRepo;
    private final ProductRoutingRepository routingRepo;
    private final ProductRepository productRepo;
    private final MaterialRepository materialRepo;
    private final OperationRepository operationRepo;
    private final MachineRepository machineRepo;
    private final CostCentreRepository costCentreRepo;
    private final SystemSettingService settings;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public ProductCostingService(ProductCostEstimateRepository estimateRepo,
                                 BomRepository bomRepo,
                                 ProductRoutingRepository routingRepo,
                                 ProductRepository productRepo,
                                 MaterialRepository materialRepo,
                                 OperationRepository operationRepo,
                                 MachineRepository machineRepo,
                                 CostCentreRepository costCentreRepo,
                                 SystemSettingService settings,
                                 DocumentNumberService numberService,
                                 AuditService auditService) {
        this.estimateRepo = estimateRepo;
        this.bomRepo = bomRepo;
        this.routingRepo = routingRepo;
        this.productRepo = productRepo;
        this.materialRepo = materialRepo;
        this.operationRepo = operationRepo;
        this.machineRepo = machineRepo;
        this.costCentreRepo = costCentreRepo;
        this.settings = settings;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    @Transactional
    public CostEstimateDto estimate(Long productId, Long bomId) {
        return estimate(productId, bomId, null);
    }

    @Transactional
    public CostEstimateDto estimate(Long productId, Long bomId, Long costCentreId) {
        Bom bom = bomRepo.findById(bomId).orElseThrow(() -> new NotFoundException("BOM", bomId));
        productRepo.findById(productId).orElseThrow(() -> new NotFoundException("Product", productId));
        if (!productId.equals(bom.getProductId())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "BOM " + bom.getBomNumber() + " does not belong to product " + productId);
        }
        if (!List.of("APPROVED", "ACTIVE").contains(bom.getStatus())) {
            throw new BusinessRuleException(ErrorCode.BOM_NOT_ACTIVE,
                    "BOM " + bom.getBomNumber() + " must be APPROVED or ACTIVE to be costed, current: " + bom.getStatus());
        }

        CostCentre cc = resolveCostCentre(costCentreId);
        BigDecimal lot = bom.getBaseQuantity();

        ProductCostEstimate est = new ProductCostEstimate();
        est.setEstimateNumber(numberService.next("CE"));
        est.setProductId(productId);
        est.setBomId(bomId);
        est.setCostCentreId(cc != null ? cc.getId() : null);
        est.setCostLotSize(lot);

        BigDecimal material = BigDecimal.ZERO;
        for (BomItem item : bom.getItems()) {
            if (!"MATERIAL".equals(item.getItemKind())) continue;
            Material mat = materialRepo.findById(item.getMaterialId())
                    .orElseThrow(() -> new NotFoundException("Material", item.getMaterialId()));
            BigDecimal scrapFactor = BigDecimal.ONE.add(item.getScrapPct().divide(HUNDRED, 6, RoundingMode.HALF_UP));
            BigDecimal qty = item.getRequiredQty().multiply(scrapFactor).setScale(6, RoundingMode.HALF_UP);
            BigDecimal rate = mat.getStandardCost();
            BigDecimal amount = qty.multiply(rate).setScale(4, RoundingMode.HALF_UP);
            material = material.add(amount);
            est.addLine(line("MATERIAL", trim(mat.getMaterialCode() + " " + mat.getName()), mat.getId(), null,
                    qty, item.getUom() != null ? item.getUom() : mat.getUom(), rate, amount));
        }

        BigDecimal labourRate = cc != null ? cc.getLabourRatePerHour() : BigDecimal.ZERO;
        BigDecimal machineRate = cc != null ? cc.getMachineRatePerHour() : BigDecimal.ZERO;
        BigDecimal labour = BigDecimal.ZERO;
        BigDecimal machine = BigDecimal.ZERO;

        List<ProductRouting> steps = routingRepo.findByProductIdAndActiveTrue(productId).stream()
                .sorted(Comparator.comparingInt(ProductRouting::getSequenceNo)).toList();
        for (ProductRouting step : steps) {
            Operation op = operationRepo.findById(step.getOperationId())
                    .orElseThrow(() -> new NotFoundException("Operation", step.getOperationId()));
            BigDecimal stdMin = step.getStandardTimeMin() != null ? step.getStandardTimeMin() : op.getStandardTimeMin();
            BigDecimal setupMin = step.getSetupTimeMin() != null ? step.getSetupTimeMin() : op.getSetupTimeMin();
            BigDecimal hours = stdMin.multiply(lot).add(setupMin).divide(SIXTY, 6, RoundingMode.HALF_UP);
            if (hours.signum() <= 0) continue;

            BigDecimal labourAmount = hours.multiply(labourRate).setScale(4, RoundingMode.HALF_UP);
            labour = labour.add(labourAmount);
            est.addLine(line("LABOUR", trim("Labour: " + op.getName()), null, op.getId(),
                    hours, "HR", labourRate, labourAmount));

            if (step.getMachineId() != null) {
                String machineName = machineRepo.findById(step.getMachineId()).map(Machine::getName).orElse("Machine");
                BigDecimal machineAmount = hours.multiply(machineRate).setScale(4, RoundingMode.HALF_UP);
                machine = machine.add(machineAmount);
                est.addLine(line("MACHINE", trim("Machine: " + machineName + " / " + op.getName()), null, op.getId(),
                        hours, "HR", machineRate, machineAmount));
            }
        }

        BigDecimal overhead = BigDecimal.ZERO;
        if (cc != null && cc.getOverheadPct().signum() > 0) {
            BigDecimal base = labour.add(machine);
            overhead = base.multiply(cc.getOverheadPct()).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            est.addLine(line("OVERHEAD", "Overhead " + cc.getOverheadPct().stripTrailingZeros().toPlainString()
                    + "% of labour and machine cost", null, null, base, null, cc.getOverheadPct(), overhead));
        }

        est.setMaterialCost(material);
        est.setLabourCost(labour);
        est.setMachineCost(machine);
        est.setOverheadCost(overhead);
        est.setTotalStandardCost(material.add(labour).add(machine).add(overhead));
        est = estimateRepo.save(est);

        auditService.record("COST_ESTIMATE_CREATE", "ProductCostEstimate", String.valueOf(est.getId()),
                null, est.getEstimateNumber() + " total=" + est.getTotalStandardCost().toPlainString(), null);
        return toDto(est);
    }

    @Transactional
    public CostEstimateDto release(Long id) {
        ProductCostEstimate est = find(id);
        if (!"DRAFT".equals(est.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Estimate " + est.getEstimateNumber() + " must be DRAFT to release, current: " + est.getStatus());
        }
        LocalDate today = LocalDate.now();
        String newNumber = est.getEstimateNumber();

        estimateRepo.findByProductIdAndStatus(est.getProductId(), "RELEASED").ifPresent(prev -> {
            prev.setStatus("HISTORICAL");
            prev.setValidTo(today);
            estimateRepo.saveAndFlush(prev);
            auditService.record("COST_ESTIMATE_SUPERSEDED", "ProductCostEstimate", String.valueOf(prev.getId()),
                    "RELEASED", "HISTORICAL", "Superseded by " + newNumber);
        });

        est.setStatus("RELEASED");
        est.setValidFrom(today);
        est.setReleasedBy(currentUserId());
        est.setReleasedAt(Instant.now());
        est = estimateRepo.save(est);
        auditService.record("COST_ESTIMATE_RELEASE", "ProductCostEstimate", String.valueOf(id),
                "DRAFT", "RELEASED", null);
        return toDto(est);
    }

    @Transactional(readOnly = true)
    public CostEstimateDto findById(Long id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public Page<CostEstimateDto> search(Long productId, String status, Pageable pageable) {
        return estimateRepo.search(productId, status, pageable).map(this::toDto);
    }

    private ProductCostEstimate find(Long id) {
        return estimateRepo.findById(id).orElseThrow(() -> new NotFoundException("ProductCostEstimate", id));
    }

    private CostCentre resolveCostCentre(Long costCentreId) {
        if (costCentreId != null) {
            return costCentreRepo.findById(costCentreId)
                    .orElseThrow(() -> new NotFoundException("CostCentre", costCentreId));
        }
        int defaultId = settings.getInt(SETTING_PRODUCTION_COST_CENTRE, 0);
        return defaultId > 0 ? costCentreRepo.findById((long) defaultId).orElse(null) : null;
    }

    private CostEstimateLine line(String element, String description, Long materialId, Long operationId,
                                  BigDecimal quantity, String uom, BigDecimal unitRate, BigDecimal amount) {
        CostEstimateLine l = new CostEstimateLine();
        l.setCostElement(element);
        l.setItemDescription(description);
        l.setMaterialId(materialId);
        l.setOperationId(operationId);
        l.setQuantity(quantity);
        l.setUom(uom);
        l.setUnitRate(unitRate);
        l.setAmount(amount);
        return l;
    }

    private String trim(String s) {
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    private CostEstimateDto toDto(ProductCostEstimate e) {
        Product product = productRepo.findById(e.getProductId()).orElse(null);
        Bom bom = bomRepo.findById(e.getBomId()).orElse(null);
        BigDecimal unit = e.getTotalStandardCost().divide(e.getCostLotSize(), 4, RoundingMode.HALF_UP);
        List<CostEstimateLineDto> lines = e.getLines().stream()
                .map(l -> new CostEstimateLineDto(l.getId(), l.getCostElement(), l.getItemDescription(),
                        l.getMaterialId(), l.getOperationId(), l.getQuantity(), l.getUom(),
                        l.getUnitRate(), l.getAmount()))
                .toList();
        return new CostEstimateDto(e.getId(), e.getEstimateNumber(), e.getProductId(),
                product != null ? product.getProductCode() : null,
                product != null ? product.getName() : null,
                e.getBomId(), bom != null ? bom.getBomNumber() : null, e.getCostCentreId(),
                e.getStatus(), e.getEstimatedAt(), e.getValidFrom(), e.getValidTo(),
                e.getMaterialCost(), e.getLabourCost(), e.getMachineCost(), e.getOverheadCost(),
                e.getTotalStandardCost(), e.getCostLotSize(), unit, e.getReleasedAt(), lines,
                "DRAFT".equals(e.getStatus()) ? List.of("release") : List.of());
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
