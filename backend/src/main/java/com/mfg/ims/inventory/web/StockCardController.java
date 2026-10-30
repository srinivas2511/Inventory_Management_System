package com.mfg.ims.inventory.web;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.inventory.dto.StockCardDto;
import com.mfg.ims.inventory.service.InventoryQueryService;

/** GET /api/inventory/stock-card/{materialId} — running stock card for a material (13.6). */
@RestController
public class StockCardController {

    private final InventoryQueryService queryService;

    public StockCardController(InventoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/inventory/stock-card/{materialId}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public StockCardDto stockCard(@PathVariable Long materialId,
                                  @RequestParam(required = false) LocalDate fromDate,
                                  @RequestParam(required = false) LocalDate toDate) {
        return queryService.stockCardWithBalance(materialId, fromDate, toDate);
    }
}
