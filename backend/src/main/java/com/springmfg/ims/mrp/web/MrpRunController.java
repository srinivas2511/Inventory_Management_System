package com.springmfg.ims.mrp.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.mrp.dto.ConvertAllResultDto;
import com.springmfg.ims.mrp.dto.MrpProposalDto;
import com.springmfg.ims.mrp.dto.MrpRunDto;
import com.springmfg.ims.mrp.dto.RunMrpRequest;
import com.springmfg.ims.mrp.service.MrpProposalService;
import com.springmfg.ims.mrp.service.MrpRunService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mrp/runs")
public class MrpRunController {

    private final MrpRunService runService;
    private final MrpProposalService proposalService;

    public MrpRunController(MrpRunService runService, MrpProposalService proposalService) {
        this.runService = runService;
        this.proposalService = proposalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MRP_VIEW')")
    public Page<MrpRunDto> list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return runService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MRP_VIEW')")
    public MrpRunDto get(@PathVariable Long id) {
        return runService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MRP_RUN')")
    public MrpRunDto run(@Valid @RequestBody(required = false) RunMrpRequest req) {
        return runService.runMrp(req);
    }

    @GetMapping("/{id}/proposals")
    @PreAuthorize("hasAuthority('MRP_VIEW')")
    public Page<MrpProposalDto> proposals(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50, sort = "proposedOrderDate") Pageable pageable) {
        return proposalService.search(id, type, status, null, null, null, pageable);
    }

    @PostMapping("/{id}/convert-all")
    @PreAuthorize("(#type == 'PURCHASE' and hasAuthority('PURCHASE_PROPOSAL'))"
            + " or (#type == 'PRODUCTION' and hasAuthority('PRODUCTION_PROPOSAL'))")
    public ConvertAllResultDto convertAll(@PathVariable Long id, @RequestParam String type) {
        return proposalService.convertAll(id, type);
    }
}
