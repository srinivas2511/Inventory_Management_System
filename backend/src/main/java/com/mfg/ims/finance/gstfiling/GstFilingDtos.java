package com.mfg.ims.finance.gstfiling;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class GstFilingDtos {

    private GstFilingDtos() {}

    public record B2bInvoice(String customerGstin, String invoiceNo, LocalDate invoiceDate,
                             BigDecimal taxableValue, BigDecimal igst, BigDecimal cgst, BigDecimal sgst, BigDecimal grandTotal) {}

    public record B2cAggregate(String stateCode, BigDecimal taxRate, BigDecimal taxableValue, BigDecimal taxAmount) {}

    public record HsnSummary(String hsnSac, BigDecimal qty, BigDecimal taxableValue, BigDecimal taxAmount) {}

    public record DocSummary(long invoices, long creditNotes) {}

    public record Gstr1Dto(LocalDate from, LocalDate to, List<B2bInvoice> b2b, List<B2cAggregate> b2c,
                           List<HsnSummary> hsnSummary, DocSummary docSummary) {}

    public record TaxLiability(BigDecimal igst, BigDecimal cgst, BigDecimal sgst) {}

    public record Gstr3bDto(LocalDate from, LocalDate to, BigDecimal outwardTaxableTurnover,
                            TaxLiability outwardTaxLiability, TaxLiability itcAvailable, BigDecimal netPayable) {}

    public record EwayBillRequest(Long salesInvoiceId) {}

    public record FilingRunDto(Long id, String returnType, LocalDate periodFrom, LocalDate periodTo,
                               String status, java.time.Instant filedAt) {}
}
