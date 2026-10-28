# DESIGN-ERP.md — Full ERP Extension

**Status:** addendum to `DESIGN.md`. Additive only: `DESIGN.md` conventions (§1) apply; no existing table is dropped or has its meaning changed. Existing tables receive only new nullable/defaulted columns. Architecture rationale is in `ARCHITECTURE-ERP.md`; scope in `REQUIREMENTS-ERP.md`; phases in `PLAN-ERP.md`.
**Reading rule for Claude Code:** before an ERP phase, read its sections here plus the referenced `ARCHITECTURE-ERP.md` sections; then check `REQUIREMENTS-ERP.md` IDs. If anything is missing, stop and flag it.
**Confidence:** schemas are the target design. Statutory content (GST schema fields, tax rules, TDS sections, Tally XML shapes) must be verified against the current official specifications and by a chartered accountant before Phase 12/16 coding; places that need that check are marked **[VERIFY]**.

## Contents
1. Conventions and enums · 2. Database design by phase · 3. Domain invariants · 4. Algorithms and components · 5. API contracts · 6. Permissions · 7. Frontend design · 8. BI, registers and reports · 9. Settings · 10. Seed data · 11. Test design · 12. Open items

---

## 1. Conventions and enums

Inherits `DESIGN.md` §1.1 data types. Additions:
- **Rate** (tax %, FX): `NUMERIC(9,4)`; **FX rate** `NUMERIC(18,8)`.
- **Standard document header columns** (called `DOC_HDR` below, in addition to standard columns): `doc_no VARCHAR(40) NOT NULL, doc_date DATE NOT NULL, branch_id BIGINT REFERENCES company_branches, series_id BIGINT REFERENCES document_series, status VARCHAR(25) NOT NULL, revision_no INT NOT NULL DEFAULT 0, root_document_id BIGINT, currency CHAR(3) NOT NULL DEFAULT 'INR', exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1, subtotal NUMERIC(18,4), discount_total NUMERIC(18,4), tax_total NUMERIC(18,4), round_off NUMERIC(18,4), grand_total NUMERIC(18,4), grand_total_inr NUMERIC(18,4), remarks VARCHAR(1000), custom JSONB NOT NULL DEFAULT '{}', UNIQUE (company_id, series_id, doc_no)`.
- **Standard line columns** (`DOC_LINE`): `line_no INT, item_kind VARCHAR(10) (MATERIAL|PRODUCT|SERVICE), material_id, product_id, description VARCHAR(300), hsn_sac VARCHAR(10), qty NUMERIC(18,3), uom, uom_qty NUMERIC(18,3), rate NUMERIC(18,4), discount_pct NUMERIC(9,4), discount_amt NUMERIC(18,4), taxable_value NUMERIC(18,4), tax_code_id, cgst_rate/sgst_rate/igst_rate/cess_rate NUMERIC(9,4), cgst_amt/sgst_amt/igst_amt/cess_amt NUMERIC(18,4), line_total NUMERIC(18,4), custom JSONB`, with `CHECK ((material_id IS NOT NULL)::int + (product_id IS NOT NULL)::int = 1)` except service lines.

### 1.1 New / extended enumerations
| Enum | Values |
|---|---|
| `ItemKind` | STOCK, SERVICE (on materials/products) |
| `DocumentKind` | RFQ, SUPPLIER_QUOTE, PURCHASE_ORDER, PURCHASE_INVOICE, PURCHASE_RETURN, QUOTATION, ORDER_CONFIRMATION, DELIVERY_CHALLAN, SALES_INVOICE, PROFORMA_INVOICE, CREDIT_NOTE, DEBIT_NOTE, PAYMENT, RECEIPT, ADVANCE_RECEIPT, JOB_WORK_ORDER, SERVICE_ORDER, SERVICE_CONFIRMATION, JOURNAL |
| `PoKind` | STANDARD, FOREIGN, ADVANCE |
| `QuotationStatus` | DRAFT, SUBMITTED, APPROVED, SENT, ACCEPTED, CONVERTED, EXPIRED, LOST, CANCELLED, SUPERSEDED |
| `OcStatus` | DRAFT, SUBMITTED, APPROVED, CONFIRMED, IN_PRODUCTION, PARTIALLY_DISPATCHED, DISPATCHED, CLOSED, CANCELLED, SUPERSEDED |
| `RfqStatus` | DRAFT, SENT, QUOTES_RECEIVED, AWARDED, CLOSED, CANCELLED |
| `InvoiceStatus` | DRAFT, SUBMITTED, APPROVED, POSTED, PARTIALLY_PAID, PAID, CANCELLED |
| `ChallanType` | RETURNABLE, NON_RETURNABLE, SERVICE |
| `ChallanStatus` | DRAFT, ISSUED, PARTIALLY_RETURNED, CLOSED, CANCELLED |
| `PaymentStatus` | DRAFT, PENDING_APPROVAL, APPROVED, POSTED, REVERSED |
| `EinvoiceStatus` | NOT_REQUIRED, PENDING, GENERATED, FAILED, CANCELLED |
| `OpenItemType` | INVOICE, DEBIT_NOTE, CREDIT_NOTE, ADVANCE, PAYMENT_ON_ACCOUNT |
| `MatchStatus` | MATCHED, QTY_VARIANCE, PRICE_VARIANCE, NO_GRN, OVERRIDDEN |
| `JobWorkStatus` | DRAFT, APPROVED, MATERIAL_ISSUED, PARTIALLY_RECEIVED, RECEIVED, INVOICED, CLOSED, CANCELLED |
| `MrpSuggestionType` | PLANNED_REQUISITION, PLANNED_PRODUCTION |
| `OutboxStatus` | PENDING, IN_FLIGHT, DELIVERED, FAILED, DEAD |
| `TransactionType` additions | `PURCHASE_RETURN`, `SUBCONTRACT_ISSUE`, `SUBCONTRACT_RECEIPT`, `SUBCONTRACT_RETURN` (all signed quantity rules as existing types) |
| `LocationType` addition | `SUBCONTRACT` |
| `AlertType` additions | MATCH_EXCEPTION, FINANCE_MISMATCH, OVERDUE_RECEIVABLE, EINVOICE_FAILED, INTEGRATION_FAILED, QUOTATION_EXPIRING, MRP_SHORTAGE |

> The `CHECK` constraints on existing enum columns (`transaction_type`, `location_type`, `alert_type`) are widened in a new migration by dropping and re-adding the constraint — this is a constraint change only, not an edit of an applied migration file.

### 1.2 Document number formats (default series; editable as data)
| Document | Default format | Example |
|---|---|---|
| RFQ | `RFQ-{FY}-{00000}` | RFQ-2026-27-00012 |
| Supplier quote | `SQ-{FY}-{00000}` | SQ-2026-27-00040 |
| Quotation | `QT-{FY}-{00000}` | QT-2026-27-00103 |
| Order confirmation | `OC-{FY}-{00000}` | OC-2026-27-00071 |
| Delivery challan | `DC-{FY}-{00000}` | DC-2026-27-00210 |
| Sales invoice (tax) | `INV-{FY}-{00000}` (per branch series) | INV-2026-27-00415 |
| Proforma invoice | `PI-{FY}-{00000}` | PI-2026-27-00032 |
| Credit note / Debit note | `CN-` / `DN-{FY}-{00000}` | CN-2026-27-00005 |
| Purchase invoice (internal) | `PIN-{FY}-{00000}` (supplier invoice no. stored separately) | PIN-2026-27-00088 |
| Purchase return | `PRT-{FY}-{00000}` | PRT-2026-27-00006 |
| Payment / Receipt / Advance receipt | `PAY-` / `RCT-` / `ARV-{FY}-{00000}` | RCT-2026-27-00150 |
| Job-work order | `JW-{FY}-{00000}` | JW-2026-27-00019 |
| Service order / confirmation | `SVO-` / `SVC-{FY}-{00000}` | SVO-2026-27-00007 |
| Journal entry | `JV-{FY}-{000000}` | JV-2026-27-001204 |
| MRP run | `MRP-{yyyyMM}-{000}` | MRP-202611-004 |
`{FY}` = Indian financial year (Apr–Mar) unless the series reset policy says otherwise. GST rules limit invoice numbers to 16 characters and a restricted character set **[VERIFY]**; the invoice series validator enforces this.

### 1.3 Migration plan (continues after V11)
| File | Phase | Content |
|---|---|---|
| V12 | 9 | uom conversions, item categories/series, item_kind, company profile, branches, bank accounts, transporters, payment terms, T&C, teams, notification prefs, document_series (+ default series migrated from `number_sequences`) |
| V13 | 9 | custom fields, tags, comments, attachments, document_links, search_index, approval rules + columns, import_jobs, jobs |
| V14 | 10 | RFQ, quotes, PO extensions, GRN/QIR links, purchase invoices, match, purchase returns |
| V15 | 11 | quotations, order confirmations, delivery challans, dispatch communication |
| V16 | 12 | currencies, rates, tax codes/rates, accounts, mappings, fiscal periods, journal, balances, open items, triggers |
| V17 | 12 | sales invoices, notes, payments, allocations, advance vouchers, TDS, reminders, e-invoice/e-way tables, GST report views |
| V18 | 13 | multi-level BOM, alternates, MRP, costing, labels, barcodes, min/max, stock card views |
| V19 | 14 | job work, service orders/confirmations, SUBCONTRACT location type |
| V20 | 15 | BI views and materialized views, saved reports, registers |
| V21 | 16 | outbox, inbox, connections, calls, Tally/Busy rules, tracking, gateway tables |
| V22+ | 17 | indexes/partitions from performance review, retention jobs |
New permissions and role mappings are inserted by the migration of the phase that needs them (§6). Partitioned tables follow `DESIGN.md` §2.2 (current + 12 monthly partitions, maintenance job).

---

## 2. Database design by phase

`id` = `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`; standard columns omitted (see `DESIGN.md` §1.1). `DOC_HDR` / `DOC_LINE` as defined in §1.

### 2.1 Phase 9 — Platform (V12–V13)
```sql
-- Units, categories, series
CREATE TABLE uom_conversions (id, from_uom VARCHAR(10) REFERENCES uoms, to_uom VARCHAR(10) REFERENCES uoms,
  factor NUMERIC(18,8) NOT NULL CHECK (factor > 0), material_id BIGINT, product_id BIGINT,   -- item-specific, or global when both NULL
  UNIQUE NULLS NOT DISTINCT (from_uom, to_uom, material_id, product_id));
CREATE TABLE item_categories (id, category_code VARCHAR(20) NOT NULL, name VARCHAR(100) NOT NULL,
  parent_id BIGINT REFERENCES item_categories, item_type VARCHAR(10) NOT NULL, UNIQUE (company_id, category_code));
CREATE TABLE item_series (id, series_code VARCHAR(20), name VARCHAR(80), prefix VARCHAR(20), next_no BIGINT NOT NULL DEFAULT 1,
  padding INT NOT NULL DEFAULT 3, category_id BIGINT REFERENCES item_categories);
ALTER TABLE materials ADD COLUMN item_kind VARCHAR(10) NOT NULL DEFAULT 'STOCK' CHECK (item_kind IN ('STOCK','SERVICE')),
  ADD COLUMN category_id BIGINT REFERENCES item_categories, ADD COLUMN hsn_sac VARCHAR(10), ADD COLUMN tax_code_id BIGINT,
  ADD COLUMN purchase_uom VARCHAR(10), ADD COLUMN barcode VARCHAR(60), ADD COLUMN custom JSONB NOT NULL DEFAULT '{}';
ALTER TABLE products ADD COLUMN item_kind VARCHAR(10) NOT NULL DEFAULT 'STOCK', ADD COLUMN category_id BIGINT,
  ADD COLUMN hsn_sac VARCHAR(10), ADD COLUMN tax_code_id BIGINT, ADD COLUMN sales_uom VARCHAR(10),
  ADD COLUMN barcode VARCHAR(60), ADD COLUMN max_stock NUMERIC(18,3), ADD COLUMN custom JSONB NOT NULL DEFAULT '{}';
ALTER TABLE suppliers ADD COLUMN pan VARCHAR(10), ADD COLUMN state_code CHAR(2), ADD COLUMN currency CHAR(3) DEFAULT 'INR',
  ADD COLUMN tds_section_id BIGINT, ADD COLUMN custom JSONB NOT NULL DEFAULT '{}', ADD COLUMN credit_days INT;
ALTER TABLE customers ADD COLUMN pan VARCHAR(10), ADD COLUMN state_code CHAR(2), ADD COLUMN currency CHAR(3) DEFAULT 'INR',
  ADD COLUMN credit_limit NUMERIC(18,4), ADD COLUMN credit_days INT, ADD COLUMN custom JSONB NOT NULL DEFAULT '{}';
CREATE TABLE party_addresses (id, party_type VARCHAR(10) CHECK (party_type IN ('SUPPLIER','CUSTOMER')), party_id BIGINT NOT NULL,
  kind VARCHAR(10) CHECK (kind IN ('BILLING','SHIPPING','OTHER')), line1 VARCHAR(150), line2 VARCHAR(150), city VARCHAR(80),
  state_code CHAR(2), pincode VARCHAR(10), country CHAR(2) DEFAULT 'IN', gstin VARCHAR(15), is_default BOOLEAN DEFAULT FALSE);
CREATE TABLE party_contacts (id, party_type VARCHAR(10), party_id BIGINT, name VARCHAR(100), designation VARCHAR(60), phone VARCHAR(20), email VARCHAR(160));

-- Company profile
CREATE TABLE company_profile (id, legal_name VARCHAR(200) NOT NULL, trade_name VARCHAR(200), pan VARCHAR(10), cin VARCHAR(25),
  logo_attachment_id BIGINT, signature_attachment_id BIGINT, default_terms_id BIGINT, default_payment_terms_id BIGINT, fiscal_year_start_month INT NOT NULL DEFAULT 4);
CREATE TABLE company_branches (id, branch_code VARCHAR(10) NOT NULL, name VARCHAR(100), gstin VARCHAR(15), state_code CHAR(2) NOT NULL,
  address VARCHAR(400), is_default BOOLEAN DEFAULT FALSE, active BOOLEAN DEFAULT TRUE, UNIQUE (company_id, branch_code));
CREATE TABLE bank_accounts (id, branch_id BIGINT, account_name VARCHAR(100), bank_name VARCHAR(100), account_no_enc BYTEA, account_no_last4 CHAR(4),
  ifsc VARCHAR(11), upi_id VARCHAR(60), show_on_documents BOOLEAN DEFAULT TRUE, is_default BOOLEAN);
CREATE TABLE transporters (id, transporter_code VARCHAR(20), name VARCHAR(120), gst_transporter_id VARCHAR(15), contact VARCHAR(100), active BOOLEAN DEFAULT TRUE);
CREATE TABLE payment_terms (id, term_code VARCHAR(20), name VARCHAR(80), net_days INT NOT NULL, discount_pct NUMERIC(9,4), discount_days INT);
CREATE TABLE terms_templates (id, template_code VARCHAR(20), applies_to VARCHAR(25), body TEXT NOT NULL, is_default BOOLEAN);
CREATE TABLE user_branch_access (user_id BIGINT, branch_id BIGINT, PRIMARY KEY (user_id, branch_id));

-- Teams, notifications
CREATE TABLE teams (id, team_code VARCHAR(20), name VARCHAR(80), active BOOLEAN DEFAULT TRUE);
CREATE TABLE team_members (team_id BIGINT, user_id BIGINT, joined_at TIMESTAMPTZ DEFAULT now(), PRIMARY KEY (team_id, user_id));
CREATE TABLE team_report_access (team_id BIGINT, report_code VARCHAR(60), PRIMARY KEY (team_id, report_code));
CREATE TABLE notification_preferences (user_id BIGINT, event_type VARCHAR(50), channel VARCHAR(10) CHECK (channel IN ('IN_APP','EMAIL')),
  enabled BOOLEAN NOT NULL DEFAULT TRUE, PRIMARY KEY (user_id, event_type, channel));
CREATE TABLE notifications (id, user_id BIGINT, event_type VARCHAR(50), title VARCHAR(150), body VARCHAR(500), entity_type VARCHAR(40), entity_id BIGINT, read_at TIMESTAMPTZ);

-- Document series (replaces direct number_sequences use for new documents)
CREATE TABLE document_series (id, document_kind VARCHAR(30) NOT NULL, series_code VARCHAR(20) NOT NULL, branch_id BIGINT,
  format VARCHAR(80) NOT NULL,                       -- tokens: {FY} {yyyy} {MM} {BR} {0..0}
  numbering VARCHAR(10) NOT NULL DEFAULT 'AUTO' CHECK (numbering IN ('AUTO','MANUAL')),
  reset_policy VARCHAR(10) NOT NULL DEFAULT 'FY' CHECK (reset_policy IN ('NEVER','FY','MONTH')),
  is_default BOOLEAN NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, statutory BOOLEAN NOT NULL DEFAULT FALSE, -- statutory: no gaps, no manual
  UNIQUE (company_id, document_kind, series_code));
CREATE UNIQUE INDEX ux_series_default ON document_series(company_id, document_kind, COALESCE(branch_id,0)) WHERE is_default AND active;
CREATE TABLE document_series_counters (series_id BIGINT, period_key VARCHAR(10), last_value BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (series_id, period_key));
```
```sql
-- V13: cross-cutting
CREATE TABLE custom_field_definitions (id, entity_type VARCHAR(40) NOT NULL, field_code VARCHAR(40) NOT NULL, label VARCHAR(80) NOT NULL,
  data_type VARCHAR(12) NOT NULL CHECK (data_type IN ('TEXT','NUMBER','DATE','BOOLEAN','ENUM','LONGTEXT')), required BOOLEAN DEFAULT FALSE,
  options JSONB, show_on_print BOOLEAN DEFAULT FALSE, display_order INT DEFAULT 0, active BOOLEAN DEFAULT TRUE, UNIQUE (company_id, entity_type, field_code));
CREATE TABLE tags (id, name VARCHAR(40) NOT NULL, colour CHAR(7), UNIQUE (company_id, name));
CREATE TABLE entity_tags (entity_type VARCHAR(40), entity_id BIGINT, tag_id BIGINT REFERENCES tags, PRIMARY KEY (entity_type, entity_id, tag_id));
CREATE TABLE comments (id, entity_type VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL, visibility VARCHAR(10) NOT NULL CHECK (visibility IN ('INTERNAL','EXTERNAL')),
  body VARCHAR(4000) NOT NULL, created_at TIMESTAMPTZ, created_by BIGINT);   -- comments are never edited; delete = soft (deleted_at) with audit
CREATE INDEX ix_comments_entity ON comments(entity_type, entity_id, created_at);
CREATE TABLE attachments (id, entity_type VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL, file_name VARCHAR(200) NOT NULL, content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes > 0), sha256 CHAR(64) NOT NULL, storage_key VARCHAR(200) NOT NULL, uploaded_at TIMESTAMPTZ, uploaded_by BIGINT, deleted_at TIMESTAMPTZ);
CREATE INDEX ix_attach_entity ON attachments(entity_type, entity_id) WHERE deleted_at IS NULL;
CREATE TABLE document_links (id, source_type VARCHAR(40), source_id BIGINT, target_type VARCHAR(40), target_id BIGINT, link_kind VARCHAR(20), created_at TIMESTAMPTZ, UNIQUE (source_type, source_id, target_type, target_id));
CREATE TABLE search_index (entity_type VARCHAR(40), entity_id BIGINT, branch_id BIGINT, permission_code VARCHAR(60) NOT NULL,
  title VARCHAR(250) NOT NULL, subtitle VARCHAR(250), tsv TSVECTOR NOT NULL, updated_at TIMESTAMPTZ, PRIMARY KEY (entity_type, entity_id));
CREATE INDEX ix_search_tsv ON search_index USING GIN (tsv);  CREATE INDEX ix_search_title_trgm ON search_index USING GIN (title gin_trgm_ops);

-- Approval engine
CREATE TABLE approval_rules (id, document_type VARCHAR(30) NOT NULL, rule_name VARCHAR(100), priority INT NOT NULL DEFAULT 100,
  condition JSONB NOT NULL DEFAULT '{"all":[]}',                 -- see §4.1
  mode VARCHAR(10) NOT NULL CHECK (mode IN ('AUTO','MANUAL')), active BOOLEAN DEFAULT TRUE, built_in BOOLEAN DEFAULT FALSE);
CREATE TABLE approval_rule_steps (id, rule_id BIGINT REFERENCES approval_rules ON DELETE CASCADE, step_no INT NOT NULL, step_group INT NOT NULL DEFAULT 0,
  approver_type VARCHAR(12) NOT NULL CHECK (approver_type IN ('PERMISSION','ROLE','TEAM','USER')), approver_ref VARCHAR(60) NOT NULL,
  quorum VARCHAR(5) NOT NULL DEFAULT 'ANY' CHECK (quorum IN ('ANY','ALL')), UNIQUE (rule_id, step_no, approver_type, approver_ref));
ALTER TABLE approval_requests ADD COLUMN rule_id BIGINT, ADD COLUMN cycle_no INT NOT NULL DEFAULT 1, ADD COLUMN step_group INT NOT NULL DEFAULT 0,
  ADD COLUMN withdrawn_at TIMESTAMPTZ, ADD COLUMN approver_type VARCHAR(12), ADD COLUMN approver_ref VARCHAR(60);

-- Bulk import & jobs
CREATE TABLE import_jobs (id, import_code VARCHAR(40) NOT NULL, file_name VARCHAR(200), file_sha256 CHAR(64), storage_key VARCHAR(200),
  status VARCHAR(12) NOT NULL CHECK (status IN ('UPLOADED','VALIDATING','VALID','INVALID','COMMITTING','DONE','FAILED')),
  total_rows INT, error_rows INT, mapping JSONB, error_file_key VARCHAR(200), started_by BIGINT, finished_at TIMESTAMPTZ);
CREATE TABLE jobs (id, job_type VARCHAR(40) NOT NULL, params JSONB, status VARCHAR(12) NOT NULL, progress INT DEFAULT 0, result JSONB, error VARCHAR(500),
  requested_by BIGINT, created_at TIMESTAMPTZ, started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ);
```
**Backfill (post-migration job, not inside the migration):** build `search_index` from existing entities; copy existing prefixes from `number_sequences` into `document_series` + counters (keeping `last_value`) so numbering continues without gaps.

