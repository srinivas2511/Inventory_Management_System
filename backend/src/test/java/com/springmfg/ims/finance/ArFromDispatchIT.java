package com.springmfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.springmfg.ims.finance.ap.dto.ApInvoiceDto;
import com.springmfg.ims.finance.ap.dto.ApPaymentRequest;
import com.springmfg.ims.finance.ap.service.ApService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.finance.ar.domain.ArInvoice;
import com.springmfg.ims.finance.ar.dto.ArInvoiceDto;
import com.springmfg.ims.finance.ar.dto.ArReceiptRequest;
import com.springmfg.ims.finance.ar.dto.ArReconcileResultDto;
import com.springmfg.ims.finance.ar.dto.MissingArInvoiceDto;
import com.springmfg.ims.finance.ar.service.ArReconciliationService;
import com.springmfg.ims.finance.ar.service.ArService;
import com.springmfg.ims.finance.gl.domain.FiscalPeriod;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.inventory.domain.Alert;
import com.springmfg.ims.inventory.repository.AlertRepository;
import com.springmfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.springmfg.ims.finance.gl.dto.JournalHeaderDto;
import com.springmfg.ims.finance.gl.dto.JournalLineDto;
import com.springmfg.ims.finance.gl.service.GlAccountResolver;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.DispatchDto;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.DispatchService;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

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
    @Autowired com.springmfg.ims.integration.webhook.service.WebhookAdminService webhooks;
    @Autowired com.springmfg.ims.integration.webhook.repository.WebhookDeliveryRepository webhookDeliveries;

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
                warehouseId, locationId, "APPROVED",
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
        var sub = webhooks.create(new com.springmfg.ims.integration.webhook.dto.WebhookDtos.SaveWebhookRequest(
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
            webhooks.update(sub.webhook().id(), new com.springmfg.ims.integration.webhook.dto.WebhookDtos.SaveWebhookRequest(
                    "AR-08 hook", "https://example.com/hook", java.util.List.of("InvoicePaid"), false, null));
        }
    }

    @Test
    @DisplayName("AP-01: a partial supplier payment leaves the invoice PARTIALLY_PAID, the rest makes it PAID")
    void apPartialPayment_thenFull() {
        LocalDate today = LocalDate.now();
        ApInvoiceDto draft = apService.createInvoice(new ApInvoiceCreateRequest(
                td.supplier().getId(), td.code("SINV"), today, today.plusDays(30), null,
                new BigDecimal("1000.0000"), BigDecimal.ZERO));
        ApInvoiceDto open = apService.openInvoice(draft.id());
        assertThat(open.status()).isEqualTo("OPEN");

        ApInvoiceDto afterPartial = apService.recordPayment(open.id(),
                new ApPaymentRequest(today, new BigDecimal("300.0000"), "BANK_TRANSFER", "PAY-1"));
        assertThat(afterPartial.status()).isEqualTo("PARTIALLY_PAID");

        ApInvoiceDto afterFull = apService.recordPayment(open.id(),
                new ApPaymentRequest(today, new BigDecimal("700.0000"), "BANK_TRANSFER", "PAY-2"));
        assertThat(afterFull.status()).isEqualTo("PAID");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private record Line(String qty, String price) {}

    private static Line line(String qty, String price) {
        return new Line(qty, price);
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
        SalesOrderDto order = orderService.approve(created.id());

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
