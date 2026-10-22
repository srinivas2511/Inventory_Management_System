package com.mfg.ims.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.analytics.AnalyticsDtos.QueryResult;
import com.mfg.ims.analytics.anomaly.AnomalyDetectionService;
import com.mfg.ims.analytics.anomaly.AnomalyDetectionService.SweepResult;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.inventory.repository.AlertRepository;
import com.mfg.ims.partner.domain.Customer;
import com.mfg.ims.partner.domain.Supplier;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * The analytics views and the BI query endpoint against real PostgreSQL with hand-built data, so every figure asserted
 * is one worked out by hand. Everything runs through the real SQL: a query that references a column that does not
 * exist fails here.
 */
class AnalyticsIT extends AbstractIntegrationTest {

    @Autowired AnalyticsService analytics;
    @Autowired AnalyticsViewRefresher refresher;
    @Autowired AnomalyDetectionService anomalies;
    @Autowired AlertRepository alertRepo;
    @Autowired TestData td;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static UsernamePasswordAuthenticationToken caller(String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(
                new CachedUser(9101L, "analytics-it", true, 1, java.util.Set.of(), java.util.Set.of(authorities)), null, granted);
    }

    private void actAs(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(caller(authorities));
    }

    private static Map<String, Object> rowFor(QueryResult r, String column, Object value) {
        return r.rows().stream().filter(m -> value.equals(m.get(column))).findFirst().orElseThrow(
                () -> new AssertionError("no row with " + column + " = " + value + " in " + r.rows()));
    }

    private static Map<String, Object> monthRow(List<Map<String, Object>> rows, LocalDate month) {
        return rows.stream().filter(m -> month.toString().equals(m.get("month"))).findFirst().orElseThrow(
                () -> new AssertionError("no row for month " + month + " in " + rows));
    }

