package com.mfg.ims.doccore.paymentterms;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lookups/payment-terms")
public class PaymentTermLookupController {

    private final PaymentTermService service;

    public PaymentTermLookupController(PaymentTermService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PaymentTermDto> list() {
        return service.list();
    }
}
