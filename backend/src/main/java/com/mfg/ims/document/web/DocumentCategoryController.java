package com.mfg.ims.document.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.document.dto.DocumentDtos.CategoryDto;
import com.mfg.ims.document.dto.DocumentDtos.SaveCategoryRequest;
import com.mfg.ims.document.service.DocumentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/document-categories")
public class DocumentCategoryController {

    private final DocumentService service;

    public DocumentCategoryController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
    public List<CategoryDto> list() {
        return service.listCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public CategoryDto create(@Valid @RequestBody SaveCategoryRequest req) {
        return service.saveCategory(null, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_MANAGE')")
    public CategoryDto update(@PathVariable Long id, @Valid @RequestBody SaveCategoryRequest req) {
        return service.saveCategory(id, req);
    }
}
