package com.mfg.ims.inventory.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mfg.ims.inventory.dto.OpeningBalanceLine;
import com.mfg.ims.inventory.dto.OpeningStockPostingPort;

/**
 * Adapter that lets the bulk importer post opening stock via the port interface
 * without creating a forbidden integration→inventory.service dependency (ArchUnit).
 */
@Component
public class OpeningStockPostingAdapter implements OpeningStockPostingPort {

    private final OpeningBalanceService openingBalanceService;

    public OpeningStockPostingAdapter(OpeningBalanceService openingBalanceService) {
        this.openingBalanceService = openingBalanceService;
    }

    @Override
    public void post(List<OpeningBalanceLine> lines) {
        openingBalanceService.loadOpeningBalances(lines);
    }
}
