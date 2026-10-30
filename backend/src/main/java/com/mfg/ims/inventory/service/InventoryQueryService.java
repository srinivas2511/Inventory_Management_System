package com.mfg.ims.inventory.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.enterprise.scope.PlantScope;
import com.mfg.ims.inventory.domain.InventoryBalance;
import com.mfg.ims.inventory.domain.InventoryTransaction;
import com.mfg.ims.inventory.dto.BelowMinimumDto;
import com.mfg.ims.inventory.dto.InventoryBalanceDto;
import com.mfg.ims.inventory.dto.InventoryTransactionDto;
import com.mfg.ims.inventory.dto.PostingResult;
import com.mfg.ims.inventory.dto.TransferCommand;
import com.mfg.ims.inventory.dto.TransferRequest;
import com.mfg.ims.inventory.dto.ValuationLineDto;
import com.mfg.ims.inventory.dto.ValuationReportDto;
import com.mfg.ims.inventory.repository.InventoryBalanceRepository;
import com.mfg.ims.inventory.repository.InventoryTransactionRepository;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.product.repository.ProductRepository;
import com.mfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.mfg.ims.warehouse.repository.WarehouseRepository;

@Service
public class InventoryQueryService {

    private final InventoryBalanceRepository balanceRepo;
    private final InventoryTransactionRepository txRepo;
    private final InventoryPostingService postingService;
    private final WarehouseRepository warehouseRepo;
    private final WarehouseLocationRepository locationRepo;
    private final PlantScope plantScope;
    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;
    private final SystemSettingService settingService;

    public InventoryQueryService(InventoryBalanceRepository balanceRepo,
                                  InventoryTransactionRepository txRepo,
                                  InventoryPostingService postingService,
                                  WarehouseRepository warehouseRepo,
                                  WarehouseLocationRepository locationRepo,
                                  PlantScope plantScope,
                                  MaterialRepository materialRepo,
                                  ProductRepository productRepo,
                                  SystemSettingService settingService) {
        this.plantScope = plantScope;
        this.balanceRepo = balanceRepo;
        this.txRepo = txRepo;
        this.postingService = postingService;
        this.warehouseRepo = warehouseRepo;
        this.locationRepo = locationRepo;
        this.materialRepo = materialRepo;
        this.productRepo = productRepo;
        this.settingService = settingService;
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
        List<InventoryBalance> filtered = results.stream()
                .filter(b -> !wh.scoped() || wh.plantIds().contains(b.getWarehouseId()))
                .filter(b -> stockStatus == null || stockStatus.equals(b.getStockStatus()))
                .filter(b -> warehouseId == null || warehouseId.equals(b.getWarehouseId()))
                .toList();

        Map<Long, String> warehouseNames = warehouseRepo.findAll().stream()
                .collect(Collectors.toMap(w -> w.getId(), w -> w.getName()));
        Map<Long, String> locationNames = locationRepo.findAll().stream()
                .collect(Collectors.toMap(l -> l.getId(), l -> l.getName()));

        return filtered.stream()
                .map(b -> InventoryBalanceDto.from(b,
                        warehouseNames.getOrDefault(b.getWarehouseId(), String.valueOf(b.getWarehouseId())),
                        locationNames.getOrDefault(b.getLocationId(), String.valueOf(b.getLocationId()))))
                .toList();
    }

