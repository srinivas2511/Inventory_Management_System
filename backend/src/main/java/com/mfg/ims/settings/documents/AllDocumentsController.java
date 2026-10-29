package com.mfg.ims.settings.documents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified "All Documents" list: surfaces every transactional document
 * (POs, GRs, SOs, Dispatches, AR/AP invoices, Production orders, Journals)
 * in one pageable, filterable endpoint.
 *
 * Requires a broad permission that most roles already hold (REPORT_VIEW).
 * Row-level visibility is handled by the frontend; this endpoint is a read
 * aggregation and does not post or mutate anything.
 */
@RestController
@RequestMapping("/api/documents/all")
public class AllDocumentsController {

    private final NamedParameterJdbcTemplate jdbc;

    public AllDocumentsController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DocumentRow(
            String docType,
            String docNumber,
            String status,
            String party,
            Instant docDate,
            String navigateTo
    ) {}

    public record AllDocumentsResponse(List<DocumentRow> rows, int totalCount) {}

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public AllDocumentsResponse list(
            @RequestParam(defaultValue = "") String docType,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        // Collect rows from each document type, apply unified sorting and paging
        List<DocumentRow> allRows = new ArrayList<>();
        allRows.addAll(fetchPurchaseOrders(search));
        allRows.addAll(fetchGoodsReceipts(search));
        allRows.addAll(fetchSalesOrders(search));
        allRows.addAll(fetchDispatches(search));
        allRows.addAll(fetchArInvoices(search));
        allRows.addAll(fetchApInvoices(search));
        allRows.addAll(fetchProductionOrders(search));
        allRows.addAll(fetchJournals(search));

        // Filter by docType if specified
        List<DocumentRow> filtered = docType.isBlank()
                ? allRows
                : allRows.stream().filter(r -> r.docType().equals(docType)).toList();

        // Sort newest first
        List<DocumentRow> sorted = filtered.stream()
                .sorted((a, b) -> {
                    if (a.docDate() == null && b.docDate() == null) return 0;
                    if (a.docDate() == null) return 1;
                    if (b.docDate() == null) return -1;
                    return b.docDate().compareTo(a.docDate());
                })
                .toList();

        int total = sorted.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return new AllDocumentsResponse(sorted.subList(from, to), total);
    }

    private List<DocumentRow> fetchPurchaseOrders(String search) {
        String sql = """
                SELECT po_number, status, created_at,
                       (SELECT name FROM ims.suppliers WHERE id = supplier_id) AS party
                FROM ims.purchase_orders
                WHERE (:s = '' OR po_number ILIKE :pat)
                ORDER BY created_at DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "Purchase Order", rs.getString("po_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                "/procurement/purchase-orders"));
    }

    private List<DocumentRow> fetchGoodsReceipts(String search) {
        String sql = """
                SELECT gr.gr_number, gr.status, gr.receipt_date AS doc_date,
                       (SELECT name FROM ims.suppliers WHERE id = po.supplier_id) AS party
                FROM ims.goods_receipts gr
                JOIN ims.purchase_orders po ON po.id = gr.purchase_order_id
                WHERE (:s = '' OR gr.gr_number ILIKE :pat)
                ORDER BY gr.receipt_date DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "Goods Receipt", rs.getString("gr_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getDate("doc_date") != null ? rs.getDate("doc_date").toLocalDate()
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : null,
                "/procurement/goods-receipts"));
    }

    private List<DocumentRow> fetchSalesOrders(String search) {
        String sql = """
                SELECT order_number, status, created_at,
                       (SELECT name FROM ims.customers WHERE id = customer_id) AS party
                FROM ims.sales_orders
                WHERE (:s = '' OR order_number ILIKE :pat)
                ORDER BY created_at DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "Sales Order", rs.getString("order_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                "/sales/orders"));
    }

    private List<DocumentRow> fetchDispatches(String search) {
        String sql = """
                SELECT dispatch_number, status, dispatch_date,
                       (SELECT name FROM ims.customers WHERE id = customer_id) AS party
                FROM ims.dispatches
                WHERE (:s = '' OR dispatch_number ILIKE :pat)
                ORDER BY dispatch_date DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "Dispatch", rs.getString("dispatch_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getDate("dispatch_date") != null ? rs.getDate("dispatch_date").toLocalDate()
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : null,
                "/sales/dispatches"));
    }

    private List<DocumentRow> fetchArInvoices(String search) {
        String sql = """
                SELECT invoice_number, status, invoice_date,
                       (SELECT name FROM ims.customers WHERE id = customer_id) AS party
                FROM ims.ar_invoices
                WHERE (:s = '' OR invoice_number ILIKE :pat)
                ORDER BY invoice_date DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "AR Invoice", rs.getString("invoice_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getDate("invoice_date") != null ? rs.getDate("invoice_date").toLocalDate()
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : null,
                "/finance/ar-invoices"));
    }

    private List<DocumentRow> fetchApInvoices(String search) {
        String sql = """
                SELECT invoice_number, status, invoice_date,
                       (SELECT name FROM ims.suppliers WHERE id = supplier_id) AS party
                FROM ims.ap_invoices
                WHERE (:s = '' OR invoice_number ILIKE :pat)
                ORDER BY invoice_date DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "AP Invoice", rs.getString("invoice_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getDate("invoice_date") != null ? rs.getDate("invoice_date").toLocalDate()
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : null,
                "/finance/ap-invoices"));
    }

    private List<DocumentRow> fetchProductionOrders(String search) {
        String sql = """
                SELECT order_number, status, created_at,
                       (SELECT name FROM ims.products WHERE id = product_id) AS party
                FROM ims.production_orders
                WHERE (:s = '' OR order_number ILIKE :pat)
                ORDER BY created_at DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "Production Order", rs.getString("order_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                "/production/orders"));
    }

    private List<DocumentRow> fetchJournals(String search) {
        String sql = """
                SELECT journal_number, status, posting_date,
                       description AS party
                FROM ims.journal_headers
                WHERE (:s = '' OR journal_number ILIKE :pat)
                ORDER BY posting_date DESC LIMIT 200
                """;
        return jdbc.query(sql, params(search), (rs, i) -> new DocumentRow(
                "Journal", rs.getString("journal_number"), rs.getString("status"),
                rs.getString("party"),
                rs.getDate("posting_date") != null ? rs.getDate("posting_date").toLocalDate()
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : null,
                "/finance/journals"));
    }

    private MapSqlParameterSource params(String search) {
        return new MapSqlParameterSource()
                .addValue("s", search)
                .addValue("pat", "%" + search + "%");
    }
}
