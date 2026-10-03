package com.springmfg.ims.mrp.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.mrp.dto.DiscardProposalRequest;
import com.springmfg.ims.mrp.dto.MrpProposalDto;
import com.springmfg.ims.mrp.service.MrpProposalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mrp/proposals")
public class MrpProposalController {

    private final MrpProposalService service;

    public MrpProposalController(MrpProposalService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MRP_VIEW')")
    public Page<MrpProposalDto> list(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Boolean capacityOverload,
            @PageableDefault(size = 50, sort = "proposedOrderDate") Pageable pageable) {
        return service.search(runId, type, status, materialId, productId, capacityOverload, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MRP_VIEW')")
    public MrpProposalDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/convert-to-pr")
    @PreAuthorize("hasAuthority('PURCHASE_PROPOSAL')")
    public MrpProposalDto convertToRequisition(@PathVariable Long id) {
        return service.convertToRequisition(id);
    }

    @PostMapping("/{id}/convert-to-production-order")
    @PreAuthorize("hasAuthority('PRODUCTION_PROPOSAL')")
    public MrpProposalDto convertToProductionOrder(@PathVariable Long id) {
        return service.convertToProductionOrder(id);
    }

    @PostMapping("/{id}/discard")
    @PreAuthorize("hasAuthority('MRP_RUN')")
    public MrpProposalDto discard(@PathVariable Long id, @Valid @RequestBody DiscardProposalRequest req) {
        return service.discard(id, req.reason());
    }
}