### 2.2 Phase 10 — Purchase (V14)
```sql
CREATE TABLE rfqs (id, DOC_HDR, requisition_id BIGINT, due_date DATE, delivery_location VARCHAR(200), terms_id BIGINT);
CREATE TABLE rfq_items (id, rfq_id BIGINT REFERENCES rfqs ON DELETE CASCADE, DOC_LINE, required_date DATE, UNIQUE (rfq_id, line_no));
CREATE TABLE rfq_suppliers (rfq_id BIGINT, supplier_id BIGINT, invited_at TIMESTAMPTZ, quote_status VARCHAR(12) DEFAULT 'PENDING', PRIMARY KEY (rfq_id, supplier_id));
CREATE TABLE supplier_quotes (id, DOC_HDR, rfq_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL, valid_until DATE, lead_time_days INT, freight NUMERIC(18,4), payment_terms_id BIGINT,
  awarded BOOLEAN DEFAULT FALSE, UNIQUE (rfq_id, supplier_id));
CREATE TABLE supplier_quote_items (id, quote_id BIGINT REFERENCES supplier_quotes ON DELETE CASCADE, rfq_item_id BIGINT NOT NULL, DOC_LINE, moq NUMERIC(18,3), awarded_qty NUMERIC(18,3) DEFAULT 0);

ALTER TABLE purchase_orders ADD COLUMN po_kind VARCHAR(10) NOT NULL DEFAULT 'STANDARD' CHECK (po_kind IN ('STANDARD','FOREIGN','ADVANCE')),
  ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'INR', ADD COLUMN exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1,
  ADD COLUMN rfq_id BIGINT, ADD COLUMN branch_id BIGINT, ADD COLUMN revision_no INT NOT NULL DEFAULT 0, ADD COLUMN root_document_id BIGINT,
  ADD COLUMN payment_terms_id BIGINT, ADD COLUMN advance_pct NUMERIC(9,4), ADD COLUMN advance_amount NUMERIC(18,4),
  ADD COLUMN incoterm VARCHAR(10), ADD COLUMN custom JSONB NOT NULL DEFAULT '{}';
ALTER TABLE po_items ADD COLUMN hsn_sac VARCHAR(10), ADD COLUMN tax_code_id BIGINT, ADD COLUMN discount_pct NUMERIC(9,4) DEFAULT 0,
  ADD COLUMN cgst_rate NUMERIC(9,4), ADD COLUMN sgst_rate NUMERIC(9,4), ADD COLUMN igst_rate NUMERIC(9,4), ADD COLUMN rfq_item_id BIGINT;
CREATE TABLE po_revisions (id, po_id BIGINT NOT NULL, revision_no INT NOT NULL, snapshot JSONB NOT NULL, reason VARCHAR(500) NOT NULL, created_by BIGINT, created_at TIMESTAMPTZ, UNIQUE (po_id, revision_no));
ALTER TABLE goods_receipts ADD COLUMN inward_kind VARCHAR(12) NOT NULL DEFAULT 'PO' CHECK (inward_kind IN ('PO','NO_PO','JOB_WORK','SERVICE','RETURN')),
  ADD COLUMN supplier_dc_no VARCHAR(40), ADD COLUMN supplier_dc_date DATE, ADD COLUMN vehicle_no VARCHAR(20), ADD COLUMN transporter_id BIGINT;

CREATE TABLE purchase_invoices (id, DOC_HDR, supplier_id BIGINT NOT NULL, supplier_invoice_no VARCHAR(40) NOT NULL, supplier_invoice_date DATE NOT NULL,
  po_id BIGINT, place_of_supply CHAR(2), reverse_charge BOOLEAN DEFAULT FALSE, tds_section_id BIGINT, tds_amount NUMERIC(18,4) DEFAULT 0,
  due_date DATE, match_status VARCHAR(12) NOT NULL DEFAULT 'MATCHED', journal_entry_id BIGINT, ap_balance NUMERIC(18,4),
  UNIQUE (company_id, supplier_id, supplier_invoice_no));      -- duplicate supplier invoice guard
CREATE TABLE purchase_invoice_items (id, invoice_id BIGINT REFERENCES purchase_invoices ON DELETE CASCADE, DOC_LINE, po_item_id BIGINT, grn_item_id BIGINT,
  match_status VARCHAR(12), qty_variance NUMERIC(18,3), price_variance NUMERIC(18,4));
CREATE TABLE match_exceptions (id, invoice_id BIGINT, invoice_item_id BIGINT, kind VARCHAR(12), detail VARCHAR(300), resolved_by BIGINT, resolved_at TIMESTAMPTZ, resolution_reason VARCHAR(500));
CREATE TABLE purchase_returns (id, DOC_HDR, supplier_id BIGINT NOT NULL, grn_id BIGINT, invoice_id BIGINT, reason_code VARCHAR(20), debit_note_id BIGINT);
CREATE TABLE purchase_return_items (id, return_id BIGINT REFERENCES purchase_returns ON DELETE CASCADE, DOC_LINE, batch_id BIGINT, location_id BIGINT NOT NULL, ledger_txn_id BIGINT);
```
**Tolerances (settings):** `purchase.match.qty_tolerance_pct` (default 2), `purchase.match.price_tolerance_pct` (default 1).

### 2.3 Phase 11 — Sales documents (V15)
```sql
CREATE TABLE quotations (id, DOC_HDR, customer_id BIGINT NOT NULL, bill_to_address_id BIGINT, ship_to_address_id BIGINT, valid_until DATE,
  salesperson_id BIGINT, payment_terms_id BIGINT, terms_id BIGINT, delivery_terms VARCHAR(200), incoterm VARCHAR(10), converted_to_type VARCHAR(25), converted_to_id BIGINT,
  lost_reason VARCHAR(200), enquiry_ref VARCHAR(60));
CREATE TABLE quotation_items (id, quotation_id BIGINT REFERENCES quotations ON DELETE CASCADE, DOC_LINE, lead_time_days INT, customer_part_no VARCHAR(40), UNIQUE (quotation_id, line_no));
CREATE TABLE order_confirmations (id, DOC_HDR, oc_kind VARCHAR(10) NOT NULL DEFAULT 'DOMESTIC' CHECK (oc_kind IN ('DOMESTIC','FOREIGN')), customer_id BIGINT NOT NULL,
  quotation_id BIGINT, customer_po_no VARCHAR(60), customer_po_date DATE, bill_to_address_id BIGINT, ship_to_address_id BIGINT, payment_terms_id BIGINT, terms_id BIGINT,
  delivery_date DATE, incoterm VARCHAR(10), port_of_loading VARCHAR(80), port_of_discharge VARCHAR(80), sales_order_id BIGINT UNIQUE, copied_from_id BIGINT);
CREATE TABLE order_confirmation_items (id, oc_id BIGINT REFERENCES order_confirmations ON DELETE CASCADE, DOC_LINE, delivery_date DATE, customer_part_no VARCHAR(40),
  production_order_id BIGINT, qty_dispatched NUMERIC(18,3) DEFAULT 0, UNIQUE (oc_id, line_no));
ALTER TABLE sales_orders ADD COLUMN oc_id BIGINT, ADD COLUMN currency CHAR(3) DEFAULT 'INR', ADD COLUMN exchange_rate NUMERIC(18,8) DEFAULT 1, ADD COLUMN branch_id BIGINT;
ALTER TABLE sales_order_items ADD COLUMN oc_item_id BIGINT, ADD COLUMN production_order_id BIGINT, ADD COLUMN rate NUMERIC(18,4), ADD COLUMN hsn_sac VARCHAR(10), ADD COLUMN tax_code_id BIGINT;
CREATE TABLE delivery_challans (id, DOC_HDR, challan_type VARCHAR(15) NOT NULL, party_type VARCHAR(10) NOT NULL, party_id BIGINT NOT NULL, ship_to_address_id BIGINT,
  dispatch_id BIGINT, job_work_order_id BIGINT, purpose VARCHAR(120), transporter_id BIGINT, vehicle_no VARCHAR(20), lr_no VARCHAR(40), expected_return_date DATE, stock_effect BOOLEAN NOT NULL DEFAULT FALSE);
CREATE TABLE delivery_challan_items (id, challan_id BIGINT REFERENCES delivery_challans ON DELETE CASCADE, DOC_LINE, batch_id BIGINT, qty_returned NUMERIC(18,3) DEFAULT 0, ledger_txn_id BIGINT);
ALTER TABLE dispatches ADD COLUMN transporter_id BIGINT, ADD COLUMN lr_no VARCHAR(40), ADD COLUMN courier VARCHAR(60), ADD COLUMN tracking_no VARCHAR(60),
  ADD COLUMN tracking_status VARCHAR(30), ADD COLUMN sent_to_customer_at TIMESTAMPTZ, ADD COLUMN sent_to_email VARCHAR(160);
```
Quotation conversion rules are in §4.9. Challans with `stock_effect = TRUE` post `SALES_DISPATCH`-class moves to a customer/party location only for returnable types (see §4.10).

### 2.4 Phase 12 — Finance, tax, GST (V16–V17)
```sql
-- Currency and tax
CREATE TABLE currencies (code CHAR(3) PRIMARY KEY, name VARCHAR(40) NOT NULL, symbol VARCHAR(5), decimals INT NOT NULL DEFAULT 2, active BOOLEAN DEFAULT TRUE);
CREATE TABLE exchange_rates (id, currency CHAR(3) NOT NULL REFERENCES currencies, rate_date DATE NOT NULL, rate_to_inr NUMERIC(18,8) NOT NULL CHECK (rate_to_inr > 0),
  rate_type VARCHAR(10) NOT NULL DEFAULT 'SPOT' CHECK (rate_type IN ('SPOT','CUSTOMS','BANK')), source VARCHAR(20) NOT NULL, UNIQUE (currency, rate_date, rate_type));
CREATE TABLE tax_codes (id, tax_code VARCHAR(20) NOT NULL, name VARCHAR(80), kind VARCHAR(10) CHECK (kind IN ('GST','EXEMPT','NIL','NON_GST','ZERO_RATED')), UNIQUE (company_id, tax_code));
CREATE TABLE tax_rates (id, tax_code_id BIGINT REFERENCES tax_codes, hsn_sac_prefix VARCHAR(10), cgst NUMERIC(9,4) NOT NULL DEFAULT 0, sgst NUMERIC(9,4) NOT NULL DEFAULT 0,
  igst NUMERIC(9,4) NOT NULL DEFAULT 0, cess NUMERIC(9,4) NOT NULL DEFAULT 0, effective_from DATE NOT NULL, effective_to DATE,
  CHECK (igst = cgst + sgst), CHECK (effective_to IS NULL OR effective_to >= effective_from));
CREATE TABLE tds_sections (id, section VARCHAR(10) NOT NULL, description VARCHAR(150), rate NUMERIC(9,4) NOT NULL, rate_no_pan NUMERIC(9,4), annual_threshold NUMERIC(18,4),
  single_threshold NUMERIC(18,4), effective_from DATE NOT NULL, effective_to DATE);
CREATE TABLE states (state_code CHAR(2) PRIMARY KEY, name VARCHAR(60) NOT NULL, gst_code CHAR(2));

-- Chart of accounts, mapping, periods
CREATE TABLE accounts (id, account_code VARCHAR(20) NOT NULL, name VARCHAR(100) NOT NULL, account_type VARCHAR(12) NOT NULL
  CHECK (account_type IN ('ASSET','LIABILITY','INCOME','EXPENSE','EQUITY','CONTROL')), is_control BOOLEAN DEFAULT FALSE, party_required BOOLEAN DEFAULT FALSE,
  system_key VARCHAR(40), active BOOLEAN DEFAULT TRUE, parent_id BIGINT, UNIQUE (company_id, account_code), UNIQUE (company_id, system_key));
CREATE TABLE account_mappings (id, event_type VARCHAR(40) NOT NULL, tax_code_id BIGINT, branch_id BIGINT, side VARCHAR(6) CHECK (side IN ('DEBIT','CREDIT')), account_id BIGINT NOT NULL,
  UNIQUE NULLS NOT DISTINCT (company_id, event_type, tax_code_id, branch_id, side));
CREATE TABLE fiscal_periods (id, fiscal_year VARCHAR(9) NOT NULL, period_start DATE NOT NULL, period_end DATE NOT NULL, status VARCHAR(8) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')), closed_by BIGINT, closed_at TIMESTAMPTZ, UNIQUE (company_id, period_start));

-- Journal (append-only, partitioned)
CREATE TABLE journal_entries (id BIGINT GENERATED ALWAYS AS IDENTITY, entry_no VARCHAR(40) NOT NULL, entry_date DATE NOT NULL, period_id BIGINT NOT NULL, branch_id BIGINT,
  source_type VARCHAR(40) NOT NULL, source_id BIGINT NOT NULL, event_type VARCHAR(40) NOT NULL, currency CHAR(3) DEFAULT 'INR', exchange_rate NUMERIC(18,8) DEFAULT 1,
  narration VARCHAR(300), reverses_entry_id BIGINT, idempotency_key VARCHAR(80) NOT NULL, posted_by BIGINT NOT NULL, posted_at TIMESTAMPTZ NOT NULL DEFAULT now(), company_id BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (id, entry_date), UNIQUE (company_id, idempotency_key, entry_date)) PARTITION BY RANGE (entry_date);
CREATE TABLE journal_lines (id BIGINT GENERATED ALWAYS AS IDENTITY, entry_id BIGINT NOT NULL, entry_date DATE NOT NULL, line_no INT NOT NULL, account_id BIGINT NOT NULL,
  party_type VARCHAR(10), party_id BIGINT, debit NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (debit >= 0), credit NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (credit >= 0),
  amount_foreign NUMERIC(18,4), currency CHAR(3), tax_code_id BIGINT, cost_centre VARCHAR(20), memo VARCHAR(200),
  PRIMARY KEY (id, entry_date), CHECK ((debit = 0) <> (credit = 0))) PARTITION BY RANGE (entry_date);
CREATE INDEX ix_jl_acct ON journal_lines(account_id, entry_date);  CREATE INDEX ix_jl_party ON journal_lines(party_type, party_id, entry_date);  CREATE INDEX ix_jl_entry ON journal_lines(entry_id);
-- triggers: BEFORE UPDATE/DELETE on both -> raise exception (same pattern as inventory_transactions); deferred CONSTRAINT TRIGGER checks sum(debit)=sum(credit) per entry
CREATE TABLE account_balances (id, account_id BIGINT NOT NULL, party_type VARCHAR(10), party_id BIGINT, period_id BIGINT NOT NULL, debit NUMERIC(18,4) DEFAULT 0, credit NUMERIC(18,4) DEFAULT 0,
  version BIGINT DEFAULT 0, UNIQUE NULLS NOT DISTINCT (account_id, party_type, party_id, period_id));
CREATE TABLE open_items (id, item_type VARCHAR(20) NOT NULL, party_type VARCHAR(10) NOT NULL, party_id BIGINT NOT NULL, source_type VARCHAR(40) NOT NULL, source_id BIGINT NOT NULL,
  doc_no VARCHAR(40), doc_date DATE NOT NULL, due_date DATE, currency CHAR(3) DEFAULT 'INR', amount NUMERIC(18,4) NOT NULL, amount_inr NUMERIC(18,4) NOT NULL,
  balance NUMERIC(18,4) NOT NULL, balance_inr NUMERIC(18,4) NOT NULL, branch_id BIGINT, version BIGINT DEFAULT 0, CHECK (abs(balance) <= abs(amount)));
CREATE INDEX ix_open_party ON open_items(party_type, party_id) WHERE balance <> 0;   CREATE INDEX ix_open_due ON open_items(due_date) WHERE balance <> 0;
CREATE TABLE inventory_cost_adjustments (id, balance_id BIGINT NOT NULL, batch_id BIGINT, value_delta NUMERIC(18,4) NOT NULL, reason VARCHAR(40) NOT NULL, source_type VARCHAR(40), source_id BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by BIGINT);   -- append-only; changes value, never quantity

-- Sales invoicing
CREATE TABLE sales_invoices (id, DOC_HDR, invoice_kind VARCHAR(10) NOT NULL CHECK (invoice_kind IN ('TAX','PROFORMA','EXPORT','BILL_OF_SUPPLY')),
  customer_id BIGINT NOT NULL, bill_to_address_id BIGINT, ship_to_address_id BIGINT, place_of_supply CHAR(2) NOT NULL, supply_type VARCHAR(10) NOT NULL
    CHECK (supply_type IN ('B2B','B2C','EXPORT_LUT','EXPORT_IGST','SEZ','DEEMED')), reverse_charge BOOLEAN DEFAULT FALSE,
  sales_order_id BIGINT, oc_id BIGINT, dispatch_id BIGINT, customer_po_no VARCHAR(60), payment_terms_id BIGINT, due_date DATE, bank_account_id BIGINT,
  tcs_amount NUMERIC(18,4) DEFAULT 0, tds_expected NUMERIC(18,4) DEFAULT 0, shipping_bill_no VARCHAR(30), shipping_bill_date DATE, port_code VARCHAR(10), incoterm VARCHAR(10),
  einvoice_status VARCHAR(14) NOT NULL DEFAULT 'NOT_REQUIRED', irn CHAR(64), ack_no VARCHAR(30), ack_date TIMESTAMPTZ, signed_qr TEXT,
  eway_status VARCHAR(14) DEFAULT 'NOT_REQUIRED', eway_bill_no VARCHAR(20), eway_valid_until TIMESTAMPTZ,
  journal_entry_id BIGINT, balance_inr NUMERIC(18,4), posted_at TIMESTAMPTZ, posted_by BIGINT, cancelled_reason VARCHAR(300));
CREATE TABLE sales_invoice_items (id, invoice_id BIGINT REFERENCES sales_invoices ON DELETE CASCADE, DOC_LINE, dispatch_item_id BIGINT, so_item_id BIGINT, batch_id BIGINT, UNIQUE (invoice_id, line_no));
CREATE TABLE adjustment_notes (id, DOC_HDR, note_kind VARCHAR(8) NOT NULL CHECK (note_kind IN ('CREDIT','DEBIT')), side VARCHAR(8) NOT NULL CHECK (side IN ('SALES','PURCHASE')),
  party_id BIGINT NOT NULL, original_invoice_type VARCHAR(25), original_invoice_id BIGINT, reason_code VARCHAR(20) NOT NULL, return_id BIGINT, journal_entry_id BIGINT, einvoice_status VARCHAR(14) DEFAULT 'NOT_REQUIRED', irn CHAR(64));
CREATE TABLE adjustment_note_items (id, note_id BIGINT REFERENCES adjustment_notes ON DELETE CASCADE, DOC_LINE, original_item_id BIGINT);

-- Payments
CREATE TABLE payments (id, doc_no VARCHAR(40) NOT NULL, doc_date DATE NOT NULL, direction VARCHAR(8) NOT NULL CHECK (direction IN ('RECEIPT','PAYMENT')),
  kind VARCHAR(10) NOT NULL DEFAULT 'REGULAR' CHECK (kind IN ('REGULAR','ADVANCE','ON_ACCOUNT')), party_type VARCHAR(10) NOT NULL, party_id BIGINT NOT NULL, branch_id BIGINT, status VARCHAR(18) NOT NULL,
  mode VARCHAR(12) NOT NULL CHECK (mode IN ('CASH','CHEQUE','NEFT','RTGS','UPI','CARD','GATEWAY','ADJUSTMENT')), bank_account_id BIGINT,
  reference_no VARCHAR(60), currency CHAR(3) DEFAULT 'INR', exchange_rate NUMERIC(18,8) DEFAULT 1, amount NUMERIC(18,4) NOT NULL CHECK (amount > 0), amount_inr NUMERIC(18,4) NOT NULL,
  tds_amount NUMERIC(18,4) DEFAULT 0, bank_charges NUMERIC(18,4) DEFAULT 0, gateway_txn_id VARCHAR(60), unallocated_inr NUMERIC(18,4) NOT NULL, journal_entry_id BIGINT,
  remarks VARCHAR(500), reversed_by_id BIGINT, UNIQUE (company_id, doc_no));
CREATE TABLE payment_allocations (id, payment_id BIGINT NOT NULL REFERENCES payments, open_item_id BIGINT NOT NULL REFERENCES open_items, allocated NUMERIC(18,4) NOT NULL CHECK (allocated > 0),
  allocated_inr NUMERIC(18,4) NOT NULL, forex_diff_inr NUMERIC(18,4) DEFAULT 0, allocated_at TIMESTAMPTZ DEFAULT now(), allocated_by BIGINT, unallocated_at TIMESTAMPTZ);
CREATE TABLE advance_receipt_vouchers (id, payment_id BIGINT UNIQUE NOT NULL, doc_no VARCHAR(40), customer_id BIGINT, sales_order_id BIGINT, oc_id BIGINT, tax_amount NUMERIC(18,4) DEFAULT 0, applied_inr NUMERIC(18,4) DEFAULT 0);
CREATE TABLE opening_balances (id, party_type VARCHAR(10), party_id BIGINT, account_id BIGINT, as_of DATE NOT NULL, debit NUMERIC(18,4) DEFAULT 0, credit NUMERIC(18,4) DEFAULT 0, doc_no VARCHAR(40), doc_date DATE, due_date DATE, posted_entry_id BIGINT);
CREATE TABLE tds_entries (id, direction VARCHAR(10) CHECK (direction IN ('DEDUCTED','COLLECTED')), party_type VARCHAR(10), party_id BIGINT, section_id BIGINT NOT NULL, source_type VARCHAR(40), source_id BIGINT,
  base_amount NUMERIC(18,4), rate NUMERIC(9,4), tds_amount NUMERIC(18,4), deduction_date DATE, deposit_date DATE, challan_no VARCHAR(30), certificate_no VARCHAR(30), fiscal_year VARCHAR(9));
CREATE TABLE payment_reminders (id, open_item_id BIGINT, party_id BIGINT, level INT, mode VARCHAR(8) CHECK (mode IN ('AUTO','MANUAL')), sent_at TIMESTAMPTZ, channel VARCHAR(8), sent_by BIGINT, template_id BIGINT);
CREATE TABLE reminder_policies (id, name VARCHAR(60), days_after_due INT NOT NULL, level INT NOT NULL, template_id BIGINT, active BOOLEAN DEFAULT TRUE);
CREATE TABLE einvoice_requests (id, document_type VARCHAR(25), document_id BIGINT, request_kind VARCHAR(10) CHECK (request_kind IN ('GENERATE','CANCEL','EWAY','EWAY_EXTEND','EWAY_CANCEL')),
  payload JSONB, status VARCHAR(10), provider VARCHAR(20), attempts INT DEFAULT 0, response JSONB, error_code VARCHAR(30), error_message VARCHAR(500), created_at TIMESTAMPTZ, completed_at TIMESTAMPTZ);
```
Statutory tables (GST schema, e-way bill fields, GSTR formats, TDS sections) **[VERIFY]** against current government specifications before coding. Posted-document immutability is enforced by a trigger on `sales_invoices`/`adjustment_notes`/`purchase_invoices` rejecting updates to monetary and identity columns when `status IN ('POSTED','PARTIALLY_PAID','PAID')` (payment status and balance columns are updated only by `FinancePostingService`).

