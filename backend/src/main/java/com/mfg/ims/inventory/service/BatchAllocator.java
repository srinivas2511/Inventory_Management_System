package com.mfg.ims.inventory.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.inventory.domain.InventoryBalance;
import com.mfg.ims.inventory.domain.MaterialBatch;
import com.mfg.ims.inventory.repository.InventoryBalanceRepository;
import com.mfg.ims.inventory.repository.MaterialBatchRepository;

/**
 * FEFO/FIFO batch allocator (DESIGN.md §4.2).
 * Selects batches to cover a required quantity; never writes stock — that is left to InventoryPostingService.
 */
@Component
public class BatchAllocator {

    private final InventoryBalanceRepository balanceRepo;
    private final MaterialBatchRepository batchRepo;

    public BatchAllocator(InventoryBalanceRepository balanceRepo, MaterialBatchRepository batchRepo) {
        this.balanceRepo = balanceRepo;
        this.batchRepo = batchRepo;
    }

    public record Allocation(Long batchId, Long locationId, Long warehouseId, BigDecimal quantity, BigDecimal unitCost) {}

    /**
     * Allocates batches to cover {@code requiredQty} for a material.
     * If {@code explicitBatchId} is set, only that batch is used (validated).
     * Order: FEFO (earliest expiry first), then FIFO (earliest received first).
     * Throws {@link BusinessRuleException} with INSUFFICIENT_STOCK if not enough available.
     */
    @Transactional(readOnly = true)
    public List<Allocation> allocate(Long materialId, BigDecimal requiredQty,
                                     Long warehouseId, Long explicitBatchId) {
        if (explicitBatchId != null) {
            return allocateExplicit(explicitBatchId, requiredQty, warehouseId);
        }
        return allocateAuto(materialId, requiredQty, warehouseId);
    }

    private List<Allocation> allocateExplicit(Long batchId, BigDecimal requiredQty, Long warehouseId) {
        MaterialBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

        if (!"AVAILABLE".equals(batch.getStatus())) {
            throw new BusinessRuleException(ErrorCode.BATCH_NOT_AVAILABLE,
                    "Batch " + batch.getBatchNumber() + " status is " + batch.getStatus());
        }

        List<InventoryBalance> balances = balanceRepo.findAvailableByMaterialAndWarehouse(batch.getMaterial().getId(), warehouseId)
                .stream()
                .filter(b -> batchId.equals(b.getMaterialBatchId()))
                .toList();

        BigDecimal available = balances.stream()
                .map(InventoryBalance::available)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (available.compareTo(requiredQty) < 0) {
            throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                    "Batch " + batch.getBatchNumber() + ": available " + available + ", requested " + requiredQty);
        }

        BigDecimal remaining = requiredQty;
        List<Allocation> result = new ArrayList<>();
        for (InventoryBalance b : balances) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal take = b.available().min(remaining);
            result.add(new Allocation(batchId, b.getLocationId(), b.getWarehouseId(), take, b.getAvgUnitCost()));
            remaining = remaining.subtract(take);
        }
        return result;
    }

    private List<Allocation> allocateAuto(Long materialId, BigDecimal requiredQty, Long warehouseId) {
        // FEFO/FIFO sorted by the DB query
        List<MaterialBatch> candidates = batchRepo.findAvailableByMaterialFefo(materialId);

        BigDecimal remaining = requiredQty;
        List<Allocation> result = new ArrayList<>();
        BigDecimal quarantineQty = BigDecimal.ZERO;

        for (MaterialBatch batch : candidates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            List<InventoryBalance> balances = balanceRepo.findAvailableByMaterialAndWarehouse(materialId, warehouseId)
                    .stream()
                    .filter(b -> batch.getId().equals(b.getMaterialBatchId()))
                    .toList();

            for (InventoryBalance b : balances) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal take = b.available().min(remaining);
                if (take.compareTo(BigDecimal.ZERO) > 0) {
                    result.add(new Allocation(batch.getId(), b.getLocationId(), b.getWarehouseId(), take, b.getAvgUnitCost()));
                    remaining = remaining.subtract(take);
                }
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // Collect quarantine/hold info for error message
            BigDecimal qQty = balanceRepo.sumQtyByMaterialAndStatus(materialId, "QUARANTINE");
            BigDecimal hQty = balanceRepo.sumQtyByMaterialAndStatus(materialId, "HOLD");
            String detail = String.format("Short by %.3f. Quarantine: %.3f, Hold: %.3f",
                    remaining, qQty != null ? qQty : BigDecimal.ZERO, hQty != null ? hQty : BigDecimal.ZERO);
            throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK, detail);
        }

        return result;
    }
}
