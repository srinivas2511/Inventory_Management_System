package com.mfg.ims.finance.gstfiling;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.mfg.ims.finance.gstfiling.GstFilingDtos.*;

@RestController
@RequestMapping("/api/finance/gst")
public class GstFilingController {

    private final GstFilingService service;

    public GstFilingController(GstFilingService service) {
        this.service = service;
    }

    @GetMapping("/gstr1")
    @PreAuthorize("hasAuthority('GST_REPORT_VIEW')")
    public Gstr1Dto gstr1(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.gstr1(from, to);
    }

    @GetMapping("/gstr3b")
    @PreAuthorize("hasAuthority('GST_REPORT_VIEW')")
    public Gstr3bDto gstr3b(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.gstr3b(from, to);
    }

    @PostMapping("/eway-bill")
    @PreAuthorize("hasAuthority('EWAY_BILL_CREATE')")
    public ResponseEntity<Map<String, Object>> ewayBill(@RequestBody EwayBillRequest req) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.ewayBillJson(req.salesInvoiceId()));
    }

    @PostMapping("/gstr1/file")
    @PreAuthorize("hasAuthority('GST_REPORT_FILE')")
    public FilingRunDto fileGstr1(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.markFiled("GSTR1", from, to);
    }

    @PostMapping("/gstr3b/file")
    @PreAuthorize("hasAuthority('GST_REPORT_FILE')")
    public FilingRunDto fileGstr3b(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.markFiled("GSTR3B", from, to);
    }

    @GetMapping("/filing-history")
    @PreAuthorize("hasAuthority('GST_REPORT_VIEW')")
    public List<FilingRunDto> filingHistory() {
        return service.filingHistory();
    }
}
