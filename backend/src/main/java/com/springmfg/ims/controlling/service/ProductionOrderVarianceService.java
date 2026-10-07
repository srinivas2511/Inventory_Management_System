package com.springmfg.ims.controlling.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.controlling.domain.CostCentre;
import com.springmfg.ims.controlling.domain.CostEstimateLine;
import com.springmfg.ims.controlling.domain.ProductCostEstimate;
import com.springmfg.ims.controlling.domain.ProductionOrderVariance;
import com.springmfg.ims.controlling.dto.VarianceDto;
import com.springmfg.ims.controlling.repository.CostCentreRepository;
import com.springmfg.ims.controlling.repository.ProductCostEstimateRepository;
import com.springmfg.ims.controlling.repository.ProductionOrderVarianceRepository;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalHeaderDto;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.production.domain.MaterialIssue;
import com.springmfg.ims.production.domain.MaterialIssueLine;
import com.springmfg.ims.production.domain.ProductionOperation;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.domain.ProductionOrderMaterial;
import com.springmfg.ims.production.service.FinishedGoodsCostService;
import com.springmfg.ims.production.repository.MaterialIssueLineRepository;
import com.springmfg.ims.production.repository.MaterialIssueRepository;
import com.springmfg.ims.production.repository.ProductionOperationRepository;
import com.springmfg.ims.production.repository.ProductionOrderMaterialRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;

/**
 * Compares actual production order cost with the released standard cost and posts the variance journal
 * through {@link JournalService} (DESIGN section 16.3). It reads production data but never touches the
 * inventory ledger.
 */
@Service
public class ProductionOrderVarianceService {

    static final String ACCOUNT_VARIANCE_EXPENSE = "5204";
    static final String ACCOUNT_VARIANCE_INCOME = "4101";
    static final String ACCOUNT_WIP = "1501";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final BigDecimal MIN_POSTING_AMOUNT = new BigDecimal("0.0001");

    private final ProductionOrderRepository orderRepo;
    private final ProductionOrderMaterialRepository orderMaterialRepo;
    private final MaterialIssueRepository issueRepo;
    private final MaterialIssueLineRepository issueLineRepo;
    private final ProductionOperationRepository operationRepo;
    private final ProductCostEstimateRepository estimateRepo;
    private final ProductionOrderVarianceRepository varianceRepo;
    private final CostCentreRepository costCentreRepo;
    private final ProductRepository productRepo;
    private final GlAccountRepository glAccountRepo;
    private final JournalService journalService;
    private final SystemSettingService settings;
    private final AuditService auditService;
    private final FinishedGoodsCostService fgCostService;

    public ProductionOrderVarianceService(ProductionOrderRepository orderRepo,
                                          ProductionOrderMaterialRepository orderMaterialRepo,
                                          MaterialIssueRepository issueRepo,
                                          MaterialIssueLineRepository issueLineRepo,
                                          ProductionOperationRepository operationRepo,
                                          ProductCostEstimateRepository estimateRepo,
                                          ProductionOrderVarianceRepository varianceRepo,
                                          CostCentreRepository costCentreRepo,
                                          ProductRepository productRepo,
                                          GlAccountRepository glAccountRepo,
                                          JournalService journalService,
                                          SystemSettingService settings,
                                          AuditService auditService,
                                          FinishedGoodsCostService fgCostService) {
        this.orderRepo = orderRepo;
        this.orderMaterialRepo = orderMaterialRepo;
        this.issueRepo = issueRepo;
        this.issueLineRepo = issueLineRepo;
        this.operationRepo = operationRepo;
        this.estimateRepo = estimateRepo;
        this.varianceRepo = varianceRepo;
        this.costCentreRepo = costCentreRepo;
        this.productRepo = productRepo;
        this.glAccountRepo = glAccountRepo;
        this.journalService = journalService;
        this.settings = settings;
        this.auditService = auditService;
        this.fgCostService = fgCostService;
    }

