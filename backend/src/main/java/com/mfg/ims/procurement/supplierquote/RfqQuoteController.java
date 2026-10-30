package com.mfg.ims.procurement.supplierquote;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nested resource controller: supplier quotes scoped to an RFQ.
 * Base path: /api/procurement/rfqs/{rfqId}/quotes
 */
@RestController
@RequestMapping("/api/procurement/rfqs/{rfqId}/quotes")
public class RfqQuoteController {

    private final SupplierQuoteService service;

    public RfqQuoteController(SupplierQuoteService service) {
        this.service = service;
    }

    /** Record a supplier quote for the given RFQ. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RFQ_UPDATE')")
    public SupplierQuoteDto recordQuote(
            @PathVariable Long rfqId,
            @Valid @RequestBody SaveSupplierQuoteRequest req) {
        // Override the rfqId with the path variable to avoid mismatch
        var adjusted = new SaveSupplierQuoteRequest(
                req.docNo(), rfqId, req.supplierId(), req.validUntil(),
                req.leadTimeDays(), req.freight(), req.notes(), req.items());
        return service.create(adjusted);
    }

    /** List all supplier quotes for the given RFQ. */
    @GetMapping
    @PreAuthorize("hasAuthority('RFQ_VIEW')")
    public List<SupplierQuoteDto> listQuotes(@PathVariable Long rfqId) {
        return service.listByRfq(rfqId);
    }

    /** Award a quote — creates a PurchaseOrder draft from the winning quote. */
    @PostMapping("/{quoteId}/award")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RFQ_AWARD')")
    public AwardResultDto award(@PathVariable Long rfqId, @PathVariable Long quoteId) {
        return service.award(rfqId, quoteId);
    }
}