### 2.5 Phase 13 — Manufacturing extensions (V18)
```sql
ALTER TABLE bom_items ADD COLUMN component_kind VARCHAR(12) NOT NULL DEFAULT 'MATERIAL' CHECK (component_kind IN ('MATERIAL','SUB_ASSEMBLY')),
  ADD COLUMN child_product_id BIGINT REFERENCES products, ADD COLUMN make_or_buy VARCHAR(5) DEFAULT 'BUY', ADD COLUMN scrap_pct NUMERIC(9,4) DEFAULT 0, ADD COLUMN level_hint INT;
  -- CHECK: exactly one of material_id / child_product_id depending on component_kind
CREATE TABLE bom_item_alternates (id, bom_item_id BIGINT NOT NULL REFERENCES bom_items ON DELETE CASCADE, alternate_material_id BIGINT NOT NULL, ratio NUMERIC(12,6) NOT NULL DEFAULT 1 CHECK (ratio > 0),
  priority INT NOT NULL DEFAULT 1, valid_from DATE, valid_to DATE, UNIQUE (bom_item_id, alternate_material_id));
ALTER TABLE production_orders ADD COLUMN parent_production_order_id BIGINT, ADD COLUMN oc_id BIGINT, ADD COLUMN source_mrp_suggestion_id BIGINT;
ALTER TABLE production_order_materials ADD COLUMN substituted_for_material_id BIGINT;      -- set when an alternate was issued
CREATE TABLE costing_policies (id, name VARCHAR(40), material_basis VARCHAR(12) CHECK (material_basis IN ('MOVING_AVG','LAST_PO','STANDARD')), include_conversion BOOLEAN DEFAULT TRUE, is_default BOOLEAN);
CREATE TABLE machine_rates (machine_id BIGINT, rate_per_hour NUMERIC(18,4), setup_rate_per_hour NUMERIC(18,4), effective_from DATE, PRIMARY KEY (machine_id, effective_from));
CREATE TABLE bom_cost_rollups (id, bom_id BIGINT NOT NULL, policy_id BIGINT, material_cost NUMERIC(18,4), conversion_cost NUMERIC(18,4), total_cost NUMERIC(18,4), detail JSONB, computed_at TIMESTAMPTZ, computed_by BIGINT);
ALTER TABLE fg_batches ADD COLUMN unit_cost NUMERIC(18,4), ADD COLUMN material_cost NUMERIC(18,4), ADD COLUMN conversion_cost NUMERIC(18,4), ADD COLUMN scrap_loss_cost NUMERIC(18,4);
CREATE TABLE process_log (id, production_order_id BIGINT NOT NULL, operation_id BIGINT, event_type VARCHAR(25) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, user_id BIGINT, machine_id BIGINT, qty NUMERIC(18,3), detail JSONB);
CREATE INDEX ix_process_log_order ON process_log(production_order_id, occurred_at);

CREATE TABLE mrp_runs (id, run_no VARCHAR(30) UNIQUE, run_by BIGINT, run_at TIMESTAMPTZ, demand_source VARCHAR(10) CHECK (demand_source IN ('FG','OC','SO','MIN_STOCK')), horizon_days INT,
  options JSONB, status VARCHAR(10), summary JSONB);
CREATE TABLE mrp_demand (id, run_id BIGINT REFERENCES mrp_runs ON DELETE CASCADE, product_id BIGINT NOT NULL, qty NUMERIC(18,3) NOT NULL, needed_by DATE, source_type VARCHAR(25), source_id BIGINT);
CREATE TABLE mrp_requirements (id, run_id BIGINT REFERENCES mrp_runs ON DELETE CASCADE, item_type VARCHAR(10), material_id BIGINT, product_id BIGINT, bom_level INT, gross NUMERIC(18,3),
  on_hand NUMERIC(18,3), reserved NUMERIC(18,3), on_order NUMERIC(18,3), in_production NUMERIC(18,3), safety NUMERIC(18,3), net NUMERIC(18,3), needed_by DATE);
CREATE TABLE mrp_suggestions (id, run_id BIGINT REFERENCES mrp_runs ON DELETE CASCADE, suggestion_type VARCHAR(25) NOT NULL, material_id BIGINT, product_id BIGINT, qty NUMERIC(18,3) NOT NULL,
  order_by DATE, needed_by DATE, supplier_id BIGINT, est_rate NUMERIC(18,4), status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CONVERTED','DISMISSED')), converted_type VARCHAR(25), converted_id BIGINT, reason VARCHAR(200));
CREATE TABLE mrp_exceptions (id, run_id BIGINT REFERENCES mrp_runs ON DELETE CASCADE, kind VARCHAR(25), material_id BIGINT, product_id BIGINT, detail VARCHAR(300));
CREATE TABLE label_templates (id, template_code VARCHAR(20), name VARCHAR(80), entity_type VARCHAR(25), width_mm NUMERIC(6,1), height_mm NUMERIC(6,1), symbology VARCHAR(10) CHECK (symbology IN ('CODE128','QR')), layout JSONB, output VARCHAR(5) CHECK (output IN ('PDF','ZPL')));
CREATE TABLE label_print_log (id, template_id BIGINT, entity_type VARCHAR(25), entity_id BIGINT, copies INT, printed_by BIGINT, printed_at TIMESTAMPTZ);
CREATE UNIQUE INDEX ux_material_barcode ON materials(company_id, barcode) WHERE barcode IS NOT NULL;
CREATE UNIQUE INDEX ux_product_barcode ON products(company_id, barcode) WHERE barcode IS NOT NULL;
-- stock card / inventory history: views over inventory_transactions (V18), see §8
```

### 2.6 Phase 14 — Sub-contract and service (V19)
```sql
CREATE TABLE job_work_orders (id, DOC_HDR, vendor_id BIGINT NOT NULL REFERENCES suppliers, process_description VARCHAR(300), output_product_id BIGINT, output_material_id BIGINT, planned_output_qty NUMERIC(18,3),
  charge_basis VARCHAR(10) CHECK (charge_basis IN ('PER_UNIT','PER_KG','LUMPSUM')), charge_rate NUMERIC(18,4), expected_return_date DATE, vendor_location_id BIGINT, production_order_id BIGINT, tds_section_id BIGINT);
CREATE TABLE job_work_materials (id, jw_id BIGINT REFERENCES job_work_orders ON DELETE CASCADE, material_id BIGINT, product_id BIGINT, planned_qty NUMERIC(18,3), issued_qty NUMERIC(18,3) DEFAULT 0,
  consumed_qty NUMERIC(18,3) DEFAULT 0, returned_qty NUMERIC(18,3) DEFAULT 0, scrap_qty NUMERIC(18,3) DEFAULT 0, CHECK (issued_qty >= consumed_qty + returned_qty + scrap_qty));
CREATE TABLE job_work_issues (id, jw_id BIGINT, challan_id BIGINT, batch_id BIGINT, qty NUMERIC(18,3), ledger_txn_id BIGINT, issued_at TIMESTAMPTZ);
CREATE TABLE job_work_receipts (id, jw_id BIGINT, doc_no VARCHAR(40), received_at TIMESTAMPTZ, output_qty NUMERIC(18,3), good_qty NUMERIC(18,3), rejected_qty NUMERIC(18,3), material_consumed JSONB, grn_id BIGINT, inspection_id BIGINT);
CREATE TABLE service_orders (id, DOC_HDR, direction VARCHAR(8) NOT NULL CHECK (direction IN ('PURCHASE','SALES')), party_id BIGINT NOT NULL, service_description VARCHAR(300), expected_date DATE, branch_id BIGINT);
CREATE TABLE service_order_items (id, service_order_id BIGINT REFERENCES service_orders ON DELETE CASCADE, DOC_LINE, confirmed_qty NUMERIC(18,3) DEFAULT 0, invoiced_qty NUMERIC(18,3) DEFAULT 0);
CREATE TABLE service_confirmations (id, DOC_HDR, service_order_id BIGINT NOT NULL, challan_id BIGINT, inward_id BIGINT, accepted_by BIGINT, notes VARCHAR(500));
CREATE TABLE service_confirmation_items (id, confirmation_id BIGINT REFERENCES service_confirmations ON DELETE CASCADE, order_item_id BIGINT NOT NULL, qty NUMERIC(18,3) NOT NULL CHECK (qty > 0));
ALTER TABLE warehouse_locations DROP CONSTRAINT IF EXISTS warehouse_locations_location_type_check;
ALTER TABLE warehouse_locations ADD CONSTRAINT warehouse_locations_location_type_check CHECK (location_type IN
  ('RAW_MATERIAL','WIP','FINISHED','QUARANTINE','QUALITY_PENDING','SCRAP','DISPATCH','SUBCONTRACT'));
-- vendor_location_id: one auto-created warehouse location (type SUBCONTRACT) per job-work vendor in a system warehouse "VENDORS"
```

### 2.7 Phase 15 — BI (V20)
```sql
CREATE TABLE saved_reports (id, owner_id BIGINT NOT NULL, report_code VARCHAR(60), name VARCHAR(120), source_view VARCHAR(50) NOT NULL, definition JSONB NOT NULL,
  share_scope VARCHAR(8) NOT NULL DEFAULT 'PRIVATE' CHECK (share_scope IN ('PRIVATE','TEAM','ROLE','ALL')), share_ref VARCHAR(60), chart JSONB, schedule JSONB, created_at TIMESTAMPTZ);
CREATE TABLE report_columns_catalogue (view_name VARCHAR(50), column_name VARCHAR(50), label VARCHAR(80), data_type VARCHAR(12), role VARCHAR(10) CHECK (role IN ('DIMENSION','MEASURE','ATTRIBUTE')),
  aggregations VARCHAR(40), permission_code VARCHAR(60), PRIMARY KEY (view_name, column_name));
-- Views: bi_sales, bi_purchase, bi_inventory, bi_production, bi_quotation, bi_accounts, reg_* registers (see §8). Materialized: mv_bi_sales_month, mv_bi_purchase_month, mv_bi_stock_value_day, mv_ageing_snapshot (refreshed by job).
CREATE ROLE ims_report_ro NOLOGIN;  GRANT SELECT ON bi_sales, bi_purchase, bi_inventory, bi_production, bi_quotation, bi_accounts TO ims_report_ro;   -- report builder runs SET LOCAL ROLE ims_report_ro
```

### 2.8 Phase 16 — Integrations (V21)
```sql
CREATE TABLE integration_connections (id, connector VARCHAR(20) NOT NULL CHECK (connector IN ('TALLY','BUSY','AFTERSHIP','GSP','PAYMENT','SMTP')), name VARCHAR(80), config JSONB NOT NULL DEFAULT '{}',
  secret_enc BYTEA, secret_key_id VARCHAR(20), active BOOLEAN DEFAULT FALSE, last_test_at TIMESTAMPTZ, last_test_ok BOOLEAN);
CREATE TABLE outbox_events (id BIGINT GENERATED ALWAYS AS IDENTITY, event_type VARCHAR(50) NOT NULL, connector VARCHAR(20) NOT NULL, connection_id BIGINT, entity_type VARCHAR(40), entity_id BIGINT,
  payload JSONB NOT NULL, status VARCHAR(10) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_error VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), delivered_at TIMESTAMPTZ, dedup_key VARCHAR(120) NOT NULL, PRIMARY KEY (id, created_at), UNIQUE (connector, dedup_key, created_at)) PARTITION BY RANGE (created_at);
CREATE INDEX ix_outbox_due ON outbox_events(next_attempt_at) WHERE status IN ('PENDING','FAILED');
CREATE TABLE inbox_events (provider VARCHAR(20), event_id VARCHAR(80), received_at TIMESTAMPTZ DEFAULT now(), payload JSONB, processed_at TIMESTAMPTZ, PRIMARY KEY (provider, event_id));
CREATE TABLE integration_calls (id BIGINT GENERATED ALWAYS AS IDENTITY, connection_id BIGINT, outbox_event_id BIGINT, direction VARCHAR(3), request_summary VARCHAR(500), status_code INT, ok BOOLEAN,
  duration_ms INT, correlation_id VARCHAR(40), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (id, created_at)) PARTITION BY RANGE (created_at);
CREATE TABLE tally_rules (id, rule_name VARCHAR(80), event_type VARCHAR(40) NOT NULL, voucher_type VARCHAR(30) NOT NULL, condition JSONB, party_ledger_template VARCHAR(100),
  line_ledger_map JSONB NOT NULL, narration_template VARCHAR(200), requires_approval BOOLEAN DEFAULT TRUE, priority INT DEFAULT 100, active BOOLEAN DEFAULT TRUE);
CREATE TABLE accounting_vouchers (id, connector VARCHAR(10) CHECK (connector IN ('TALLY','BUSY')), source_type VARCHAR(40), source_id BIGINT, voucher_type VARCHAR(30), payload_xml TEXT,
  status VARCHAR(18) NOT NULL CHECK (status IN ('PENDING_APPROVAL','APPROVED','SENT','POSTED','EXCEPTION','REJECTED')), exception_code VARCHAR(30), exception_detail VARCHAR(400),
  approved_by BIGINT, approved_at TIMESTAMPTZ, external_ref VARCHAR(60), journal_entry_id BIGINT, UNIQUE (connector, source_type, source_id, voucher_type));
CREATE TABLE external_ledger_map (id, connector VARCHAR(10), account_id BIGINT, party_type VARCHAR(10), party_id BIGINT, external_name VARCHAR(150) NOT NULL, verified_at TIMESTAMPTZ, UNIQUE (connector, external_name));
CREATE TABLE shipment_trackings (id, dispatch_id BIGINT NOT NULL, courier_slug VARCHAR(40), tracking_no VARCHAR(60), provider_ref VARCHAR(60), status VARCHAR(30), last_checkpoint JSONB, delivered_at TIMESTAMPTZ, UNIQUE (courier_slug, tracking_no));
CREATE TABLE payment_links (id, invoice_id BIGINT NOT NULL, provider_ref VARCHAR(60), url VARCHAR(400), amount NUMERIC(18,4), status VARCHAR(12), expires_at TIMESTAMPTZ, payment_id BIGINT);
```

