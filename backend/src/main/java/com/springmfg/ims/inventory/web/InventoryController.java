package com.springmfg.ims.inventory.web;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import com.springmfg.ims.inventory.dto.InventoryBalanceDto;
import com.springmfg.ims.inventory.dto.InventoryTransactionDto;
import com.springmfg.ims.inventory.dto.MaterialBatchDto;
import com.springmfg.ims.inventory.dto.OpeningBalanceLine;
import com.springmfg.ims.inventory.dto.PostingResult;
import com.springmfg.ims.inventory.dto.TransferRequest;
import com.springmfg.ims.inventory.service.BatchService;
import com.springmfg.ims.inventory.service.InventoryQueryService;
import com.springmfg.ims.inventory.service.OpeningBalanceService;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryQueryService queryService;
    private final OpeningBalanceService openingBalanceService;
    private final BatchService batchService;

    public InventoryController(InventoryQueryService queryService,
                                OpeningBalanceService openingBalanceService,
                                BatchService batchService) {
        this.queryService = queryService;
        this.openingBalanceService = openingBalanceService;
        this.batchService = batchService;
    }

    // ── Stock balances ────────────────────────────────────────────────────────

    @GetMapping("/balances")
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','INVENTORY_VIEW_ALL')")
    public List<InventoryBalanceDto> balances(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String stockStatus) {
        return queryService.queryBalances(warehouseId, materialId, productId, stockStatus);
    }

    @GetMapping("/balances/valuation")
    @PreAuthorize("hasAuthority('VALUATION_VIEW')")
    public Map<String, java.math.BigDecimal> valuation(@RequestParam(required = false) Long warehouseId) {
        return queryService.valuation(warehouseId);
    }

    // ── Ledger ────────────────────────────────────────────────────────────────

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','INVENTORY_VIEW_ALL')")
    public Page<InventoryTransactionDto> ledger(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {
        return queryService.ledger(materialId, productId, warehouseId, transactionType, from, to, pageable);
    }

    @GetMapping("/ledger/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public InventoryTransactionDto getTransaction(@PathVariable Long id) {
        return queryService.getTransaction(id);
    }

    // ── Batches ───────────────────────────────────────────────────────────────

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public Page<MaterialBatchDto> batches(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return batchService.searchMaterialBatches(materialId, status, pageable);
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public MaterialBatchDto getBatch(@PathVariable Long id) {
        return batchService.findBatchById(id);
    }

    // ── Opening balances ──────────────────────────────────────────────────────

    @PostMapping("/opening-balances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    public List<PostingResult> loadOpeningBalances(@Valid @RequestBody List<OpeningBalanceLine> lines) {
        return openingBalanceService.loadOpeningBalances(lines);
    }

    // ── Stock transfer ────────────────────────────────────────────────────────

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public List<PostingResult> transfer(@Valid @RequestBody TransferRequest req) {
        return queryService.transfer(req);
    }

    // ── Reversal ──────────────────────────────────────────────────────────────

    @PostMapping("/ledger/{id}/reverse")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    public PostingResult reverse(@PathVariable Long id, @RequestParam String reason) {
        return queryService.reverse(id, reason);
    }
}
