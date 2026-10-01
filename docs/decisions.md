# Decision log (business decisions awaiting confirmation)

Defaults are in force so work never stalls. Change the status when the product owner confirms or overrides.
Source: DESIGN.md §14, PLAN.md §14.

| # | Decision | Default in force | Needed before | Status | Owner |
|---|---|---|---|---|---|
| 11 | Attribute set for Belleville and Custom springs | Belleville: outer/inner Ø, thickness, free height, load, stack; Custom: free-form definitions | Phase 1 (1.9) | Default | Engineering |
| 2 | Stock-adjustment Admin threshold | variance **value**, ₹10,000 (`inventory.adjustment.admin_threshold`) | Phase 2 (2.7) | Default | Management / Stores |
| 3 | Who approves purchase orders | any user holding `PURCHASE_APPROVE` (Admin in demo) | Phase 3 (3.1) | Default | Management |
| 6 | Tax / currency / invoicing | INR, GST % per PO line, invoice number is a text reference only | Phase 3 | Default | Finance |
| 1 | BOM quantity basis | 0.045 kg per piece (requirement example "0.45 kg" read as per 10 pieces); `base_quantity` supported | Phase 4 (4.2) | Default | Engineering |
| 7 | Traceability granularity | order-level; batch-level optional (`quality.trace_granularity`) | Phase 4 | Default | Quality |
| 9 | Hold quantity in final inspection | HOLD stock status until decision | Phase 5 | Default | Quality |
| 4 | Production target source | single monthly setting (`production.monthly_target`) | Phase 7 | Default | Production |
| 8 | Customer complaint register / CAPA | `CUSTOMER_RETURN` + remarks only | Phase 7 | Default | Quality |
| 10 | UI languages | English, i18n-ready | Phase 8 | Default | Management |
| 12 | Barcode labels | not in v1 (batch/location codes are scan-ready) | Phase 8 | Default | Stores |
| 13 | Hosting target, backup policy, e-mail provider | Docker Compose on a Linux VM, nightly backups | Phase 8 | Open | IT |

## Phase 0 technical notes
- Java: the project targets Java 17 (`<release>17`); it also builds on newer JDKs.
- Fonts and icons are self-hosted from npm (`@fontsource/roboto`, `material-icons`) so builds and the running app work without internet access (ADR-15).
