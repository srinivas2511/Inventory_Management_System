package com.springmfg.ims.maintenance.service;

import java.time.LocalDate;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.insight.alert.AlertService;
import com.springmfg.ims.maintenance.domain.PmSchedule;
import com.springmfg.ims.maintenance.repository.PmScheduleRepository;

@Component
public class PMDueSweepJob {

    private static final Logger log = LoggerFactory.getLogger(PMDueSweepJob.class);

    private final PmScheduleRepository pmRepo;
    private final AlertService alertService;

    public PMDueSweepJob(PmScheduleRepository pmRepo, AlertService alertService) {
        this.pmRepo = pmRepo;
        this.alertService = alertService;
    }

    @Scheduled(cron = "0 0 6 * * *")
    @SchedulerLock(name = "pm_due_sweep", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    @Transactional
    public void sweep() {
        LocalDate cutoff = LocalDate.now().plusDays(7);
        List<PmSchedule> due = pmRepo.findDue(cutoff);
        log.info("PM due sweep: {} schedules due within 7 days", due.size());

        for (PmSchedule s : due) {
            // AlertService.raise() deduplicates — safe to call every sweep
            alertService.raise("OVERDUE_PM", "MEDIUM", "PM_SCHEDULE", s.getId(),
                    "PM schedule '" + s.getTaskName() + "' is due by " + s.getNextDueDate());
        }
    }
}
