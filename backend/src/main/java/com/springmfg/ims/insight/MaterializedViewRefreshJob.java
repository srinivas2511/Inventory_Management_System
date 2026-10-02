package com.springmfg.ims.insight;

import jakarta.persistence.EntityManager;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MaterializedViewRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(MaterializedViewRefreshJob.class);

    private final EntityManager em;
    private final CacheManager cacheManager;

    public MaterializedViewRefreshJob(EntityManager em, CacheManager cacheManager) {
        this.em = em;
        this.cacheManager = cacheManager;
    }

    @Scheduled(cron = "0 0 */1 * * *")
    @SchedulerLock(name = "mv_refresh", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    @Transactional
    public void refresh() {
        log.info("Refreshing materialized views");
        em.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY ims.mv_production_monthly").executeUpdate();
        em.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY ims.mv_inventory_value_monthly").executeUpdate();
        var cache = cacheManager.getCache("dashboardKpis");
        if (cache != null) cache.clear();
        log.info("Materialized view refresh complete");
    }
}