    private static List<Map<String, Object>> rowsFor(QueryResult r, String column, Object value) {
        return r.rows().stream().filter(m -> value.equals(m.get(column))).toList();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────────────────────

    private long insertPo(Supplier supplier, long materialId, LocalDate poDate, LocalDate expected, LocalDate receipt,
                          int accepted, int rejected) {
        long poId = jdbc.queryForObject("""
                INSERT INTO ims.purchase_orders (po_number, supplier_id, po_date, expected_delivery_date, status, grand_total)
                VALUES (?, ?, ?, ?, 'RECEIVED', 11000) RETURNING id""", Long.class,
                "IT-PO-" + UUID.randomUUID().toString().substring(0, 8), supplier.getId(), poDate, expected);
        long poItemId = jdbc.queryForObject("""
                INSERT INTO ims.purchase_order_items (purchase_order_id, line_no, material_id, ordered_qty, received_qty,
                       accepted_qty, rejected_qty, rate, line_total)
                VALUES (?, 1, ?, 100, ?, ?, ?, 110, 11000) RETURNING id""", Long.class,
                poId, materialId, accepted + rejected, accepted, rejected);
        jdbc.update("""
                INSERT INTO ims.goods_receipts (gr_number, purchase_order_id, receipt_date, warehouse_id, received_by)
                VALUES (?, ?, ?, 1, 1)""", "IT-GR-" + UUID.randomUUID().toString().substring(0, 8), poId, receipt);
        return poItemId;
    }

    /** Four months of one supplier: pass rate 98 (two POs, one late), 97, 99 and 70 this month; price 10 % over standard. */
    private Supplier supplierHistory() {
        Supplier supplier = td.supplier();
        long materialId = td.material().getId();
        jdbc.update("UPDATE ims.materials SET standard_cost = 100 WHERE id = ?", materialId);
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate m3 = thisMonth.minusMonths(3);
        // three months ago: one PO on time, one 3 days late
        insertPo(supplier, materialId, m3, m3.plusDays(10), m3.plusDays(5), 98, 2);
        insertPo(supplier, materialId, m3, m3.plusDays(5), m3.plusDays(8), 98, 2);
        insertPo(supplier, materialId, thisMonth.minusMonths(2), thisMonth.minusMonths(2).plusDays(10),
                thisMonth.minusMonths(2).plusDays(5), 97, 3);
        insertPo(supplier, materialId, thisMonth.minusMonths(1), thisMonth.minusMonths(1).plusDays(10),
                thisMonth.minusMonths(1).plusDays(5), 99, 1);
        insertPo(supplier, materialId, thisMonth, thisMonth.plusDays(10), thisMonth, 70, 30);
        refresher.refreshAll();
        return supplier;
    }

    // ── supplier scorecard and the anomaly it feeds ──────────────────────────────────────────────────────────────

    @Test
    void supplierScorecardAggregatesOnTimeQualityAndPriceVarianceByMonth() {
        actAs("ANALYTICS_VIEW");
        Supplier supplier = supplierHistory();
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

        QueryResult r = analytics.run("SUPPLIER_SCORECARD", thisMonth.minusMonths(3), LocalDate.now(), null);
        List<Map<String, Object>> mine = rowsFor(r, "supplier_code", supplier.getSupplierCode());

        assertThat(mine).hasSize(4);
        Map<String, Object> m3 = monthRow(mine, thisMonth.minusMonths(3));   // two POs, one on time
        assertThat((Number) m3.get("po_count")).isEqualTo(2L);
        assertThat((BigDecimal) m3.get("ordered_value")).isEqualByComparingTo("22000");
        assertThat((BigDecimal) m3.get("on_time_pct")).as("1 of 2 on time").isEqualByComparingTo("50.00");
        assertThat((BigDecimal) m3.get("received_qty")).isEqualByComparingTo("200");
        assertThat((BigDecimal) m3.get("accepted_qty")).isEqualByComparingTo("196");
        assertThat((BigDecimal) m3.get("rejected_qty")).isEqualByComparingTo("4");
        assertThat((BigDecimal) m3.get("quality_pass_pct")).as("196 / 200").isEqualByComparingTo("98.00");
        assertThat((BigDecimal) m3.get("price_variance_pct")).as("(110 - 100) / 100").isEqualByComparingTo("10.00");

        Map<String, Object> current = monthRow(mine, thisMonth);
        assertThat((BigDecimal) current.get("quality_pass_pct")).as("70 / 100").isEqualByComparingTo("70.00");
        assertThat((BigDecimal) current.get("on_time_pct")).isEqualByComparingTo("100.00");
    }

    @Test
    void aMidMonthFromDateStillIncludesThatWholeMonth() {
        actAs("ANALYTICS_VIEW");
        Supplier supplier = supplierHistory();
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

        QueryResult r = analytics.run("SUPPLIER_SCORECARD", thisMonth.minusMonths(1).plusDays(14), LocalDate.now(), null);

        assertThat(r.from()).as("the monthly view's range starts on the first of the month")
                .isEqualTo(thisMonth.minusMonths(1));
        assertThat(rowsFor(r, "supplier_code", supplier.getSupplierCode())).hasSize(2);
    }

    @Test
    void theSupplierQualityDropRaisesOneAnomalyAlertAndTheSweepRunsEveryDetector() {
        actAs("ANALYTICS_VIEW");
        Supplier supplier = supplierHistory();

        SweepResult first = anomalies.sweep();

        assertThat(first.failedDetectors()).as("every detector's SQL runs against the real schema").isEmpty();
        assertThat(first.findings()).anySatisfy(f -> assertThat(f)
                .contains("70.0%").contains("98.0%").contains("previous 3 months"));
        var open = alertRepo.findOpenByTypeAndEntity("ANOMALY_DETECTED", "SUPPLIER_QUALITY", supplier.getId());
        assertThat(open).hasSize(1);

        anomalies.sweep();
        assertThat(alertRepo.findOpenByTypeAndEntity("ANOMALY_DETECTED", "SUPPLIER_QUALITY", supplier.getId()))
                .as("a repeat finding refreshes the open alert instead of adding another").hasSize(1);
    }

    // ── customer sales and profitability ─────────────────────────────────────────────────────────────────────────

    @Test
    void customerSalesSummaryAndProfitabilityMatchTheOrderDispatchAndInvoice() {
        actAs("ANALYTICS_VIEW", "ANALYTICS_FINANCE");
        Customer customer = td.customer();
        Product product = td.product();
        long orderId = jdbc.queryForObject("""
                INSERT INTO ims.sales_orders (order_number, customer_id, status) VALUES (?, ?, 'APPROVED') RETURNING id""",
                Long.class, "IT-SO-" + UUID.randomUUID().toString().substring(0, 8), customer.getId());
        long itemId = jdbc.queryForObject("""
                INSERT INTO ims.sales_order_items (sales_order_id, line_no, product_id, ordered_qty, dispatched_qty, unit_price)
                VALUES (?, 1, ?, 10, 10, 50) RETURNING id""", Long.class, orderId, product.getId());
        long batchId = jdbc.queryForObject("""
                INSERT INTO ims.fg_batches (batch_number, product_id, production_order_id, produced_qty, produced_on)
                VALUES (?, ?, 0, 10, CURRENT_DATE) RETURNING id""", Long.class,
                "IT-FG-" + UUID.randomUUID().toString().substring(0, 8), product.getId());
        long dispatchId = jdbc.queryForObject("""
                INSERT INTO ims.dispatches (dispatch_number, sales_order_id, customer_id, delivery_address, dispatched_by)
                VALUES (?, ?, ?, 'IT address', 1) RETURNING id""", Long.class,
                "IT-DN-" + UUID.randomUUID().toString().substring(0, 8), orderId, customer.getId());
        jdbc.update("""
                INSERT INTO ims.dispatch_items (dispatch_id, sales_order_item_id, product_id, fg_batch_id, quantity, unit_price)
                VALUES (?, ?, ?, ?, 10, 50)""", dispatchId, itemId, product.getId(), batchId);
        long periodId = jdbc.queryForObject("SELECT id FROM ims.fiscal_periods ORDER BY start_date LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO ims.ar_invoices (invoice_number, customer_id, dispatch_id, invoice_date, due_date, subtotal,
                       tax_total, total_amount, received_amount, status, period_id)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + 30, 500, 90, 590, 200, 'PARTIALLY_PAID', ?)""",
                "IT-INV-" + UUID.randomUUID().toString().substring(0, 8), customer.getId(), dispatchId, periodId);
        refresher.refreshAll();

        QueryResult sales = analytics.run("CUSTOMER_SALES_SUMMARY", LocalDate.now(), LocalDate.now(), null);
        Map<String, Object> row = rowFor(sales, "customer_code", customer.getCustomerCode());
        assertThat((Number) row.get("orders")).isEqualTo(1L);
        assertThat((BigDecimal) row.get("ordered_value")).as("10 x 50").isEqualByComparingTo("500");
        assertThat((Number) row.get("dispatches")).isEqualTo(1L);
        assertThat((BigDecimal) row.get("dispatched_value")).isEqualByComparingTo("500");
        assertThat((BigDecimal) row.get("invoiced_total")).isEqualByComparingTo("590");
        assertThat((BigDecimal) row.get("received_total")).isEqualByComparingTo("200");

        QueryResult profit = analytics.run("CUSTOMER_PROFITABILITY", LocalDate.now(), LocalDate.now(), null);
        Map<String, Object> p = rowFor(profit, "customer_code", customer.getCustomerCode());
        assertThat((BigDecimal) p.get("revenue")).isEqualByComparingTo("500");
        assertThat((BigDecimal) p.get("cogs")).as("no stock was costed to this dispatch").isEqualByComparingTo("0");
        assertThat((BigDecimal) p.get("gross_margin")).isEqualByComparingTo("500");
        assertThat((BigDecimal) p.get("margin_pct")).isEqualByComparingTo("100.00");
    }

    // ── financial summary ────────────────────────────────────────────────────────────────────────────────────────

    private long accountId(String code) {
        return jdbc.queryForObject("SELECT id FROM ims.gl_accounts WHERE account_code = ?", Long.class, code);
    }

    private void journal(long periodId, LocalDate date, String status, String drCode, String crCode, String amount) {
        long id = jdbc.queryForObject("""
                INSERT INTO ims.journal_headers (journal_number, journal_type, posting_date, period_id, description,
                       prepared_by, status)
                VALUES (?, 'GL', ?, ?, 'analytics IT', 1, ?) RETURNING id""", Long.class,
                "IT-J-" + UUID.randomUUID().toString().substring(0, 12), date, periodId, status);
        jdbc.update("INSERT INTO ims.journal_lines (journal_header_id, line_number, gl_account_id, debit_amount, credit_amount) "
                + "VALUES (?, 1, ?, ?, 0)", id, accountId(drCode), new BigDecimal(amount));
        jdbc.update("INSERT INTO ims.journal_lines (journal_header_id, line_number, gl_account_id, debit_amount, credit_amount) "
                + "VALUES (?, 2, ?, 0, ?)", id, accountId(crCode), new BigDecimal(amount));
    }

    @Test
    void financialSummaryCountsPostedJournalsAndIgnoresDrafts() {
        actAs("ANALYTICS_VIEW", "ANALYTICS_FINANCE");
        Map<String, Object> period = jdbc.queryForList("""
                SELECT id, start_date FROM ims.fiscal_periods
                 ORDER BY (CURRENT_DATE BETWEEN start_date AND end_date) DESC, start_date DESC LIMIT 1""").get(0);
        long periodId = ((Number) period.get("id")).longValue();
        LocalDate start = ((java.sql.Date) period.get("start_date")).toLocalDate();

        refresher.refreshAll();
        Map<String, Object> before = rowFor(analytics.run("FIN_SUMMARY_MONTHLY", start, start, null), "month", start.toString());

        journal(periodId, start, "POSTED", "1201", "4001", "1000");     // revenue 1000
        journal(periodId, start, "POSTED", "5001", "1410", "600");      // cost of goods sold 600
        journal(periodId, start, "POSTED", "5201", "1201", "150");      // expense 150
        journal(periodId, start, "DRAFT", "1201", "4001", "999999");    // a draft is not a result
        journal(periodId, start, "REVERSED", "1201", "4001", "300");    // reversed original ...
        journal(periodId, start, "POSTED", "4001", "1201", "300");      // ... and its mirror image net to zero
        refresher.refreshAll();
        Map<String, Object> after = rowFor(analytics.run("FIN_SUMMARY_MONTHLY", start, start, null), "month", start.toString());

        assertThat(delta(before, after, "revenue")).isEqualByComparingTo("1000");
        assertThat(delta(before, after, "cogs")).isEqualByComparingTo("600");
        assertThat(delta(before, after, "gross_profit")).isEqualByComparingTo("400");
        assertThat(delta(before, after, "expenses")).isEqualByComparingTo("150");
        assertThat(delta(before, after, "net_profit")).isEqualByComparingTo("250");
    }

    private static BigDecimal delta(Map<String, Object> before, Map<String, Object> after, String column) {
        return ((BigDecimal) after.get(column)).subtract((BigDecimal) before.get(column));
    }

    // ── access control, catalogue and plumbing ───────────────────────────────────────────────────────────────────

    @Test
    void financeQueriesNeedTheFinancePermissionAndTheCatalogueHidesThem() {
        actAs("ANALYTICS_VIEW");
        assertThat(analytics.catalogue()).extracting(AnalyticsDtos.QueryDescriptor::key)
                .contains("SUPPLIER_SCORECARD", "OEE_DAILY", "PRODUCTION_MONTHLY", "INVENTORY_TURNS", "CUSTOMER_SALES_SUMMARY")
                .doesNotContain("FIN_SUMMARY_MONTHLY", "CUSTOMER_PROFITABILITY", "COST_VARIANCE");
        for (String key : List.of("FIN_SUMMARY_MONTHLY", "CUSTOMER_PROFITABILITY", "COST_VARIANCE")) {
            assertThatThrownBy(() -> analytics.run(key, null, null, null)).as(key).isInstanceOf(AccessDeniedException.class);
        }

        actAs("ANALYTICS_VIEW", "ANALYTICS_FINANCE");
        assertThat(analytics.catalogue()).hasSize(8);

        SecurityContextHolder.clearContext();
        assertThat(analytics.catalogue()).as("nobody authenticated sees nothing").isEmpty();
    }

    @Test
    void everyCatalogueQueryExecutesAgainstTheRealSchema() {
        actAs("ANALYTICS_VIEW", "ANALYTICS_FINANCE");
        refresher.refreshAll();
        for (AnalyticsDtos.QueryDescriptor q : analytics.catalogue()) {
            QueryResult r = analytics.run(q.key(), null, null, 5);
            assertThat(r.key()).isEqualTo(q.key());
            assertThat(r.columns()).as(q.key()).isNotEmpty();
            r.rows().forEach(row -> assertThat(row.keySet()).as(q.key() + " returns its declared columns")
                    .containsAll(q.columns().stream().map(AnalyticsQuery.Column::name).toList()));
        }
    }

    @Test
    void limitTruncationUnknownKeysAndSwappedDatesAreHandled() {
        actAs("ANALYTICS_VIEW");
        supplierHistory();
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

        QueryResult limited = analytics.run("SUPPLIER_SCORECARD", thisMonth.minusMonths(3), LocalDate.now(), 2);
        assertThat(limited.rows()).hasSize(2);
        assertThat(limited.truncated()).isTrue();

        QueryResult swapped = analytics.run("supplier_scorecard", LocalDate.now(), thisMonth.minusMonths(3), 2000);
        assertThat(swapped.from()).as("from and to are put in order, and keys are case-insensitive")
                .isEqualTo(thisMonth.minusMonths(3));
        assertThat(swapped.rows()).isNotEmpty();

        assertThatThrownBy(() -> analytics.run("NOPE", null, null, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> analytics.run(null, null, null, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theQueryEndpointEnforcesTheTwoLevelsOfPermission() throws Exception {
        var viewer = caller("ANALYTICS_VIEW");
        mockMvc.perform(get("/api/analytics/query").param("key", "PRODUCTION_MONTHLY").with(authentication(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.key").value("PRODUCTION_MONTHLY"));
        mockMvc.perform(get("/api/analytics/query").param("key", "FIN_SUMMARY_MONTHLY").with(authentication(viewer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analytics/query").param("key", "FIN_SUMMARY_MONTHLY")
                        .with(authentication(caller("ANALYTICS_VIEW", "ANALYTICS_FINANCE"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/query").param("key", "PRODUCTION_MONTHLY")
                        .with(authentication(caller("REPORT_VIEW"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analytics/query").param("key", "PRODUCTION_MONTHLY")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/query").param("key", "NOPE").with(authentication(viewer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshingEveryViewSucceedsAndReportsNoFailures() {
        var result = refresher.refreshAll();
        assertThat(result.failed()).isEmpty();
        assertThat(result.refreshed()).containsExactlyElementsOf(AnalyticsViewRefresher.VIEWS);
    }
}
