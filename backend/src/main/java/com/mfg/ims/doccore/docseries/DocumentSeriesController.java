package com.mfg.ims.doccore.docseries;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/document-series")
public class DocumentSeriesController {

    private final DocumentSeriesService service;

    public DocumentSeriesController(DocumentSeriesService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DOCUMENT_SERIES_MANAGE','SETTINGS_MANAGE')")
    public List<DocumentSeriesDto> list(@RequestParam(required = false) String documentKind) {
        return service.list(documentKind);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT_SERIES_MANAGE')")
    public DocumentSeriesDto create(@Valid @RequestBody SaveDocumentSeriesRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_SERIES_MANAGE')")
    public DocumentSeriesDto update(@PathVariable Long id, @Valid @RequestBody SaveDocumentSeriesRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DOCUMENT_SERIES_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
