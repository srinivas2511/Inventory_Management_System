package com.mfg.ims.integration.gst;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.integration.gst.GstEInvoiceDtos.DownloadResponse;
import com.mfg.ims.integration.gst.GstEInvoiceDtos.EInvoiceDto;
import com.mfg.ims.integration.gst.GstEInvoiceDtos.GenerateRequest;

@RestController
@RequestMapping("/api/integration/gst-einvoice")
public class GstEInvoiceController {

    private final GstEInvoiceService service;

    public GstEInvoiceController(GstEInvoiceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GST_EINVOICE_VIEW')")
    public List<EInvoiceDto> findAll() {
        return service.findAll();
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('GST_EINVOICE_GENERATE')")
    public EInvoiceDto generate(
        @RequestBody GenerateRequest req,
        @AuthenticationPrincipal CachedUser principal
    ) {
        return service.generate(req, principal.id());
    }

    @GetMapping("/{id}/payload")
    @PreAuthorize("hasAuthority('GST_EINVOICE_VIEW')")
    public ResponseEntity<String> downloadPayload(@PathVariable Long id) {
        String json = service.getPayload(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"einvoice-" + id + ".json\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json);
    }
}
