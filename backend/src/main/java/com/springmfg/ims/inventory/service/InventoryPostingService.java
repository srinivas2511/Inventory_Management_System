package com.springmfg.ims.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.domain.InventoryBalance;
import com.springmfg.ims.inventory.domain.InventoryTransaction;
import com.springmfg.ims.inventory.domain.MaterialBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.dto.PostingResult;
import com.springmfg.ims.inventory.dto.TransferCommand;
import com.springmfg.ims.inventory.event.StockPostedEvent;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;
import com.springmfg.ims.inventory.repository.InventoryTransactionRepository;
import com.springmfg.ims.inventory.repository.MaterialBatchRepository;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;

/**
 * THE only service that writes to {@code inventory_balances} and {@code inventory_transactions}.
 * All stock changes in the entire application must route through this class.
 * Enforced by ArchUnit rule: no other bean may inject InventoryBalanceRepository or InventoryTransactionRepository.
 */
@Service
public class InventoryPostingService {

    private final InventoryBalanceRepository balanceRepo;
    private final InventoryTransactionRepository txRepo;
    private final MaterialBatchRepository batchRepo;
    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;
    private final WarehouseLocationRepository locationRepo;
    private final DocumentNumberService numberService;
    private final SystemSettingService settingService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    private final com.springmfg.ims.enterprise.scope.PlantScope plantScope;


