package com.mfg.ims.sales.challan;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.mfg.ims.notification.service.EmailSender;

@RestController
@RequestMapping("/api/sales/delivery-challans")
public class ChallanController {

    private final ChallanService service;
    private final ChallanRenderer renderer;
    private final EmailSender emailSender;

    public ChallanController(ChallanService service, ChallanRenderer renderer,
                             EmailSender emailSender) {
        this.service = service;
        this.renderer = renderer;
        this.emailSender = emailSender;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CHALLAN_VIEW')")
    public List<ChallanDto> list() {
        return service.list(1L);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CHALLAN_VIEW')")
    public ChallanDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CHALLAN_CREATE')")
    public ChallanDto create(@Valid @RequestBody SaveChallanRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('CHALLAN_ISSUE')")
    public ChallanDto issue(@PathVariable Long id) {
        return service.issue(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CHALLAN_RETURN')")
    public ChallanDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    /** 11.4: Mark a RETURNABLE challan as returned. */
    @PostMapping("/{id}/mark-returned")
    @PreAuthorize("hasAuthority('CHALLAN_RETURN')")
    public ChallanDto markReturned(@PathVariable Long id) {
        return service.markReturned(id);
    }

    /**
     * 11.5: Download the challan as a PDF.
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('CHALLAN_VIEW')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        var challan = service.get(id);
        var customer = service.customerInfo(challan.customerId());
        String customerName = customer.name() != null ? customer.name() : "";
        String customerAddress = customer.address() != null ? customer.address() : "";
        byte[] pdfBytes = renderer.render(challan, customerName, customerAddress);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "challan-" + challan.docNo().replace("/", "-") + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    /**
     * 11.5: Send challan PDF to the customer's email address.
     * Returns 200 with a skip message if customer has no email configured.
     */
    @PostMapping("/{id}/send-email")
    @PreAuthorize("hasAuthority('CHALLAN_ISSUE')")
    public Map<String, String> sendEmail(@PathVariable Long id) {
        var challan = service.get(id);
        var customer = service.customerInfo(challan.customerId());
        if (customer.email() == null || customer.email().isBlank()) {
            return Map.of("message", "Email skipped - no customer email");
        }
        String customerName = customer.name() != null ? customer.name() : "";
        String customerAddress = customer.address() != null ? customer.address() : "";
        emailSender.send(
                customer.email(),
                "Delivery Challan " + challan.docNo(),
                "Dear " + ChallanRenderer.safe(customerName) + ",\n\n"
                + "Please find attached delivery challan " + challan.docNo()
                + " dated " + (challan.docDate() != null ? challan.docDate().toString() : "")
                + " for your reference.\n\n"
                + "Delivery address: " + ChallanRenderer.safe(challan.deliveryAddress() != null
                        ? challan.deliveryAddress() : customerAddress)
                + "\n\nThank you for your business."
        );
        return Map.of("message", "Email sent to " + customer.email());
    }
}
