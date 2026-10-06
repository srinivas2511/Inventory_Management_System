package com.springmfg.ims.integration.webhook.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.integration.webhook.dto.WebhookDtos.SaveWebhookRequest;
import com.springmfg.ims.integration.webhook.dto.WebhookDtos.WebhookDeliveryDto;
import com.springmfg.ims.integration.webhook.dto.WebhookDtos.WebhookDto;
import com.springmfg.ims.integration.webhook.dto.WebhookDtos.WebhookSecretDto;
import com.springmfg.ims.integration.webhook.service.WebhookAdminService;

/** Webhook administration (INT-002). The signing secret is returned once, by create and rotate, and never again. */
@RestController
@RequestMapping("/api/integration")
public class WebhookController {

    private final WebhookAdminService service;

    public WebhookController(WebhookAdminService service) {
        this.service = service;
    }

    @GetMapping("/webhooks")
    @PreAuthorize("hasAuthority('WEBHOOK_VIEW')")
    public List<WebhookDto> list() {
        return service.list();
    }

    @GetMapping("/webhooks/event-types")
    @PreAuthorize("hasAuthority('WEBHOOK_VIEW')")
    public List<String> eventTypes() {
        return service.eventTypes();
    }

    @GetMapping("/webhooks/{id}")
    @PreAuthorize("hasAuthority('WEBHOOK_VIEW')")
    public WebhookDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/webhooks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('WEBHOOK_MANAGE')")
    public WebhookSecretDto create(@Valid @RequestBody SaveWebhookRequest request) {
        return service.create(request);
    }

    @PutMapping("/webhooks/{id}")
    @PreAuthorize("hasAuthority('WEBHOOK_MANAGE')")
    public WebhookDto update(@PathVariable Long id, @Valid @RequestBody SaveWebhookRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/webhooks/{id}/rotate-secret")
    @PreAuthorize("hasAuthority('WEBHOOK_MANAGE')")
    public WebhookSecretDto rotateSecret(@PathVariable Long id) {
        return service.rotateSecret(id);
    }

    @PostMapping("/webhooks/{id}/test")
    @PreAuthorize("hasAuthority('WEBHOOK_MANAGE')")
    public WebhookDeliveryDto test(@PathVariable Long id) {
        return service.sendTest(id);
    }

    @GetMapping("/webhook-deliveries")
    @PreAuthorize("hasAuthority('WEBHOOK_VIEW')")
    public Page<WebhookDeliveryDto> deliveries(
            @RequestParam(required = false) Long subscriptionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.deliveries(subscriptionId, status,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id")));
    }

    @PostMapping("/webhook-deliveries/{id}/retry")
    @PreAuthorize("hasAuthority('WEBHOOK_MANAGE')")
    public WebhookDeliveryDto retry(@PathVariable Long id) {
        return service.retry(id);
    }
}
