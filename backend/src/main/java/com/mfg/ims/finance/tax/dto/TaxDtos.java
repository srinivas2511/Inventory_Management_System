package com.mfg.ims.finance.tax.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class TaxDtos {

    private TaxDtos() {}

    /** One tax line of a document, as shown on an invoice. */
    public record TaxLineDto(String taxCode, String taxType, BigDecimal ratePct, BigDecimal taxableAmount,
                             BigDecimal taxAmount) {}

    /** The outcome of a tax determination: what a document would be taxed, and why. */
    public record DeterminationDto(String slabCode, String supplyType, String companyStateCode,
                                   String partnerStateCode, boolean partnerStateAssumed, BigDecimal taxableAmount,
                                   BigDecimal taxTotal, List<TaxLineDto> lines) {}

    public record SlabComponentDto(String supplyType, String taxCode, String taxType, BigDecimal ratePct) {}

    public record SlabDto(String slabCode, String description, List<SlabComponentDto> components) {}

    public record ProductSlabDto(Long productId, String slabCode) {}

    public record ProductSlabRequest(@Size(max = 15) String slabCode) {}
}
