package com.mfg.ims.integration.tally;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tally import files. Without dates the vouchers cover the current month to date. */
@RestController
@RequestMapping("/api/integration/tally")
public class TallyController {

    private final TallyExportService service;

    public TallyController(TallyExportService service) {
        this.service = service;
    }

    @GetMapping("/ledgers")
    @PreAuthorize("hasAuthority('TALLY_EXPORT')")
    public ResponseEntity<String> ledgers() {
        return xml("tally-ledgers.xml", service.ledgers());
    }

    @GetMapping("/vouchers")
    @PreAuthorize("hasAuthority('TALLY_EXPORT')")
    public ResponseEntity<String> vouchers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return xml("tally-vouchers.xml", service.vouchers(from, to));
    }

    private static ResponseEntity<String> xml(String filename, String body) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(body);
    }
}
