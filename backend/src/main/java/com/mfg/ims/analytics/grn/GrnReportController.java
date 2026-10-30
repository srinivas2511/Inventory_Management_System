package com.mfg.ims.analytics.grn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.analytics.register.RegisterService;
import com.mfg.ims.procurement.dto.GrnMismatchDto;
import com.mfg.ims.procurement.dto.GrnPendingDto;

@RestController
@RequestMapping("/api/procurement/grn-reports")
public class GrnReportController {

    private final RegisterService registerService;

    public GrnReportController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public List<GrnPendingDto> pending() {
        return registerService.grnPending().stream()
                .map(r -> new GrnPendingDto(
                        toLong(r.get("grn_id")),
                        (String) r.get("grn_number"),
                        toLong(r.get("supplier_id")),
                        (String) r.get("supplier_name"),
                        toLocalDate(r.get("receipt_date")),
                        (String) r.get("status")))
                .toList();
    }

    @GetMapping("/invoice-mismatch")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public List<GrnMismatchDto> invoiceMismatch() {
        return registerService.grnInvoiceMismatch().stream()
                .map(r -> new GrnMismatchDto(
                        toLong(r.get("grn_id")),
                        (String) r.get("grn_number"),
                        toLong(r.get("invoice_id")),
                        (String) r.get("invoice_number"),
                        toBigDecimal(r.get("invoice_value")),
                        toBigDecimal(r.get("grn_value")),
                        toBigDecimal(r.get("mismatch_amount"))))
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }

    private static LocalDate toLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(v.toString());
    }
}