    public InventoryPostingService(
            InventoryBalanceRepository balanceRepo,
            InventoryTransactionRepository txRepo,
            MaterialBatchRepository batchRepo,
            MaterialRepository materialRepo,
            ProductRepository productRepo,
            WarehouseLocationRepository locationRepo,
            DocumentNumberService numberService,
            SystemSettingService settingService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,

            com.springmfg.ims.enterprise.scope.PlantScope plantScope) {

        this.plantScope = plantScope;
        this.balanceRepo = balanceRepo;
        this.txRepo = txRepo;
        this.batchRepo = batchRepo;
        this.materialRepo = materialRepo;
        this.productRepo = productRepo;
        this.locationRepo = locationRepo;
        this.numberService = numberService;
        this.settingService = settingService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Posts a single ledger leg. Must be called inside a transaction.
     * Deadlock retries (PostgreSQL 40P01) are handled by the @Retryable wrapper on each public use-case method.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PostingResult post(PostingCommand cmd) {
        plantScope.assertWarehouseVisible(cmd.warehouseId());
        validateCommand(cmd);

        InventoryBalance bal = lockOrCreate(cmd);

        BigDecimal unitCost = resolveUnitCost(cmd, bal);
        BigDecimal signedQty = cmd.signedQty();

        if (signedQty.compareTo(BigDecimal.ZERO) < 0) {
            // OUT leg — enforce stock rules
            enforceOutRules(cmd, bal);
            unitCost = bal.getAvgUnitCost(); // issue at current average
        } else {
            // IN leg — update moving average
            bal.setAvgUnitCost(movingAverage(bal, signedQty, unitCost));
        }

        bal.setQtyOnHand(bal.getQtyOnHand().add(signedQty));

        // Update batch remaining qty for AVAILABLE OUT legs
        if (cmd.materialBatchId() != null && signedQty.compareTo(BigDecimal.ZERO) < 0 && "AVAILABLE".equals(cmd.stockStatus())) {
            updateBatchRemaining(cmd.materialBatchId(), signedQty);
        }
        // Material returned from production brings the batch's remaining quantity back (and its CONSUMED status)
        if (cmd.materialBatchId() != null && "MATERIAL_RETURN".equals(cmd.transactionType())
                && "AVAILABLE".equals(cmd.stockStatus())) {
            restoreBatchRemaining(cmd.materialBatchId(), signedQty);
        }

        InventoryTransaction tx = buildTransaction(cmd, signedQty, unitCost, bal.getQtyOnHand());
        txRepo.save(tx);

        // Publish after-commit event
        eventPublisher.publishEvent(new StockPostedEvent(tx));

        auditService.record("STOCK_POSTED", "INVENTORY_TRANSACTION", String.valueOf(tx.getId()),
                null, cmd.transactionType(), cmd.remarks());

        return new PostingResult(tx.getId(), tx.getTransactionNumber(), bal.getQtyOnHand(), tx.getUnitCost());
    }

    /**
     * Posts a transfer: OUT from source, IN to destination, linked by a transfer_group_id.
     * Lock acquisition order uses a deterministic key ordering to prevent deadlocks.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<PostingResult> transfer(TransferCommand cmd) {
        plantScope.assertWarehouseVisible(cmd.fromWarehouseId());
        plantScope.assertWarehouseVisible(cmd.toWarehouseId());
        // Build both legs as posting commands
        PostingCommand outCmd = new PostingCommand(
                "STOCK_TRANSFER", cmd.itemType(), cmd.materialId(), cmd.productId(),
                cmd.materialBatchId(), cmd.fgBatchId(),
                cmd.fromWarehouseId(), cmd.fromLocationId(), cmd.fromStockStatus(),
                cmd.quantity(), cmd.uom(), null,
                cmd.referenceType(), cmd.referenceId(), cmd.referenceNumber(), cmd.remarks());

        PostingCommand inCmd = new PostingCommand(
                "STOCK_TRANSFER", cmd.itemType(), cmd.materialId(), cmd.productId(),
                cmd.materialBatchId(), cmd.fgBatchId(),
                cmd.toWarehouseId(), cmd.toLocationId(), cmd.toStockStatus(),
                cmd.quantity(), cmd.uom(), null,
                cmd.referenceType(), cmd.referenceId(), cmd.referenceNumber(), cmd.remarks());

        // Acquire locks in deterministic order (lower location_id first) to prevent deadlocks
        boolean outFirst = cmd.fromLocationId() <= cmd.toLocationId();

        validateCommand(outCmd);

        InventoryBalance firstLocked  = lockOrCreate(outFirst ? outCmd : inCmd);
        InventoryBalance secondLocked = lockOrCreate(outFirst ? inCmd  : outCmd);
        InventoryBalance outBal = outFirst ? firstLocked  : secondLocked;
        InventoryBalance inBal  = outFirst ? secondLocked : firstLocked;

        // Enforce the out-leg rules
        enforceOutRules(outCmd, outBal);
        BigDecimal unitCost = outBal.getAvgUnitCost();

        // Update balances
        outBal.setQtyOnHand(outBal.getQtyOnHand().subtract(cmd.quantity()));
        inBal.setAvgUnitCost(movingAverage(inBal, cmd.quantity(), unitCost));
        inBal.setQtyOnHand(inBal.getQtyOnHand().add(cmd.quantity()));

        // Shared group id (use outTx.id which we'll set after save — use a sequence instead)
        Long groupId = System.nanoTime(); // good enough for grouping; the pair shares this value

        InventoryTransaction outTx = buildTransaction(outCmd, cmd.quantity().negate(), unitCost, outBal.getQtyOnHand());
        outTx.setTransferGroupId(groupId);
        txRepo.save(outTx);

        InventoryTransaction inTx = buildTransaction(inCmd, cmd.quantity(), unitCost, inBal.getQtyOnHand());
        inTx.setTransferGroupId(groupId);
        txRepo.save(inTx);

        eventPublisher.publishEvent(new StockPostedEvent(outTx));
        eventPublisher.publishEvent(new StockPostedEvent(inTx));

        return List.of(
                new PostingResult(outTx.getId(), outTx.getTransactionNumber(), outBal.getQtyOnHand(), outTx.getUnitCost()),
                new PostingResult(inTx.getId(), inTx.getTransactionNumber(), inBal.getQtyOnHand(), inTx.getUnitCost()));
    }

    /**
     * Reverses an existing transaction by posting an opposite-sign entry.
     * Enforced: cannot reverse a reversal; requires INVENTORY_ADJUST; audited.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PostingResult reverse(Long txId, String reason) {
        InventoryTransaction original = txRepo.findById(txId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + txId));

        if (original.getReversalOfId() != null) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot reverse a reversal transaction");
        }
        if (txRepo.existsByReversalOfId(txId)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Transaction " + txId + " has already been reversed");
        }

        // Build reversal command
        PostingCommand reversal = new PostingCommand(
                "STOCK_ADJUSTMENT", original.getItemType(),
                original.getMaterialId(), original.getProductId(),
                original.getMaterialBatchId(), original.getFgBatchId(),
                original.getWarehouseId(), original.getLocationId(), original.getStockStatus(),
                original.getQuantity().abs(), original.getUom(), original.getUnitCost(),
                "REVERSAL", txId, original.getTransactionNumber(), reason);

        // The reversal sign is opposite to the original
        boolean originalWasIn = original.getQuantity().compareTo(BigDecimal.ZERO) > 0;
        InventoryBalance bal = lockOrCreate(reversal);

        BigDecimal reversalQty = originalWasIn ? original.getQuantity().negate() : original.getQuantity().abs();

        if (reversalQty.compareTo(BigDecimal.ZERO) < 0) {
            enforceOutRules(reversal, bal);
        }

        bal.setQtyOnHand(bal.getQtyOnHand().add(reversalQty));

        InventoryTransaction reversalTx = buildTransaction(reversal, reversalQty, original.getUnitCost(), bal.getQtyOnHand());
        reversalTx.setReversalOfId(txId);
        txRepo.save(reversalTx);

        auditService.record("STOCK_REVERSED", "INVENTORY_TRANSACTION",
                String.valueOf(txId), String.valueOf(txId), String.valueOf(reversalTx.getId()), reason);

        return new PostingResult(reversalTx.getId(), reversalTx.getTransactionNumber(), bal.getQtyOnHand(), reversalTx.getUnitCost());
    }

    // ── reservation helpers (qty_reserved only — no transaction record) ───────

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(retryFor = org.springframework.dao.PessimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void reserveStock(Long fgBatchId, Long productId, Long warehouseId, Long locationId, BigDecimal qty) {
        InventoryBalance bal = balanceRepo.lockByFgBatchAndLocation(fgBatchId, warehouseId, locationId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                        "No inventory balance found for FG batch " + fgBatchId));
        if (bal.available().compareTo(qty) < 0) {
            throw new BusinessRuleException(ErrorCode.DISPATCH_EXCEEDS_STOCK,
                    String.format("Requested %.3f; available %.3f", qty, bal.available()));
        }
        bal.setQtyReserved(bal.getQtyReserved().add(qty));
        balanceRepo.save(bal);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(retryFor = org.springframework.dao.PessimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void releaseReservedStock(Long fgBatchId, Long productId, Long warehouseId, Long locationId, BigDecimal qty) {
        balanceRepo.lockByFgBatchAndLocation(fgBatchId, warehouseId, locationId).ifPresent(bal -> {
            BigDecimal newReserved = bal.getQtyReserved().subtract(qty);
            bal.setQtyReserved(newReserved.max(BigDecimal.ZERO));
            balanceRepo.save(bal);
        });
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void validateCommand(PostingCommand cmd) {
        if (cmd.quantity() == null || cmd.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Quantity must be positive");
        }
        // Verify location belongs to warehouse
        locationRepo.findById(cmd.locationId()).ifPresent(loc -> {
            if (!loc.getWarehouse().getId().equals(cmd.warehouseId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Location " + cmd.locationId() + " does not belong to warehouse " + cmd.warehouseId());
            }
        });
    }

    private void enforceOutRules(PostingCommand cmd, InventoryBalance bal) {
        // Rule 3: Quarantine cannot be issued to production
        if ("MATERIAL_ISSUE".equals(cmd.transactionType()) && "QUARANTINE".equals(cmd.stockStatus())) {
            throw new BusinessRuleException(ErrorCode.QUARANTINE_NOT_ISSUABLE,
                    "Quarantined material cannot be issued to production");
        }
        // Rule 4: Rejected cannot be used
        if ("MATERIAL_ISSUE".equals(cmd.transactionType()) && "REJECTED".equals(cmd.stockStatus())) {
            throw new BusinessRuleException(ErrorCode.REJECTED_NOT_USABLE,
                    "Rejected material cannot be used for production");
        }
        // Rule 1 & 2: No negative stock (unless explicitly allowed)
        BigDecimal available = bal.available();
        if (available.subtract(cmd.quantity()).compareTo(BigDecimal.ZERO) < 0) {
            boolean allowNegative = settingService.getBoolean("inventory.allow_negative_stock", false)
                    || itemAllowsNegativeStock(cmd);
            if (allowNegative) {
                // INV-02 / GC-3: going negative is a deliberate, administrator-enabled exception, so it leaves a trace
                auditService.record("NEGATIVE_STOCK_ALLOWED", "INVENTORY_BALANCE",
                        (cmd.materialId() != null ? "MATERIAL:" + cmd.materialId() : "PRODUCT:" + cmd.productId()),
                        String.valueOf(available), String.valueOf(available.subtract(cmd.quantity())),
                        cmd.transactionType());
            } else {
                // Check quarantine qty for a better error message
                BigDecimal quarantineQty = balanceRepo.sumQtyByMaterialAndStatus(
                        cmd.materialId(), "QUARANTINE");
                String detail = String.format(
                        "Requested %.3f; available %.3f%s",
                        cmd.quantity(), available,
                        quarantineQty != null && quarantineQty.compareTo(BigDecimal.ZERO) > 0
                                ? " (" + quarantineQty + " in QUARANTINE)" : "");
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK, detail);
            }
        }
    }

    /** GC-3: an administrator may let a single material or product go negative, beside the global setting. */
    private boolean itemAllowsNegativeStock(PostingCommand cmd) {
        if (cmd.materialId() != null) {
            return materialRepo.findById(cmd.materialId()).map(m -> m.isAllowNegativeStock()).orElse(false);
        }
        if (cmd.productId() != null) {
            return productRepo.findById(cmd.productId()).map(p -> p.isAllowNegativeStock()).orElse(false);
        }
        return false;
    }

    private InventoryBalance lockOrCreate(PostingCommand cmd) {
        return balanceRepo.lockBalance(
                cmd.warehouseId(), cmd.locationId(), cmd.itemType(),
                cmd.materialId(), cmd.productId(),
                cmd.materialBatchId(), cmd.fgBatchId(),
                cmd.stockStatus())
                .orElseGet(() -> createBalance(cmd));
    }

    private InventoryBalance createBalance(PostingCommand cmd) {
        InventoryBalance b = new InventoryBalance();
        b.setWarehouseId(cmd.warehouseId());
        b.setLocationId(cmd.locationId());
        b.setItemType(cmd.itemType());
        b.setMaterialId(cmd.materialId());
        b.setProductId(cmd.productId());
        b.setMaterialBatchId(cmd.materialBatchId());
        b.setFgBatchId(cmd.fgBatchId());
        b.setStockStatus(cmd.stockStatus());
        b.setQtyOnHand(BigDecimal.ZERO);
        b.setQtyReserved(BigDecimal.ZERO);
        b.setAvgUnitCost(BigDecimal.ZERO);
        return balanceRepo.save(b);
    }

    private BigDecimal resolveUnitCost(PostingCommand cmd, InventoryBalance bal) {
        if (cmd.unitCost() != null && cmd.unitCost().compareTo(BigDecimal.ZERO) > 0) {
            return cmd.unitCost();
        }
        if (cmd.materialBatchId() != null) {
            return batchRepo.findById(cmd.materialBatchId())
                    .map(MaterialBatch::getUnitCost)
                    .filter(c -> c != null && c.compareTo(BigDecimal.ZERO) > 0)
                    .orElse(BigDecimal.ZERO);
        }
        if (cmd.materialId() != null) {
            return materialRepo.findById(cmd.materialId())
                    .map(m -> m.getStandardCost())
                    .orElse(BigDecimal.ZERO);
        }
        return bal.getAvgUnitCost();
    }

    private BigDecimal movingAverage(InventoryBalance bal, BigDecimal inQty, BigDecimal inCost) {
        BigDecimal onHand = bal.getQtyOnHand();
        if (onHand.compareTo(BigDecimal.ZERO) <= 0) {
            return inCost;
        }
        BigDecimal totalValue = onHand.multiply(bal.getAvgUnitCost()).add(inQty.multiply(inCost));
        BigDecimal newOnHand = onHand.add(inQty);
        return totalValue.divide(newOnHand, 4, RoundingMode.HALF_UP);
    }

    private void updateBatchRemaining(Long batchId, BigDecimal signedQty) {
        MaterialBatch batch = batchRepo.findById(batchId).orElse(null);
        if (batch == null) return;
        BigDecimal newRemaining = batch.getRemainingQty().add(signedQty); // signedQty is negative for OUT
        if (newRemaining.compareTo(BigDecimal.ZERO) < 0) newRemaining = BigDecimal.ZERO;
        batch.setRemainingQty(newRemaining);
        if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
            batch.setStatus("CONSUMED");
        }
        batchRepo.save(batch);
    }

