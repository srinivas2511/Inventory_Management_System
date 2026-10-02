-- Phase 8 — Performance indexes (DESIGN.md §13, PLAN.md task 8.1)
-- Composite indexes to cover the most frequent filter + sort patterns.
-- All are non-blocking CONCURRENTLY; downtime not required.
SET search_path TO ims;

-- inventory_transactions: range scans by reference, entity, and time
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invtx_ref_type_id
    ON inventory_transactions(reference_type, reference_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invtx_material_time
    ON inventory_transactions(material_id, posted_at DESC)
    WHERE material_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invtx_product_time
    ON inventory_transactions(product_id, posted_at DESC)
    WHERE product_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invtx_batch_time
    ON inventory_transactions(material_batch_id, posted_at DESC)
    WHERE material_batch_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invtx_fg_batch_time
    ON inventory_transactions(fg_batch_id, posted_at DESC)
    WHERE fg_batch_id IS NOT NULL;

-- inventory_balances: warehouse + item lookups (avoids full scans during allocation)
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invbal_wh_mat_status
    ON inventory_balances(warehouse_id, material_id, stock_status)
    WHERE material_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invbal_wh_prod_status
    ON inventory_balances(warehouse_id, product_id, stock_status)
    WHERE product_id IS NOT NULL;

-- production_orders: common filters (status, product, supervisor, date range)
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_prodord_status_date
    ON production_orders(status, start_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_prodord_product_status
    ON production_orders(product_id, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_prodord_supervisor
    ON production_orders(supervisor_id)
    WHERE supervisor_id IS NOT NULL;

-- production_operations: work-queue queries by order and status
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_prodop_order_seq
    ON production_operations(production_order_id, sequence_no);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_prodop_operator_status
    ON production_operations(assigned_operator_id, status)
    WHERE assigned_operator_id IS NOT NULL;

-- quality_inspections: dashboard and pending-work queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_qi_status_type
    ON quality_inspections(status, inspection_type);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_qi_material_batch
    ON quality_inspections(material_batch_id)
    WHERE material_batch_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_qi_fg_batch
    ON quality_inspections(fg_batch_id)
    WHERE fg_batch_id IS NOT NULL;

-- sales_orders: customer + status + date
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_so_customer_status
    ON sales_orders(customer_id, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_so_status_date
    ON sales_orders(status, order_date DESC);

-- dispatches: linked to sales order and dispatch date
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_dispatch_sales_order
    ON dispatches(sales_order_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_dispatch_date
    ON dispatches(dispatch_date DESC);

-- audit_logs: user + entity + time (for security and compliance queries)
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_audit_entity
    ON audit_logs(entity_type, entity_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_audit_user_time
    ON audit_logs(created_by, created_at DESC);

-- alerts: active alerts sweep job and dashboard
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_alert_severity_open
    ON alerts(severity, resolved_at)
    WHERE resolved_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_alert_entity
    ON alerts(entity_type, entity_id)
    WHERE entity_id IS NOT NULL;

-- purchase_orders: status and supplier
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_po_status_date
    ON purchase_orders(status, po_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_po_supplier_status
    ON purchase_orders(supplier_id, status);

-- material_batches: FEFO/FIFO allocation (already ix_mb_material, ix_mb_status; add composite)
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_mb_mat_status_expiry
    ON material_batches(material_id, status, expiry_date NULLS LAST, received_date);
