package com.mfg.ims.inventory.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.enterprise.scope.PlantScope;
import com.mfg.ims.inventory.domain.InventoryBalance;
import com.mfg.ims.inventory.domain.InventoryTransaction;
import com.mfg.ims.inventory.dto.InventoryBalanceDto;
import com.mfg.ims.inventory.dto.InventoryTransactionDto;
import com.mfg.ims.inventory.dto.PostingResult;
import com.mfg.ims.inventory.dto.TransferCommand;
import com.mfg.ims.inventory.dto.TransferRequest;
import com.mfg.ims.inventory.repository.InventoryBalanceRepository;
import com.mfg.ims.inventory.repository.InventoryTransactionRepository;

@Service
public class InventoryQueryService {

    private final InventoryBalanceRepository balanceRepo;
    private final InventoryTransactionRepository txRepo;
    private final InventoryPostingService postingService;

    private final com.mfg.ims.enterprise.scope.PlantScope plantScope;


    public InventoryQueryService(InventoryBalanceRepository balanceRepo,
                                  InventoryTransactionRepository txRepo,
                                  InventoryPostingService postingService,

            com.mfg.ims.enterprise.scope.PlantScope plantScope) {

        this.plantScope = plantScope;
        this.balanceRepo = balanceRepo;
        this.txRepo = txRepo;
        this.postingService = postingService;
    }

    @Transactional(readOnly = true)
    public List<InventoryBalanceDto> queryBalances(Long warehouseId, Long materialId, Long productId, String stockStatus) {
        List<InventoryBalance> results;
        if (materialId != null) {
            results = balanceRepo.findByMaterialId(materialId);
        } else if (productId != null) {
            results = balanceRepo.findByProductId(productId);
        } else if (warehouseId != null) {
            results = balanceRepo.findByWarehouseId(warehouseId);
        } else {
            results = balanceRepo.findAll();
        }

        PlantScope.Filter wh = plantScope.warehouseFilter();
        return results.stream()
                .filter(b -> !wh.scoped() || wh.plantIds().contains(b.getWarehouseId()))
                .filter(b -> stockStatus == null || stockStatus.equals(b.getStockStatus()))
                .filter(b -> warehouseId == null || warehouseId.equals(b.getWarehouseId()))
                .map(InventoryBalanceDto::from)
                .toList();
    }

    /** Available on-hand quantity per material (0 when the material has no available stock). */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> availableQtyByMaterial(java.util.Collection<Long> materialIds) {
        Map<Long, BigDecimal> out = new java.util.HashMap<>();
        for (Long id : materialIds) {
            BigDecimal qty = balanceRepo.sumQtyByMaterialAndStatus(id, "AVAILABLE");
            out.put(id, qty == null ? BigDecimal.ZERO : qty);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> valuation(Long warehouseId) {
        PlantScope.Filter wh = plantScope.warehouseFilter();
        List<InventoryBalance> balances = (warehouseId != null
                ? balanceRepo.findByWarehouseId(warehouseId)
                : balanceRepo.findAll()).stream()
                .filter(b -> !wh.scoped() || wh.plantIds().contains(b.getWarehouseId()))
                .toList();

        BigDecimal totalValue = balances.stream()
                .filter(b -> b.getQtyOnHand().compareTo(BigDecimal.ZERO) > 0)
                .map(b -> b.getQtyOnHand().multiply(b.getAvgUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rmValue = balances.stream()
                .filter(b -> "MATERIAL".equals(b.getItemType()) && "AVAILABLE".equals(b.getStockStatus()))
                .map(b -> b.getQtyOnHand().multiply(b.getAvgUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fgValue = balances.stream()
                .filter(b -> "PRODUCT".equals(b.getItemType()) && "AVAILABLE".equals(b.getStockStatus()))
                .map(b -> b.getQtyOnHand().multiply(b.getAvgUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of("totalValue", totalValue, "rmValue", rmValue, "fgValue", fgValue);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionDto> ledger(Long materialId, Long productId, Long warehouseId,
                                                 String transactionType, Instant from, Instant to,
                                                 Pageable pageable) {
        PlantScope.Filter wh = plantScope.warehouseFilter();
        return txRepo.searchInWarehouses(materialId, productId, warehouseId, transactionType, from, to,
                        wh.scoped(), wh.plantIds(), pageable)
                .map(InventoryTransactionDto::from);
    }

    @Transactional(readOnly = true)
    public InventoryTransactionDto getTransaction(Long id) {
        InventoryTransaction tx = txRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
        if (plantScope.isScoped() && !plantScope.canSee(plantScope.warehousePlantId(tx.getWarehouseId()))) {
            throw new NotFoundException("Transaction not found: " + id);
        }
        return InventoryTransactionDto.from(tx);
    }

    @Transactional
    public List<PostingResult> transfer(TransferRequest req) {
        TransferCommand cmd = new TransferCommand(
                req.itemType(), req.materialId(), req.productId(),
                req.materialBatchId(), req.fgBatchId(),
                req.fromWarehouseId(), req.fromLocationId(), req.fromStockStatus(),
                req.toWarehouseId(), req.toLocationId(), req.toStockStatus(),
                req.quantity(), req.uom(),
                req.referenceType() != null ? req.referenceType() : "MANUAL_TRANSFER",
                req.referenceId() != null ? req.referenceId() : 0L,
                req.referenceNumber(), req.remarks());
        return postingService.transfer(cmd);
    }

    @Transactional
    public PostingResult reverse(Long txId, String reason) {
        return postingService.reverse(txId, reason);
    }
}
