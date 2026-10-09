package com.springmfg.ims.sales.web;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.DispatchDto;
import com.springmfg.ims.sales.dto.DispatchNoteDto;
import com.springmfg.ims.sales.service.DispatchDocumentRenderer;
import com.springmfg.ims.sales.service.DispatchService;

@RestController
@RequestMapping("/api/sales/dispatches")
public class DispatchController {

    private final DispatchService service;
    private final DispatchDocumentRenderer renderer;

    public DispatchController(DispatchService service, DispatchDocumentRenderer renderer) {
        this.service = service;
        this.renderer = renderer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DISPATCH_CREATE')")
    public DispatchDto create(@Valid @RequestBody CreateDispatchRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE','SALES_VIEW')")
    public DispatchDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE','SALES_VIEW')")
    public Page<DispatchDto> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.search(customerId, from, to, pageable);
    }

    @GetMapping("/{id}/dispatch-note")
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE')")
    public DispatchNoteDto dispatchNote(@PathVariable Long id) {
        return service.generateDispatchNote(id);
    }

    /** SAL-005: the printable delivery note (prices and values). */
    @GetMapping(value = "/{id}/delivery-note.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE')")
    public ResponseEntity<byte[]> deliveryNotePdf(@PathVariable Long id) {
        DispatchNoteDto note = service.generateDispatchNote(id);
        return pdf(renderer.deliveryNote(note), fileSafe(note.dispatchNumber()) + "-delivery-note.pdf");
    }

    /** SAL-005: the printable packing list (batches and quantities, no prices). */
    @GetMapping(value = "/{id}/packing-list.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('DISPATCH_VIEW','DISPATCH_CREATE')")
    public ResponseEntity<byte[]> packingListPdf(@PathVariable Long id) {
        DispatchNoteDto note = service.generateDispatchNote(id);
        return pdf(renderer.packingList(note), fileSafe(note.dispatchNumber()) + "-packing-list.pdf");
    }

    /** Dispatch numbers look like DN/26/00001; a file name must not carry path separators. */
    private static String fileSafe(String documentNumber) {
        return documentNumber == null ? "dispatch" : documentNumber.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(body);
    }
}
