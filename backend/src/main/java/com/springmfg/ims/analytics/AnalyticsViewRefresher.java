package com.springmfg.ims.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.springmfg.ims.analytics.AnalyticsDtos.RefreshResult;

/**
 * Refreshes the reporting materialized views one at a time, each in its own transaction, so one failing view neither
 * blocks the others nor anything that writes business data (PLAN R19-7.1).
 */
@Component
public class AnalyticsViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsViewRefresher.class);

    /** Fixed names, never taken from a request. */
    public static final List<String> VIEWS = List.of(
            "mv_production_monthly", "mv_inventory_value_monthly", "mv_financial_summary_monthly",
            "mv_supplier_performance", "mv_customer_sales_summary");

    private final EntityManager em;
    private final PlatformTransactionManager transactionManager;

    public AnalyticsViewRefresher(EntityManager em, PlatformTransactionManager transactionManager) {
        this.em = em;
        this.transactionManager = transactionManager;
    }

    public RefreshResult refreshAll() {
        List<String> refreshed = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();
        for (String view : VIEWS) {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        em.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY ims." + view).executeUpdate());
                refreshed.add(view);
            } catch (RuntimeException e) {
                log.error("Refreshing {} failed: {}", view, e.toString());
                failed.put(view, e.getClass().getSimpleName());
            }
        }
        return new RefreshResult(refreshed, failed);
    }
}
