package com.springmfg.ims.inventory.service;

import java.math.BigDecimal;
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
import com.springmfg.ims.inventory.domain.StockAdjustment;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.dto.StockAdjustmentDto;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;
import com.springmfg.ims.inventory.repository.StockAdjustmentRepository;

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

    public StockAdjustmentService(StockAdjustmentRepository adjRepo,
                                   InventoryBalanceRepository balanceRepo,
                                   InventoryPostingService postingService,
                                   ApprovalService approvalService,
                                   DocumentNumberService numberService,
                                   SystemSettingService settingService,
                                   AuditService auditService) {
        this.adjRepo = adjRepo;
        this.balanceRepo = balanceRepo;
        this.postingService = postingService;
        this.approvalService = approvalService;
        this.numberService = numberService;
        this.settingService = settingService;
        this.auditService = auditService;
    }

    @Transactional
    public StockAdjustmentDto request(String itemType, Long materialId, Long productId,
                                       Long batchId, Long warehouseId, Long locationId,
                                       String stockStatus, BigDecimal countedQty, String reason) {
        // Read current system quantity from the balance
        BigDecimal systemQty = balanceRepo.findBalance(
                        warehouseId, locationId, itemType,
                        materialId, productId,
                        "MATERIAL".equals(itemType) ? batchId : null,
                        "PRODUCT".equals(itemType) ? batchId : null,
                        stockStatus)
                .map(b -> b.getQtyOnHand())
                .orElse(BigDecimal.ZERO);

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

        // Compute variance value
        BigDecimal varianceQty = adj.varianceQty();
        // A rough variance value (qty × avg cost) — will be refined when approved
        BigDecimal avgCost = balanceRepo.findBalance(warehouseId, locationId, itemType,
                        materialId, productId,
                        "MATERIAL".equals(itemType) ? batchId : null,
                        "PRODUCT".equals(itemType) ? batchId : null,
                        stockStatus)
                .map(b -> b.getAvgUnitCost())
                .orElse(BigDecimal.ZERO);
        adj.setVarianceValue(varianceQty.abs().multiply(avgCost));
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

        PostingCommand cmd = new PostingCommand(
                "STOCK_ADJUSTMENT",
                adj.getItemType(), adj.getMaterialId(), adj.getProductId(),
                "MATERIAL".equals(adj.getItemType()) ? adj.getBatchId() : null,
                "PRODUCT".equals(adj.getItemType()) ? adj.getBatchId() : null,
                adj.getWarehouseId(), adj.getLocationId(), adj.getStockStatus(),
                variance.abs(), "KG", null,
                "STOCK_ADJUSTMENT", adj.getId(), adj.getAdjustmentNumber(), adj.getReason());

        // For negative variance we need an out-leg type
        PostingCommand finalCmd = variance.compareTo(BigDecimal.ZERO) < 0
                ? new PostingCommand("STOCK_ADJUSTMENT_OUT",
                        cmd.itemType(), cmd.materialId(), cmd.productId(),
                        cmd.materialBatchId(), cmd.fgBatchId(),
                        cmd.warehouseId(), cmd.locationId(), cmd.stockStatus(),
                        cmd.quantity(), cmd.uom(), cmd.unitCost(),
                        cmd.referenceType(), cmd.referenceId(), cmd.referenceNumber(), cmd.remarks())
                : cmd;

        var result = postingService.post(finalCmd);
        adj.setPostedTransactionId(result.transactionId());
        adj.setStatus("POSTED");
        adjRepo.save(adj);

        auditService.record("ADJ_POSTED", "STOCK_ADJUSTMENT", String.valueOf(adj.getId()),
                String.valueOf(adj.getSystemQty()), String.valueOf(adj.getCountedQty()), adj.getReason());
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
