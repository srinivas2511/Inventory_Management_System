package com.mfg.ims.inventory.dto;

import java.util.List;

/**
 * Port (DIP): lets the bulk importer post opening stock without the integration package depending on
 * inventory.service (which ArchUnit forbids). Implemented by OpeningStockPostingAdapter in inventory/service.
 */
public interface OpeningStockPostingPort {
    void post(List<OpeningBalanceLine> lines);
}
