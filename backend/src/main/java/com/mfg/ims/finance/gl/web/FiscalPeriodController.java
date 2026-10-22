package com.mfg.ims.finance.gl.web;

import com.mfg.ims.finance.gl.dto.FiscalPeriodDto;
import com.mfg.ims.finance.gl.dto.PeriodReasonRequest;
import com.mfg.ims.finance.gl.service.FiscalPeriodService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/periods")
public class FiscalPeriodController {

    private final FiscalPeriodService periodService;

    public FiscalPeriodController(FiscalPeriodService periodService) {
        this.periodService = periodService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW')")
    public List<FiscalPeriodDto> list() {
        return periodService.findAll();
    }

    @PostMapping("/{id}/initiate-close")
    @PreAuthorize("hasAuthority('PERIOD_CLOSE')")
    public FiscalPeriodDto initiateClose(@PathVariable Long id) {
        return periodService.initiatePeriodClose(id);
    }

    @PostMapping("/{id}/finalize-close")
    @PreAuthorize("hasAuthority('PERIOD_CLOSE')")
    public FiscalPeriodDto finalizeClose(@PathVariable Long id, @Valid @RequestBody PeriodReasonRequest request) {
        return periodService.finalizePeriodClose(id, request.reason());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('PERIOD_CLOSE')")
    public FiscalPeriodDto reopen(@PathVariable Long id, @Valid @RequestBody PeriodReasonRequest request) {
        return periodService.reopenPeriod(id, request.reason());
    }
}
