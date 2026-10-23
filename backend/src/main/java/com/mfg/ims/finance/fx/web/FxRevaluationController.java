package com.mfg.ims.finance.fx.web;

import com.mfg.ims.finance.fx.dto.FxRevaluationDtos.FxRevaluationDto;
import com.mfg.ims.finance.fx.dto.FxRevaluationDtos.FxRevaluationRunResult;
import com.mfg.ims.finance.fx.service.FxRevaluationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/fx-revaluation")
public class FxRevaluationController {

    private final FxRevaluationService service;

    public FxRevaluationController(FxRevaluationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('FX_REVALUATION_RUN')")
    public FxRevaluationRunResult run(@RequestParam Long periodId) {
        return service.run(periodId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FX_REVALUATION_RUN')")
    public List<FxRevaluationDto> list(@RequestParam(required = false) Long periodId) {
        return service.list(periodId);
    }
}
