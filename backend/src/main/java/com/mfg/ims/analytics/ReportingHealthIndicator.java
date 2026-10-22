package com.mfg.ims.analytics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.mfg.ims.analytics.AnalyticsDtos.RefreshResult;

/**
 * Exposes the last materialized-view refresh result at /actuator/health/reporting (via the group).
 * NOT included in the readiness group — a degraded reporting subsystem never blocks
 * the application from receiving traffic (NFR-003).
 */
@Component
public class ReportingHealthIndicator implements HealthIndicator {

    private final AtomicReference<RefreshResult> lastResult = new AtomicReference<>();

    /** Called by MaterializedViewRefreshJob after each scheduled run. */
    public void updateLastResult(RefreshResult result) {
        lastResult.set(result);
    }

    @Override
    public Health health() {
        RefreshResult result = lastResult.get();
        if (result == null) {
            return Health.unknown().withDetail("message", "No refresh has run yet").build();
        }
        if (result.failed().isEmpty()) {
            return Health.up().withDetail("refreshed", result.refreshed().size()).build();
        }
        return Health.status("DEGRADED")
                .withDetail("refreshed", result.refreshed().size())
                .withDetail("failed", result.failed().keySet())
                .build();
    }
}
