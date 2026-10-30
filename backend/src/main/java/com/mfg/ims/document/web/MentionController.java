package com.mfg.ims.document.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.document.dto.DocumentDtos.CreateMentionRequest;
import com.mfg.ims.document.dto.DocumentDtos.MentionDto;
import com.mfg.ims.document.service.DocumentReminderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mentions")
public class MentionController {

    private final DocumentReminderService service;

    public MentionController(DocumentReminderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('REMINDER_CREATE','DOCUMENT_VIEW')")
    public MentionDto create(@Valid @RequestBody CreateMentionRequest body) {
        return service.createMention(body, currentUserId());
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        return null;
    }
}
