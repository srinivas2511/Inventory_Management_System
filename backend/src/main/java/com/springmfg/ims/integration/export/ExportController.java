package com.springmfg.ims.integration.export;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.springmfg.ims.integration.export.ExportService.ExportSpec;

/** Streamed full-entity export (CSV or JSON). The access check and the audit record happen before streaming starts. */
@RestController
@RequestMapping("/api/integration/export")
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DATA_EXPORT')")
    public List<String> entities() {
        return service.entities();
    }

    @GetMapping("/{entity}")
    @PreAuthorize("hasAuthority('DATA_EXPORT')")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable String entity,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String f = format.toLowerCase();
        ExportSpec spec = service.begin(entity, f, from, to);
        StreamingResponseBody body = out -> service.stream(spec, f, from, to, out);
        MediaType type = "csv".equals(f) ? MediaType.parseMediaType("text/csv") : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + spec.entity() + "-" + LocalDate.now() + "." + f + "\"")
                .contentType(type)
                .body(body);
    }
}