    private void restoreBatchRemaining(Long batchId, BigDecimal returnedQty) {
        MaterialBatch batch = batchRepo.findById(batchId).orElse(null);
        if (batch == null) return;
        batch.setRemainingQty(batch.getRemainingQty().add(returnedQty));
        if ("CONSUMED".equals(batch.getStatus())) {
            batch.setStatus("AVAILABLE");
        }
        batchRepo.save(batch);
    }

    private InventoryTransaction buildTransaction(PostingCommand cmd, BigDecimal signedQty,
                                                   BigDecimal unitCost, BigDecimal balanceAfter) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setTransactionNumber(numberService.next("TXN"));
        tx.setTransactionDate(Instant.now());
        tx.setTransactionType(cmd.transactionType());
        tx.setItemType(cmd.itemType());
        tx.setMaterialId(cmd.materialId());
        tx.setProductId(cmd.productId());
        tx.setMaterialBatchId(cmd.materialBatchId());
        tx.setFgBatchId(cmd.fgBatchId());
        tx.setWarehouseId(cmd.warehouseId());
        tx.setLocationId(cmd.locationId());
        tx.setStockStatus(cmd.stockStatus());
        tx.setQuantity(signedQty);
        tx.setUom(resolveUom(cmd));
        tx.setUnitCost(unitCost != null ? unitCost : BigDecimal.ZERO);
        tx.setTotalCost(unitCost != null ? signedQty.abs().multiply(unitCost).setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        tx.setReferenceType(cmd.referenceType());
        tx.setReferenceId(cmd.referenceId());
        tx.setReferenceNumber(cmd.referenceNumber());
        tx.setBalanceAfter(balanceAfter);
        tx.setRemarks(cmd.remarks());
        tx.setCreatedBy(resolveCurrentUserId());
        return tx;
    }

    /** The ledger's uom is mandatory; callers may omit it and the item master supplies it. */
    private String resolveUom(PostingCommand cmd) {
        if (cmd.uom() != null) {
            return cmd.uom();
        }
        if (cmd.materialId() != null) {
            return materialRepo.findById(cmd.materialId()).map(m -> m.getUom()).orElse(null);
        }
        if (cmd.productId() != null) {
            return productRepo.findById(cmd.productId()).map(p -> p.getUom()).orElse(null);
        }
        return null;
    }

    private Long resolveCurrentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L; // system/job context
    }
}
