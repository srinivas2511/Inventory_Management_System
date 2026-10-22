package com.mfg.ims.qualityplus.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.qualityplus.dto.SpcDtos.AddPointsRequest;
import com.mfg.ims.qualityplus.dto.SpcDtos.CapabilityDto;
import com.mfg.ims.qualityplus.dto.SpcDtos.ChartDetailDto;
import com.mfg.ims.qualityplus.dto.SpcDtos.ChartSummaryDto;
import com.mfg.ims.qualityplus.dto.SpcDtos.SaveChartRequest;
import com.mfg.ims.qualityplus.dto.SpcDtos.UpdateChartRequest;
import com.mfg.ims.qualityplus.service.SpcService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/spc")
public class SpcController {

    private final SpcService service;

    public SpcController(SpcService service) {
        this.service = service;
    }

    @GetMapping("/charts")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public List<ChartSummaryDto> list(@RequestParam(required = false) Long productId,
                                      @RequestParam(required = false) String q) {
        return service.list(productId, q);
    }

    @GetMapping("/charts/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public ChartDetailDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/charts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SPC_MANAGE')")
    public ChartDetailDto create(@Valid @RequestBody SaveChartRequest req) {
        return service.create(req);
    }

    @PutMapping("/charts/{id}")
    @PreAuthorize("hasAuthority('SPC_MANAGE')")
    public ChartDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateChartRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/charts/{id}/points")
    @PreAuthorize("hasAuthority('QUALITY_INSPECT')")
    public ChartDetailDto addPoints(@PathVariable Long id, @Valid @RequestBody AddPointsRequest req) {
        return service.addPoints(id, req);
    }

    @PostMapping("/charts/{id}/recalculate-limits")
    @PreAuthorize("hasAuthority('SPC_MANAGE')")
    public ChartDetailDto recalculateLimits(@PathVariable Long id) {
        return service.recalculateLimits(id);
    }

    @GetMapping("/charts/{id}/capability")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','QUALITY_INSPECT')")
    public CapabilityDto capability(@PathVariable Long id,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.capability(id, from, to);
    }
}
