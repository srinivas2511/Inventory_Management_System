package com.mfg.ims.insight.traceability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;

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

        // Forward: orders that consumed the batch -> the FG batches they produced -> dispatches -> customers
        // (DESIGN §5.7 forward query).
        var forwardRows = (List<Object[]>) em.createNativeQuery("""
                SELECT po.order_number, fg.batch_number, fg.produced_qty, fg.created_at,
                       d.dispatch_number, di.quantity, d.dispatch_date, c.name
                FROM ims.material_issue_lines mil
                JOIN ims.material_issues mi ON mi.id = mil.material_issue_id
                JOIN ims.production_orders po ON po.id = mi.production_order_id
                JOIN ims.fg_batches fg ON fg.production_order_id = po.id
                LEFT JOIN ims.dispatch_items di ON di.fg_batch_id = fg.id
                LEFT JOIN ims.dispatches d ON d.id = di.dispatch_id
                LEFT JOIN ims.customers c ON c.id = d.customer_id
                WHERE mil.material_batch_id = :batchId
                GROUP BY po.order_number, fg.batch_number, fg.produced_qty, fg.created_at,
                         d.dispatch_number, di.quantity, d.dispatch_date, c.name
                ORDER BY fg.created_at, fg.batch_number, d.dispatch_date, d.dispatch_number
                """)
                .setParameter("batchId", batchId)
                .getResultList();
        java.util.Set<String> producedSeen = new java.util.HashSet<>();
        for (Object[] r : forwardRows) {
            String fgNumber = r[1].toString();
            if (producedSeen.add(fgNumber)) {
                trail.add(new TraceabilityDto.TraceStep("PRODUCED", fgNumber,
                        "Finished goods from order " + r[0] + ", produced qty: " + r[2], toInstant(r[3])));
            }
            if (r[4] != null) {
                trail.add(new TraceabilityDto.TraceStep("DISPATCHED", r[4].toString(),
                        "Dispatched qty: " + r[5] + " of " + fgNumber + " to " + r[7], toInstant(r[6])));
            }
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

        // Backward (DESIGN §5.7): the raw-material batches, supplier and PO that fed this batch. Batch-level links in
        // fg_batch_material_sources take precedence when they exist; otherwise every batch issued to the order counts.
        Number linkCount = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM ims.fg_batch_material_sources WHERE fg_batch_id = :fgBatchId")
                .setParameter("fgBatchId", fgBatchId)
                .getSingleResult();
        String sourceSql = linkCount.longValue() > 0
                ? """
                  SELECT mb.batch_number, m.material_code, mb.heat_number, mb.certificate_number, s.name,
                         po.po_number, src.quantity, mb.received_date
                  FROM ims.fg_batch_material_sources src
                  JOIN ims.material_batches mb ON mb.id = src.material_batch_id
                  JOIN ims.materials m ON m.id = mb.material_id
                  LEFT JOIN ims.suppliers s ON s.id = mb.supplier_id
                  LEFT JOIN ims.purchase_orders po ON po.id = mb.purchase_order_id
                  WHERE src.fg_batch_id = :fgBatchId
                  ORDER BY mb.batch_number
                  """
                : """
                  SELECT mb.batch_number, m.material_code, mb.heat_number, mb.certificate_number, s.name,
                         po.po_number, SUM(mil.quantity), mb.received_date
                  FROM ims.fg_batches fg
                  JOIN ims.material_issues mi ON mi.production_order_id = fg.production_order_id
                  JOIN ims.material_issue_lines mil ON mil.material_issue_id = mi.id
                  JOIN ims.material_batches mb ON mb.id = mil.material_batch_id
                  JOIN ims.materials m ON m.id = mb.material_id
                  LEFT JOIN ims.suppliers s ON s.id = mb.supplier_id
                  LEFT JOIN ims.purchase_orders po ON po.id = mb.purchase_order_id
                  WHERE fg.id = :fgBatchId
                  GROUP BY mb.batch_number, m.material_code, mb.heat_number, mb.certificate_number, s.name,
                           po.po_number, mb.received_date
                  ORDER BY mb.batch_number
                  """;
        var sourceRows = (List<Object[]>) em.createNativeQuery(sourceSql)
                .setParameter("fgBatchId", fgBatchId)
                .getResultList();
        for (Object[] r : sourceRows) {
            StringBuilder what = new StringBuilder("Material ").append(r[1]).append(", qty used: ").append(r[6]);
            if (r[4] != null) what.append(", supplier: ").append(r[4]);
            if (r[5] != null) what.append(", PO: ").append(r[5]);
            if (r[2] != null) what.append(", heat: ").append(r[2]);
            if (r[3] != null) what.append(", certificate: ").append(r[3]);
            trail.add(new TraceabilityDto.TraceStep("MATERIAL_SOURCE", r[0].toString(), what.toString(),
                    toInstant(r[7])));
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