---

## 3. Domain invariants (checked in the domain layer, backed by DB constraints)

| ID | Invariant | Enforcement |
|---|---|---|
| F-01 | Every journal entry balances: Σdebit = Σcredit (to 4 decimals, and to the paisa in INR) | service check + deferred constraint trigger |
| F-02 | Journal entries/lines are never updated or deleted; corrections are reversing entries | triggers (as ledger) |
| F-03 | No posting into a CLOSED period | service check + trigger on `journal_entries.period_id` |
| F-04 | Posted invoices/notes: identity, party, lines, amounts, tax are immutable | trigger + service |
| F-05 | `open_items.balance` = amount − Σ(active allocations) − Σ(applied notes) | recomputed by nightly job; mismatch = alert |
| F-06 | A payment allocation never exceeds the open item balance or the payment's unallocated amount | row locks on `open_items` + `payments` (ordered by id) |
| F-07 | Statutory series (invoices, notes) have no gaps and no manual numbers; numbers are assigned at POST | `document_series.statutory` |
| F-08 | Tax breakup on a line is either CGST+SGST or IGST (or none), never mixed; rates stored per line | CHECK + calculator |
| F-09 | Foreign-currency documents store currency, rate and INR value at posting; they are not recalculated afterwards | columns + immutability |
| F-10 | Purchase invoice cannot post if match status is a variance and no override exists | service |
| F-11 | A purchase supplier-invoice number is unique per supplier | unique index |
| F-12 | Stock effects occur only through `InventoryPostingService`; money effects only through `FinancePostingService`; documents calling both do so in one DB transaction | ArchUnit |
| F-13 | A job-work material line satisfies issued ≥ consumed + returned + scrap | CHECK |
| F-14 | Alternate-item substitution is recorded on the issue (`substituted_for_material_id`) | service |
| F-15 | An approval request cannot be decided by its requester; decisions are append-only per cycle | service |
| F-16 | An MRP run is immutable after completion; suggestions change only `status`/`converted_*` | service + trigger |
| F-17 | Credit note total ≤ original invoice outstanding total (value and per-line quantity) unless it is a pure rate/discount note flagged accordingly | service |
| F-18 | OC ↔ SO is 1:1; reservation/dispatch logic is unchanged | unique constraint `order_confirmations.sales_order_id` |
| F-19 | BOM graph is acyclic across levels | service at approval (recursive CTE) |
| F-20 | Attachments are immutable; "delete" is a soft delete with audit | service |

---

## 4. Algorithms and components

### 4.1 Approval rules
Condition JSON (evaluated by `ConditionEvaluator`, no scripting):
```json
{ "all": [ {"field":"grand_total_inr","op":">=","value":100000},
           {"any":[ {"field":"supplier_id","op":"in","value":[12,15]}, {"field":"po_kind","op":"=","value":"FOREIGN"} ]} ] }
```
Operators: `= != > >= < <= in not_in between contains`. Allowed fields are declared per document type in code (whitelist), so a rule cannot read arbitrary columns.
**Selection:** active rules for the document type, ascending `priority`, first match wins; no match → document's default rule (`built_in`), and if none exists the document cannot be submitted (config error shown to admin).
**Execution:** AUTO → document goes straight to APPROVED with an `approval_requests` row marked auto. MANUAL → create requests for step 1 (all `step_group` members in parallel; `quorum ANY|ALL`); when a step's quorum is met the next step opens; last step approved → fire `DocumentApproved`. "Approve and send" = approve then call the document's `send()` hook in the same transaction. **Withdraw:** only requester, only while PENDING; marks requests `withdrawn_at`, document → DRAFT, audit. **Amend/cancel an approved document:** creates a new cycle (`cycle_no + 1`); cancellation of an approved document needs `<DOC>_CANCEL` permission plus the rule's cancel path.
**Built-ins** (migration inserts, `built_in = true`): `PURCHASE_ORDER` (PURCHASE_APPROVE), `STOCK_ADJUSTMENT` (supervisor → manager → admin above `inventory.adjustment.admin_threshold`), `BOM` (engineering review → approve). Existing behaviour must match byte-for-byte in API responses; Phase 2–4 tests run unchanged and are the regression proof.

### 4.2 Document series allocation (`DocumentSeriesService.next(kind, branch, date)`)
1. Resolve series: explicit `series_id` if provided, else the default for `(kind, branch)` falling back to `(kind, NULL)`.
2. `period_key` = `FY` → `2026-27`; `MONTH` → `2026-10`; `NEVER` → `ALL`.
3. `SELECT … FROM document_series_counters WHERE series_id=? AND period_key=? FOR UPDATE`; insert row if absent (with `ON CONFLICT DO NOTHING` then lock); increment; format.
4. **Statutory series are allocated inside the posting transaction** — if the transaction rolls back the number is not consumed, so no gaps. Non-statutory series may allocate at creation; gaps are tolerated and reported.
5. Drafts of statutory documents carry a temporary label `DRAFT-{id}`.
6. Manual numbering: validated against series regex and uniqueness; disallowed for statutory series.
7. Format tokens: `{FY}` `{yyyy}` `{MM}` `{BR}` (branch code) `{0000}` (zero-padded counter). Length validator enforces 16 characters for invoice/note series **[VERIFY]**.

### 4.3 Tax calculation (`TaxCalculator`)
Inputs: lines (qty, rate, discount, `tax_code`, `hsn_sac`), seller branch state, `place_of_supply`, `supply_type`, `reverse_charge`, document date. Pure function; rates read from `tax_rates` effective on the date.
```
gross      = qty × rate
discount   = discount_amt OR gross × discount_pct/100     (percent discount rounded half-up to 2 dp)
taxable    = gross − discount
split      = EXPORT_LUT/EXEMPT/NIL/ZERO_RATED → none
             seller_state == place_of_supply     → CGST + SGST (each = rate/2)
             otherwise (incl. EXPORT_IGST, SEZ)  → IGST
tax_x      = round_half_up(taxable × rate_x / 100, 2)
line_total = taxable + tax_cgst + tax_sgst + tax_igst + cess
document   : subtotal = Σ taxable ; tax_total = Σ taxes ; pre_round = subtotal + tax_total (+ TCS − none)
             round_off = round(pre_round, 0) − pre_round ; grand_total = pre_round + round_off
```
**Worked example A (intra-state):** qty 1,000 × ₹12.50, discount 2%, GST 18%, seller and place of supply Karnataka → gross 12,500.00; discount 250.00; taxable 12,250.00; CGST 9% = 1,102.50; SGST 9% = 1,102.50; line total 14,455.00; round-off 0.00; grand total ₹14,455.00.
**Example B (inter-state):** same line, place of supply Maharashtra → IGST 18% = 2,205.00; total 14,455.00.
**Example C (export under LUT):** same line in USD at rate 0.15 → gross 150.00 USD; no tax; INR value = 150.00 × FX rate (stored).
These are fixtures for golden tests; the accountant supplies additional ones (reverse charge, SEZ, cess, TCS) **[VERIFY]**.
Server is the only calculator of record; `/tax/preview` returns the same result for UI display.

### 4.4 Currency and forex
- Rate selection (decision E3): `ims.fx.mode` = `MANUAL` (user supplies on document, defaulted from latest `exchange_rates`), `DAILY` (nightly import job adds rates; document defaults to rate on `doc_date`), `PER_TRANSACTION`. Posting fails with `FX_RATE_MISSING` if no rate is available.
- INR value = round_half_up(amount_foreign × rate, 2).
- **Settlement forex difference:** for allocation of payment P (rate r_p) to invoice I (booked at r_i): `forex_diff_inr = allocated_foreign × (r_p − r_i)`; receivable side: positive = gain. Posted to Forex gain/loss accounts in the payment's journal entry. Example: USD 1,000 invoiced at 83.00 (₹83,000.00), received at 84.00 (₹84,000.00) → bank debit 84,000; receivable credit 83,000; forex gain credit 1,000. *(Rates illustrative.)*
- Month-end revaluation of open foreign items is a manual, permissioned action producing a reversing journal (`FX_REVALUATION`), off by default.

### 4.5 `FinancePostingService.post(JournalCommand)`
```
JournalCommand { eventType, sourceType, sourceId, entryDate, branchId, currency, rate, narration,
                 lines[ {accountKey|accountId, partyType, partyId, debit|credit, amountForeign, taxCodeId, memo} ],
                 openItemEffects[ create(OpenItem) | allocate(paymentId, openItemId, amount) ],
                 idempotencyKey }
```
1. **Idempotency:** same `(company, key)` returns the existing entry.
2. Resolve `accountKey`s through `account_mappings` (event type × tax code × branch × side); a missing mapping → `ACCOUNT_MAPPING_MISSING` (422) with the exact event/tax code shown.
3. Validate: period OPEN (`PERIOD_CLOSED`), accounts active, `party_required` accounts have a party, Σdebit = Σcredit (`JOURNAL_UNBALANCED`).
4. Lock `account_balances` rows (account, party, period) and `open_items` rows being touched, **ordered by id**.
5. Insert `journal_entries` + `journal_lines`; update balances; apply open-item effects; for allocation re-check F-06.
6. After commit publish `JournalPosted` (reconciliation, outbox for Tally/Busy, dashboard cache).
Reversal: `reverse(entryId, reason)` creates mirrored lines, links `reverses_entry_id`, restores open items, and audits; only allowed while the source document is in a state that permits it (e.g. a payment REVERSED, an invoice CANCELLED within policy).

### 4.6 MRP (`MrpService.run(RunRequest)`)
Deterministic; same inputs → same results.
1. **Demand:** FG list (product, qty, needed_by) from the request, or open OC/SO lines (remaining qty = ordered − dispatched − fulfilled-from-stock-reservation), or items below reorder level (`MIN_STOCK`).
2. **Low-level code:** compute each item's deepest BOM level (cycle → exception, item skipped).
3. For `level = 0..max`: for each item at this level:
   - `gross` = Σ(parent planned qty × qty_per × (1 + scrap%)) + independent demand.
   - `available` = on-hand **AVAILABLE status only** (quarantine/hold/rejected excluded) − reserved − `safety` (min_stock).
   - `scheduled` = open PO remaining quantities due ≤ `needed_by` + open production order outputs due ≤ `needed_by` (+ job-work expected output).
   - `net = max(0, gross − available − scheduled)`. If `available + scheduled > gross` the surplus is recorded (never a negative net).
   - **Lot sizing:** `planned = max(net, moq)` rounded up to `multiple_of`.
   - **Offset:** `order_by = needed_by − lead_time` (supplier `lead_time_days` / routing + queue time for make items). If `order_by < today` → exception `LATE`.
   - Create suggestion: `BUY` → `PLANNED_REQUISITION` (supplier = preferred, else last-PO supplier, else none → exception `NO_SUPPLIER`); `MAKE` (sub-assembly/product) → `PLANNED_PRODUCTION` and its planned qty feeds the next level as demand.
   - **Alternates:** if the primary is short and an alternate has stock, add a note on the suggestion (never auto-substitute).
4. Persist `mrp_runs/demand/requirements/suggestions/exceptions` in one transaction.
5. **Convert:** a planner selects suggestions → `DocumentConverter` creates DRAFT purchase requisitions (grouped per supplier) or DRAFT production orders; the suggestion becomes `CONVERTED` with link. Dismissals need a reason.
**Fixture (golden test):** FG-A demand 100 @ day 20; BOM: 2 × SUB-1 + 0.5 kg RM-X; SUB-1: 1.2 kg RM-X + 3 × RM-Y. On-hand RM-X 100 kg, RM-Y 200; open PO RM-Y 150 due day 10; lead times RM-X 10 d, RM-Y 7 d, SUB-1 5 d; no scrap, no MOQ. → SUB-1 gross 200, net 200 (make, start day 10); RM-X gross = 100×0.5 + 200×1.2 = 290, available 100, net 190 (order by day 10); RM-Y gross = 600, available 200 + scheduled 150 = 350, net 250 (order by day 3 → exception LATE if today > day 3).

### 4.7 BOM explosion and costing
```sql
WITH RECURSIVE x(bom_id, product_id, component_id, kind, qty, lvl, path) AS (
  SELECT b.id, b.product_id, i.material_id, 'MATERIAL', i.qty_per * (1+COALESCE(i.scrap_pct,0)/100), 1, ARRAY[b.product_id]
  FROM boms b JOIN bom_items i ON i.bom_id=b.id WHERE b.id=:root AND i.component_kind='MATERIAL'
  UNION ALL … sub-assembly rows joined to the child product's ACTIVE bom, multiplying qty, appending to path,
  with  WHERE NOT (child_product_id = ANY(path))  -- cycle guard; a detected cycle is also a hard error at approval
) SELECT * FROM x;
```
- **Cost roll-up** per `costing_policies`: material cost per component by policy (moving average from `inventory_balances` value/qty, last PO rate, or standard); conversion = Σ(operation standard time × machine rate) + setup/qty; result + breakdown JSON in `bom_cost_rollups`. "Cost check" compares to `products.standard_cost` and flags variance > `costing.variance_alert_pct`.
- **FG costing at completion:** `unit_cost = (Σ consumed material value + Σ conversion cost from actual operation times × machine rates + scrap loss allocation) / good_qty`; scrap loss = value of line-rejected quantity absorbed by good output. `PRODUCTION_RECEIPT` is valued at `unit_cost`. Stored on `fg_batches`.
- **Release snapshot:** multi-level explosion is flattened into `production_order_materials` with `bom_level`, `parent_item_id`; sub-assembly lines flagged `make_or_buy = MAKE` generate child draft orders only when the planner chooses ("create child orders").

### 4.8 Three-way match (`PurchaseInvoiceMatcher`)
For each invoice line with `grn_item_id`:
```
received_ok = accepted qty on GRN item
already     = Σ posted invoice qty for that grn_item
remaining   = received_ok − already
qty_var     = line.qty − remaining
price_var   = (line.rate − po_item.rate) / po_item.rate
status      = NO_GRN            if grn_item_id is null and item is STOCK
              QTY_VARIANCE      if qty_var > remaining × qty_tol%
              PRICE_VARIANCE    if |price_var| > price_tol%
              MATCHED otherwise
```
`SERVICE` items match against `service_confirmation_items` instead of a GRN. Any non-MATCHED line creates a `match_exception`; document-level `match_status` is the worst line. Posting requires MATCHED or OVERRIDDEN (override = `PURCHASE_INVOICE_OVERRIDE` + reason + audit). Posting steps (single transaction): tax calc → journal (GRNI/Inventory split, input taxes, payables, TDS payable) → open item (payable) → cost adjustment for price variance (`inventory_cost_adjustments`, value only) → update PO line `invoiced_qty` → alerts resolved.

### 4.9 Quotation and order-confirmation rules
- **Quotation:** DRAFT → SUBMITTED → APPROVED (rule) → SENT → ACCEPTED / LOST / EXPIRED. **Amend:** creates revision n+1 in DRAFT; prior → SUPERSEDED (still printable). **Cancel:** `QUOTATION_CANCEL`, reason required. Expiry job sets EXPIRED after `valid_until`, alert 3 days before.
- **Convert** (`QUOTATION_CONVERT`): allowed from APPROVED/SENT/ACCEPTED; selects lines and quantities (partial allowed); target OC (or directly a sales order for domestic simple flow); copies prices, taxes (recomputed at conversion date), terms, customer part numbers; creates `document_links`; quotation → CONVERTED when all quantities converted.
- **OC:** DRAFT → SUBMITTED → APPROVED → **CONFIRMED** (creates exactly one sales order, status ORDERED, copying lines) → IN_PRODUCTION → (PARTIALLY_)DISPATCHED → CLOSED. FOREIGN kind requires currency ≠ INR, incoterm, ports; price check uses customer-specific specs. **Copy OC:** new DRAFT with new number, `copied_from_id`, prices refreshed optionally.
- **Create work order** (`OC_CREATE_WORKORDER`): per OC line with a product having an ACTIVE BOM: production order DRAFT, `qty = line qty − (setting oc.workorder.net_of_stock ? available FG : 0)`, `needed_by = delivery_date`; skips lines with sufficient stock and lists them. Order → normal approval/release; release snapshots BOM.
- Customer-specific product specs (`customer_product_specs`) print on OC/dispatch (Phase 6 behaviour kept).

### 4.10 Delivery challans and job-work flows
| Challan type | Stock effect | Notes |
|---|---|---|
| NON_RETURNABLE (with dispatch) | none (dispatch already posted `SALES_DISPATCH`) | references dispatch; carries transporter/e-way bill data |
| RETURNABLE (samples, demo, tools, job-work material) | `stock_effect = TRUE`: `STOCK_TRANSFER` store → party location (type SUBCONTRACT, one per party) | return receipt moves it back; overdue returns raise an alert |
| SERVICE | none | used with service orders |
**Job work** (all postings through `InventoryPostingService`):
1. *Issue:* approved order → returnable challan → `SUBCONTRACT_ISSUE` (store → vendor location), lines pick batches via `BatchAllocator`; `issued_qty` updated.
2. *Receive:* vendor returns output. Entered: output good/rejected qty and material consumed (default = BOM ratio × total output; editable within tolerance). Postings: `MATERIAL_CONSUMPTION` from vendor location for consumed materials; `SUBCONTRACT_RECEIPT` of output into quarantine/QUALITY_PENDING; `SCRAP` for vendor scrap; `SUBCONTRACT_RETURN` for unused material back to store. F-13 checked.
3. *Inspect:* incoming inspection on the output (reuses Phase 3).
4. *Invoice:* purchase invoice with service lines (job charge) and TDS section; output item value = consumed material value + job charge (cost adjustment at invoice posting).
Vendor material balance report = issued − consumed − returned − scrap, with ageing of days at vendor.