    /**
     * Calculates (or returns the already posted) variance for a COMPLETED/CLOSED order and posts the variance
     * journal. Runs in its own transaction so it also works when invoked after the closing transaction committed.
     * A variance that has already been journalled is returned unchanged, so a reopen-and-reclose does not repost.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VarianceDto calculate(Long productionOrderId) {
        ProductionOrder order = orderRepo.findById(productionOrderId)
                .orElseThrow(() -> new NotFoundException("ProductionOrder", productionOrderId));
        if (!List.of("COMPLETED", "CLOSED").contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Variance can only be calculated for COMPLETED or CLOSED orders, current: " + order.getStatus());
        }

        ProductionOrderVariance existing = varianceRepo.findByProductionOrderId(productionOrderId).orElse(null);
        if (existing != null && existing.getJournalHeaderId() != null) {
            return toDto(existing);
        }

        CostCentre cc = productionCostCentre();
        BigDecimal labourRate = cc != null ? cc.getLabourRatePerHour() : BigDecimal.ZERO;
        BigDecimal machineRate = cc != null ? cc.getMachineRatePerHour() : BigDecimal.ZERO;
        BigDecimal overheadPct = cc != null ? cc.getOverheadPct() : BigDecimal.ZERO;

        ProductCostEstimate estimate = estimateRepo.findByProductIdAndStatus(order.getProductId(), "RELEASED").orElse(null);
        BigDecimal goodQty = order.getGoodQty();
        BigDecimal lot = estimate != null ? estimate.getCostLotSize() : BigDecimal.ONE;

        Map<Long, CostEstimateLine> standardMaterials = new HashMap<>();
        BigDecimal standardHoursPerLot = BigDecimal.ZERO;
        if (estimate != null) {
            for (CostEstimateLine l : estimate.getLines()) {
                if ("MATERIAL".equals(l.getCostElement()) && l.getMaterialId() != null) {
                    standardMaterials.put(l.getMaterialId(), l);
                } else if ("LABOUR".equals(l.getCostElement()) && l.getQuantity() != null) {
                    standardHoursPerLot = standardHoursPerLot.add(l.getQuantity());
                }
            }
        }

        // Actual material: issued value net of returns, per material at the average issue cost
        Map<Long, BigDecimal[]> issued = new HashMap<>(); // [qty, value]
        for (MaterialIssue issue : issueRepo.findByProductionOrderId(productionOrderId)) {
            for (MaterialIssueLine line : issueLineRepo.findByMaterialIssueId(issue.getId())) {
                BigDecimal[] acc = issued.computeIfAbsent(line.getMaterialId(),
                        k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
                acc[0] = acc[0].add(line.getQuantity());
                acc[1] = acc[1].add(line.getQuantity().multiply(line.getUnitCost()));
            }
        }
        Map<Long, BigDecimal> returned = new HashMap<>();
        for (ProductionOrderMaterial pom : orderMaterialRepo.findByProductionOrderId(productionOrderId)) {
            // returned to stock or written off as scrap: neither is part of what the order's pieces cost
            returned.put(pom.getMaterialId(), pom.getReturnedQty().add(pom.getScrapQty()));
        }

        BigDecimal actualMaterial = BigDecimal.ZERO;
        BigDecimal priceVariance = BigDecimal.ZERO;
        BigDecimal quantityVariance = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal[]> e : issued.entrySet()) {
            BigDecimal issuedQty = e.getValue()[0];
            if (issuedQty.signum() == 0) continue;
            BigDecimal avgCost = e.getValue()[1].divide(issuedQty, 8, RoundingMode.HALF_UP);
            BigDecimal netQty = issuedQty.subtract(returned.getOrDefault(e.getKey(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            actualMaterial = actualMaterial.add(netQty.multiply(avgCost));

            CostEstimateLine std = standardMaterials.get(e.getKey());
            BigDecimal stdRate = std != null ? std.getUnitRate() : avgCost;
            BigDecimal stdQty = std != null
                    ? std.getQuantity().multiply(goodQty).divide(lot, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            priceVariance = priceVariance.add(avgCost.subtract(stdRate).multiply(netQty));
            quantityVariance = quantityVariance.add(netQty.subtract(stdQty).multiply(stdRate));
        }

        // Actual labour and machine: elapsed operation time at the cost centre rates
        BigDecimal actualHours = BigDecimal.ZERO;
        BigDecimal actualLabour = BigDecimal.ZERO;
        BigDecimal actualMachine = BigDecimal.ZERO;
        for (ProductionOperation op : operationRepo.findByProductionOrderId(productionOrderId)) {
            if (op.getStartedAt() == null || op.getCompletedAt() == null) continue;
            BigDecimal hours = BigDecimal.valueOf(Duration.between(op.getStartedAt(), op.getCompletedAt()).toSeconds())
                    .divide(SECONDS_PER_HOUR, 6, RoundingMode.HALF_UP);
            actualHours = actualHours.add(hours);
            actualLabour = actualLabour.add(hours.multiply(labourRate));
            if (op.getMachineId() != null) {
                actualMachine = actualMachine.add(hours.multiply(machineRate));
            }
        }
        BigDecimal actualOverhead = actualLabour.add(actualMachine).multiply(overheadPct).divide(HUNDRED, 8, RoundingMode.HALF_UP);

        BigDecimal actualCost = scale(actualMaterial.add(actualLabour).add(actualMachine).add(actualOverhead));
        BigDecimal standardCost = estimate != null
                ? scale(estimate.getTotalStandardCost().multiply(goodQty).divide(lot, 8, RoundingMode.HALF_UP))
                : BigDecimal.ZERO.setScale(4);
        BigDecimal total = estimate != null ? actualCost.subtract(standardCost) : BigDecimal.ZERO.setScale(4);
        // Scrap recorded on the operations was already charged (Dr 5202 / Cr 1501) when it was recorded, but its material
        // is part of the actual cost, so it sits in the total variance. The journal posts only the rest.
        BigDecimal scrapRelieved = estimate != null ? scale(fgCostService.scrapRelieved(productionOrderId)) : BigDecimal.ZERO.setScale(4);
        BigDecimal journalAmount = total.subtract(scrapRelieved);

        BigDecimal standardHours = standardHoursPerLot.multiply(goodQty).divide(lot, 6, RoundingMode.HALF_UP);
        BigDecimal efficiencyVariance = actualHours.subtract(standardHours).multiply(labourRate);
        if (estimate == null) {
            priceVariance = BigDecimal.ZERO;
            quantityVariance = BigDecimal.ZERO;
            efficiencyVariance = BigDecimal.ZERO;
        }
        BigDecimal other = total.subtract(scale(priceVariance)).subtract(scale(quantityVariance)).subtract(scale(efficiencyVariance));

        ProductionOrderVariance v = existing != null ? existing : new ProductionOrderVariance();
        v.setProductionOrderId(productionOrderId);
        v.setProductId(order.getProductId());
        v.setEstimateId(estimate != null ? estimate.getId() : null);
        v.setGoodQty(goodQty);
        v.setStandardCost(standardCost);
        v.setActualMaterialCost(scale(actualMaterial));
        v.setActualLabourCost(scale(actualLabour));
        v.setActualMachineCost(scale(actualMachine));
        v.setActualOverheadCost(scale(actualOverhead));
        v.setActualCost(actualCost);
        v.setPriceVariance(scale(priceVariance));
        v.setQuantityVariance(scale(quantityVariance));
        v.setEfficiencyVariance(scale(efficiencyVariance));
        v.setOtherVariance(scale(other));
        v.setScrapRelieved(scrapRelieved);
        v.setTotalVariance(total);
        v.setCalculatedAt(Instant.now());
        v.setCalculatedBy(currentUserId() != null ? currentUserId() : order.getClosedBy());

        if (estimate == null) {
            v.setStatus("NO_ESTIMATE");
        } else if (journalAmount.abs().compareTo(MIN_POSTING_AMOUNT) < 0) {
            v.setStatus("NO_VARIANCE");
        } else {
            JournalHeaderDto journal = postVarianceJournal(order, journalAmount, cc);
            v.setStatus("POSTED");
            v.setJournalHeaderId(journal.id());
            auditService.record("PRODUCTION_VARIANCE_POST", "ProductionOrder", String.valueOf(productionOrderId),
                    null, journalAmount.toPlainString(), "journal=" + journal.journalNumber());
        }
        return toDto(varianceRepo.save(v));
    }

    @Transactional(readOnly = true)
    public VarianceDto findByOrder(Long productionOrderId) {
        return toDto(varianceRepo.findByProductionOrderId(productionOrderId)
                .orElseThrow(() -> new NotFoundException("ProductionOrderVariance", productionOrderId)));
    }

    @Transactional(readOnly = true)
    public Page<VarianceDto> search(Pageable pageable) {
        return varianceRepo.findAll(pageable).map(this::toDto);
    }

    private JournalHeaderDto postVarianceJournal(ProductionOrder order, BigDecimal total, CostCentre cc) {
        Long wip = accountId(ACCOUNT_WIP);
        Long debit;
        Long credit;
        if (total.signum() > 0) {
            debit = accountId(ACCOUNT_VARIANCE_EXPENSE);
            credit = wip;
        } else {
            debit = wip;
            credit = accountId(ACCOUNT_VARIANCE_INCOME);
        }
        BigDecimal amount = total.abs();
        Long costCentreId = cc != null ? cc.getId() : null;
        int profitCentre = settings.getInt("controlling.default_profit_centre_id", 0);
        Long profitCentreId = profitCentre > 0 ? (long) profitCentre : null;
        String description = "Production variance " + order.getOrderNumber();

        return journalService.postAutoJournal(new AutoJournalRequest(
                "AUTO", "CONTROLLING", "PRODUCTION_VARIANCE", order.getId(),
                LocalDate.now(), description, order.getClosedBy(),
                List.of(new JournalLineRequest(debit, amount, BigDecimal.ZERO, description, costCentreId, profitCentreId),
                        new JournalLineRequest(credit, BigDecimal.ZERO, amount, description, costCentreId, profitCentreId))));
    }

    private Long accountId(String code) {
        return glAccountRepo.findByAccountCode(code)
                .orElseThrow(() -> new NotFoundException("GlAccount", code)).getId();
    }

    private CostCentre productionCostCentre() {
        int id = settings.getInt(ProductCostingService.SETTING_PRODUCTION_COST_CENTRE, 0);
        return id > 0 ? costCentreRepo.findById((long) id).orElse(null) : null;
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP);
    }

    private VarianceDto toDto(ProductionOrderVariance v) {
        ProductionOrder order = orderRepo.findById(v.getProductionOrderId()).orElse(null);
        Product product = productRepo.findById(v.getProductId()).orElse(null);
        BigDecimal pct = v.getStandardCost().signum() > 0
                ? v.getTotalVariance().multiply(HUNDRED).divide(v.getStandardCost(), 2, RoundingMode.HALF_UP)
                : null;
        return new VarianceDto(v.getId(), v.getProductionOrderId(),
                order != null ? order.getOrderNumber() : null,
                v.getProductId(),
                product != null ? product.getProductCode() : null,
                product != null ? product.getName() : null,
                v.getEstimateId(), v.getGoodQty(), v.getStandardCost(),
                v.getActualMaterialCost(), v.getActualLabourCost(), v.getActualMachineCost(),
                v.getActualOverheadCost(), v.getActualCost(), v.getPriceVariance(), v.getQuantityVariance(),
                v.getEfficiencyVariance(), v.getOtherVariance(), v.getTotalVariance(), pct,
                v.getStatus(), v.getJournalHeaderId(), v.getCalculatedAt());
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}
