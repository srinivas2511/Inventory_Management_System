package com.springmfg.ims.finance.ar.web;

import com.springmfg.ims.finance.ar.dto.CreditNoteDtos.CreateCreditNoteRequest;
import com.springmfg.ims.finance.ar.dto.CreditNoteDtos.CreditNoteDto;
import com.springmfg.ims.finance.ar.service.CreditNoteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ar/credit-notes")
public class CreditNoteController {

    private final CreditNoteService service;

    public CreditNoteController(CreditNoteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('AR_MANAGE','CREDIT_NOTE_APPROVE')")
    public Page<CreditNoteDto> list(@RequestParam(required = false) Long invoiceId,
                                    @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.list(invoiceId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('AR_MANAGE','CREDIT_NOTE_APPROVE')")
    public CreditNoteDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AR_MANAGE')")
    public CreditNoteDto create(@Valid @RequestBody CreateCreditNoteRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('CREDIT_NOTE_APPROVE')")
    public CreditNoteDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('AR_MANAGE')")
    public CreditNoteDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
