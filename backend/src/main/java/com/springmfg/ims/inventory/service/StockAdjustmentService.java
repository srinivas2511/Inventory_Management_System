package com.springmfg.ims.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.approval.event.ApprovalCompletedEvent;
import com.springmfg.ims.approval.service.ApprovalService;
import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.domain.InventoryBalance;
import com.springmfg.ims.inventory.domain.StockAdjustment;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.dto.StockAdjustmentDto;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;
import com.springmfg.ims.inventory.repository.StockAdjustmentRepository;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.product.repository.ProductRepository;

/**
 * Adjustment workflow: REQUESTED → SUPERVISOR_REVIEW → MANAGER_APPROVAL → [ADMIN_APPROVAL] → POSTED.
 * Adjustments above the configurable threshold (₹10,000) require an additional Admin step (DESIGN §4.5).
 */
@Service
public class StockAdjustmentService {

    private static final String DOC_TYPE = "STOCK_ADJUSTMENT";

    private final StockAdjustmentRepository adjRepo;
    private final InventoryBalanceRepository balanceRepo;
    private final InventoryPostingService postingService;
    private final ApprovalService approvalService;
    private final DocumentNumberService numberService;
    private final SystemSettingService settingService;
    private final AuditService auditService;
    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;

    public StockAdjustmentService(StockAdjustmentRepository adjRepo,
                                   InventoryBalanceRepository balanceRepo,
                                   InventoryPostingService postingService,
                                   ApprovalService approvalService,
                                   DocumentNumberService numberService,
                                   SystemSettingService settingService,
                                   AuditService auditService,
                                   MaterialRepository materialRepo,
                                   ProductRepository productRepo) {
        this.adjRepo = adjRepo;
        this.balanceRepo = balanceRepo;
        this.postingService = postingService;
        this.approvalService = approvalService;
        this.numberService = numberService;
        this.settingService = settingService;
        this.auditService = auditService;
        this.materialRepo = materialRepo;
        this.productRepo = productRepo;
    }

    @Transactional
    public StockAdjustmentDto request(String itemType, Long materialId, Long productId,
                                       Long batchId, Long warehouseId, Long locationId,
                                       String stockStatus, BigDecimal countedQty, String reason) {
        // The system quantity is what the ledger holds for what was counted: the named batch, or, when the count names
        // no batch, every batch of the item at that location and status (balances are kept per batch).
        List<InventoryBalance> counted = balancesCovered(itemType, materialId, productId, batchId,
                warehouseId, locationId, stockStatus);
        BigDecimal systemQty = counted.stream().map(InventoryBalance::getQtyOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (batchId == null && countedQty.compareTo(systemQty) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The count is " + countedQty.subtract(systemQty).stripTrailingZeros().toPlainString()
                            + " above the system quantity of " + systemQty.stripTrailingZeros().toPlainString()
                            + ". Stock can only be added to a named batch: select the batch to adjust.");
        }

        StockAdjustment adj = new StockAdjustment();
        adj.setAdjustmentNumber(numberService.next("ADJ"));
        adj.setItemType(itemType);
        adj.setMaterialId(materialId);
        adj.setProductId(productId);
        adj.setBatchId(batchId);
        adj.setWarehouseId(warehouseId);
        adj.setLocationId(locationId);
        adj.setStockStatus(stockStatus);
        adj.setSystemQty(systemQty);
        adj.setCountedQty(countedQty);
        adj.setReason(reason);
        adj.setStatus("REQUESTED");
        adj.setRequestedBy(currentUserId());
        adjRepo.save(adj);

        // Value of the variance at the weighted average cost of the stock counted (the item's standard cost when the
        // ledger holds nothing to average), so the Admin-approval threshold is decided on the real money at stake.
        BigDecimal varianceQty = adj.varianceQty();
        BigDecimal unitCost = unitCostOf(counted, itemType, materialId, productId);
        adj.setVarianceValue(varianceQty.abs().multiply(unitCost).setScale(4, RoundingMode.HALF_UP));
        adjRepo.save(adj);

        // Build approval steps
        BigDecimal threshold = settingService.getDecimal("inventory.adjustment.admin_threshold",
                new BigDecimal("10000"));
        List<ApprovalService.StepDefinition> steps = new ArrayList<>();
        steps.add(new ApprovalService.StepDefinition(1, "ADJUST_REVIEW"));
        steps.add(new ApprovalService.StepDefinition(2, "ADJUST_APPROVE"));
        if (adj.getVarianceValue() != null && adj.getVarianceValue().compareTo(threshold) > 0) {
            steps.add(new ApprovalService.StepDefinition(3, "ADJUST_APPROVE_ADMIN"));
        }
        approvalService.requestApproval(DOC_TYPE, adj.getId(), adj.getRequestedBy(), steps);

        auditService.record("ADJ_REQUESTED", "STOCK_ADJUSTMENT", String.valueOf(adj.getId()),
                String.valueOf(systemQty), String.valueOf(countedQty), reason);

        return StockAdjustmentDto.from(adj);
    }

    @Transactional
    public void approveStep(Long approvalRequestId, String comment) {
        approvalService.approve(approvalRequestId, comment);
    }

    @Transactional
    public void rejectStep(Long approvalRequestId, String comment) {
        StockAdjustment adj = findByApprovalRequest(approvalRequestId);
        approvalService.reject(approvalRequestId, comment);
        adj.setStatus("REJECTED");
        adjRepo.save(adj);
        auditService.record("ADJ_REJECTED", "STOCK_ADJUSTMENT", String.valueOf(adj.getId()),
                adj.getStatus(), "REJECTED", comment);
    }