### 4.11 Payment allocation and ageing
- **Allocate** (`PaymentService.allocate`): validates direction vs open-item side, same party, currency, F-06; supports many-to-many; **auto-allocation** option oldest-due-first (FIFO by due date) or exact-match; unallocated remainder stays on account (`PAYMENT_ON_ACCOUNT` open item) and can be applied later. **Unallocate** (`PAYMENT_UNALLOCATE`) restores balances with audit.
- **Advance receipt voucher:** receipt with `kind = ADVANCE` creates an `ADVANCE` open item; GST on advance **[VERIFY]**; applying to an invoice allocates it exactly like a payment.
- **Ageing:** as-of date D; for each open item with `balance ≠ 0`, `days = D − COALESCE(due_date, doc_date)`; buckets `Not due (≤0)`, `1–30`, `31–60`, `61–90`, `>90`. Per party and total; credit notes and advances shown as negative amounts in a separate column. Source: `open_items` only (never scans journal).
- **Reminders:** daily job: for open receivables past due, apply `reminder_policies` (level by days after due); AUTO creates an outbox e-mail and logs `payment_reminders`; MANUAL from the UI uses the same template; no duplicate within `reminder.min_interval_days`.
- **TDS:** on supplier payment/invoice, section from supplier (or override); amount = base × rate unless below threshold (annual cumulative per party/FY tracked from `tds_entries`); no-PAN rate applied when PAN missing. Entries feed TDS-payable and certificate tracking **[VERIFY]**.

### 4.12 E-invoice / e-way bill orchestration
1. On `POSTED` of a TAX/EXPORT invoice or note: evaluate eligibility (settings: turnover threshold, B2B only, exempt types **[VERIFY]**). Eligible → `einvoice_status = PENDING`, create `einvoice_requests(GENERATE)` + outbox event.
2. Payload builder maps invoice → government schema JSON (versioned builder class; validation against bundled JSON schema before sending).
3. Provider adapter (`GstProvider`): `generateIrn`, `cancelIrn`, `generateEwb`, `extendEwb`, `cancelEwb`, `getByIrn`. Responses store IRN, ack no/date, signed QR; on success re-render PDF with QR; on error store provider code/message, status FAILED, alert `EINVOICE_FAILED`, manual retry.
4. Cancel window rules and reasons enforced from settings **[VERIFY]**; cancelled IRN cannot be reused.
5. **JSON export mode:** same builder; user downloads file; manual "mark as generated" records IRN/ack entered by the user (audited).
6. E-way bill: Part-A from invoice (supply type, document, from/to, HSN lines, value), Part-B (vehicle/transporter) from dispatch; threshold in settings; validity computed from distance **[VERIFY]**.
7. GST returns: `GSTR1` sections (B2B, B2CL, B2CS, EXP, CDNR, HSN, DOCS) and `GSTR3B` totals are SQL views over `sales_invoices`, `adjustment_notes`, `purchase_invoices`; exports in portal JSON and Excel **[VERIFY schema]**.

