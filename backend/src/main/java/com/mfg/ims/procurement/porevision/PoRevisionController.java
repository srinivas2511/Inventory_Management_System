package com.mfg.ims.procurement.porevision;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-orders")
public class PoRevisionController {

    private final PoRevisionService service;

    public PoRevisionController(PoRevisionService service) {
        this.service = service;
    }

    public record AmendRequest(@NotBlank String reason) {}

    @PostMapping("/{id}/amend")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_AMEND')")
    public PoRevisionDto amend(@PathVariable Long id, @Valid @RequestBody AmendRequest req) {
        return service.amend(id, req.reason());
    }

    @GetMapping("/{id}/revisions")
    @PreAuthorize("hasAnyAuthority('PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_APPROVE')")
    public List<PoRevisionDto> revisions(@PathVariable Long id) {
        return service.getHistory(id);
    }
}
