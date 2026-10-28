package com.mfg.ims.procurement.rfq;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rfqs")
public class RfqController {

    private final RfqService service;

    public RfqController(RfqService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RFQ_VIEW')")
    public List<RfqDto> list() {
        return service.list(1L);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RFQ_VIEW')")
    public RfqDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RFQ_CREATE')")
    public RfqDto create(@Valid @RequestBody SaveRfqRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('RFQ_SEND')")
    public RfqDto send(@PathVariable Long id) {
        return service.send(id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('RFQ_AWARD')")
    public RfqDto close(@PathVariable Long id) {
        return service.close(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('RFQ_UPDATE')")
    public RfqDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
