package com.mfg.ims.doccore.importjob;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
public class ImportJobController {

    private final ImportJobService service;

    public ImportJobController(ImportJobService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('IMPORT_RUN','IMPORT_VIEW')")
    public List<ImportJobDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('IMPORT_RUN','IMPORT_VIEW')")
    public ImportJobDto get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('IMPORT_RUN')")
    public ImportJobDto create(@RequestParam String entityType,
                               @RequestParam("file") MultipartFile file) throws IOException {
        return service.create(entityType, file);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IMPORT_RUN')")
    public ImportJobDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
