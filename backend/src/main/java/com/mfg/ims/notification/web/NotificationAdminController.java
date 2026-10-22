package com.mfg.ims.notification.web;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationStatus;
import com.mfg.ims.notification.dto.NotificationDtos.DeliveryLogDto;
import com.mfg.ims.notification.dto.NotificationDtos.SaveTemplateRequest;
import com.mfg.ims.notification.dto.NotificationDtos.TemplateDto;
import com.mfg.ims.notification.service.NotificationAdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class NotificationAdminController {

    private final NotificationAdminService service;

    public NotificationAdminController(NotificationAdminService service) {
        this.service = service;
    }

    @GetMapping("/notification-templates")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public List<TemplateDto> templates() {
        return service.templates();
    }

    @PutMapping("/notification-templates/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public TemplateDto update(@PathVariable Long id, @Valid @RequestBody SaveTemplateRequest req) {
        return service.updateTemplate(id, req);
    }

    @GetMapping("/notification-log")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public Page<DeliveryLogDto> log(@RequestParam(required = false) NotificationStatus status,
                                    @RequestParam(required = false) NotificationChannel channel,
                                    @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
                                    Pageable pageable) {
        return service.log(status, channel, pageable);
    }
}
