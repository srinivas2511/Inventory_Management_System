package com.mfg.ims.oee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** Keeps mv_oee_daily, which the OEE trend reads, fresh. ShedLock makes one node refresh at a time. */
@Component
public class OeeViewRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(OeeViewRefreshJob.class);

    private final OeeRepository repo;

    public OeeViewRefreshJob(OeeRepository repo) {
        this.repo = repo;
    }

    @Scheduled(cron = "0 */15 * * * *")
    @SchedulerLock(name = "mv_oee_daily_refresh", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void refresh() {
        try {
            repo.refreshView();
            log.info("mv_oee_daily refreshed");
        } catch (Exception e) {
            log.error("Refreshing mv_oee_daily failed: {}", e.getMessage(), e);
        }
    }
}
