package com.mfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.mfg.ims.finance.ap.dto.ApInvoiceDto;
import com.mfg.ims.finance.ap.dto.ApPaymentRequest;
import com.mfg.ims.finance.ap.service.ApService;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.finance.ar.domain.ArInvoice;
import com.mfg.ims.finance.ar.dto.ArInvoiceDto;
import com.mfg.ims.finance.ar.dto.ArReceiptRequest;
import com.mfg.ims.finance.ar.dto.CreditNoteDtos.CreateCreditNoteRequest;
import com.mfg.ims.finance.ar.dto.CreditNoteDtos.CreditNoteDto;
import com.mfg.ims.finance.ar.service.CreditNoteService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.finance.ar.dto.ArReconcileResultDto;
import com.mfg.ims.finance.ar.dto.MissingArInvoiceDto;
import com.mfg.ims.finance.ar.service.ArReconciliationService;
import com.mfg.ims.finance.ar.service.ArService;
import com.mfg.ims.finance.gl.domain.FiscalPeriod;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.mfg.ims.inventory.domain.Alert;
import com.mfg.ims.inventory.repository.AlertRepository;
import com.mfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineDto;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.inventory.domain.FgBatch;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.repository.FgBatchRepository;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.mfg.ims.sales.dto.CreateDispatchRequest;
import com.mfg.ims.sales.dto.CreateSalesOrderRequest;
import com.mfg.ims.sales.dto.DispatchDto;
import com.mfg.ims.sales.dto.SalesOrderDto;
import com.mfg.ims.sales.service.DispatchService;
import com.mfg.ims.sales.service.SalesOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;
import com.mfg.ims.warehouse.domain.Warehouse;
import com.mfg.ims.warehouse.domain.WarehouseLocation;
import com.mfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.mfg.ims.warehouse.repository.WarehouseRepository;

/**
 * A dispatch must produce its AR invoice and revenue journal. Deliberately NOT {@code @Transactional}: the
 * invoice is created after the dispatch commits, and a test-managed transaction would hide exactly the
 * commit-time behaviour under test (a failed accounting step must never roll the dispatch back).
 */
class ArFromDispatchIT extends AbstractIntegrationTest {

    private static final String AR_CONTROL = "1101";
    private static final String REVENUE = "4001";

    @Autowired SalesOrderService orderService;
    @Autowired DispatchService dispatchService;
    @Autowired ArInvoiceRepository arInvoiceRepo;
    @Autowired JournalService journalService;
    @Autowired ArService arService;
    @Autowired ArReconciliationService reconciliation;
    @Autowired ApService apService;
    @Autowired AlertRepository alertRepo;
    @Autowired FiscalPeriodRepository periodRepo;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired InventoryPostingService postingService;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired WarehouseLocationRepository locationRepo;
    @Autowired GlAccountResolver glAccounts;
    @Autowired TestData td;
    @Autowired JdbcTemplate jdbc;
    @Autowired CreditNoteService creditNotes;
    @Autowired com.mfg.ims.insight.report.definitions.GstOutwardSuppliesReport outward;
    @Autowired com.mfg.ims.integration.webhook.service.WebhookAdminService webhooks;
    @Autowired com.mfg.ims.integration.webhook.repository.WebhookDeliveryRepository webhookDeliveries;

    private Long customerId;
    private Long productId;
    private Long warehouseId;
    private Long locationId;
    private Long fgBatchId;

