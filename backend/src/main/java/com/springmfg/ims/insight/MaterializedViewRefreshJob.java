package com.springmfg.ims.insight;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.springmfg.ims.analytics.AnalyticsDtos.RefreshResult;
import com.springmfg.ims.analytics.AnalyticsViewRefresher;

/** Hourly refresh of the reporting views; a failing view is logged and does not stop the others. */
@Component
public class MaterializedViewRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(MaterializedViewRefreshJob.class);

    private final AnalyticsViewRefresher refresher;
    private final CacheManager cacheManager;

    public MaterializedViewRefreshJob(AnalyticsViewRefresher refresher, CacheManager cacheManager) {
        this.refresher = refresher;
        this.cacheManager = cacheManager;
    }

    @Scheduled(cron = "0 0 */1 * * *")
    @SchedulerLock(name = "mv_refresh", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void refresh() {
        log.info("Refreshing materialized views");
        RefreshResult result = refresher.refreshAll();
        var cache = cacheManager.getCache("dashboardKpis");
        if (cache != null) {
            cache.clear();
        }
        if (result.failed().isEmpty()) {
            log.info("Materialized view refresh complete");
        } else {
            log.error("Materialized view refresh finished with failures: {}", result.failed().keySet());
        }
    }
}
