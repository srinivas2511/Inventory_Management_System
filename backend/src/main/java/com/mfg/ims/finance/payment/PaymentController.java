package com.mfg.ims.finance.payment;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public List<PaymentDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public PaymentDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public PaymentDto create(@Valid @RequestBody SavePaymentRequest req,
                             @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('PAYMENT_POST')")
    public PaymentDto post(@PathVariable Long id,
                           @AuthenticationPrincipal CachedUser actor) {
        return service.post(id, actor.id());
    }
}
