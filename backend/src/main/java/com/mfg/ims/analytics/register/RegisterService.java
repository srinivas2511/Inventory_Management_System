package com.mfg.ims.analytics.register;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RegisterService {

    private static final Map<String, String> REGISTER_QUERIES = Map.ofEntries(
            Map.entry("REG_SALES_INVOICE",  "SELECT * FROM ims.reg_sales_invoice ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_PURCHASE_ORDER", "SELECT * FROM ims.reg_purchase_order ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_PAYMENT",        "SELECT * FROM ims.reg_payment ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_QUOTATION",      "SELECT * FROM ims.reg_quotation ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_PO_ITEMS",       "SELECT * FROM ims.reg_po_items ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_SO_ITEMS",       "SELECT * FROM ims.reg_so_items ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_INVOICE_ITEMS",  "SELECT * FROM ims.reg_invoice_items ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_ADJUSTMENT_NOTES", "SELECT * FROM ims.reg_adjustment_notes ORDER BY id DESC LIMIT 500"),
            Map.entry("REG_LAST_PRICE",     "SELECT * FROM ims.reg_last_price ORDER BY last_purchase_date DESC LIMIT 500"),
            Map.entry("REG_PRODUCTION_SUMMARY", "SELECT * FROM ims.reg_production_summary ORDER BY order_id DESC LIMIT 500"),
            Map.entry("REG_PO_BALANCE",     "SELECT * FROM ims.reg_po_balance ORDER BY po_date DESC LIMIT 500")
    );

    private static final Map<String, String> REGISTER_NAMES = Map.ofEntries(
            Map.entry("REG_SALES_INVOICE",      "Sales Invoice Register"),
            Map.entry("REG_PURCHASE_ORDER",     "Purchase Order Register"),
            Map.entry("REG_PAYMENT",            "Payment Register"),
            Map.entry("REG_QUOTATION",          "Quotation Register"),
            Map.entry("REG_PO_ITEMS",           "Purchase Order Items Register"),
            Map.entry("REG_SO_ITEMS",           "Sales Order Items Register"),
            Map.entry("REG_INVOICE_ITEMS",      "Sales Invoice Items Register"),
            Map.entry("REG_ADJUSTMENT_NOTES",   "Adjustment Notes Register"),
            Map.entry("REG_LAST_PRICE",         "Last Purchase Price Register"),
            Map.entry("REG_PRODUCTION_SUMMARY", "Production Summary Register"),
            Map.entry("REG_PO_BALANCE",         "Purchase Order Balance Register")
    );

    private final JdbcTemplate jdbc;

    public RegisterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> listCodes() {
        return List.copyOf(REGISTER_QUERIES.keySet());
    }

    public RegisterDto get(String code) {
        String sql = REGISTER_QUERIES.get(code.toUpperCase());
        if (sql == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Register not found: " + code);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return new RegisterDto(code, REGISTER_NAMES.getOrDefault(code, code), rows);
    }
}
