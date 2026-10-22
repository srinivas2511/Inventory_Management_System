package com.mfg.ims.finance.ar.web;

import com.mfg.ims.finance.ar.dto.ArAgeingRow;
import com.mfg.ims.finance.ar.dto.ArInvoiceDto;
import com.mfg.ims.finance.ar.dto.ArReceiptRequest;
import com.mfg.ims.finance.ar.service.ArService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ar")
public class ArController {

    private final ArService arService;

    public ArController(ArService arService) {
        this.arService = arService;
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('AR_MANAGE')")
    public Page<ArInvoiceDto> listInvoices(@PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return arService.findAll(pageable);
    }

    @PostMapping("/invoices/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AR_MANAGE')")
    public ArInvoiceDto recordReceipt(@PathVariable Long id,
                                      @Valid @RequestBody ArReceiptRequest req) {
        return arService.recordReceipt(id, req);
    }

    @GetMapping("/invoices/{id}/receipts")
    @PreAuthorize("hasAuthority('AR_MANAGE')")
    public List<com.mfg.ims.finance.ap.dto.SettlementDto> receipts(@PathVariable Long id) {
        return arService.receipts(id);
    }

    @GetMapping("/ageing")
    @PreAuthorize("hasAuthority('FINANCE_REPORT')")
    public List<ArAgeingRow> ageing() {
        return arService.getAgeing();
    }
}
