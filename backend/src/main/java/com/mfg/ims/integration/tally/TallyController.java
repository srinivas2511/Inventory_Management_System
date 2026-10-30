package com.mfg.ims.integration.tally;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tally import files. Without dates the vouchers cover the current month to date. */
@RestController
@RequestMapping("/api/integration/tally")
public class TallyController {

    private final TallyExportService service;
    private final TallyLedgerMappingService ledgerMappingService;
    private final TallyVoucherQueueService voucherQueueService;

    public TallyController(TallyExportService service,
                            TallyLedgerMappingService ledgerMappingService,
                            TallyVoucherQueueService voucherQueueService) {
        this.service = service;
        this.ledgerMappingService = ledgerMappingService;
        this.voucherQueueService = voucherQueueService;
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

    /**
     * Returns a readiness checklist for the Tally integration:
     * - Company GSTIN configured
     * - Tally ledger mappings count
     * - Pending (APPROVED) vouchers count
     */
    @GetMapping("/readiness")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public TallyReadinessDto readiness() {
        List<TallyReadinessDto.CheckItem> checks = new ArrayList<>();

        // Check 1 — Company GSTIN
        String gstin = voucherQueueService.companyGstin();
        boolean gstinOk = gstin != null && !gstin.isBlank();
        checks.add(new TallyReadinessDto.CheckItem(
                "Company GSTIN configured",
                gstinOk,
                gstinOk ? gstin : "No GSTIN found — configure in Company Settings"
        ));

        // Check 2 — Tally ledger mappings
        int mappingCount = ledgerMappingService.list().size();
        boolean mappingsOk = mappingCount > 0;
        checks.add(new TallyReadinessDto.CheckItem(
                "Tally ledger mappings",
                mappingsOk,
                mappingCount + " mapping" + (mappingCount == 1 ? "" : "s") + " configured"
        ));

        // Check 3 — Pending vouchers (APPROVED, awaiting dispatch to Tally)
        int pendingCount = voucherQueueService.countByStatus("APPROVED");
        boolean pendingOk = pendingCount == 0;
        checks.add(new TallyReadinessDto.CheckItem(
                "Pending vouchers",
                pendingOk,
                pendingCount == 0 ? "No vouchers awaiting sync"
                        : pendingCount + " voucher" + (pendingCount == 1 ? "" : "s") + " awaiting sync"
        ));

        boolean ready = checks.stream().allMatch(TallyReadinessDto.CheckItem::ok);
        return new TallyReadinessDto(checks, ready);
    }

    // ── Day-book ──────────────────────────────────────────────────────────────

    @GetMapping("/day-book")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public List<TallyVoucherQueueService.DayBookEntryDto> dayBook(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return voucherQueueService.dayBook(fromDate, toDate);
    }

    // ── Reconciliation ────────────────────────────────────────────────────────

    @GetMapping("/reconciliation-status")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public TallyVoucherQueueService.ReconciliationStatusDto reconciliationStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return voucherQueueService.reconciliationStatus(fromDate, toDate);
    }

    public record ReconcileRequest(List<Long> voucherQueueIds) {}

    @PostMapping("/reconcile")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public void reconcile(@RequestBody ReconcileRequest req) {
        voucherQueueService.reconcile(req.voucherQueueIds());
    }

    private static ResponseEntity<String> xml(String filename, String body) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(body);
    }
}
