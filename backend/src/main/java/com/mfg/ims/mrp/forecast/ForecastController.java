package com.mfg.ims.mrp.forecast;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.mrp.forecast.ForecastTypes.ForecastResult;
import com.mfg.ims.mrp.forecast.ForecastTypes.ProviderInfo;

/** The demand-forecast seam (R19-3): which provider is active, and generating forecasts into the table MRP reads. */
@RestController
@RequestMapping("/api/analytics/forecast")
public class ForecastController {

    private final ForecastService forecasts;

    public ForecastController(ForecastService forecasts) {
        this.forecasts = forecasts;
    }

    @GetMapping("/provider")
    @PreAuthorize("hasAuthority('DEMAND_PLAN')")
    public ProviderInfo provider() {
        return forecasts.info();
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('DEMAND_PLAN')")
    public ForecastResult generate(@RequestParam(required = false) Integer horizonDays) {
        return forecasts.generate(horizonDays);
    }
}
