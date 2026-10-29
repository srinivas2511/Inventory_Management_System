package com.mfg.ims.inventory.scrap;

import java.math.BigDecimal;
import java.time.Instant;

public final class ScrapLotDtos {

    private ScrapLotDtos() {}

    public record ScrapLotDto(
            Long id,
            String lotNumber,
            String sourceType,
            Long sourceId,
            String itemName,
            BigDecimal quantity,
            String uom,
            String scrapReason,
            String status,
            BigDecimal salePrice,
            String buyerName,
            Instant disposedAt,
            String notes,
            Instant createdAt
    ) {}

    public record CreateScrapLotRequest(
            String sourceType,
            Long sourceId,
            String itemName,
            BigDecimal quantity,
            String uom,
            String scrapReason,
            String notes
    ) {}

    public record SellScrapRequest(
            BigDecimal salePrice,
            String buyerName,
            String notes
    ) {}

    public record DisposeScrapRequest(String notes) {}
}
