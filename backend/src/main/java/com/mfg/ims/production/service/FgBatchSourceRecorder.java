package com.mfg.ims.production.service;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.settings.SystemSettingService;

/**
 * Batch-level traceability (setting {@code quality.trace_granularity} = BATCH): links a finished-goods batch to the
 * material batches issued to its order, with the issued quantity. Under the default ORDER granularity nothing is
 * written and the backward trace falls back to the order-level join.
 */
@Component
public class FgBatchSourceRecorder {

    private final EntityManager em;
    private final SystemSettingService settings;

    public FgBatchSourceRecorder(EntityManager em, SystemSettingService settings) {
        this.em = em;
        this.settings = settings;
    }

    public void record(Long orderId, Long fgBatchId) {
        if (!"BATCH".equalsIgnoreCase(settings.get("quality.trace_granularity", "ORDER"))) {
            return;
        }
        em.createNativeQuery("""
                INSERT INTO ims.fg_batch_material_sources (fg_batch_id, material_batch_id, quantity)
                SELECT :fgBatchId, mil.material_batch_id, SUM(mil.quantity)
                FROM ims.material_issue_lines mil
                JOIN ims.material_issues mi ON mi.id = mil.material_issue_id
                WHERE mi.production_order_id = :orderId
                GROUP BY mil.material_batch_id
                ON CONFLICT (fg_batch_id, material_batch_id) DO NOTHING
                """)
                .setParameter("fgBatchId", fgBatchId)
                .setParameter("orderId", orderId)
                .executeUpdate();
    }
}
