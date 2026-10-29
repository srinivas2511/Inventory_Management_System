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

    private static final Map<String, String> REGISTER_QUERIES = Map.of(
            "REG_SALES_INVOICE",  "SELECT * FROM ims.reg_sales_invoice ORDER BY id DESC LIMIT 500",
            "REG_PURCHASE_ORDER", "SELECT * FROM ims.reg_purchase_order ORDER BY id DESC LIMIT 500",
            "REG_PAYMENT",        "SELECT * FROM ims.reg_payment ORDER BY id DESC LIMIT 500"
    );

    private static final Map<String, String> REGISTER_NAMES = Map.of(
            "REG_SALES_INVOICE",  "Sales Invoice Register",
            "REG_PURCHASE_ORDER", "Purchase Order Register",
            "REG_PAYMENT",        "Payment Register"
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
