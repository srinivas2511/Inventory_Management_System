package com.mfg.ims.finance.consolidation;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.finance.consolidation.ConsolidationDtos.AddEliminationRequest;
import com.mfg.ims.finance.consolidation.ConsolidationDtos.ConsolidationReportDto;
import com.mfg.ims.finance.consolidation.ConsolidationDtos.EliminationDto;

@RestController
@RequestMapping("/api/finance/consolidation")
public class ConsolidationController {

    private final ConsolidationService service;

    public ConsolidationController(ConsolidationService service) {
        this.service = service;
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('CONSOLIDATION_VIEW')")
    public ConsolidationReportDto report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.consolidatedReport(from, to);
    }

    @PostMapping("/eliminations")
    @PreAuthorize("hasAuthority('CONSOLIDATION_MANAGE')")
    public EliminationDto addElimination(@RequestBody AddEliminationRequest req) {
        return service.addElimination(req);
    }
}
