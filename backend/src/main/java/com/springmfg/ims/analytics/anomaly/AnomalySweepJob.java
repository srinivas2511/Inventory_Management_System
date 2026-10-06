package com.springmfg.ims.analytics.anomaly;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily anomaly sweep. A failure is logged and never reaches the scheduler's thread. */
@Component
public class AnomalySweepJob {

    private static final Logger log = LoggerFactory.getLogger(AnomalySweepJob.class);

    private final AnomalyDetectionService service;

    public AnomalySweepJob(AnomalyDetectionService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "anomaly_sweep", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void run() {
        try {
            var result = service.sweep();
            log.info("Anomaly sweep raised {} finding(s); failed detectors: {}", result.raised(), result.failedDetectors());
        } catch (RuntimeException e) {
            log.error("Anomaly sweep failed: {}", e.toString());
        }
    }
}
