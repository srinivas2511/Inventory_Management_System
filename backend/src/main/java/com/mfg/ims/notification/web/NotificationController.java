package com.mfg.ims.notification.web;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.notification.dto.NotificationDtos.NotificationDto;
import com.mfg.ims.notification.dto.NotificationDtos.PreferenceDto;
import com.mfg.ims.notification.dto.NotificationDtos.SetPreferenceRequest;
import com.mfg.ims.notification.dto.NotificationDtos.UnreadCountDto;
import com.mfg.ims.notification.service.NotificationInboxService;

import jakarta.validation.Valid;

/** The signed-in user's own notifications and preferences; any authenticated user may use them. */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationInboxService service;

    public NotificationController(NotificationInboxService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    public Page<NotificationDto> inbox(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                       @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                       Pageable pageable) {
        return service.inbox(unreadOnly, pageable);
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("isAuthenticated()")
    public UnreadCountDto unreadCount() {
        return service.unreadCount();
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public NotificationDto markRead(@PathVariable Long id) {
        return service.markRead(id);
    }

    @PostMapping("/notifications/read-all")
    @PreAuthorize("isAuthenticated()")
    public UnreadCountDto markAllRead() {
        return service.markAllRead();
    }

    @GetMapping("/notification-preferences")
    @PreAuthorize("isAuthenticated()")
    public List<PreferenceDto> preferences() {
        return service.preferences();
    }

    @PutMapping("/notification-preferences")
    @PreAuthorize("isAuthenticated()")
    public List<PreferenceDto> setPreference(@Valid @RequestBody SetPreferenceRequest req) {
        return service.setPreference(req);
    }
}
