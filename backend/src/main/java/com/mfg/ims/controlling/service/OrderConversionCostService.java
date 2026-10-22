package com.mfg.ims.controlling.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.controlling.domain.CostCentre;
import com.mfg.ims.controlling.domain.ProductionOrderConversionCost;
import com.mfg.ims.controlling.repository.CostCentreRepository;
import com.mfg.ims.controlling.repository.ProductionOrderConversionCostRepository;
import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.production.domain.ProductionOrder;
import com.mfg.ims.production.repository.ProductionOperationRepository;
import com.mfg.ims.production.repository.ProductionOrderRepository;
import com.mfg.ims.production.service.ConversionCostLookup;

/**
 * Conversion-cost absorption (DESIGN 16.3 and decisions.md "Conversion cost absorption"). When a production order
 * completes, what its operations cost to run is fixed in the completion transaction (so the finished-goods cost sees
 * it) and, after commit, absorbed into WIP: Dr 1501 WIP / Cr 5303 Conversion Cost Absorbed. The journal is posted
 * once; the variance journal later compares the same actual cost with the standard.
 */
@Service
public class OrderConversionCostService implements ConversionCostLookup {

    static final String ACCOUNT_WIP = "1501";
    static final String ACCOUNT_CONVERSION_ABSORBED = "5303";
    private static final int MAX_REASON = 300;

    private final ProductionOrderConversionCostRepository repo;
    private final ProductionOperationRepository operationRepo;
    private final ProductionOrderRepository orderRepo;
    private final CostCentreRepository costCentreRepo;
    private final SystemSettingService settings;
    private final GlAccountResolver accounts;
    private final JournalService journalService;
    private final AuditService auditService;

    public OrderConversionCostService(ProductionOrderConversionCostRepository repo,
                                      ProductionOperationRepository operationRepo,
                                      ProductionOrderRepository orderRepo,
                                      CostCentreRepository costCentreRepo,
                                      SystemSettingService settings,
                                      GlAccountResolver accounts,
                                      JournalService journalService,
                                      AuditService auditService) {
        this.repo = repo;
        this.operationRepo = operationRepo;
        this.orderRepo = orderRepo;
        this.costCentreRepo = costCentreRepo;
        this.settings = settings;
        this.accounts = accounts;
        this.journalService = journalService;
        this.auditService = auditService;
    }

    /** Fixes the order's conversion cost in the caller's transaction (production's completion) and returns the total. */
    @Override
    @Transactional
    public BigDecimal freezeConversionCost(Long productionOrderId) {
        return freeze(productionOrderId).getTotalCost();
    }

    /** Same, in its own committed transaction, for orders that completed before this release. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureFrozen(Long productionOrderId) {
        freeze(productionOrderId);
    }

    /**
     * Posts the absorption journal once. Returns true when it posted now; false when there was nothing to post (no
     * cost centre rate applies) or it was already posted. A failure rolls this transaction back, so the caller records
     * it with {@link #recordFailure}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean post(Long productionOrderId) {
        freeze(productionOrderId);
        ProductionOrderConversionCost rec = repo.lockByProductionOrderId(productionOrderId)
                .orElseThrow(() -> new NotFoundException("ProductionOrderConversionCost", productionOrderId));
        if (!rec.needsPosting()) {
            return false;
        }
        ProductionOrder order = orderRepo.findById(productionOrderId)
                .orElseThrow(() -> new NotFoundException("ProductionOrder", productionOrderId));
        BigDecimal amount = rec.getTotalCost();
        String description = "Conversion cost absorbed " + order.getOrderNumber();

        JournalHeaderDto journal = journalService.postAutoJournal(new AutoJournalRequest(
                "AUTO", "CONTROLLING", "CONVERSION_ABSORPTION", order.getId(),
                LocalDate.now(), description, currentUserId(),
                // No cost or profit centre on the lines: the credit to 5303 has no matching real expense per cost centre
                // (payroll is not posted by cost centre), so tagging it would show a negative "actual" in the cost
                // centre reports. The order's cost centre is kept on the conversion-cost row instead.
                List.of(new JournalLineRequest(accounts.idOf(ACCOUNT_WIP), amount, BigDecimal.ZERO, description,
                                null, null),
                        new JournalLineRequest(accounts.idOf(ACCOUNT_CONVERSION_ABSORBED), BigDecimal.ZERO, amount,
                                description, null, null))));
        rec.setStatus(ProductionOrderConversionCost.POSTED);
        rec.setJournalHeaderId(journal.id());
        rec.setPostedAt(Instant.now());
        rec.setFailureReason(null);
        repo.save(rec);
        auditService.record("CONVERSION_ABSORPTION_POST", "ProductionOrder", String.valueOf(productionOrderId),
                null, amount.toPlainString(), "journal=" + journal.journalNumber());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long productionOrderId, String reason) {
        repo.findByProductionOrderId(productionOrderId).ifPresent(rec -> {
            if (rec.needsPosting()) {
                rec.setStatus(ProductionOrderConversionCost.FAILED);
                String text = reason == null ? "unknown error" : reason;
                rec.setFailureReason(text.length() <= MAX_REASON ? text : text.substring(0, MAX_REASON - 3) + "...");
                repo.save(rec);
            }
        });
    }

    private ProductionOrderConversionCost freeze(Long productionOrderId) {
        return repo.findByProductionOrderId(productionOrderId).orElseGet(() -> {
            CostCentre cc = productionCostCentre();
            ConversionCostCalculator.Result r = ConversionCostCalculator.compute(
                    operationRepo.findByProductionOrderId(productionOrderId),
                    cc != null ? cc.getLabourRatePerHour() : BigDecimal.ZERO,
                    cc != null ? cc.getMachineRatePerHour() : BigDecimal.ZERO,
                    cc != null ? cc.getOverheadPct() : BigDecimal.ZERO);
            ProductionOrderConversionCost rec = new ProductionOrderConversionCost();
            rec.setProductionOrderId(productionOrderId);
            rec.setCostCentreId(cc != null ? cc.getId() : null);
            rec.setActualHours(r.hours());
            rec.setLabourCost(r.labour());
            rec.setMachineCost(r.machine());
            rec.setOverheadCost(r.overhead());
            rec.setTotalCost(r.total());
            rec.setStatus(r.total().signum() > 0
                    ? ProductionOrderConversionCost.PENDING : ProductionOrderConversionCost.NOT_REQUIRED);
            return repo.save(rec);
        });
    }

    private CostCentre productionCostCentre() {
        int id = settings.getInt(ProductCostingService.SETTING_PRODUCTION_COST_CENTRE, 0);
        return id > 0 ? costCentreRepo.findById((long) id).orElse(null) : null;
    }

    private static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CachedUser u ? u.id() : null;
    }
}
