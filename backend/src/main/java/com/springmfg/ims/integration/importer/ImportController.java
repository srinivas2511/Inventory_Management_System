package com.springmfg.ims.integration.importer;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.springmfg.ims.integration.importer.ImportDtos.ImportResult;

/** Bulk master-data import. {@code dryRun} defaults to true: validate first, then repeat with dryRun=false. */
@RestController
@RequestMapping("/api/integration/import")
public class ImportController {

    private final BulkImportService service;

    public ImportController(BulkImportService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DATA_IMPORT')")
    public List<String> entities() {
        return service.entities();
    }

    @GetMapping("/{entity}/template")
    @PreAuthorize("hasAuthority('DATA_IMPORT')")
    public ResponseEntity<String> template(@PathVariable String entity) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + entity.replaceAll("[^A-Za-z]", "") + "-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.template(entity));
    }

    @PostMapping(value = "/{entity}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DATA_IMPORT')")
    public ImportResult importFile(@PathVariable String entity, @RequestParam("file") MultipartFile file,
                                   @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
        return service.importFile(entity, file, dryRun);
    }
}
