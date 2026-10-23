package com.mfg.ims.finance.ap.web;

import com.mfg.ims.finance.ap.dto.ApDebitNoteDtos.ApDebitNoteDto;
import com.mfg.ims.finance.ap.dto.ApDebitNoteDtos.CreateDebitNoteRequest;
import com.mfg.ims.finance.ap.service.ApDebitNoteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ap/debit-notes")
public class ApDebitNoteController {

    private final ApDebitNoteService service;

    public ApDebitNoteController(ApDebitNoteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','DEBIT_NOTE_APPROVE')")
    public Page<ApDebitNoteDto> list(@RequestParam(required = false) Long invoiceId,
                                     @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.list(invoiceId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','DEBIT_NOTE_APPROVE')")
    public ApDebitNoteDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public ApDebitNoteDto create(@Valid @RequestBody CreateDebitNoteRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('DEBIT_NOTE_APPROVE')")
    public ApDebitNoteDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public ApDebitNoteDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