    @BeforeEach
    void setup() {
        // Receipts and payments record who made them (NOT NULL created_by), as every request does in production.
        var actor = new CachedUser(7002L, "ar-it", true, 1, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        customerId = td.customer().getId();
        Product product = td.product();
        productId = product.getId();
        Warehouse wh = warehouseRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No warehouse in test DB"));
        warehouseId = wh.getId();
        locationId = ensureLocation(wh, "FINISHED").getId();

        FgBatch batch = new FgBatch();
        batch.setBatchNumber("FG-AR-IT-" + System.nanoTime());
        batch.setProduct(product);
        batch.setProductionOrderId(999L);
        batch.setProducedQty(new BigDecimal("1000.000"));
        batch.setApprovedQty(new BigDecimal("1000.000"));
        batch.setRemainingQty(new BigDecimal("1000.000"));
        batch.setProducedOn(LocalDate.now());
        batch.setQualityStatus("APPROVED");
        batch.setUnitCost(new BigDecimal("50.0000"));
        fgBatchId = fgBatchRepo.save(batch).getId();
        postingService.post(new PostingCommand(
                "FG_RECEIPT", "PRODUCT", null, productId, null, fgBatchId,
                warehouseId, locationId, "AVAILABLE",
                new BigDecimal("1000.000"), product.getUom(), new BigDecimal("50.0000"),
                "FG_BATCH", fgBatchId, batch.getBatchNumber(), "IT setup"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AR-01: a priced dispatch creates an OPEN invoice and a balanced Dr AR / Cr Revenue journal")
    void pricedDispatch_createsInvoiceAndJournal() {
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "60.0000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("6000.0000");
        assertThat(inv.getSubtotal()).isEqualByComparingTo("6000.0000");
        assertThat(inv.getStatus()).isEqualTo("OPEN");
        assertThat(inv.getJournalHeaderId()).isNotNull();

        JournalHeaderDto j = journalService.findById(inv.getJournalHeaderId());
        assertThat(j.status()).isEqualTo("POSTED");
        assertBalanced(j);
        assertThat(j.lines()).hasSize(2);
        assertThat(debit(j, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("6000.0000");
        assertThat(credit(j, glAccounts.idOf(REVENUE))).isEqualByComparingTo("6000.0000");
    }

    @Test
    @DisplayName("AR-02: a zero-priced line among priced lines is skipped; the invoice covers the priced lines only")
    void mixedZeroAndPricedLines_invoiceCoversPricedLines() {
        DispatchDto d = dispatch(LocalDate.now(), line("10.000", "25.0000"), line("5.000", "0.0000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("250.0000");
        JournalHeaderDto j = journalService.findById(inv.getJournalHeaderId());
        assertBalanced(j);
        assertThat(j.lines()).hasSize(2).allSatisfy(l ->
                assertThat(l.debitAmount().add(l.creditAmount())).isGreaterThan(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("AR-03: a zero-total dispatch gets a settled zero-value invoice and no journal")
    void zeroTotalDispatch_zeroInvoiceNoJournal() {
        DispatchDto d = dispatch(LocalDate.now(), line("4.000", "0.0000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(inv.getStatus()).isEqualTo("PAID");
        assertThat(inv.getJournalHeaderId()).isNull();
    }

    @Test
    @DisplayName("AR-04: line amounts are rounded to 4 dp half-up before they are summed")
    void lineAmountsRoundedHalfUpPerLine() {
        // 0.333 x 0.3333 = 0.1109889, which rounds half-up to 0.1110 on each of the two lines
        DispatchDto d = dispatch(LocalDate.now(), line("0.333", "0.3333"), line("0.333", "0.3333"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.2220");
        assertBalanced(journalService.findById(inv.getJournalHeaderId()));
    }

    @Test
    @DisplayName("AR-05: a failed invoice never undoes the dispatch; it raises an alert and is listed as missing")
    void failedInvoice_dispatchSurvives_alertRaised_listedAsMissing() {
        LocalDate noPeriod = LocalDate.of(2019, 6, 15);
        assertThat(periodRepo.findOpenPeriodForDate(noPeriod)).as("precondition: no period for 2019-06").isEmpty();

        DispatchDto d = dispatch(noPeriod, line("2.000", "10.0000"));

        assertThat(dispatchService.findById(d.id()).dispatchNumber()).isEqualTo(d.dispatchNumber());
        assertThat(arInvoiceRepo.findByDispatchId(d.id())).isEmpty();
        List<Alert> open = alertRepo.findOpenByTypeAndEntity("MISSING_AR_INVOICE", "DISPATCH", d.id());
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getMessage()).contains(d.dispatchNumber()).contains("No open period");
        assertThat(reconciliation.findMissing()).extracting(MissingArInvoiceDto::dispatchId).contains(d.id());

        ArReconcileResultDto stillFailing = reconciliation.retryMissing();
        assertThat(stillFailing.failures()).extracting(ArReconcileResultDto.Failure::dispatchId).contains(d.id());
        assertThat(alertRepo.findOpenByTypeAndEntity("MISSING_AR_INVOICE", "DISPATCH", d.id()))
                .as("repeated failures update the one open alert instead of adding more").hasSize(1);
    }

    @Test
    @DisplayName("AR-06: once the cause is fixed a retry invoices the dispatch exactly once and closes the alert")
    void retry_createsInvoiceOnce_closesAlert_isIdempotent() {
        LocalDate date = LocalDate.of(2018, 3, 10);
        DispatchDto d = dispatch(date, line("5.000", "20.0000"));
        assertThat(arInvoiceRepo.findByDispatchId(d.id())).isEmpty();
        assertThat(alertRepo.findOpenByTypeAndEntity("MISSING_AR_INVOICE", "DISPATCH", d.id())).hasSize(1);

        FiscalPeriod period = new FiscalPeriod();
        period.setPeriodName("MAR-2018");
        period.setFiscalYear(2017);
        period.setPeriodNumber((short) 12);
        period.setStartDate(LocalDate.of(2018, 3, 1));
        period.setEndDate(LocalDate.of(2018, 3, 31));
        period.setStatus("OPEN");
        periodRepo.save(period);

        ArReconcileResultDto first = reconciliation.retryMissing();
        assertThat(first.created()).isGreaterThanOrEqualTo(1);
        assertThat(first.failures()).extracting(ArReconcileResultDto.Failure::dispatchId).doesNotContain(d.id());

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("100.0000");
        assertBalanced(journalService.findById(inv.getJournalHeaderId()));
        assertThat(alertRepo.findOpenByTypeAndEntity("MISSING_AR_INVOICE", "DISPATCH", d.id())).isEmpty();

        reconciliation.retryMissing();
        arService.autoCreateFromDispatch(d.id());
        assertThat(arInvoiceRepo.findAll().stream().filter(i -> d.id().equals(i.getDispatchId())).count())
                .as("one invoice per dispatch, however often it is retried").isEqualTo(1);
        assertThat(reconciliation.findMissing()).extracting(MissingArInvoiceDto::dispatchId).doesNotContain(d.id());
    }

    @Test
    @DisplayName("AR-07: a partial receipt leaves the invoice PARTIALLY_PAID, the full amount makes it PAID")
    void partialReceipt_thenFull() {
        DispatchDto d = dispatch(LocalDate.now(), line("10.000", "100.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        ArInvoiceDto afterPartial = arService.recordReceipt(inv.getId(),
                new ArReceiptRequest(LocalDate.now(), new BigDecimal("400.0000"), "BANK_TRANSFER", "UTR-1"));
        assertThat(afterPartial.status()).isEqualTo("PARTIALLY_PAID");
        assertThat(afterPartial.balanceAmount()).isEqualByComparingTo("600.0000");

        ArInvoiceDto afterFull = arService.recordReceipt(inv.getId(),
                new ArReceiptRequest(LocalDate.now(), new BigDecimal("600.0000"), "BANK_TRANSFER", "UTR-2"));
        assertThat(afterFull.status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("AR-08: only the receipt that settles an invoice queues an InvoicePaid webhook")
    void settlingReceipt_queuesInvoicePaidWebhook() {
        var sub = webhooks.create(new com.mfg.ims.integration.webhook.dto.WebhookDtos.SaveWebhookRequest(
                "AR-08 hook", "https://example.com/hook", java.util.List.of("InvoicePaid"), true, null));
        try {
            DispatchDto d = dispatch(LocalDate.now(), line("10.000", "100.0000"));
            ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
            String key = "InvoicePaid:" + inv.getId();

            arService.recordReceipt(inv.getId(),
                    new ArReceiptRequest(LocalDate.now(), new BigDecimal("400.0000"), "BANK_TRANSFER", "UTR-A"));
            assertThat(webhookDeliveries.existsBySubscriptionIdAndEventKey(sub.webhook().id(), key))
                    .as("a partial receipt settles nothing").isFalse();

            arService.recordReceipt(inv.getId(),
                    new ArReceiptRequest(LocalDate.now(), new BigDecimal("600.0000"), "BANK_TRANSFER", "UTR-B"));
            var queued = webhookDeliveries.search(sub.webhook().id(), "", org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(queued.getContent()).hasSize(1);
            assertThat(queued.getContent().get(0).getEventKey()).isEqualTo(key);
            assertThat(queued.getContent().get(0).getEventType()).isEqualTo("InvoicePaid");
            assertThat(queued.getContent().get(0).getPayload()).containsEntry("invoiceNumber", inv.getInvoiceNumber())
                    .containsEntry("currency", "INR");
            assertThat(new BigDecimal(String.valueOf(queued.getContent().get(0).getPayload().get("totalAmount"))))
                    .isEqualByComparingTo("1000.0000");
        } finally {
            webhooks.update(sub.webhook().id(), new com.mfg.ims.integration.webhook.dto.WebhookDtos.SaveWebhookRequest(
                    "AR-08 hook", "https://example.com/hook", java.util.List.of("InvoicePaid"), false, null));
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AR-TAX-1: a product under GST18 sold within the company state adds CGST 1,800 and SGST 1,800 to a 20,000 invoice")
    void intraStateSaleAddsOutputCgstSgst() {
        sellUnderSlab("GST18", "27AAAAA0000A1Z5");

        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "200.0000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getSubtotal()).isEqualByComparingTo("20000");
        assertThat(inv.getTaxTotal()).isEqualByComparingTo("3600");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("23600");
        assertThat(inv.getSupplyType()).isEqualTo("INTRA");
        JournalHeaderDto j = journalService.findById(inv.getJournalHeaderId());
        assertBalanced(j);
        assertThat(debit(j, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("23600");
        assertThat(credit(j, glAccounts.idOf(REVENUE))).isEqualByComparingTo("20000");
        assertThat(credit(j, glAccounts.idOf("2311"))).isEqualByComparingTo("1800");
        assertThat(credit(j, glAccounts.idOf("2312"))).isEqualByComparingTo("1800");
        assertThat(arService.findAll(org.springframework.data.domain.PageRequest.of(0, 50)).getContent())
                .filteredOn(x -> x.id().equals(inv.getId())).singleElement()
                .satisfies(x -> assertThat(x.taxLines()).extracting(l -> l.taxCode()).containsExactlyInAnyOrder("CGST9", "SGST9"));
    }

    @Test
    @DisplayName("AR-TAX-2: the same sale to another state is IGST 3,600 on the output IGST account")
    void interStateSaleAddsOutputIgst() {
        sellUnderSlab("GST18", "29AAAAA0000A1Z5");

        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "200.0000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("23600");
        assertThat(inv.getSupplyType()).isEqualTo("INTER");
        JournalHeaderDto j = journalService.findById(inv.getJournalHeaderId());
        assertBalanced(j);
        assertThat(credit(j, glAccounts.idOf("2313"))).isEqualByComparingTo("3600");
        assertThat(j.lines()).hasSize(3);
    }

    @Test
    @DisplayName("AR-TAX-3: a product without a slab is invoiced without tax, exactly as before the tax engine")
    void productWithoutSlabHasNoTax() {
        jdbc.update("UPDATE ims.customers SET gst_number = ? WHERE id = ?", "27AAAAA0000A1Z5", customerId);

        DispatchDto d = dispatch(LocalDate.now(), line("10.000", "100.0000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTaxTotal()).isEqualByComparingTo("0");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("1000");
        assertThat(inv.getSupplyType()).isNull();
        assertThat(journalService.findById(inv.getJournalHeaderId()).lines()).hasSize(2);
    }

    @Test
    @DisplayName("AR-FX-1: a USD customer's 100 x USD 2.50 at 80 is USD 250, booked as Dr AR 20,000 / Cr Revenue 20,000")
    void foreignCustomerInvoiceIsConvertedToInr() {
        td.foreignCurrencyPeriods("80.000000", "82.000000", "78.000000");
        jdbc.update("UPDATE ims.customers SET currency_code = 'USD' WHERE id = ?", customerId);

        DispatchDto d = dispatch(LocalDate.of(2012, 1, 20), line("100.000", "2.5000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getCurrencyCode()).isEqualTo("USD");
        assertThat(inv.getExchangeRate()).isEqualByComparingTo("80");
        assertThat(inv.getTotalAmount()).as("the invoice stays in USD").isEqualByComparingTo("250");
        assertThat(inv.getTotalInr()).isEqualByComparingTo("20000");
        JournalHeaderDto j = journalService.findById(inv.getJournalHeaderId());
        assertBalanced(j);
        assertThat(debit(j, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("20000");
        assertThat(credit(j, glAccounts.idOf(REVENUE))).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("AR-FX-2: receiving USD 250 at 78 against a receivable carried at 80 books a realised loss of 500")
    void receiptAtALowerRateBooksALoss() {
        td.foreignCurrencyPeriods("80.000000", "82.000000", "78.000000");
        jdbc.update("UPDATE ims.customers SET currency_code = 'USD' WHERE id = ?", customerId);
        DispatchDto d = dispatch(LocalDate.of(2012, 1, 20), line("100.000", "2.5000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        ArInvoiceDto paid = arService.recordReceipt(inv.getId(),
                new ArReceiptRequest(LocalDate.of(2012, 3, 5), new BigDecimal("250"), "BANK_TRANSFER", "FX-R1"));

        assertThat(paid.status()).isEqualTo("PAID");
        Long journal = jdbc.queryForObject("SELECT journal_header_id FROM ims.ar_receipts WHERE ar_invoice_id = ?",
                Long.class, inv.getId());
        JournalHeaderDto j = journalService.findById(journal);
        assertBalanced(j);
        assertThat(debit(j, glAccounts.idOf("1201"))).isEqualByComparingTo("19500");
        assertThat(debit(j, glAccounts.idOf("5208"))).isEqualByComparingTo("500");
        assertThat(credit(j, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("AR-FX-3: receiving at 82 books a realised gain of 500, and two part receipts clear the receivable exactly")
    void receiptAtAHigherRateBooksAGain() {
        td.foreignCurrencyPeriods("80.000000", "82.000000", "78.000000");
        jdbc.update("UPDATE ims.customers SET currency_code = 'USD' WHERE id = ?", customerId);
        DispatchDto d = dispatch(LocalDate.of(2012, 1, 20), line("100.000", "2.5000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        arService.recordReceipt(inv.getId(), new ArReceiptRequest(LocalDate.of(2012, 2, 10), new BigDecimal("100"), "UPI", "R1"));
        ArInvoiceDto paid = arService.recordReceipt(inv.getId(),
                new ArReceiptRequest(LocalDate.of(2012, 2, 10), new BigDecimal("150"), "UPI", "R2"));

        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(jdbc.queryForList("SELECT carrying_inr, fx_difference FROM ims.ar_receipts WHERE ar_invoice_id = ? ORDER BY id",
                inv.getId())).extracting(r -> ((BigDecimal) r.get("carrying_inr")).toPlainString() + "/"
                + ((BigDecimal) r.get("fx_difference")).toPlainString())
                .containsExactly("8000.0000/200.0000", "12000.0000/300.0000");
    }

    @Test
    @DisplayName("AR-FX-4: a USD sale with 18% GST: tax USD 45 -> output tax of 1,800 CGST and 1,800 SGST at rate 80")
    void foreignInvoiceWithGst() {
        td.foreignCurrencyPeriods("80.000000", "82.000000", "78.000000");
        jdbc.update("UPDATE ims.customers SET currency_code = 'USD' WHERE id = ?", customerId);
        sellUnderSlab("GST18", "27AAAAA0000A1Z5");
        jdbc.update("UPDATE ims.customers SET currency_code = 'USD' WHERE id = ?", customerId);

        DispatchDto d = dispatch(LocalDate.of(2012, 1, 20), line("100.000", "2.5000"));

        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        assertThat(inv.getTaxTotal()).isEqualByComparingTo("45");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("295");
        JournalHeaderDto j = journalService.findById(inv.getJournalHeaderId());
        assertBalanced(j);
        assertThat(debit(j, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("23600");
        assertThat(credit(j, glAccounts.idOf(REVENUE))).isEqualByComparingTo("20000");
        assertThat(credit(j, glAccounts.idOf("2311"))).isEqualByComparingTo("1800");
        assertThat(credit(j, glAccounts.idOf("2312"))).isEqualByComparingTo("1800");
    }

    // ── R9-7.4: AR credit notes ────────────────────────────────────────────────

    private CreditNoteDto credit(ArInvoice inv, String subtotal, LocalDate date) {
        return creditNotes.create(new CreateCreditNoteRequest(inv.getId(), date, new BigDecimal(subtotal),
                "Price correction", null, null, null, null));
    }

    private CreditNoteDto approveAsSecondUser(Long id) {
        return td.asSecondUser(() -> creditNotes.approve(id), "CREDIT_NOTE_APPROVE");
    }

    @Test
    @DisplayName("CN-1: crediting 2,000 of a 6,000 invoice is approved by another user: Dr Revenue 2,000 / Cr AR 2,000, balance 4,000")
    void partialCreditReducesTheBalance() {
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "60.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        CreditNoteDto draft = credit(inv, "2000", LocalDate.now());
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.totalAmount()).isEqualByComparingTo("2000");
        assertThatThrownBy(() -> creditNotes.approve(draft.id()))
                .as("the user who raised it cannot approve it")
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES));

        CreditNoteDto posted = approveAsSecondUser(draft.id());

        assertThat(posted.status()).isEqualTo("POSTED");
        JournalHeaderDto j = journalService.findById(posted.journalHeaderId());
        assertBalanced(j);
        assertThat(j.lines()).hasSize(2);
        assertThat(debit(j, glAccounts.idOf(REVENUE))).isEqualByComparingTo("2000");
        assertThat(credit(j, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("2000");
        ArInvoiceDto after = arService.findAll(org.springframework.data.domain.PageRequest.of(0, 50)).getContent().stream()
                .filter(x -> x.id().equals(inv.getId())).findFirst().orElseThrow();
        assertThat(after.creditedAmount()).isEqualByComparingTo("2000");
        assertThat(after.balanceAmount()).isEqualByComparingTo("4000");
        assertThat(after.status()).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    @DisplayName("CN-2: on a 20,000 + 3,600 GST invoice, 10,000 reverses 900 + 900 of tax; the final 10,000 reverses exactly the remaining 1,800")
    void taxIsReversedInProportionAndTheFinalCreditTakesTheRemainder() {
        sellUnderSlab("GST18", "27AAAAA0000A1Z5");
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "200.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        CreditNoteDto first = approveAsSecondUser(credit(inv, "10000", LocalDate.now()).id());

        assertThat(first.taxTotal()).isEqualByComparingTo("1800");
        assertThat(first.totalAmount()).isEqualByComparingTo("11800");
        JournalHeaderDto j1 = journalService.findById(first.journalHeaderId());
        assertBalanced(j1);
        assertThat(debit(j1, glAccounts.idOf(REVENUE))).isEqualByComparingTo("10000");
        assertThat(debit(j1, glAccounts.idOf("2311"))).isEqualByComparingTo("900");
        assertThat(debit(j1, glAccounts.idOf("2312"))).isEqualByComparingTo("900");
        assertThat(credit(j1, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("11800");

        CreditNoteDto last = approveAsSecondUser(credit(inv, "10000", LocalDate.now()).id());

        assertThat(last.taxTotal()).as("3,600 less the 1,800 already reversed").isEqualByComparingTo("1800");
        ArInvoice settled = arInvoiceRepo.findById(inv.getId()).orElseThrow();
        assertThat(settled.getCreditedAmount()).isEqualByComparingTo("23600");
        assertThat(settled.getStatus()).as("fully credited, nothing received").isEqualTo("CREDIT_NOTE");
        assertThatThrownBy(() -> credit(settled, "1", LocalDate.now())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("CN-3: a credit above the part not yet credited, or above the open balance after a receipt, is refused")
    void creditCannotExceedTheInvoiceOrItsOpenBalance() {
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "60.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        assertThatThrownBy(() -> credit(inv, "6000.01", LocalDate.now()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not yet credited");

        arService.recordReceipt(inv.getId(), new ArReceiptRequest(LocalDate.now(), new BigDecimal("5000"), "BANK_TRANSFER", "R"));
        assertThatThrownBy(() -> credit(inv, "2000", LocalDate.now()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("open balance");
        assertThat(approveAsSecondUser(credit(inv, "1000", LocalDate.now()).id()).status()).isEqualTo("POSTED");
        assertThat(arInvoiceRepo.findById(inv.getId()).orElseThrow().getStatus())
                .as("5,000 received + 1,000 credited settles the invoice").isEqualTo("PAID");
    }

    @Test
    @DisplayName("CN-4: a cancelled draft frees its amount; a posted note cannot be cancelled")
    void cancelFreesTheAmount() {
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "60.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        CreditNoteDto big = credit(inv, "6000", LocalDate.now());
        assertThatThrownBy(() -> credit(inv, "1", LocalDate.now())).isInstanceOf(BusinessRuleException.class);

        assertThat(creditNotes.cancel(big.id()).status()).isEqualTo("CANCELLED");
        CreditNoteDto again = credit(inv, "6000", LocalDate.now());
        CreditNoteDto posted = approveAsSecondUser(again.id());
        assertThatThrownBy(() -> creditNotes.cancel(posted.id())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("CN-5: a return of 40 pieces at cost 50 is received into QUARANTINE and journals Dr FG stock 2,000 / Cr COGS 2,000")
    void customerReturnIsReceivedAtTheBatchCost() {
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "60.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        BigDecimal fgStock = glBalance("1410");
        BigDecimal cogs = glBalance("5001");

        CreditNoteDto draft = creditNotes.create(new CreateCreditNoteRequest(inv.getId(), LocalDate.now(),
                new BigDecimal("2400"), "40 pieces returned", fgBatchId, new BigDecimal("40"), warehouseId, locationId));
        CreditNoteDto posted = approveAsSecondUser(draft.id());

        assertThat(posted.returnTransactionNumber()).startsWith("TXN/");
        assertThat(jdbc.queryForList("""
                SELECT transaction_type, stock_status, quantity, unit_cost, total_cost
                  FROM ims.inventory_transactions WHERE transaction_number = ?
                """, posted.returnTransactionNumber()))
                .singleElement().satisfies(r -> {
                    assertThat(r.get("transaction_type")).isEqualTo("CUSTOMER_RETURN");
                    assertThat(r.get("stock_status")).isEqualTo("QUARANTINE");
                    assertThat((BigDecimal) r.get("quantity")).isEqualByComparingTo("40");
                    assertThat((BigDecimal) r.get("unit_cost")).isEqualByComparingTo("50");
                    assertThat((BigDecimal) r.get("total_cost")).isEqualByComparingTo("2000");
                });
        assertThat(glBalance("1410")).as("FG stock up by 40 x 50").isEqualByComparingTo(fgStock.add(new BigDecimal("2000")));
        assertThat(glBalance("5001")).as("cost of goods sold reversed").isEqualByComparingTo(cogs.subtract(new BigDecimal("2000")));
    }

    @Test
    @DisplayName("CN-6: a return is limited to what was dispatched on the invoice, and a half-specified return is refused")
    void returnIsLimitedToTheDispatch() {
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "60.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        assertThatThrownBy(() -> creditNotes.create(new CreateCreditNoteRequest(inv.getId(), LocalDate.now(),
                new BigDecimal("100"), "too many", fgBatchId, new BigDecimal("101"), warehouseId, locationId)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exceed");
        assertThatThrownBy(() -> creditNotes.create(new CreateCreditNoteRequest(inv.getId(), LocalDate.now(),
                new BigDecimal("100"), "wrong batch", 999999L, new BigDecimal("1"), warehouseId, locationId)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("was not dispatched");
        assertThatThrownBy(() -> creditNotes.create(new CreateCreditNoteRequest(inv.getId(), LocalDate.now(),
                new BigDecimal("100"), "no place", fgBatchId, new BigDecimal("1"), null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("warehouse and location");
        // two returns together may not exceed the dispatch either
        creditNotes.create(new CreateCreditNoteRequest(inv.getId(), LocalDate.now(), new BigDecimal("60"), "first",
                fgBatchId, new BigDecimal("60"), warehouseId, locationId));
        assertThatThrownBy(() -> creditNotes.create(new CreateCreditNoteRequest(inv.getId(), LocalDate.now(),
                new BigDecimal("60"), "second", fgBatchId, new BigDecimal("41"), warehouseId, locationId)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("60");
    }

    @Test
    @DisplayName("CN-7: USD 100 credited on a USD 250 invoice at 80 is Dr Revenue 8,000 / Cr AR 8,000; the last USD 150 received at 82 loses 300 and leaves no residue")
    void creditOnAForeignInvoiceThenReceipt() {
        td.foreignCurrencyPeriods("80.000000", "82.000000", "78.000000");
        jdbc.update("UPDATE ims.customers SET currency_code = 'USD' WHERE id = ?", customerId);
        DispatchDto d = dispatch(LocalDate.of(2012, 1, 20), line("100.000", "2.5000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();

        CreditNoteDto note = approveAsSecondUser(credit(inv, "100", LocalDate.of(2012, 1, 25)).id());

        JournalHeaderDto j = journalService.findById(note.journalHeaderId());
        assertBalanced(j);
        assertThat(debit(j, glAccounts.idOf(REVENUE))).isEqualByComparingTo("8000");
        assertThat(credit(j, glAccounts.idOf(AR_CONTROL))).isEqualByComparingTo("8000");

        ArInvoiceDto paid = arService.recordReceipt(inv.getId(),
                new ArReceiptRequest(LocalDate.of(2012, 2, 10), new BigDecimal("150"), "BANK_TRANSFER", "FX-C"));

        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(jdbc.queryForList("SELECT carrying_inr, fx_difference FROM ims.ar_receipts WHERE ar_invoice_id = ?", inv.getId()))
                .singleElement().satisfies(r -> {
                    assertThat((BigDecimal) r.get("carrying_inr")).as("20,000 less the 8,000 credited").isEqualByComparingTo("12000");
                    assertThat((BigDecimal) r.get("fx_difference")).isEqualByComparingTo("300");
                });
    }

    private BigDecimal glBalance(String accountCode) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(jl.debit_amount - jl.credit_amount), 0)
                  FROM ims.journal_lines jl
                  JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id AND jh.status = 'POSTED'
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE a.account_code = ?
                """, BigDecimal.class, accountCode);
    }

    private void sellUnderSlab(String slab, String customerGstin) {
        jdbc.update("UPDATE ims.customers SET gst_number = ? WHERE id = ?", customerGstin, customerId);
        jdbc.update("INSERT INTO ims.product_tax_slabs (product_id, slab_code) VALUES (?, ?)", productId, slab);
    }

    private record Line(String qty, String price) {}

    private static Line line(String qty, String price) {
        return new Line(qty, price);
    }

    @Test
    @DisplayName("GST-REP-2: the outward register lists CGST/SGST 1,800 each on a 20,000 invoice and nets a 10,000 credit note as -900 each")
    void outwardRegisterListsInvoicesAndNetsCreditNotes() {
        sellUnderSlab("GST18", "27AAAAA0000A1Z5");
        DispatchDto d = dispatch(LocalDate.now(), line("100.000", "200.0000"));
        ArInvoice inv = arInvoiceRepo.findByDispatchId(d.id()).orElseThrow();
        CreditNoteDto note = approveAsSecondUser(credit(inv, "10000", LocalDate.now()).id());

        var all = outward.runQuery(new com.mfg.ims.insight.report.ReportRequest(null, null, null, null, 0, 500));
        var invoiceRows = all.stream().filter(r -> inv.getInvoiceNumber().equals(r.get("Number"))).toList();
        var noteRows = all.stream().filter(r -> note.creditNoteNumber().equals(r.get("Number"))).toList();

        assertThat(invoiceRows).extracting(r -> r.get("Tax type") + " " + plain(r.get("Taxable value (INR)")) + " "
                + plain(r.get("Tax (INR)"))).containsExactlyInAnyOrder("CGST 20000 1800", "SGST 20000 1800");
        assertThat(invoiceRows).allSatisfy(r -> {
            assertThat(r.get("Document")).isEqualTo("Invoice");
            assertThat(r.get("GSTIN")).isEqualTo("27AAAAA0000A1Z5");
        });
        assertThat(noteRows).extracting(r -> r.get("Tax type") + " " + plain(r.get("Taxable value (INR)")) + " "
                + plain(r.get("Tax (INR)"))).containsExactlyInAnyOrder("CGST -10000 -900", "SGST -10000 -900");
    }

    private static String plain(Object value) {
        return ((BigDecimal) value).stripTrailingZeros().toPlainString();
    }

    private DispatchDto dispatch(LocalDate date, Line... lines) {
        BigDecimal totalQty = BigDecimal.ZERO;
        for (Line l : lines) {
            totalQty = totalQty.add(new BigDecimal(l.qty()));
        }
        List<AddSalesOrderItemRequest> items = new java.util.ArrayList<>();
        for (Line l : lines) {
            items.add(new AddSalesOrderItemRequest(productId, new BigDecimal(l.qty()),
                    new BigDecimal(l.price()), null, null));
        }
        SalesOrderDto created = orderService.create(new CreateSalesOrderRequest(
                customerId, LocalDate.now(), LocalDate.now().plusDays(30),
                "123 Test St", "NET30", null, items));
        orderService.confirm(created.id());
        SalesOrderDto order = td.asSecondUser(() -> orderService.approve(created.id()), "SALES_APPROVE");

        List<CreateDispatchRequest.DispatchLineRequest> reqLines = new java.util.ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            reqLines.add(new CreateDispatchRequest.DispatchLineRequest(
                    order.items().get(i).id(), fgBatchId, new BigDecimal(lines[i].qty()),
                    warehouseId, locationId, new BigDecimal(lines[i].price())));
        }
        return dispatchService.create(new CreateDispatchRequest(
                order.id(), date, null, null, null, "123 Test St", null, reqLines, null));
    }

    private static void assertBalanced(JournalHeaderDto j) {
        BigDecimal dr = j.lines().stream().map(JournalLineDto::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = j.lines().stream().map(JournalLineDto::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dr).isEqualByComparingTo(cr);
        assertThat(dr).isGreaterThan(BigDecimal.ZERO);
    }

    private static BigDecimal debit(JournalHeaderDto j, long glAccountId) {
        return j.lines().stream().filter(l -> l.glAccountId() == glAccountId)
                .map(JournalLineDto::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal credit(JournalHeaderDto j, long glAccountId) {
        return j.lines().stream().filter(l -> l.glAccountId() == glAccountId)
                .map(JournalLineDto::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private WarehouseLocation ensureLocation(Warehouse wh, String locType) {
        return locationRepo.findByWarehouseIdAndActiveTrue(wh.getId()).stream()
                .filter(l -> locType.equals(l.getLocationType()))
                .findFirst()
                .orElseGet(() -> {
                    WarehouseLocation loc = new WarehouseLocation();
                    loc.setWarehouse(wh);
                    loc.setLocationCode(td.code("L"));
                    loc.setName(locType);
                    loc.setLocationType(locType);
                    loc.setActive(true);
                    return locationRepo.save(loc);
                });
    }
}
