package com.springmfg.ims.crm.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuotationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(QuotationExpiryJob.class);

    private final CrmQuotationService quotationService;

    public QuotationExpiryJob(CrmQuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @Scheduled(cron = "0 15 1 * * *")
    @SchedulerLock(name = "crm_quotation_expiry", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void sweep() {
        int expired = quotationService.expireOverdue();
        log.info("CRM quotation expiry sweep: {} quotations expired", expired);
    }
}
