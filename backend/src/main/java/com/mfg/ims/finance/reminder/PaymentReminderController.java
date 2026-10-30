package com.mfg.ims.finance.reminder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/payment-reminders")
public class PaymentReminderController {

    private final PaymentReminderService service;

    public PaymentReminderController(PaymentReminderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REMINDER_MANAGE')")
    public List<PaymentReminderDto> list(@RequestParam(required = false) Long partyId) {
        return service.list(partyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REMINDER_MANAGE')")
    public PaymentReminderDto create(@Valid @RequestBody PaymentReminderDto req,
                                     @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }
}