    /** Called by the approval engine when all steps are approved. */
    @EventListener
    @Transactional
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!DOC_TYPE.equals(event.documentType())) return;
        StockAdjustment adj = adjRepo.findById(event.documentId())
                .orElseThrow(() -> new NotFoundException("Adjustment not found: " + event.documentId()));
        postAdjustment(adj);
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustmentDto> search(String status, Long warehouseId, Pageable pageable) {
        return adjRepo.search(status, warehouseId, pageable).map(StockAdjustmentDto::from);
    }

    @Transactional(readOnly = true)
    public StockAdjustmentDto findById(Long id) {
        return StockAdjustmentDto.from(adjRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Adjustment not found: " + id)));
    }

    private void postAdjustment(StockAdjustment adj) {
        BigDecimal variance = adj.varianceQty();
        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            adj.setStatus("POSTED");
            adjRepo.save(adj);
            return;
        }

        Long firstTransactionId = null;
        if (variance.compareTo(BigDecimal.ZERO) > 0) {
            // A gain is added to the named batch (request() refuses a gain without one).
            firstTransactionId = postLeg("STOCK_ADJUSTMENT", adj, adj.getBatchId(), variance);
        } else {
            // A loss is taken from the named batch, or, for a count that named none, from the batches at the location
            // oldest first, never more than each can give up once reservations are respected.
            BigDecimal remaining = variance.abs();
            for (InventoryBalance b : balancesCovered(adj.getItemType(), adj.getMaterialId(), adj.getProductId(),
                    adj.getBatchId(), adj.getWarehouseId(), adj.getLocationId(), adj.getStockStatus())) {
                if (remaining.signum() == 0) break;
                BigDecimal take = remaining.min(b.available());
                if (take.signum() <= 0) continue;
                Long batch = "MATERIAL".equals(adj.getItemType()) ? b.getMaterialBatchId() : b.getFgBatchId();
                Long txId = postLeg("STOCK_ADJUSTMENT_OUT", adj, batch, take);
                if (firstTransactionId == null) firstTransactionId = txId;
                remaining = remaining.subtract(take);
            }
            if (remaining.signum() > 0) {
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                        "Adjustment " + adj.getAdjustmentNumber() + " needs " + variance.abs().toPlainString()
                                + " but " + remaining.toPlainString() + " of it is reserved or no longer on hand.");
            }
        }
        adj.setPostedTransactionId(firstTransactionId);
        adj.setStatus("POSTED");
        adjRepo.save(adj);

        auditService.record("ADJ_POSTED", "STOCK_ADJUSTMENT", String.valueOf(adj.getId()),
                String.valueOf(adj.getSystemQty()), String.valueOf(adj.getCountedQty()), adj.getReason());
    }

    private List<InventoryBalance> balancesCovered(String itemType, Long materialId, Long productId, Long batchId,
                                                   Long warehouseId, Long locationId, String stockStatus) {
        if (batchId != null) {
            return balanceRepo.findBalance(warehouseId, locationId, itemType, materialId, productId,
                            "MATERIAL".equals(itemType) ? batchId : null,
                            "PRODUCT".equals(itemType) ? batchId : null, stockStatus)
                    .map(List::of).orElse(List.of());
        }
        return balanceRepo.findAllForItemAtLocation(warehouseId, locationId, itemType, materialId, productId, stockStatus);
    }

    /** Weighted average cost of the counted stock; the item's standard cost when there is nothing to average. */
    private BigDecimal unitCostOf(List<InventoryBalance> counted, String itemType, Long materialId, Long productId) {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        for (InventoryBalance b : counted) {
            if (b.getQtyOnHand().signum() > 0) {
                qty = qty.add(b.getQtyOnHand());
                value = value.add(b.getQtyOnHand().multiply(b.getAvgUnitCost()));
            }
        }
        if (qty.signum() > 0 && value.signum() > 0) {
            return value.divide(qty, 4, RoundingMode.HALF_UP);
        }
        BigDecimal standard = "MATERIAL".equals(itemType)
                ? materialRepo.findById(materialId).map(m -> m.getStandardCost()).orElse(null)
                : productRepo.findById(productId).map(p -> p.getStandardCost()).orElse(null);
        return standard == null ? BigDecimal.ZERO : standard;
    }

    private Long postLeg(String transactionType, StockAdjustment adj, Long batchId, BigDecimal quantity) {
        var result = postingService.post(new PostingCommand(
                transactionType, adj.getItemType(), adj.getMaterialId(), adj.getProductId(),
                "MATERIAL".equals(adj.getItemType()) ? batchId : null,
                "PRODUCT".equals(adj.getItemType()) ? batchId : null,
                adj.getWarehouseId(), adj.getLocationId(), adj.getStockStatus(),
                quantity, null, null,
                "STOCK_ADJUSTMENT", adj.getId(), adj.getAdjustmentNumber(), adj.getReason()));
        return result.transactionId();
    }

    private StockAdjustment findByApprovalRequest(Long approvalRequestId) {
        var ar = approvalService.getById(approvalRequestId);
        if (!DOC_TYPE.equals(ar.getDocumentType())) {
            throw new NotFoundException("Approval request " + approvalRequestId + " is not for a stock adjustment");
        }
        return adjRepo.findById(ar.getDocumentId())
                .orElseThrow(() -> new NotFoundException("Adjustment not found: " + ar.getDocumentId()));
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
