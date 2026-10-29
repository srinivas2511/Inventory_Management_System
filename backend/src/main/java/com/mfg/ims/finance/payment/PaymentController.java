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
    private final RazorpayService razorpay;

    public PaymentController(PaymentService service, RazorpayService razorpay) {
        this.service = service;
        this.razorpay = razorpay;
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

    @PostMapping("/razorpay/create-order")
    @PreAuthorize("hasAuthority('PAYMENT_GATEWAY_USE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RazorpayService.OrderResponse razorpayCreateOrder(
            @RequestBody RazorpayService.CreateOrderRequest req,
            @AuthenticationPrincipal CachedUser actor) {
        return razorpay.createOrder(req, actor.id());
    }

    @PostMapping("/razorpay/verify-signature")
    @PreAuthorize("hasAuthority('PAYMENT_GATEWAY_USE')")
    public java.util.Map<String, Object> razorpayVerify(
            @RequestBody RazorpayService.VerifyRequest req) {
        boolean ok = razorpay.verifyAndCapture(req);
        return java.util.Map.of("verified", ok);
    }
}