### 4.13 Outbox dispatcher and voucher mapping
- **Dispatcher:** every 5 s, `SELECT … FROM outbox_events WHERE status IN ('PENDING','FAILED') AND next_attempt_at <= now() ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 50`; mark IN_FLIGHT; call connector; success → DELIVERED; failure → `attempts++`, `next_attempt_at = now + [1m,5m,30m,2h,6h,12h,24h][attempts-1]`, after 8 attempts → DEAD + alert `INTEGRATION_FAILED`. Connectors receive `dedup_key` and must be idempotent.
- **Tally mapping** (`tally_rules` evaluated in priority order):
| IMS event | Tally voucher | Party ledger | Line ledgers |
|---|---|---|---|
| SALES_INVOICE_POSTED | Sales | customer ledger (`external_ledger_map`) | sales account, output CGST/SGST/IGST, round-off |
| PURCHASE_INVOICE_POSTED | Purchase | supplier ledger | purchase/GRNI, input taxes, TDS payable |
| RECEIPT_POSTED | Receipt | customer | bank ledger |
| PAYMENT_POSTED | Payment | supplier | bank ledger |
| CREDIT_NOTE_POSTED / DEBIT_NOTE_POSTED | Credit Note / Debit Note | party | as original |
| JOURNAL (forex, adjustments) | Journal | — | per lines |
Flow: event → `accounting_vouchers` row `PENDING_APPROVAL` (if rule `requires_approval`) → approver (permission `ACCOUNTING_VOUCHER_APPROVE`) approves/rejects → `APPROVED` → outbox → XML request over HTTP → `POSTED` with `external_ref`. **Missing ledger** → status `EXCEPTION`, code `LEDGER_MISSING`, detail names the ledger, UI offers "create ledger in Tally" (ledger master push) or "map to existing". XML shapes, TDL extension and import options **[VERIFY against the customer's Tally edition]**. Extraction: `pull()` fetches ledger list and day book for reconciliation reports; downloaded reports are stored as attachments of the sync run.
- **Busy:** same rule/approval tables with `connector = 'BUSY'`; format per Busy's import specification **[VERIFY]**.
- **Aftership:** `POST` tracking on dispatch; webhook → `shipment_trackings`/`dispatches.tracking_status`; delivered → timeline entry + notification.
- **Payment gateway:** invoice → `payment_links`; webhook (signature-verified, de-duplicated by `inbox_events`) → creates RECEIPT with `mode = GATEWAY`, allocates to the invoice, posts via finance; daily settlement file reconciliation report.

### 4.13.1 Tally TDL extension and browser helper
**Decision E5 (2026-10-28):** Tally **Prime**, on a PC separate from IMS. Therefore `BROWSER_HELPER` is the default mode and the one to build and test first.
**Connection modes** (`integration_connections.config.mode` for `TALLY`): `BROWSER_HELPER` (default), `FILE` (download XML for manual import; fallback), `SERVER_HTTP` (IMS server reaches Tally's HTTP port directly — only if a network path such as a VPN is later provided; not built in the first pass).
**Tally Prime set-up required (once, on the Tally PC):** enable Tally's HTTP server (connectivity setting, default port 9000) and allow the browser PC to reach it **[VERIFY exact Prime menu path and port]**. Helper config holds the Tally host (default `localhost`, or the Tally PC's LAN address if the browser runs on another PC), port, and company name.

**TDL extension** (`integrations/tally/ims.tdl`, versioned `IMS_TDL_VERSION`):
- Adds user-defined fields on vouchers: *IMS Document No*, *IMS Document Id*, *IMS Status*; filled from the incoming XML so every IMS-originated voucher is traceable from Tally.
- Adds a report/menu *IMS Vouchers* (list with IMS number, status, party, amount) and a *Pull IMS status* action only if the customer's Tally edition supports it **[VERIFY]**.
- Optional setting to warn or block edits/deletes of IMS-originated vouchers (default: warn).
- Loaded through Tally's TDL configuration; install and rollback steps documented; no change to Tally company data structure beyond the UDFs. Target is **Tally Prime** only (release number recorded in the install guide **[VERIFY]**); ERP 9 is not supported.

**Browser helper** (Chrome extension, Manifest V3, in `integrations/tally-helper/`):
1. User opens IMS *Accounting Vouchers → Approved* in Chrome and clicks **Sync to Tally** (extension popup or page button).
2. Helper calls `GET /api/accounting-vouchers/pending-sync` using the user's IMS session (short-lived token with permission `ACCOUNTING_VOUCHER_SYNC`); gets APPROVED vouchers for the configured Tally company with their XML.
3. User confirms the batch (count and total shown). Helper POSTs each XML to Tally's HTTP port on the same PC (default `http://localhost:9000` by default; host, port and company configurable) and parses the response.
4. Helper reports each result: `POST /api/accounting-vouchers/{id}/sync-result` `{status: POSTED|EXCEPTION, externalRef, errorCode, errorMessage}`; `LEDGER_MISSING` results show the ledger name and a link to the mapping screen.
5. Safety: host permissions limited to the IMS origin and the configured localhost port; no credentials stored; no remote code; requests carry the voucher id as idempotency key so a retry cannot create a second voucher (Tally duplicate check via the *IMS Document Id* UDF); the helper logs only counts and ids; version check against the server (`/api/integrations/tally-helper/min-version`).
6. Distribution: signed package or enterprise policy install; Chrome Web Store listing optional.
Alternative for sites that cannot use a browser extension: the same API consumed by a small desktop agent — out of v1 unless requested.
New permission: `ACCOUNTING_VOUCHER_SYNC`. New endpoints: `GET /api/accounting-vouchers/pending-sync`, `POST /api/accounting-vouchers/{id}/sync-result`, `GET /api/integrations/tally-helper/min-version`.

### 4.14 Report builder query translation
Definition:
```json
{ "view":"bi_sales", "columns":["customer_name","invoice_month"], "filters":[{"field":"invoice_date","op":"between","value":["2026-04-01","2026-09-30"]}],
  "groupBy":["customer_name","invoice_month"], "aggregates":[{"field":"taxable_value","fn":"sum","as":"sales"}], "orderBy":[{"field":"sales","dir":"desc"}], "limit":1000 }
```
1. Every identifier (view, column, alias) must exist in `report_columns_catalogue` for the view and be viewable by the caller (`permission_code`); aliases are generated, never user text in SQL.
2. `fn ∈ {sum, avg, min, max, count, count_distinct}` and must be allowed for the column's `aggregations`.
3. SQL is composed from catalogue-quoted identifiers; **all values are bound parameters**; `op ∈ {=,!=,>,>=,<,<=,in,between,like,is_null}`.
4. Mandatory predicates are appended server-side: branch scope, warehouse scope, company id.
5. Runs in a read-only transaction with `SET LOCAL ROLE ims_report_ro`, `SET LOCAL statement_timeout = 15000`, `LIMIT ≤ 50 000` interactive (larger exports async, streaming).
6. Output includes column metadata for the grid/chart; execution and export are audited (user, definition hash, rows, ms).
7. Custom fields are exposed only if flagged `reportable` in their definition.

### 4.15 Bulk import pipeline
`ImportDefinition { code, permission, columns[{name, type, required, validator}], keyColumns, upsert: CREATE_ONLY|UPSERT, handler }`.
Stages: **upload** (store file, hash) → **parse** (xlsx via streaming reader, csv; header row detection; max rows setting) → **map** (auto-map by header synonyms, user can adjust; saved per import code) → **validate** (dry run through the same service-layer validators; collects `row, column, message`) → **review** (error xlsx; counts; "commit valid rows only" disabled by default) → **commit** (one transaction per file; chunked flush; audit entry `BULK_IMPORT` with job id) → **result**. Re-upload of an already committed hash is blocked unless forced with reason.
Registered definitions: `materials`, `products`, `suppliers`, `customers`, `bom` (header + items, multi-level), `uom_conversions`, `opening_stock` (posts `OPENING_BALANCE` through `InventoryPostingService`), `opening_balances_ar_ap` (posts opening journal through `FinancePostingService` and creates open items), `item_prices`, `warehouses_locations`. Templates (xlsx with header comments, enum lists, example row) downloadable from `/api/imports/{code}/template`.

### 4.16 Barcode / QR payloads and scanning
- **Code128:** the business key (`RM-BATCH-2026-001`, `FG-2026-0050`, item code, location code, document number).
- **QR:** `IMS1|{type}|{id}|{hmac8}` where `hmac8` = first 8 hex chars of HMAC-SHA256(secret, `type|id`); prevents guessable enumeration. `GET /api/scan/{code}` resolves either format, returns entity summary and `allowedActions` for the caller (e.g. issue, transfer, count, inspect).
- **Label content** per template: code, name, batch, heat/lot, quantity, date, supplier/customer, logo. ZPL output for thermal printers; PDF for A4/label sheets.
- Item `barcode` is user-assignable (EAN/UPC) and unique per company.

### 4.17 Scheduled jobs (additions to `DESIGN.md` §4.12)
| Job | Schedule | Purpose |
|---|---|---|
| `FinanceReconciliationJob` | nightly | F-01, F-05, control accounts vs open items; alerts `FINANCE_MISMATCH` |
| `OutboxDispatcher` | every 5 s | deliver integration events |
| `OutboxRetention` | daily | purge delivered events/calls beyond retention |
| `ExchangeRateImport` | daily (if `DAILY` mode) | add rates |
| `PaymentReminderJob` | daily 09:00 IST | reminders by policy |
| `QuotationExpiryJob` | daily | expire/alert quotations |
| `ChallanReturnOverdueJob` | daily | overdue returnable challans |
| `SearchIndexRebuild` | on demand / weekly check | repair drift |
| `BiRefreshJob` | hourly / nightly | refresh materialized views |
| `EinvoiceRetryJob` | every 15 min | retry FAILED within policy |
| `PartitionMaintenance` | monthly | create future partitions for journal, outbox, calls (extends existing) |
| `ImportJobCleanup` | daily | purge old uploads |

---

## 5. API contracts

Conventions from `DESIGN.md` §6.1 apply: `/api/...`, JSON, RFC 7807 errors with stable `code`, `Idempotency-Key` on every POST that posts stock or money, optimistic `version` on updates, paging `page,size,sort`, `allowedActions[]` in document responses. Every endpoint has `@PreAuthorize`. Money and quantities are JSON strings or numbers with fixed scale (never floats in logic).

### 5.1 Common document actions (all document types)
| Method + path | Meaning | Permission |
|---|---|---|
| `GET /{docs}` `?q&status&from&to&partyId&branchId&tag&custom.<code>` | list | `<DOC>_VIEW` |
| `POST /{docs}` | create DRAFT | `<DOC>_CREATE` |
| `GET /{docs}/{id}` | detail incl. totals, `allowedActions`, links | `<DOC>_VIEW` |
| `PUT /{docs}/{id}` | edit DRAFT (with `version`) | `<DOC>_UPDATE` |
| `POST /{docs}/{id}/submit` · `/withdraw` · `/approve` · `/reject` · `/approve-and-send` · `/send` · `/cancel` | workflow | per action |
| `POST /{docs}/{id}/amend` | new revision | `<DOC>_AMEND` |
| `POST /{docs}/{id}/copy` | copy as new DRAFT | `<DOC>_CREATE` |
| `GET /{docs}/{id}/pdf` | printable | `<DOC>_VIEW` |
| `GET/POST /{docs}/{id}/comments` · `/attachments` · `GET /{docs}/{id}/timeline` · `GET /{docs}/{id}/links` | collaboration | `<DOC>_VIEW` (+ `COMMENT_ADD`, `ATTACHMENT_ADD`) |
| `PUT /{docs}/{id}/tags` · `/custom` | tags, custom fields | `<DOC>_UPDATE` |
`{docs}` ∈ `rfqs, supplier-quotes, purchase-orders, purchase-invoices, purchase-returns, quotations, order-confirmations, delivery-challans, sales-invoices, credit-debit-notes, job-work-orders, service-orders, service-confirmations`.

### 5.2 Platform
| Endpoint | Notes | Permission |
|---|---|---|
| `GET/POST/PUT /api/uom-conversions`, `/item-categories`, `/item-series` | masters | `ITEM_MASTER_MANAGE` |
| `GET/PUT /api/company/profile`, `/company/branches`, `/company/bank-accounts`, `/transporters`, `/payment-terms`, `/terms-templates` | company set-up; bank numbers masked in GET | `COMPANY_MANAGE` |
| `GET/POST/PUT /api/document-series` (+ `POST /{id}/preview` shows next number) | series admin | `SERIES_MANAGE` |
| `GET/POST/PUT /api/custom-fields`, `/api/tags` | definitions | `CUSTOMFIELD_MANAGE` |
| `GET/POST/PUT /api/approval-rules` (+ `POST /test` evaluates a rule against a sample document) | rules | `APPROVAL_RULE_MANAGE` |
| `GET /api/approvals/pending` · `GET /api/approvals/history?entityType&entityId` · `POST /api/approvals/{id}/approve|reject` | inbox and history | `APPROVAL_DECIDE` |
| `GET/POST /api/teams`, `PUT /api/teams/{id}/members`, `PUT /api/teams/{id}/report-access` | teams | `TEAM_MANAGE` |
| `GET/PUT /api/me/notification-preferences`, `GET /api/me/notifications`, `POST /api/me/notifications/{id}/read` | self-service | authenticated |
| `GET /api/search?q&types&limit` | global search | authenticated (results filtered) |
| `GET /api/imports/{code}/template` · `POST /api/imports/{code}` (multipart) · `GET /api/imports/{jobId}` · `POST /api/imports/{jobId}/commit` · `GET /api/imports/{jobId}/errors` | bulk upload | `BULK_IMPORT` + entity permission |
| `GET /api/jobs/{id}` | async job status | owner or `JOB_VIEW_ALL` |
| `GET /api/scan/{code}` | barcode resolve | authenticated (actions filtered) |

### 5.3 Purchase
| Endpoint | Notes | Permission |
|---|---|---|
| `POST /api/rfqs/{id}/suppliers` · `POST /api/rfqs/{id}/send` | invite and mail | `RFQ_SEND` |
| `POST /api/supplier-quotes` (per supplier) · `GET /api/rfqs/{id}/comparison` | side-by-side: per line each supplier's rate, tax, freight, lead time, landed cost, lowest flagged | `RFQ_VIEW` |
| `POST /api/rfqs/{id}/award` body `{ "lines":[{ "rfqItemId", "quoteItemId", "qty" }] }` | creates PO drafts per supplier | `RFQ_AWARD` |
| `POST /api/purchase-orders/{id}/amend` body `{ "reason" }` | revision + `po_revisions` snapshot | `PURCHASE_AMEND` |
| `GET /api/purchase-orders/{id}/revisions` | history | `PURCHASE_VIEW` |
| `POST /api/goods-receipts` supports `inwardKind` NO_PO/JOB_WORK/SERVICE/RETURN | inward | existing GRN permission |
| `GET /api/purchase-invoices/{id}/match` | per-line match results | `PURCHASE_INVOICE_VIEW` |
| `POST /api/purchase-invoices/{id}/post` | runs match → tax → journal → cost adjustment | `PURCHASE_INVOICE_POST` |
| `POST /api/purchase-invoices/{id}/override-match` body `{reason}` | | `PURCHASE_INVOICE_OVERRIDE` |
| `POST /api/purchase-returns/{id}/post` | stock out + debit note | `PURCHASE_RETURN_POST` |
| `GET /api/reports/grn-pending`, `/grn-invoice-mismatch` | registers | `REPORT_PURCHASE` |

### 5.4 Sales
| Endpoint | Notes | Permission |
|---|---|---|
| `POST /api/quotations/{id}/convert` body `{ "target":"OC"\|"SO", "lines":[{ "itemId","qty" }] }` | | `QUOTATION_CONVERT` |
| `POST /api/order-confirmations/{id}/confirm` | creates the sales order | `OC_CONFIRM` |
| `POST /api/order-confirmations/{id}/work-orders` body `{ "lineIds":[…], "netOfStock":true }` → `{ created:[…], skipped:[{lineId, reason}] }` | | `OC_CREATE_WORKORDER` |
| `POST /api/delivery-challans/{id}/issue` · `/return` (partial returns) | | `CHALLAN_ISSUE` / `CHALLAN_RETURN` |
| `POST /api/dispatches/{id}/send` body `{ "to":[…], "message" }` | PDF + tracking by e-mail via outbox | `DISPATCH_SEND` |
| `GET /api/dispatches/{id}/tracking` | | `DISPATCH_VIEW` |

### 5.5 Finance
| Endpoint | Notes | Permission |
|---|---|---|
| `GET/POST /api/currencies`, `/exchange-rates` (+ `POST /exchange-rates/import`) | | `FX_MANAGE` |
| `GET/POST/PUT /api/tax-codes`, `/tax-rates`, `/tds-sections` | effective-dated | `TAX_MANAGE` |
| `POST /api/tax/preview` body `{lines, branchId, placeOfSupply, supplyType, date}` | same calculator as posting | `INVOICE_VIEW` or `PURCHASE_INVOICE_VIEW` |
| `POST /api/sales-invoices` (kind TAX/PROFORMA/EXPORT/BILL_OF_SUPPLY; `fromDispatchId` / `fromSalesOrderId` or direct/ad-hoc lines) | draft | `INVOICE_CREATE` |
| `POST /api/sales-invoices/{id}/post` | assigns number, journals, open item | `INVOICE_POST` |
| `POST /api/sales-invoices/{id}/cancel` body `{reason}` | only where policy allows; else credit note | `INVOICE_CANCEL` |
| `POST /api/sales-invoices/{id}/einvoice` · `/einvoice/cancel` · `/eway` · `GET /einvoice/json` · `GET /eway/json` | | `GST_EINVOICE` / `GST_EWAY` |
| `POST /api/credit-debit-notes/{id}/post` | | `NOTE_POST` |
| `POST /api/receipts` · `POST /api/payments` | draft; `Idempotency-Key` required on post | `PAYMENT_CREATE` |
| `POST /api/payments/{id}/post` · `/reverse` | | `PAYMENT_POST` / `PAYMENT_REVERSE` |
| `POST /api/payments/{id}/allocations` body `{ "allocations":[{ "openItemId","amount" }], "auto":"FIFO"\|null }` · `DELETE …/allocations/{aid}` | | `PAYMENT_ALLOCATE` |
| `POST /api/advance-receipts` | | `PAYMENT_CREATE` |
| `GET /api/ledgers/party/{type}/{id}?from&to` · `GET /api/ledgers/account/{id}?from&to` · `GET /api/ledgers/trial-balance?asOf` | | `LEDGER_VIEW` |
| `GET /api/receivables?asOf&bucketSet` · `/payables` · `GET /api/ageing/debtors` · `/creditors` | ageing | `LEDGER_VIEW` |
| `POST /api/reminders/send` body `{openItemIds[], channel}` · `GET/PUT /api/reminder-policies` | | `REMINDER_SEND` |
| `GET/POST /api/tds-entries`, `GET /api/tds/summary` | | `TDS_MANAGE` |
| `POST /api/opening-balances` (ar/ap/ledger) | via finance posting | `OPENING_BALANCE_POST` |
| `GET/POST /api/accounts`, `/account-mappings`; `POST /api/fiscal-periods/{id}/close|reopen` | | `ACCOUNT_MANAGE`, `PERIOD_CLOSE` |
| `GET /api/gst/gstr1?period` · `/gstr3b` · `/hsn-summary` (+ `?format=json\|xlsx`) | | `GST_REPORT` |
| `POST /api/journals` (manual journal) · `POST /api/journals/{id}/reverse` | | `JOURNAL_POST` |

### 5.6 Manufacturing, planning, sub-contract
| Endpoint | Notes | Permission |
|---|---|---|
| `GET /api/boms/{id}/explosion?qty&levels` · `GET /api/boms/{id}/where-used?materialId` · `POST /api/boms/{id}/duplicate` · `POST /api/boms/{id}/cost-check?policyId` | | `BOM_VIEW`, `BOM_CREATE`, `COST_VIEW` |
| `PUT /api/bom-items/{id}/alternates` | | `BOM_UPDATE` |
| `GET /api/production-orders/{id}/process-log` · `GET /api/production-orders/{id}/costing` | | `PRODUCTION_VIEW` + `COST_VIEW` |
| `POST /api/mrp/runs` body `{source, productIds/ocIds, horizonDays, options}` → async job → `GET /api/mrp/runs/{id}` (requirements, suggestions, exceptions) | | `MRP_RUN` |
| `POST /api/mrp/suggestions/convert` body `{ids[], groupBy}` · `POST /api/mrp/suggestions/{id}/dismiss` | | `MRP_CONVERT` |
| `GET /api/stock-card?itemId&warehouseId&from&to` · `GET /api/inventory/history` | | `INVENTORY_VIEW` |
| `GET/POST /api/label-templates` · `POST /api/labels/render` body `{templateId, entities[], copies}` | PDF/ZPL | `LABEL_PRINT` |
| `POST /api/job-work-orders/{id}/issue-material` · `/receive` · `/return-material` · `/close` | | `JOBWORK_ISSUE`, `JOBWORK_RECEIVE` |
| `GET /api/job-work/vendor-balance?vendorId` | material at vendor | `JOBWORK_VIEW` |
| `POST /api/service-orders/{id}/confirmations` | | `SERVICE_CONFIRM` |

### 5.7 BI and reports
| Endpoint | Notes | Permission |
|---|---|---|
| `GET /api/bi/{area}` area ∈ sales, purchase, inventory, production, quotation, accounts, aggregate; `?from&to&branchId&groupBy` | widget payloads | `BI_<AREA>` |
| `GET /api/registers/{code}` + `?format=json\|xlsx\|csv\|pdf` | codes in §8.2 | register permission |
| `GET /api/report-builder/catalogue` · `POST /api/report-builder/run` · `POST /api/report-builder/export` (async) | | `REPORT_BUILD` |
| `GET/POST/PUT/DELETE /api/saved-reports` (+ `PUT /{id}/share`) | | `REPORT_BUILD` (own) / `REPORT_RUN` (shared) |

### 5.8 Integrations
| Endpoint | Notes | Permission |
|---|---|---|
| `GET/POST/PUT /api/integrations/connections` (secrets write-only), `POST /{id}/test` | | `INTEGRATION_MANAGE` |
| `GET /api/integrations/outbox?status&connector` · `POST /outbox/{id}/retry` · `GET /integrations/calls` | | `INTEGRATION_MANAGE` |
| `GET /api/accounting-vouchers?status` · `POST /{id}/approve|reject|retry` · `PUT /external-ledger-map` · `POST /tally/ledgers/sync` · `GET /tally/day-book` | | `ACCOUNTING_VOUCHER_APPROVE`, `INTEGRATION_MANAGE` |
| `GET/POST/PUT /api/tally-rules` | | `INTEGRATION_MANAGE` |
| `POST /api/invoices/{id}/payment-link` | | `PAYMENT_LINK_CREATE` |
| `POST /api/webhooks/aftership` · `/webhooks/payments/{provider}` | public path, signature-verified, always 200 after dedup | none (signature) |

### 5.9 New error codes (extend `DESIGN.md` §6.2; all stable)
`PERIOD_CLOSED`(422), `JOURNAL_UNBALANCED`(422), `ACCOUNT_MAPPING_MISSING`(422), `FX_RATE_MISSING`(422), `TAX_RATE_MISSING`(422), `PLACE_OF_SUPPLY_REQUIRED`(422), `INVOICE_IMMUTABLE`(409), `ALLOCATION_EXCEEDS_BALANCE`(422), `ALLOCATION_PARTY_MISMATCH`(422), `PAYMENT_ALREADY_REVERSED`(409), `CREDIT_EXCEEDS_INVOICE`(422), `MATCH_VARIANCE`(422), `DUPLICATE_SUPPLIER_INVOICE`(409), `QUOTATION_EXPIRED`(422), `ALREADY_CONVERTED`(409), `BOM_CYCLE`(422), `NO_ACTIVE_BOM`(422), `JOBWORK_BALANCE_EXCEEDED`(422), `APPROVAL_RULE_NOT_FOUND`(422), `APPROVAL_NOT_PENDING`(409), `WITHDRAW_NOT_ALLOWED`(422), `IMPORT_INVALID`(422), `IMPORT_DUPLICATE_FILE`(409), `REPORT_FIELD_NOT_ALLOWED`(403), `REPORT_LIMIT_EXCEEDED`(422), `EINVOICE_NOT_ELIGIBLE`(422), `EINVOICE_PROVIDER_ERROR`(502), `LEDGER_MISSING`(422), `INTEGRATION_UNAVAILABLE`(503), `SERIES_EXHAUSTED_OR_INVALID`(422), `STATUTORY_NUMBER_MANUAL_NOT_ALLOWED`(422).

---

## 6. Permissions (additions to `DESIGN.md` §7.1)

Added by the migration of the phase that introduces the feature. Names are the stable authorities used in `@PreAuthorize("hasAuthority('…')")`.

| Area | Permissions |
|---|---|
| Platform | `ITEM_MASTER_MANAGE, COMPANY_MANAGE, SERIES_MANAGE, CUSTOMFIELD_MANAGE, APPROVAL_RULE_MANAGE, APPROVAL_DECIDE, TEAM_MANAGE, BULK_IMPORT, JOB_VIEW_ALL, COMMENT_ADD, ATTACHMENT_ADD, ATTACHMENT_DELETE` |
| Field-level | `COST_VIEW, PRICE_VIEW, MARGIN_VIEW` (DTO fields omitted when absent) |
| Purchase | `RFQ_VIEW/CREATE/UPDATE/SEND/AWARD, PURCHASE_AMEND, PURCHASE_INVOICE_VIEW/CREATE/UPDATE/POST/OVERRIDE, PURCHASE_RETURN_VIEW/CREATE/POST` |
| Sales | `QUOTATION_VIEW/CREATE/UPDATE/AMEND/CANCEL/CONVERT, OC_VIEW/CREATE/UPDATE/CONFIRM/CANCEL/CREATE_WORKORDER, CHALLAN_VIEW/CREATE/ISSUE/RETURN, DISPATCH_SEND` |
| Finance | `INVOICE_VIEW/CREATE/UPDATE/POST/CANCEL, NOTE_VIEW/CREATE/POST, PAYMENT_VIEW/CREATE/POST/REVERSE/ALLOCATE, LEDGER_VIEW, JOURNAL_VIEW, JOURNAL_POST, ACCOUNT_MANAGE, PERIOD_CLOSE, TAX_MANAGE, FX_MANAGE, TDS_MANAGE, REMINDER_SEND, OPENING_BALANCE_POST, GST_EINVOICE, GST_EWAY, GST_REPORT, PAYMENT_LINK_CREATE` |
| Manufacturing | `MRP_RUN, MRP_VIEW, MRP_CONVERT, LABEL_PRINT, LABEL_TEMPLATE_MANAGE, JOBWORK_VIEW/CREATE/ISSUE/RECEIVE, SERVICE_VIEW/CREATE/CONFIRM` |
| Reporting | `BI_SALES, BI_PURCHASE, BI_INVENTORY, BI_PRODUCTION, BI_QUOTATION, BI_ACCOUNTS, BI_AGGREGATE, REPORT_BUILD, REPORT_RUN, REPORT_EXPORT, REGISTER_<CODE>` |
| Integration | `INTEGRATION_MANAGE, ACCOUNTING_VOUCHER_APPROVE, ACCOUNTING_VOUCHER_VIEW, ACCOUNTING_VOUCHER_SYNC` |

**Default role mapping:** the 13 existing roles keep their current mapping; ERP permissions are granted as follows (initial seed, editable data):
- **Admin:** all.
- **Purchase Manager / Purchase Officer:** purchase set; no finance posting.
- **Sales Manager / Sales Executive:** sales set, `PRICE_VIEW`, `BI_SALES, BI_QUOTATION`; no finance posting.
- **Store Manager:** challan, job-work issue/receive, label print, scan, import for opening stock.
- **Production Manager / Planner:** MRP, BOM multi-level, `COST_VIEW`.
- **Management (read-only):** all `*_VIEW`, `BI_*`, `LEDGER_VIEW`; no writes.
- **New optional roles** (rows, off by default): **Accountant** (invoice create/post, payment create/allocate, notes, ledger view, GST reports), **Accounts Manager** (adds approvals, reversal, period close, TDS, vouchers approve), **Integration Admin**.
- **Operators/Inspectors:** none of the above; `COST_VIEW`/`PRICE_VIEW` never granted.
Segregation settings: `finance.sod.invoice_creator_ne_poster` (default on), `finance.sod.payment_creator_ne_approver` (default on).

---

## 7. Frontend design

### 7.1 Route map additions (lazy, each guarded by permission)
```
/sales/quotations(/:id)  /sales/order-confirmations(/:id)  /sales/challans  /sales/invoices(/:id)  /sales/notes
/purchase/rfqs(/:id)  /purchase/quotes/compare/:rfqId  /purchase/invoices(/:id)  /purchase/returns
/accounts/receipts  /accounts/payments  /accounts/advances  /accounts/ledgers  /accounts/ageing  /accounts/tds  /accounts/gst  /accounts/vouchers
/planning/bom-explorer/:productId  /planning/mrp  /planning/mrp/:runId
/jobwork/orders  /jobwork/vendor-balance  /service/orders
/bi/:area   /reports/registers/:code   /reports/builder   /reports/saved
/settings/company  /settings/series  /settings/custom-fields  /settings/approval-rules  /settings/tax  /settings/currency  /settings/teams
/settings/integrations  /settings/imports  /settings/labels
/search?q=
```
Menu groups extend `nav.config.ts` (Sales, Purchase, Accounts, Planning, Job work, Reports, Settings). Items appear only when the user holds the view permission.

### 7.2 Key screens
- **Document screen (all documents):** `DocumentShell` — header (number, status chip, party, dates), action bar from `allowedActions`, line grid, totals/tax breakup panel, side tabs: **Timeline**, **Comments** (internal/external toggle), **Files**, **Links** (upstream/downstream documents), **Custom fields**, **Tags**. Unsaved-changes guard; optimistic-lock conflict dialog.
- **Line grid:** keyboard-first entry, item picker with search and barcode scan, UOM selector with conversion preview, rate/discount/tax columns, per-line HSN/tax code, running totals fed by `/tax/preview` (debounced). Foreign currency shows both currencies.
- **RFQ comparison:** matrix lines × suppliers; cell shows rate, landed cost, lead time; lowest highlighted; select cells to award; summary of awarded value per supplier.
- **Invoice screen:** supply type and place of supply with auto-suggestion; e-invoice panel (status chip, IRN, QR, retry/export/cancel); e-way panel; payment panel (balance, receipts applied, "record receipt", "send payment link", "send reminder").
- **Payment screen:** party + amount + mode; open-items grid with allocate inputs, auto-allocate (oldest first), unallocated remainder, forex difference preview, TDS field.
- **Ledgers and ageing:** party ledger with opening/closing and running balance; ageing table with bucket drilldown, chart, export; credit-limit usage bar.
- **Approval rules editor:** rule list ordered by priority; condition builder (field/op/value rows, any/all groups); step builder; "test with a sample document" panel.
- **Bulk import wizard:** template download → upload → mapping → validation summary with error grid → commit → result.
- **BOM explorer:** tree with quantities per level, alternates drawer, cost roll-up panel with policy selector, where-used.
- **MRP screen:** inputs panel; result tabs Requirements | Suggestions | Exceptions; multi-select convert with grouping; badges LATE, NO_SUPPLIER.
- **Job-work screen:** order, material issue with batch picks, receive form with consumption editor, vendor balance card, linked challans/invoices.
- **Report builder:** field catalogue (searchable, permission-aware), filter builder, group/aggregate selectors, live preview grid, chart picker, save/share.
- **Integrations admin:** connection cards with Test button, outbox table with retry, voucher approval queue with exception reasons, ledger-map editor.
- **Global search:** header search box with type-ahead grouped by entity; Enter opens full results page.

### 7.3 New reusable components
`DocumentShell, LineGrid, TaxBreakup, PartyPicker, ItemPicker, MoneyInput, CurrencyPill, StatusChip (extended colours), ApprovalPanel, ConditionBuilder, ImportWizard, ReportBuilder, ChartCard, AgeingTable, BarcodeScanButton, LabelPrintDialog, TimelineList, CommentThread, FileList, TagInput, CustomFieldsForm (renders from definitions), IntegrationStatusBadge`.

### 7.4 UX rules
- Money right-aligned with fixed decimals and Indian digit grouping (₹ 12,45,000.00) via a `formatMoney` pipe; quantity per UOM decimals.
- Posted documents show a lock icon and an explanation of how to correct them (credit note / reversal).
- Destructive or posting actions require confirmation with a summary of effects ("This posts 3 journal lines and reduces stock by 480 KG").
- All errors map `code` → localized message with field highlights (existing interceptor).
- Cost, price and margin fields are absent (not hidden by CSS) from API payloads for users without the permission; the UI hides the columns accordingly.

---

## 8. BI, registers and reports

### 8.1 BI by area (KPI definitions; role-aware widgets)
| Area | KPIs (all with date range, branch, team filter) |
|---|---|
| Sales | invoiced value (month, YTD), orders booked vs invoiced, top customers/products, quotation → OC conversion rate, average realisation, OTIF (OC delivery date vs dispatch), outstanding receivables |
| Purchase | PO value by supplier/category, open PO value, GRN pending, supplier on-time %, price variance vs last PO, payables due this week |
| Inventory | stock value by warehouse/category, slow/non-moving, below min/above max, stock turnover, shortage list |
| Production | orders by status, WIP quantity/value, yield %, rejection %, on-time completion, FG cost vs standard |
| Quotation | open quotes, value, win/loss, expiring soon, revision count |
| Accounts | receivables/payables ageing, DSO, collections vs target, GST payable (output − input), TDS payable, forex gain/loss |
| Aggregate | one card per area plus alerts, pending approvals, integration health |

### 8.2 Registers (each: filters, columns, totals, export)
`REG_SALES_INVOICE, REG_SALES_ORDER, REG_PURCHASE_ORDER, REG_PAYMENT, REG_GRN_PENDING, REG_GRN_INVOICE_MISMATCH, REG_ITEM_PRICE (price history by item/party), REG_ITEM_BOM, REG_PRODUCT_AGEING, REG_NEW_PRODUCT, REG_PROCESS_DETAILS` (plus existing Phase 7 reports). Definitions are `ReportDefinition` JSON over `reg_*` views; the same definitions are available to the report builder as starting points.

### 8.3 Views (V20) — contracts
`bi_sales(invoice_id, invoice_no, invoice_date, invoice_month, fiscal_year, branch_id, customer_id, customer_name, customer_group, item_id, item_name, category, hsn_sac, qty, uom, rate, taxable_value, tax_total, total_inr, salesperson_id, team_id, status)` and analogous views for the other areas. Every column is registered in `report_columns_catalogue` with role (dimension/measure), permission code, allowed aggregations. Price/cost columns require `PRICE_VIEW`/`COST_VIEW`.

---

## 9. Settings (additions to `DESIGN.md` §10.1, in `system_settings`)

| Key | Default | Meaning |
|---|---|---|
| `purchase.match.qty_tolerance_pct` / `price_tolerance_pct` | 2 / 1 | three-way match |
| `purchase.po.over_receipt_pct` | existing | unchanged |
| `finance.fx.mode` | MANUAL | MANUAL / DAILY / PER_TRANSACTION |
| `finance.rounding.invoice` | NEAREST_RUPEE | round-off policy |
| `finance.period.lock_days` | 0 | auto-block posting older than N days (0 = off) |
| `finance.sod.invoice_creator_ne_poster` | true | segregation |
| `finance.sod.payment_creator_ne_approver` | true | segregation |
| `finance.einvoice.enabled` / `threshold_inr` | false / [VERIFY] | eligibility |
| `finance.eway.enabled` / `threshold_inr` | false / [VERIFY] | |
| `finance.reminder.enabled` / `min_interval_days` | false / 7 | |
| `oc.workorder.net_of_stock` | true | work order quantity |
| `quotation.default_validity_days` | 30 | |
| `challan.returnable.default_days` | 30 | overdue alert |
| `costing.variance_alert_pct` | 10 | BOM cost check |
| `mrp.default_horizon_days` | 90 | |
| `search.enabled` | true | |
| `attachments.max_mb` / `allowed_types` | 20 / pdf,png,jpg,xlsx,docx,csv,txt | |
| `imports.max_rows` | 20000 | |
| `reports.max_rows_interactive` / `export` | 50000 / 1000000 | |
| `integration.outbox.max_attempts` | 8 | |
| `features.<phase>` | false until UAT | phase feature flags (`features.finance`, `features.integrations`, …) |
Application properties: `ims.files.path`, `ims.integration.key` (secret-encryption key; required in prod), `ims.qr.secret`, SMTP settings, `ims.jobs.pool-size`. Never committed.

---

## 10. Seed data

- **Reference (migrations):** states and GST state codes; currencies INR, USD, EUR, GBP, AED; default `document_series` (§1.2) — statutory flags on invoice and note series; starter chart of accounts and `account_mappings` for all events in §4.5; fiscal periods for the current FY; tax codes (exempt, nil, GST 0/5/12/18/28) and rates **[VERIFY with accountant]**; TDS sections common to the business **[VERIFY]**; approval rules (built-ins); label templates (batch label 50×25 mm Code128, QR); reminder policy skeleton; report catalogue for views.
- **Demo data (dev/demo only, through services):** 2 branches (Karnataka, Maharashtra) with GSTINs marked `TEST`; a foreign customer with USD; one RFQ with 3 supplier quotes; a quotation → OC → work order → production → dispatch → invoice (intra-state) and an export invoice in USD; a receipt split across two invoices; an advance receipt; a credit note; a purchase invoice with a price variance and a match exception; a returnable challan and a job-work order with partial receipt; an MRP run reproducing the §4.6 fixture; saved reports; Tally/Busy/GSP connections pointing at stubs.
- Demo accounts: Accountant, Accounts Manager, Sales Executive, Purchase Executive (password policy as existing; forced change).

### 10.1 ERP demonstration workflow (acceptance script, extends `DESIGN.md` §12)
23. Create quotation → approve by rule → send → convert to OC → confirm (SO created).
24. Create work order from OC (net of stock) → release → issue → produce → inspect → FG approved.
25. Dispatch against SO → challan → invoice (CGST+SGST) → e-invoice JSON export → mark generated → e-way JSON.
26. Receive payment split over two invoices → ageing updates → send reminder for an overdue third invoice.
27. Issue RFQ to 3 suppliers → compare → award → PO (foreign) → GRN → inspection → purchase invoice (price variance exception → override) → payment with TDS.
28. Purchase return → debit note → stock and payable reduced.
29. Job work: issue material by returnable challan → receive output with scrap → invoice job charge.
30. Run MRP for an OC → convert suggestions → requisition drafts.
31. Import 200 items from Excel (one bad row rejected with report) → opening stock post.
32. Build a custom report grouped by customer × month → save → share with a team → export.
33. Tally: approve vouchers → one `LEDGER_MISSING` exception → map ledger → resend.
34. Close the month; attempt to post into it → `PERIOD_CLOSED`.

---

## 11. Test design

### 11.1 Backend (selected; each has an ID for traceability)
| ID | Case |
|---|---|
| F-T01 | Invoice post creates a balanced journal, an open item, and consumes a statutory number; rollback consumes none (no gap) |
| F-T02 | Example A/B/C of §4.3 produce the stated amounts; reverse-charge, SEZ, cess, TCS fixtures from the accountant |
| F-T03 | Mixed CGST and IGST on one line is rejected |
| F-T04 | Posted invoice update/delete attempts fail (service and direct SQL) |
| F-T05 | Posting into a CLOSED period fails `PERIOD_CLOSED` (and via SQL trigger) |
| F-T06 | Property test: random invoices, receipts, notes, reversals keep Σdebit=Σcredit and open item balances = totals − allocations |
| F-T07 | Payment split across 2 invoices; over-allocation rejected; unallocate restores; concurrent allocations on one invoice never exceed its balance |
| F-T08 | Forex example in §4.4 (₹1,000 gain) and a loss case |
| F-T09 | Idempotent replay of post returns the same entry, creates one journal |
| F-T10 | Credit note cannot exceed invoice; note against a paid invoice creates a refundable credit |
| P-T01 | Three-way match: qty variance, price variance, no GRN, service match; override requires permission and reason |
| P-T02 | Duplicate supplier invoice number rejected |
| P-T03 | Purchase return reduces stock only via posting service and creates debit note in the same transaction (fault injection rolls back both) |
| P-T04 | RFQ comparison picks lowest landed cost; award creates POs with correct quantities |
| S-T01 | Quotation amend → previous SUPERSEDED; convert partial then remainder → CONVERTED; expired cannot convert |
| S-T02 | OC confirm creates exactly one SO (F-18); copy OC; work order skips lines with sufficient stock |
| S-T03 | Returnable challan moves stock to party location and back; overdue alert |
| A-T01 | Approval rule selection by priority; AUTO vs MANUAL; ANY/ALL quorum; self-approval blocked; withdraw; cycle increments on amend |
| A-T02 | **Regression:** all Phase 2–4 approval tests pass unchanged on the new engine |
| A-T03 | Series: FY reset, month reset, manual validation, statutory no-gap, concurrent allocation of 100 numbers yields 100 unique numbers |
| M-T01 | Multi-level explosion with scrap; cycle detection rejects BOM approval |
| M-T02 | MRP fixture of §4.6 produces exactly the stated suggestions and exceptions; run is deterministic |
| M-T03 | Alternate item issue records substitution; shortage message lists alternates |
| M-T04 | FG cost = consumed material + conversion + scrap loss; receipt valued accordingly |
| J-T01 | Job-work lifecycle keeps F-13; vendor balance equals ledger; scrap and return paths |
| I-T01 | Outbox: failure retries with backoff; dedup key prevents double delivery; dead-letter alert |
| I-T02 | Tally voucher mapping golden files per event; missing ledger → EXCEPTION with message |
| I-T03 | Webhook signature valid/invalid; replay ignored |
| B-T01 | Report builder rejects unknown/forbidden fields, SQL injection payloads in values/aliases/order-by, over-limit; applies branch scope |
| B-T02 | Import: bad row → nothing committed, error file correct; duplicate file blocked; opening stock posts ledger rows |
| X-T01 | ArchUnit: only `FinancePostingService` writes `journal_*`/`account_balances`/`open_items`; only `InventoryPostingService` writes ledger tables; foundation modules do not depend on features |
| X-T02 | Every new endpoint has `@PreAuthorize`; role matrix test for ERP permissions (403 for all others) |
| X-T03 | Cost/price fields absent from JSON for users without `COST_VIEW`/`PRICE_VIEW` |

### 11.2 Frontend
Component tests for `LineGrid` (totals, keyboard), `ConditionBuilder`, `ImportWizard`, `ReportBuilder`, payment allocation grid, permission-driven menu and field hiding; end-to-end (Playwright, added in Phase 17) for demo steps 23–34.

### 11.3 Non-functional targets
Invoice post p95 < 300 ms (50 lines); payment allocation to 20 invoices < 400 ms; MRP (500 FG, 3-level) < 30 s; ageing for 50k open items < 2 s; global search p95 < 300 ms; report builder interactive query < 5 s on 1 M rows with indexes; outbox throughput ≥ 50 events/s. Security: injection suite for report builder; upload type/size tests; webhook abuse tests; secrets never in logs (log scan test).

---

## 12. Open items and assumptions

1. **E1 books of account:** designed as an internal lightweight ledger; if the owner chooses "Tally is the books", keep journal for sub-ledgers and export only.
2. **E2 GSP provider** and credentials; sandbox access; until then JSON export only.
3. **E3 exchange-rate mode** default MANUAL.
4. **E4 financial year and multi-GSTIN:** April–March assumed; branches per GSTIN.
5. **E5 Tally edition** and connector style; **E6 payment gateway**; **E7 single vs multi-company**; **E8 print formats**.
6. All **[VERIFY]** items need an accountant/official-spec check before the matching task starts: GST e-invoice/e-way schema and eligibility thresholds, tax rates and place-of-supply edge cases, advance-receipt GST treatment, TDS sections and thresholds, GSTR formats, invoice-number rules.
7. Estimates in `PLAN-ERP.md` assume the design here; any scope change (for example full financial statements, payroll, fixed assets, multi-company) is outside this document.
8. Items from the supplied list deliberately not built: free vs paid plans, Knowledge Centre, product-support features, the "Why…" video topics (their behaviours are covered by validation messages and empty states).
9. Verification limits: backend and integration code cannot be compiled or exercised against vendor sandboxes in the cloud build environment; the owner runs `mvn verify`, the frontend checks and sandbox tests locally at each gate.


---

## 13. Additions from the video-title review (2026-10-28)

Additive to §1–§12. Requirement IDs refer to `REQUIREMENTS-ERP.md`. Migrations: items for Phase 9 go into V13 (or a V13a file if V13 is already applied), and so on per phase — never edit an applied file.

### 13.1 Schema additions
```sql
-- Phase 9 (ERP-A-10…A-15)
CREATE TABLE item_vendor_map (id, material_id BIGINT, product_id BIGINT, supplier_id BIGINT NOT NULL REFERENCES suppliers, supplier_item_code VARCHAR(40), supplier_item_name VARCHAR(150),
  last_price NUMERIC(18,4), last_price_date DATE, currency CHAR(3) DEFAULT 'INR', moq NUMERIC(18,3), multiple_of NUMERIC(18,3), lead_time_days INT, is_preferred BOOLEAN DEFAULT FALSE, active BOOLEAN DEFAULT TRUE,
  CHECK ((material_id IS NOT NULL)::int + (product_id IS NOT NULL)::int = 1), UNIQUE NULLS NOT DISTINCT (material_id, product_id, supplier_id));
CREATE TABLE document_reminders (id, entity_type VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL, remind_at TIMESTAMPTZ NOT NULL, message VARCHAR(300), user_id BIGINT, team_id BIGINT, created_by BIGINT, done_at TIMESTAMPTZ);
CREATE INDEX ix_doc_rem_due ON document_reminders(remind_at) WHERE done_at IS NULL;
CREATE TABLE comment_mentions (comment_id BIGINT NOT NULL REFERENCES comments, user_id BIGINT, team_id BIGINT, notified_at TIMESTAMPTZ);
CREATE TABLE team_notification_defaults (team_id BIGINT, event_type VARCHAR(50), channel VARCHAR(10), enabled BOOLEAN NOT NULL DEFAULT TRUE, PRIMARY KEY (team_id, event_type, channel));
CREATE TABLE team_module_access (team_id BIGINT, module_code VARCHAR(30), enabled BOOLEAN NOT NULL DEFAULT TRUE, PRIMARY KEY (team_id, module_code));  -- SALES, PURCHASE, INVENTORY, PRODUCTION, SERVICE, ACCOUNTS, REPORTS, SETTINGS
CREATE TABLE setup_steps (step_code VARCHAR(30) PRIMARY KEY, done_at TIMESTAMPTZ, done_by BIGINT, skipped BOOLEAN DEFAULT FALSE);
CREATE TABLE company_locations (id, branch_id BIGINT, location_name VARCHAR(100), kind VARCHAR(10) CHECK (kind IN ('BILLING','DELIVERY','BOTH')), line1 VARCHAR(150), city VARCHAR(80), state_code CHAR(2), pincode VARCHAR(10), is_default BOOLEAN);
CREATE TABLE document_packages (id, entity_type VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL, package_no VARCHAR(20), package_type VARCHAR(30), pieces NUMERIC(18,3), gross_weight_kg NUMERIC(12,3), net_weight_kg NUMERIC(12,3), dimensions VARCHAR(60), marks VARCHAR(100));
ALTER TABLE custom_field_definitions ADD COLUMN copy_on_convert BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN show_on_lines BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN reportable BOOLEAN NOT NULL DEFAULT FALSE;
-- document-level party override (snapshot), added to quotations, order_confirmations, sales_orders, sales_invoices, adjustment_notes, purchase_orders, purchase_invoices, delivery_challans, rfqs:
--   ADD COLUMN party_name_override VARCHAR(150), ADD COLUMN bill_to_snapshot JSONB, ADD COLUMN ship_to_snapshot JSONB, ADD COLUMN party_gstin_override VARCHAR(15), ADD COLUMN customer_email_sent_to VARCHAR(160)
ALTER TABLE customers ALTER COLUMN email DROP NOT NULL;  ALTER TABLE suppliers ALTER COLUMN email DROP NOT NULL;   -- only if currently NOT NULL

-- Phase 10 (ERP-B-5)
ALTER TABLE goods_receipts ADD COLUMN challan_id BIGINT, ADD COLUMN service_order_id BIGINT, ADD COLUMN service_confirmation_id BIGINT, ADD COLUMN po_revision_no INT;
ALTER TABLE goods_receipts DROP CONSTRAINT IF EXISTS goods_receipts_inward_kind_check;
ALTER TABLE goods_receipts ADD CONSTRAINT goods_receipts_inward_kind_check CHECK (inward_kind IN ('PO','NO_PO','JOB_WORK','SERVICE','RETURN','CHALLAN','SERVICE_ORDER','SERVICE_CONFIRMATION'));
ALTER TABLE po_items ADD COLUMN open_qty NUMERIC(18,3) GENERATED ALWAYS AS (qty - COALESCE(received_qty,0)) STORED;     -- "undelivered quantity" (column names per existing po_items)
CREATE TABLE price_history (id, party_type VARCHAR(10) NOT NULL, party_id BIGINT NOT NULL, material_id BIGINT, product_id BIGINT, source_type VARCHAR(40) NOT NULL, source_id BIGINT NOT NULL,
  doc_date DATE NOT NULL, rate NUMERIC(18,4) NOT NULL, currency CHAR(3) DEFAULT 'INR', qty NUMERIC(18,3), uom VARCHAR(10));   -- appended when PO/purchase invoice/OC/sales invoice is APPROVED/POSTED
CREATE INDEX ix_price_hist ON price_history(party_type, party_id, material_id, product_id, doc_date DESC);

-- Phase 11 (ERP-C-5)
ALTER TABLE delivery_challans DROP CONSTRAINT IF EXISTS delivery_challans_challan_type_check;
ALTER TABLE delivery_challans ADD CONSTRAINT delivery_challans_challan_type_check CHECK (challan_type IN ('RETURNABLE','NON_RETURNABLE','SERVICE','PURCHASE_RETURN','SERVICE_OUT'));
ALTER TABLE delivery_challans ADD COLUMN service_order_id BIGINT, ADD COLUMN service_confirmation_id BIGINT, ADD COLUMN purchase_return_id BIGINT;

-- Phase 12 (ERP-D-10)
ALTER TABLE sales_invoices ADD COLUMN service_confirmation_id BIGINT, ADD COLUMN auto_dispatch_id BIGINT, ADD COLUMN stock_effect VARCHAR(10) NOT NULL DEFAULT 'FROM_DISPATCH' CHECK (stock_effect IN ('NONE','FROM_DISPATCH','AUTO_DISPATCH'));
ALTER TABLE purchase_invoices ADD COLUMN service_confirmation_id BIGINT;
ALTER TABLE opening_balances ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DELETED','SETTLED')), ADD COLUMN deleted_by BIGINT, ADD COLUMN deleted_at TIMESTAMPTZ, ADD COLUMN delete_reason VARCHAR(300);

-- Phase 13 (ERP-E-6)
ALTER TABLE production_orders ADD COLUMN completion_mode VARCHAR(10) NOT NULL DEFAULT 'STANDARD' CHECK (completion_mode IN ('STANDARD','QUICK','DIRECT')), ADD COLUMN skip_child_production BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE production_order_materials ADD COLUMN skip_child BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE routing_change_log (id, production_order_id BIGINT NOT NULL, change JSONB NOT NULL, reason VARCHAR(300) NOT NULL, changed_by BIGINT NOT NULL, changed_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE scrap_entries (id, source_type VARCHAR(40), source_id BIGINT, material_id BIGINT, product_id BIGINT, batch_id BIGINT, qty NUMERIC(18,3) NOT NULL CHECK (qty > 0), uom VARCHAR(10),
  reason_id BIGINT, valuation NUMERIC(18,4), disposition VARCHAR(10) NOT NULL DEFAULT 'HELD' CHECK (disposition IN ('HELD','SOLD','REUSED','DISPOSED')), disposed_qty NUMERIC(18,3) DEFAULT 0,
  sale_invoice_id BIGINT, ledger_txn_id BIGINT, recorded_by BIGINT, recorded_at TIMESTAMPTZ DEFAULT now());
CREATE VIEW v_shop_floor_material AS /* open production orders' issued − consumed − returned − scrap per material, by order and operation */ SELECT … ;
CREATE VIEW v_production_plan AS /* planned/approved/released orders by needed_by with component availability flags */ SELECT … ;
INSERT INTO system_settings(key,value,value_type,description) VALUES … -- see §13.4

-- Phase 16 (ERP-H-5)
ALTER TABLE accounting_vouchers ADD COLUMN external_voucher_no VARCHAR(40), ADD COLUMN combine_key VARCHAR(120), ADD COLUMN combined_into_id BIGINT;
CREATE TABLE tally_readiness_checks (id, connection_id BIGINT, run_at TIMESTAMPTZ DEFAULT now(), run_by BIGINT, ok BOOLEAN, results JSONB);
```

### 13.2 Rules and algorithms
1. **Item–vendor mapping (ERP-A-10).** *Interpretation (to be confirmed):* "Vendor Mapping" = linking items to the suppliers who can supply them, with the supplier's own code/name and terms. Effects: PO and RFQ pickers can filter to mapped suppliers; PO line pre-fills supplier item code, last price, MOQ and lead time; MRP picks the preferred active mapping first (replaces the "preferred_supplier_id" fallback order in §4.6); price history updates `last_price`. Unmapped suppliers can still be used when the setting `purchase.require_vendor_mapping` is false (default).
2. **Item delete/deactivate.** Delete is allowed only if the item has no document lines, ledger rows, BOM links or batches; otherwise `ITEM_IN_USE` (409) and the UI offers Deactivate. Inactive items are excluded from pickers (with the explainability hint, rule 6) but remain in history, stock card and reports (filter "include inactive"). Bulk edit uses the import definition with `UPSERT` plus a multi-select "set field" action (permission `ITEM_MASTER_MANAGE`).
3. **Document reminders.** Daily/5-minute job turns due rows into notifications (in-app and e-mail per preferences); reminders are never silently dropped — an unsent reminder after 24 h raises an alert. **@mentions**: `@name` or `@team` in a comment resolves to `comment_mentions`; mentioned users get a notification only if they hold view permission on the entity (no leakage). **Team defaults**: user preference > team default > system default.
4. **Module access per team.** Menu and API access = (role permission) AND (module enabled for at least one of the user's teams, when the user belongs to any team with explicit module rows). Admin bypass. `GET /api/me/modules` returns the effective list. Report access combines with `team_report_access` (§2.1).
5. **All Documents.** `GET /api/documents?types=&status=&partyId=&from=&to=&tag=&owner=&q=` is a union view over the document tables (a `document_directory` SQL view with common columns: kind, id, number, date, party, status, total, owner, branch), permission-filtered per kind (same technique as search, ARCH-ERP §5.3). It also serves "check the status of a transaction" (status chip plus link chain) and "find any document".
6. **Explainability.** (a) Document API adds `blockedActions: [{ action, code, message }]` next to `allowedActions` (for example `{ "action":"CONVERT_TO_OC", "code":"QUOTATION_NOT_APPROVED", "message":"Approve the quotation first" }`); the state machine already knows the reason. (b) Pickers call `GET /api/{lookup}/explain?id=` or send `explain=true` on search; the response lists failed predicates (`INACTIVE`, `WRONG_KIND`, `NOT_MAPPED_TO_SUPPLIER`, `OUTSIDE_BRANCH`, `NO_ACTIVE_BOM`, `NO_HSN_OR_TAX_CODE`, `NO_PERMISSION`). (c) Empty reports/BI show the applied filters, the data scope and the date range, and name missing prerequisites ("no invoices posted in this period"). (d) Documents with lines lacking tax code produce a warning (or error when `tax.require_tax_code=true`) naming the lines — this is the "tax not calculating on PO" case. (e) Ledger-missing and BOM-not-active conditions use the same mechanism.
7. **Custom-field propagation.** On conversion (`DocumentConverter`) every field with `copy_on_convert=true` and an identical `field_code` in the target entity is copied; if the code differs, an explicit mapping row in `custom_field_maps(source_entity, source_code, target_entity, target_code)` is used. Item-master custom fields with `show_on_lines=true` are fetched into line `custom` at line creation and are read-only there; they print and can be reported when `reportable`.
8. **Party override.** At document creation the user may edit party name, addresses and GSTIN for that document only; the values are stored in the snapshot columns and used for printing, tax place-of-supply (if GSTIN/state is overridden the tax calculation uses the override), and e-invoice payloads; masters are untouched. Changing GSTIN state on a posted document is impossible (F-04).
9. **Email optional.** Sending steps (`send`, dispatch mail, reminder) are skipped with a warning when no recipient e-mail exists; the user may enter an ad-hoc address, stored in `customer_email_sent_to`. Document creation never requires an e-mail.
10. **Packaging.** `document_packages` rows on invoice/challan/dispatch; totals (packages, gross, net weight) print and feed e-way bill/e-invoice dispatch fields where applicable **[VERIFY]**.
11. **Total quantity footer.** Computed per UOM (sums only same-UOM lines; mixed UOMs show a per-UOM list) by the server and returned in `totals.qtyByUom`.
12. **Last-price insight.** `GET /api/price-insight?partyType=&partyId=&itemId=&limit=` returns the last N rates for that party and item from `price_history` plus best/worst recent price across parties (permission `PRICE_VIEW`); the line grid shows the last rate beside the rate field with a one-click "use". Price history is appended in the same transaction that approves/posts the document and is not edited afterwards.
13. **PO balance / undelivered quantity.** `open_qty` per PO line (generated column) and `po_balance` register: PO, supplier, item, ordered, received, accepted, rejected, invoiced, open, due date, days late. PO list shows a balance badge. Cancelled/closed lines show open 0.
14. **Amendment impact rules** (`AmendmentGuard`), applied by `POST /{docs}/{id}/amend` for PO, OC, quotation, service order, service confirmation:
    - quantity may not go below downstream-consumed quantity (received for PO, dispatched/invoiced for OC, confirmed for service order); price changes are allowed but create a repricing note on downstream invoices not yet posted;
    - removing a line with downstream documents is blocked (`AMEND_BLOCKED_BY_DOWNSTREAM`) with the list of blocking documents;
    - after amendment, downstream draft documents are flagged "source amended" for review; posted documents are unchanged;
    - the amended revision goes through the approval rule again; the previous approved revision remains the effective one until the new revision is APPROVED.
15. **Inward while amendment pending.** A GRN can be created against a PO whose latest revision is DRAFT/SUBMITTED; it references `po_revision_no` of the last APPROVED revision, and quantities are validated against that revision. After approval of the new revision, remaining open quantity follows the new revision. GRNs never block an amendment unless rule 14 forbids it.
16. **Inward kinds.** `CHALLAN` (against a supplier delivery challan or our returnable challan return), `SERVICE_ORDER` and `SERVICE_CONFIRMATION` (receipt of goods tied to a service flow, e.g. goods returned from a service vendor) use the same GRN service and the same stock posting; kind decides which source document's open quantities are validated and updated.
17. **Inventory approval mode.** Document types `INWARD`, `STOCK_ENTRY`, `STOCK_TRANSFER`, `STOCK_ADJUSTMENT` are registered with the approval engine. Setting `inventory.approval.mode` per type: AUTO (default for inward/transfer) or MANUAL (default for adjustment, as today). In MANUAL mode the stock is posted only after approval (document stays DRAFT/SUBMITTED, no ledger rows).
18. **Change a posted stock entry.** `POST /stock-entries/{id}/amend` performs, in one transaction: reverse the original ledger rows via `InventoryPostingService.reverse`, create a new revision with the corrected values and post it; both revisions stay visible, the audit row carries old/new/reason. Not allowed when reversal would make any downstream balance negative (`NEGATIVE_STOCK_NOT_ALLOWED`) — the message lists the affected documents.
19. **Quotation cancel cascade.** Cancelling a CONVERTED quotation: if its OC/SO has no downstream dispatch/production/invoice, offer "cancel quotation and its OC/SO" (single confirmation, audited as one action); otherwise `CANCEL_BLOCKED_BY_DOWNSTREAM` listing the blockers. Cancelling an OC cancels its auto-created SO and releases reservations; production orders created from it become "unlinked" (user decides).
20. **Challan kinds and price.** `PURCHASE_RETURN` challan is created from a purchase return (carries return reason and debit-note link); `SERVICE_OUT` covers goods sent out for service/repair (stock to the vendor party location, expected return date, links to a service order; on return via inward `SERVICE_CONFIRMATION`); challans can reference a service order/confirmation. `challan.price_required.<kind>` settings decide if rate is mandatory (default: required for NON_RETURNABLE and PURCHASE_RETURN because tax/e-way need value; optional for RETURNABLE/SERVICE_OUT; the UI states the reason when a price is demanded).
21. **Direct invoice stock effect.** When a TAX/EXPORT/BILL_OF_SUPPLY invoice has STOCK lines and no `dispatch_id`/`so_item_id`, posting it (setting `invoice.direct.stock_effect=AUTO_DISPATCH`, default) creates a system dispatch (`DSP-…`, flagged `auto_created`) with batch allocation by `BatchAllocator`, posts `SALES_DISPATCH` through `InventoryPostingService`, then the finance journal — one transaction; failure (insufficient stock, quarantine) rejects the invoice post with the standard stock error and nothing is posted. `NONE` (service-only or customer-owned goods) skips stock. Invoices created from a dispatch use `FROM_DISPATCH` (as designed).
22. **Invoice from service confirmation.** `fromServiceConfirmationId` fills lines from confirmed service quantities; sales or purchase side; prevents invoicing more than confirmed minus already invoiced (`INVOICE_EXCEEDS_CONFIRMED`).
23. **Cancel invoice and payments.** Cancelling a posted invoice with allocations is blocked (`INVOICE_HAS_PAYMENTS`) with a link to the payments; the guided order is: reverse/unallocate payments → cancel or credit-note the invoice (cancel only within policy; otherwise credit note). Payment reversal is the "delete logged payment voucher" case: it never deletes; it creates the reversal entry and marks the payment REVERSED.
24. **Opening balances delete/settle.** Delete (`OPENING_BALANCE_DELETE`) is allowed while unallocated and no later documents depend on it: reversing journal entry, `status=DELETED`, reason, audit. Settle = ordinary payment allocation against the opening-balance open item (`item_type = INVOICE`, `source_type = OPENING`).
25. **Quick complete / direct production / bulk.**
    - *Quick complete* (`POST /production-orders/{id}/quick-complete`, permission `PRODUCTION_QUICK_COMPLETE`): for a RELEASED order with a snapshot BOM and no started operations, in one transaction issues all planned materials by `BatchAllocator`, marks all operations completed with planned quantities (editable good/rejected/scrap inputs), backflushes consumption, receives FG (to QUALITY_PENDING if final inspection is required), and completes the order; any shortage stops the whole action.
    - *Direct FG from BOM* (`POST /production/direct`): body `{productId, bomId?, qty, warehouseId, batchNo?}`; creates a production order in mode DIRECT, snapshots the active BOM, then runs quick complete; used for simple conversions without routing.
    - *Bulk create* (`POST /production-orders/bulk` or import code `production_orders`): list of {product, qty, needed_by, oc/so ref}; each row validated like a normal create; result per row; optional "release and quick-complete" is not offered in bulk (too risky).
26. **Skip child production.** On order creation/release for a multi-level BOM, each SUB_ASSEMBLY line has `skip_child`: when true, the sub-assembly is treated as a stocked component (issued from stock, no child order); allowed only if on-hand AVAILABLE ≥ required at release time or the user accepts a shortage warning; the snapshot records the choice. Default from `make_or_buy`.
27. **Routing update on an order.** `PUT /production-orders/{id}/routing` allowed in DRAFT/APPROVED/RELEASED while the changed operations are still PENDING; can add/remove/reorder operations, change machines and standard times; writes `routing_change_log` (old/new JSON, reason) and re-snapshots only the routing part; BOM snapshot untouched (ADR-10). Permission `PRODUCTION_ROUTING_EDIT`; audit action `ROUTING_CHANGED`.
28. **BOM edit restriction.** Editing an ACTIVE BOM is impossible by design (new revision); creating/editing DRAFT BOMs needs `BOM_UPDATE`; additionally `bom.edit_users_only_team` (setting, optional) limits BOM editing to a team (default off) — answers "restrict user from editing BOM" without a new permission model.
29. **Scrap management.** Production rejection or line scrap creates `scrap_entries` (valued at the cost of the rejected quantity); the scrap screen lists HELD scrap by item/reason/age. Dispositions: SELL (creates a sales invoice draft/ad-hoc line against a scrap customer and posts `SCRAP` stock out through the posting service), REUSE (transfer back to RM with approval and a costing note), DISPOSE (write-off with approval, journal to scrap loss). Reports: scrap by reason/machine/product, scrap value, ageing.
30. **Shop-floor material.** `v_shop_floor_material` = Σ issued − consumed − returned − scrap per open order/operation/material (and the WIP location balance for non-order stock); screen groups by order, machine and operation; mismatches against the WIP location balance raise `SHOPFLOOR_MISMATCH` alert via the reconciliation job.
31. **Production planning board.** `GET /api/production/plan?from&to` lists DRAFT/APPROVED/RELEASED orders by `needed_by` with per-order readiness (materials available/short, BOM active, routing present, machine assigned), allows reprioritising and bulk approve/release (each through normal permissions). It does **not** schedule by machine capacity (REQUIREMENTS §16 future).
32. **Tally details.** (a) Voucher number: `external_voucher_no = IMS document number` by default (setting `tally.voucher_no_source = IMS_NUMBER | TALLY_AUTO`); duplicates are detected by Tally voucher number + IMS Document Id UDF before sending. (b) Combine: when `tally.combine_purchase_vouchers=true`, purchase documents with the same supplier and supplier invoice number are merged into one voucher (lines concatenated, taxes summed) with `combine_key = supplierId|supplierInvoiceNo|voucherType`; splitting back on cancel is not supported — cancel is blocked once combined (`VOUCHER_COMBINED`). (c) Readiness check (`POST /api/integrations/tally/readiness`): verifies Tally reachable, company present, GST enabled where required, voucher types exist, all mapped ledgers exist, duplicate-ledger names, financial-year dates match; results stored in `tally_readiness_checks` and shown as a checklist; first sync is blocked until ok (override with reason). (d) Flow sections in UI: *Completed* (posted in IMS, no voucher yet) → *Pending approval* → *Approved* → *Sent/Posted* → *Exceptions*; voucher type is chosen by `tally_rules` (invoice kind, export, note type).
33. **Onboarding and help.** Setup wizard steps (`setup_steps`): COMPANY, BRANCHES, BANK, USERS_TEAMS, TAX, SERIES, ITEMS (import), PARTIES (import), OPENING_STOCK, OPENING_BALANCES, INTEGRATIONS; progress bar on the dashboard until done/skipped. Help menu: links to docs, shortcut list, support contact (`settings help.support_email`). Version banner: `GET /api/system/version` polled on focus; when it differs from the loaded build a "New version available — reload" banner appears (replaces "clear cache" instructions). **Remove demo data** (`POST /api/admin/demo-data/reset`): enabled only when profile is dev/demo (`ims.demo.enabled=true`), requires `ADMIN_DEMO_RESET`, deletes only rows tagged `demo=true` through services where possible and a documented SQL script otherwise; never runs in production (hard check on profile + setting).

### 13.3 API additions (extends §5)
| Endpoint | Purpose | Permission |
|---|---|---|
| `GET/POST/PUT /api/item-vendor-map` · `GET /api/items/{id}/suppliers` | vendor mapping | `ITEM_MASTER_MANAGE` / view |
| `POST /api/items/bulk-update` | set field on selection | `ITEM_MASTER_MANAGE` |
| `GET/POST /api/reminders` · `POST /api/reminders/{id}/done` | document reminders | authenticated (own) / `TEAM_MANAGE` |
| `GET/PUT /api/teams/{id}/modules` · `/notification-defaults` · `GET /api/me/modules` | team module access, defaults | `TEAM_MANAGE` |
| `GET /api/documents` | All Documents | per-kind view |
| `GET /api/{lookup}/explain` · `blockedActions[]` in document responses | explainability | view of the entity |
| `GET /api/price-insight` | last price | `PRICE_VIEW` |
| `GET/PUT /api/{docs}/{id}/packages` | packaging | `<DOC>_UPDATE` |
| `GET /api/reports/po-balance` | undelivered qty | `REPORT_PURCHASE` |
| `POST /api/stock-entries/{id}/amend` | reverse and repost | `STOCK_ENTRY_AMEND` |
| `POST /api/production-orders/{id}/quick-complete` · `POST /api/production/direct` · `POST /api/production-orders/bulk` · `PUT /api/production-orders/{id}/routing` | production convenience | `PRODUCTION_QUICK_COMPLETE`, `PRODUCTION_CREATE`, `PRODUCTION_ROUTING_EDIT` |
| `GET /api/production/plan` · `GET /api/production/shop-floor-material` | planning, shop floor | `PRODUCTION_VIEW` |
| `GET/POST /api/scrap` · `POST /api/scrap/{id}/dispose` | scrap management | `SCRAP_VIEW`, `SCRAP_DISPOSE` |
| `POST /api/opening-balances/{id}/delete` | delete opening balance | `OPENING_BALANCE_DELETE` |
| `POST /api/integrations/tally/readiness` | Tally checklist | `INTEGRATION_MANAGE` |
| `GET /api/setup/steps` · `PUT /api/setup/steps/{code}` · `GET /api/system/version` · `POST /api/admin/demo-data/reset` | onboarding, version, demo reset | `COMPANY_MANAGE`; authenticated; `ADMIN_DEMO_RESET` |
New permissions: `STOCK_ENTRY_AMEND, PRODUCTION_QUICK_COMPLETE, PRODUCTION_ROUTING_EDIT, SCRAP_VIEW, SCRAP_DISPOSE, OPENING_BALANCE_DELETE, ADMIN_DEMO_RESET`. New error codes: `ITEM_IN_USE`, `AMEND_BLOCKED_BY_DOWNSTREAM`, `CANCEL_BLOCKED_BY_DOWNSTREAM`, `INVOICE_HAS_PAYMENTS`, `INVOICE_EXCEEDS_CONFIRMED`, `VOUCHER_COMBINED`, `TALLY_NOT_READY`, `DEMO_RESET_DISABLED`, `ROUTING_LOCKED`, `QUICK_COMPLETE_NOT_ALLOWED`.

### 13.4 Settings added
`purchase.require_vendor_mapping=false`, `tax.require_tax_code=false`, `inventory.approval.mode.INWARD=AUTO`, `.STOCK_TRANSFER=AUTO`, `.STOCK_ENTRY=AUTO`, `.STOCK_ADJUSTMENT=MANUAL`, `challan.price_required.NON_RETURNABLE=true`, `.PURCHASE_RETURN=true`, `.RETURNABLE=false`, `.SERVICE_OUT=false`, `invoice.direct.stock_effect=AUTO_DISPATCH`, `bom.edit_users_only_team=` (empty), `tally.voucher_no_source=IMS_NUMBER`, `tally.combine_purchase_vouchers=false`, `reminder.document.enabled=true`, `help.support_email=`, `ims.demo.enabled=false`.

### 13.5 Tests added (extends §11.1)
| ID | Case |
|---|---|
| G-T01 | Item used in a document cannot be deleted (`ITEM_IN_USE`) but can be deactivated; inactive item hidden in pickers with explanation, visible in history |
| G-T02 | `blockedActions` returns the right code for each state-machine refusal (quotation not approved, PO under amendment, invoice with payments) |
| G-T03 | Custom field copy-on-convert and show-on-lines; mapping by `custom_field_maps` |
| G-T04 | Party override changes place of supply and tax split on a draft; master unchanged; posted doc immutable |
| G-T05 | Invoice and challan create/send without customer e-mail; send skipped with warning |
| G-T06 | Price history appended once per posted document; last-price insight order and permission |
| G-T07 | Amendment guard: qty below received blocked; downstream draft flagged; previous revision effective until approval; inward during pending amendment uses last approved revision |
| G-T08 | Inward kinds CHALLAN/SERVICE_ORDER/SERVICE_CONFIRMATION update the correct source quantities |
| G-T09 | Inventory approval MANUAL: no ledger rows until approved; AUTO posts immediately |
| G-T10 | Stock entry amend reverses and reposts atomically; blocked when it would drive a downstream balance negative |
| G-T11 | Cancel converted quotation: cascade success and blocked cases |
| G-T12 | Direct invoice auto-dispatch posts one `SALES_DISPATCH` and one journal in one transaction; insufficient stock rejects both; idempotent replay |
| G-T13 | Invoice from service confirmation cannot exceed confirmed − invoiced |
| G-T14 | Cancel invoice with payments blocked; after reversal, credit note path works; opening balance delete/settle |
| G-T15 | Quick complete: success path totals; any shortage rolls back everything; not allowed when operations started |
| G-T16 | Direct FG from BOM creates mode DIRECT order and correct ledger rows; bulk create row-level results |
| G-T17 | Skip child production: sub-assembly issued from stock; shortage warning; snapshot records choice |
| G-T18 | Routing update on PENDING operations logs change; refused after start (`ROUTING_LOCKED`) |
| G-T19 | Scrap dispositions post correct stock/journal; scrap value = cost of rejected quantity |
| G-T20 | Shop-floor material equals ledger-derived WIP; mismatch alert |
| G-T21 | Document reminders fire once; mentions notify only users with view permission; team module access limits menu and API |
| G-T22 | All Documents never returns a document the caller cannot view |
| G-T23 | Tally: voucher number = IMS number; duplicate guard; combine rule; readiness check blocks first sync; exception states |
| G-T24 | Demo reset refuses in production profile; removes only tagged rows elsewhere |
