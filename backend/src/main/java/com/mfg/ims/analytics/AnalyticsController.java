package com.mfg.ims.analytics;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.analytics.AnalyticsDtos.QueryDescriptor;
import com.mfg.ims.analytics.AnalyticsDtos.QueryResult;
import com.mfg.ims.analytics.AnalyticsDtos.RefreshResult;
import com.mfg.ims.analytics.anomaly.AnomalyDetectionService;
import com.mfg.ims.analytics.anomaly.AnomalyDetectionService.SweepResult;

/** BI query endpoint (R19-2.2), on-demand refresh and sweep, and the anomaly sweep (R19-3.3). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final AnalyticsViewRefresher refresher;
    private final AnomalyDetectionService anomalies;

    public AnalyticsController(AnalyticsService analytics, AnalyticsViewRefresher refresher,
                               AnomalyDetectionService anomalies) {
        this.analytics = analytics;
        this.refresher = refresher;
        this.anomalies = anomalies;
    }

    /** The queries the caller may run. */
    @GetMapping("/catalogue")
    @PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
    public List<QueryDescriptor> catalogue() {
        return analytics.catalogue();
    }

    @GetMapping("/query")
    @PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
    public QueryResult query(
            @RequestParam String key,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return analytics.run(key, from, to, limit);
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('ANALYTICS_MANAGE')")
    public RefreshResult refresh() {
        return refresher.refreshAll();
    }

    @PostMapping("/anomalies/run")
    @PreAuthorize("hasAuthority('ANALYTICS_MANAGE')")
    public SweepResult runAnomalySweep() {
        return anomalies.sweep();
    }
}
