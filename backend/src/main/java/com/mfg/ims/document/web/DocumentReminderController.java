package com.mfg.ims.document.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.document.dto.DocumentDtos.CreateReminderRequest;
import com.mfg.ims.document.dto.DocumentDtos.ReminderDto;
import com.mfg.ims.document.service.DocumentReminderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reminders")
public class DocumentReminderController {

    private final DocumentReminderService service;

    public DocumentReminderController(DocumentReminderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('REMINDER_CREATE','DOCUMENT_VIEW')")
    public List<ReminderDto> list(@RequestParam String entityType, @RequestParam Long entityId) {
        return service.listReminders(entityType, entityId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REMINDER_CREATE')")
    public ReminderDto create(@Valid @RequestBody CreateReminderRequest body) {
        return service.createReminder(body, currentUserId());
    }

    @PutMapping("/{id}/dismiss")
    @PreAuthorize("hasAuthority('REMINDER_CREATE')")
    public ReminderDto dismiss(@PathVariable Long id) {
        return service.dismissReminder(id);
    }

    @GetMapping("/my-upcoming")
    @PreAuthorize("hasAnyAuthority('REMINDER_CREATE','DOCUMENT_VIEW')")
    public List<ReminderDto> myUpcoming() {
        return service.myUpcoming(currentUserId());
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        return null;
    }
}
