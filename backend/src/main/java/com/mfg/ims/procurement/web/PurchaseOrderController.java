package com.mfg.ims.procurement.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.dto.PurchaseOrderDto;
import com.mfg.ims.procurement.porevision.PoRevisionDto;
import com.mfg.ims.procurement.porevision.PoRevisionService;
import com.mfg.ims.procurement.service.PurchaseOrderService;

@RestController
@RequestMapping("/api/procurement/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    private final PurchaseOrderRenderer renderer;
    private final PoRevisionService revisionService;

    public PurchaseOrderController(PurchaseOrderService service, PurchaseOrderRenderer renderer,
            PoRevisionService revisionService) {
        this.service = service;
        this.renderer = renderer;
        this.revisionService = revisionService;
    }

    public record CancelRequest(@NotBlank String reason) {}

    public record AmendRequest(@NotBlank String reason) {}

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_APPROVE')")
    public Page<PurchaseOrderDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, supplierId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_APPROVE')")
    public PurchaseOrderDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseOrderDto create(@Valid @RequestBody CreatePoRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseOrderDto submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')")
    public PurchaseOrderDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/send-to-supplier")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')")
    public PurchaseOrderDto sendToSupplier(@PathVariable Long id) {
        return service.sendToSupplier(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('PURCHASE_CREATE','PURCHASE_APPROVE')")
    public PurchaseOrderDto cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return service.cancel(id, req.reason());
    }

    /** Record a revision / amendment reason for an APPROVED or SENT purchase order. */
    @PostMapping("/{id}/amend")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_AMEND')")
    public PoRevisionDto amend(@PathVariable Long id, @Valid @RequestBody AmendRequest req) {
        return revisionService.amend(id, req.reason());
    }

    /** Return the full amendment history for a purchase order. */
    @GetMapping("/{id}/revisions")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_APPROVE','PURCHASE_AMEND')")
    public List<PoRevisionDto> revisions(@PathVariable Long id) {
        return revisionService.getHistory(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = "application/pdf")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_APPROVE')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        PurchaseOrderDto po = service.findById(id);
        byte[] bytes = renderer.render(po);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"po-" + po.poNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
