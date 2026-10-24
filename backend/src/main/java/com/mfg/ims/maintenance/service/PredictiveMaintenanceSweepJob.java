package com.mfg.ims.maintenance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
public class PredictiveMaintenanceSweepJob {

    private static final Logger log = LoggerFactory.getLogger(PredictiveMaintenanceSweepJob.class);

    private final PredictiveMaintenanceService service;

    public PredictiveMaintenanceSweepJob(PredictiveMaintenanceService service) {
        this.service = service;
    }

    /** Run predictions nightly at 02:30 */
    @Scheduled(cron = "0 30 2 * * *")
    @SchedulerLock(name = "predictive_maint_sweep", lockAtLeastFor = "PT2M", lockAtMostFor = "PT10M")
    public void sweep() {
        var result = service.runPredictions();
        log.info("Predictive maintenance sweep: {} machines analysed, {} predictions, {} high-risk",
            result.summary().machinesAnalyzed(),
            result.summary().predictionsGenerated(),
            result.summary().highRiskCount());
    }
}