    /** Per-item valuation report with cost method context (INV-010, RPT-002). */
    @Transactional(readOnly = true)
    public ValuationReportDto valuationReport(Long warehouseId) {
        String costMethod = settingService.get("enterprise.costing_method", "MOVING_AVERAGE").toUpperCase();
        PlantScope.Filter wh = plantScope.warehouseFilter();

        List<InventoryBalance> balances = (warehouseId != null
                ? balanceRepo.findByWarehouseId(warehouseId)
                : balanceRepo.findAll()).stream()
                .filter(b -> !wh.scoped() || wh.plantIds().contains(b.getWarehouseId()))
                .filter(b -> b.getQtyOnHand().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        Set<Long> matIds = balances.stream()
                .filter(b -> "MATERIAL".equals(b.getItemType()) && b.getMaterialId() != null)
                .map(InventoryBalance::getMaterialId).collect(Collectors.toSet());
        Set<Long> prodIds = balances.stream()
                .filter(b -> "PRODUCT".equals(b.getItemType()) && b.getProductId() != null)
                .map(InventoryBalance::getProductId).collect(Collectors.toSet());

        Map<Long, Material> materials = matIds.isEmpty() ? Map.of()
                : materialRepo.findAllById(matIds).stream().collect(Collectors.toMap(Material::getId, m -> m));
        Map<Long, Product> products = prodIds.isEmpty() ? Map.of()
                : productRepo.findAllById(prodIds).stream().collect(Collectors.toMap(Product::getId, p -> p));

        List<ValuationLineDto> lines = balances.stream()
                .map(b -> {
                    if ("MATERIAL".equals(b.getItemType())) {
                        Material m = materials.get(b.getMaterialId());
                        if (m == null) return null;
                        return new ValuationLineDto("MATERIAL", m.getId(), m.getMaterialCode(), m.getName(), m.getUom(),
                                b.getQtyOnHand(), b.getAvgUnitCost(),
                                m.getStandardCost() != null ? m.getStandardCost() : BigDecimal.ZERO,
                                b.getQtyOnHand().multiply(b.getAvgUnitCost()));
                    } else {
                        Product p = products.get(b.getProductId());
                        if (p == null) return null;
                        return new ValuationLineDto("PRODUCT", p.getId(), p.getProductCode(), p.getName(), p.getUom(),
                                b.getQtyOnHand(), b.getAvgUnitCost(),
                                p.getStandardCost() != null ? p.getStandardCost() : BigDecimal.ZERO,
                                b.getQtyOnHand().multiply(b.getAvgUnitCost()));
                    }
                })
                .filter(l -> l != null)
                .sorted(Comparator.comparing(ValuationLineDto::itemType).thenComparing(ValuationLineDto::itemCode))
                .toList();

        BigDecimal rmTotal = lines.stream().filter(l -> "MATERIAL".equals(l.itemType()))
                .map(ValuationLineDto::totalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fgTotal = lines.stream().filter(l -> "PRODUCT".equals(l.itemType()))
                .map(ValuationLineDto::totalValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ValuationReportDto(costMethod, lines, rmTotal, fgTotal, rmTotal.add(fgTotal));
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

    /** Returns materials whose total AVAILABLE on-hand is below min_stock. */
    @Transactional(readOnly = true)
    public List<BelowMinimumDto> belowMinimum() {
        List<Object[]> rows = balanceRepo.findBelowMinimum();
        return rows.stream().map(r -> {
            Long matId = ((Number) r[0]).longValue();
            String code = (String) r[1];
            String name = (String) r[2];
            BigDecimal minStock = r[3] != null ? new BigDecimal(r[3].toString()) : BigDecimal.ZERO;
            String uom = (String) r[4];
            BigDecimal onHand = r[5] != null ? new BigDecimal(r[5].toString()) : BigDecimal.ZERO;
            BigDecimal shortage = minStock.subtract(onHand);
            return new BelowMinimumDto(matId, code, name, minStock, onHand, uom, shortage);
        }).collect(Collectors.toList());
    }

    /** Full movement history (stock card) for one material, newest first. */
    @Transactional(readOnly = true)
    public Page<InventoryTransactionDto> stockCard(Long materialId, Pageable pageable) {
        PlantScope.Filter wh = plantScope.warehouseFilter();
        return txRepo.searchInWarehouses(materialId, null, null, null, null, null,
                        wh.scoped(), wh.plantIds(), pageable)
                .map(InventoryTransactionDto::from);
    }

    /** Stock card with running balance for a material, filtered by date range (gap 13.6). */
    @Transactional(readOnly = true)
    public com.mfg.ims.inventory.dto.StockCardDto stockCardWithBalance(Long materialId,
                                                                        java.time.LocalDate fromDate,
                                                                        java.time.LocalDate toDate) {
        Material mat = materialRepo.findById(materialId)
                .orElseThrow(() -> new NotFoundException("Material not found: " + materialId));
        Instant from = fromDate != null ? fromDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant to   = toDate   != null ? toDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : Instant.now().plusSeconds(86400);

        PlantScope.Filter wh = plantScope.warehouseFilter();
        var txs = txRepo.searchInWarehouses(materialId, null, null, null, from, to,
                        wh.scoped(), wh.plantIds(),
                        org.springframework.data.domain.PageRequest.of(0, 2000,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.ASC, "transactionDate")))
                .getContent();

        java.util.List<com.mfg.ims.inventory.dto.StockCardDto.StockCardLineDto> lines = new java.util.ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        for (InventoryTransaction tx : txs) {
            String whCode = warehouseRepo.findById(tx.getWarehouseId())
                    .map(w -> w.getWarehouseCode()).orElse(null);
            BigDecimal qty = tx.getQuantity();
            BigDecimal inQty  = qty.compareTo(BigDecimal.ZERO) > 0 ? qty : BigDecimal.ZERO;
            BigDecimal outQty = qty.compareTo(BigDecimal.ZERO) < 0 ? qty.abs() : BigDecimal.ZERO;
            balance = balance.add(qty);
            lines.add(new com.mfg.ims.inventory.dto.StockCardDto.StockCardLineDto(
                    tx.getTransactionDate(), tx.getReferenceNumber(), tx.getTransactionType(),
                    inQty, outQty, balance, whCode));
        }
        return new com.mfg.ims.inventory.dto.StockCardDto(mat.getId(), mat.getMaterialCode(), mat.getName(), lines);
    }
}
