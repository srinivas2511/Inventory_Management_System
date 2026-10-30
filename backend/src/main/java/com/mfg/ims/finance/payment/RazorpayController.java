package com.mfg.ims.finance.payment;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Razorpay payment gateway endpoints (16.5).
 * Create a payment order for an AR invoice and verify the payment signature.
 */
@RestController
@RequestMapping("/api/finance/payments/razorpay")
public class RazorpayController {

    private final RazorpayService razorpayService;

    public RazorpayController(RazorpayService razorpayService) {
        this.razorpayService = razorpayService;
    }

    public record CreateOrderRequest(Long invoiceId, String currency) {}

    public record CreateOrderResponse(
            Long dbId,
            String razorpayOrderId,
            Long amount,
            String currency,
            String keyId) {}

    public record VerifyRequest(
            Long dbId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature,
            Long invoiceId) {}

    public record VerifyResponse(boolean success, String message) {}

    @PostMapping("/create-order")
    @PreAuthorize("hasAuthority('PAYMENT_GATEWAY_USE')")
    public CreateOrderResponse createOrder(@RequestBody CreateOrderRequest req,
                                            @AuthenticationPrincipal CachedUser user) {
        RazorpayService.OrderResponse resp = razorpayService.createOrder(
                new RazorpayService.CreateOrderRequest(req.invoiceId(), req.currency()),
                user.id());
        return new CreateOrderResponse(resp.dbId(), resp.razorpayOrderId(),
                resp.amountPaise(), resp.currency(), resp.keyId());
    }

    @PostMapping("/verify-signature")
    @PreAuthorize("hasAuthority('PAYMENT_GATEWAY_USE')")
    public VerifyResponse verify(@RequestBody VerifyRequest req,
                                  @AuthenticationPrincipal CachedUser user) {
        boolean valid = razorpayService.verifyAndCapture(new RazorpayService.VerifyRequest(
                req.dbId(), req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature()));
        if (valid) {
            return new VerifyResponse(true, "Payment recorded");
        } else {
            return new VerifyResponse(false, "Payment signature verification failed");
        }
    }
}
