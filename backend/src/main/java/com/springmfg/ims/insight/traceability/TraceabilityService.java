package com.springmfg.ims.insight.traceability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.NotFoundException;

@Service
public class TraceabilityService {

    private final EntityManager em;

    public TraceabilityService(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public TraceabilityDto traceMaterialBatch(Long batchId) {
        // Fetch batch number
        var batchRows = em.createNativeQuery(
                "SELECT batch_number FROM ims.material_batches WHERE id = :id")
                .setParameter("id", batchId)
                .getResultList();
        if (batchRows.isEmpty()) throw new NotFoundException("MaterialBatch", batchId);
        String batchNumber = batchRows.get(0).toString();

        List<TraceabilityDto.TraceStep> trail = new ArrayList<>();

        // GRN receipt
        var grnRows = (List<Object[]>) em.createNativeQuery("""
                SELECT gr.gr_number, gri.received_qty, gr.receipt_date
                FROM ims.goods_receipt_items gri
                JOIN ims.goods_receipts gr ON gr.id = gri.goods_receipt_id
                WHERE gri.material_batch_id = :batchId
                ORDER BY gr.receipt_date
                """)
                .setParameter("batchId", batchId)
                .getResultList();
        for (Object[] r : grnRows) {
            trail.add(new TraceabilityDto.TraceStep("GRN_RECEIPT", r[0].toString(),
                    "Received qty: " + r[1], toInstant(r[2])));
        }

        // Quality inspection
        var qiRows = (List<Object[]>) em.createNativeQuery("""
                SELECT qi.inspection_number, qi.status, qi.created_at
                FROM ims.quality_inspections qi
                WHERE qi.material_batch_id = :batchId
                ORDER BY qi.created_at
                """)
                .setParameter("batchId", batchId)
                .getResultList();
        for (Object[] r : qiRows) {
            trail.add(new TraceabilityDto.TraceStep("QUALITY_INSPECTION", r[0].toString(),
                    "Inspection result: " + r[1], toInstant(r[2])));
        }

        // Production material issues
        var issueRows = (List<Object[]>) em.createNativeQuery("""
                SELECT po.order_number, mil.quantity, mi.issue_date
                FROM ims.material_issue_lines mil
                JOIN ims.material_issues mi ON mi.id = mil.material_issue_id
                JOIN ims.production_orders po ON po.id = mi.production_order_id
                WHERE mil.material_batch_id = :batchId
                ORDER BY mi.issue_date
                """)
                .setParameter("batchId", batchId)
                .getResultList();
        for (Object[] r : issueRows) {
            trail.add(new TraceabilityDto.TraceStep("MATERIAL_ISSUED", r[0].toString(),
                    "Issued qty: " + r[1], toInstant(r[2])));
        }

        return new TraceabilityDto("MATERIAL_BATCH", batchId, batchNumber, trail);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public TraceabilityDto traceFgBatch(Long fgBatchId) {
        var batchRows = em.createNativeQuery(
                "SELECT batch_number FROM ims.fg_batches WHERE id = :id")
                .setParameter("id", fgBatchId)
                .getResultList();
        if (batchRows.isEmpty()) throw new NotFoundException("FgBatch", fgBatchId);
        String batchNumber = batchRows.get(0).toString();

        List<TraceabilityDto.TraceStep> trail = new ArrayList<>();

        // Production order
        var poRows = (List<Object[]>) em.createNativeQuery("""
                SELECT po.order_number, fb.produced_qty, fb.created_at
                FROM ims.fg_batches fb
                JOIN ims.production_orders po ON po.id = fb.production_order_id
                WHERE fb.id = :fgBatchId
                """)
                .setParameter("fgBatchId", fgBatchId)
                .getResultList();
        for (Object[] r : poRows) {
            trail.add(new TraceabilityDto.TraceStep("PRODUCED", r[0].toString(),
                    "Produced qty: " + r[1], toInstant(r[2])));
        }

        // Quality inspection
        var qiRows = (List<Object[]>) em.createNativeQuery("""
                SELECT qi.inspection_number, qi.status, qi.created_at
                FROM ims.quality_inspections qi
                WHERE qi.fg_batch_id = :fgBatchId
                ORDER BY qi.created_at
                """)
                .setParameter("fgBatchId", fgBatchId)
                .getResultList();
        for (Object[] r : qiRows) {
            trail.add(new TraceabilityDto.TraceStep("QUALITY_INSPECTION", r[0].toString(),
                    "Inspection result: " + r[1], toInstant(r[2])));
        }

        // Dispatch
        var dispRows = (List<Object[]>) em.createNativeQuery("""
                SELECT d.dispatch_number, di.quantity, d.dispatch_date
                FROM ims.dispatch_items di
                JOIN ims.dispatches d ON d.id = di.dispatch_id
                WHERE di.fg_batch_id = :fgBatchId
                ORDER BY d.dispatch_date
                """)
                .setParameter("fgBatchId", fgBatchId)
                .getResultList();
        for (Object[] r : dispRows) {
            trail.add(new TraceabilityDto.TraceStep("DISPATCHED", r[0].toString(),
                    "Dispatched qty: " + r[1], toInstant(r[2])));
        }

        return new TraceabilityDto("FG_BATCH", fgBatchId, batchNumber, trail);
    }

    /**
     * Native queries return whatever the driver maps the column to: a TIMESTAMPTZ arrives as a Timestamp or Instant,
     * but a DATE column arrives as a java.sql.Date (whose toInstant() throws) or a LocalDate. A date-only value is the
     * start of that day in UTC.
     */
    private Instant toInstant(Object val) {
        if (val == null) return null;
        if (val instanceof Instant i) return i;
        if (val instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (val instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        if (val instanceof java.util.Date d) return d.toInstant();
        if (val instanceof java.time.LocalDate ld) return ld.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        if (val instanceof java.time.LocalDateTime ldt) return ldt.toInstant(java.time.ZoneOffset.UTC);
        if (val instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        String text = val.toString();
        try {
            return Instant.parse(text);
        } catch (java.time.format.DateTimeParseException notATimestamp) {
            return java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        }
    }
}
