package com.springmfg.ims.insight.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final DashboardTrendService trendService;

    public DashboardController(DashboardService service, DashboardTrendService trendService) {
        this.service = service;
        this.trendService = trendService;
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public DashboardKpiDto getKpis() {
        return service.getKpis();
    }

    @GetMapping("/trends")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public DashboardTrendsDto getTrends(@RequestParam(defaultValue = "6") int months) {
        return trendService.trends(months);
    }
}
