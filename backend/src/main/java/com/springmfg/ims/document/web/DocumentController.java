package com.springmfg.ims.document.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.springmfg.ims.document.domain.DocumentStatus;
import com.springmfg.ims.document.dto.DocumentDtos.AddVersionRequest;
import com.springmfg.ims.document.dto.DocumentDtos.CreateDocumentRequest;
import com.springmfg.ims.document.dto.DocumentDtos.DocumentDto;
import com.springmfg.ims.document.dto.DocumentDtos.DocumentSummaryDto;
import com.springmfg.ims.document.dto.DocumentDtos.LinkRequest;
import com.springmfg.ims.document.dto.DocumentDtos.ReasonRequest;
import com.springmfg.ims.document.service.DocumentService;
import com.springmfg.ims.document.service.DocumentService.DocumentDownload;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
    public Page<DocumentSummaryDto> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) DocumentStatus status,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) Long entityId,
                                         @PageableDefault(size = 50, sort = "updatedAt", direction = Sort.Direction.DESC)
                                         Pageable pageable) {
        return service.search(q, category, status, entityType, entityId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
    public DocumentDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public DocumentDto create(@RequestPart("meta") @Valid CreateDocumentRequest meta,
                              @RequestPart("file") MultipartFile file) {
        return service.create(meta, file);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public DocumentDto addVersion(@PathVariable Long id,
                                  @RequestPart(value = "meta", required = false) @Valid AddVersionRequest meta,
                                  @RequestPart("file") MultipartFile file) {
        return service.addVersion(id, meta, file);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id,
                                                        @RequestParam(required = false) Integer version) {
        DocumentDownload d = service.download(id, version);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mimeType()))
                .contentLength(d.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.filename(), StandardCharsets.UTF_8).build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(d.content()));
    }

    @PostMapping("/{id}/links")
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public DocumentDto addLink(@PathVariable Long id, @Valid @RequestBody LinkRequest req) {
        return service.addLink(id, req);
    }

    @DeleteMapping("/{id}/links/{linkId}")
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public DocumentDto removeLink(@PathVariable Long id, @PathVariable Long linkId) {
        return service.removeLink(id, linkId);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public DocumentDto submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('DOCUMENT_APPROVE')")
    public DocumentDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('DOCUMENT_APPROVE')")
    public DocumentDto reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return service.reject(id, req.reason());
    }

    @PostMapping("/{id}/obsolete")
    @PreAuthorize("hasAuthority('DOCUMENT_APPROVE')")
    public DocumentDto obsolete(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return service.obsolete(id, req.reason());
    }
}
