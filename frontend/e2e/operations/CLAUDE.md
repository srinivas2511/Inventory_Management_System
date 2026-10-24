# e2e/operations/ CLAUDE.md

E2E tests for operational and integration features added in R19.

## Spec files
| File | Tests | What it covers |
|------|-------|---------------|
| `predictive-iot-gst.spec.ts` | 15 | Predictive maintenance (R19-5.2), IoT ingestion (R19-6.3), GST e-invoice (R19-6.5) |

## Test groups

### Predictive Maintenance (PRED-1 … PRED-6)
- Records a sensor reading via API; verifies it appears in the readings list.
- Runs the MTBF prediction engine; checks the run summary and machine risk list.
- Acknowledges an active prediction; verifies it disappears from the active view.
- Opens the `/maintenance/predictive` screen as `maint1` (PREDICTIVE_MAINT_VIEW).

### IoT Ingestion (IOT-1 … IOT-3)
- Posts a `SENSOR_READING` event to `POST /api/integration/iot/events`; verifies the reading is persisted.
- Posts a `MAINTENANCE_ALERT` event; verifies it is accepted.
- Confirms the IoT endpoint requires authentication (unauthenticated request → 401).

### GST E-Invoice (GST-EI-1 … GST-EI-5)
- Generates a GST e-invoice for a completed dispatch via `POST /api/integration/gst-einvoice/generate`.
- Verifies the new invoice appears in `GET /api/integration/gst-einvoice`.
- Downloads the GSTN-schema JSON payload and validates its structure.
- Opens `/integration/gst-einvoice` as `admin` (GST_EINVOICE_VIEW); verifies the screen renders.
- Confirms a user without GST_EINVOICE_VIEW is denied (403).

## Notes
- The predictive maintenance tests seed their own sensor reading through the API rather than relying on demo data.
- GST-EI-1 falls back to `dispatchOnePiece()` when the demo workflow has not yet produced a DISPATCHED dispatch.
- Users: `maint1` (MAINTENANCE role), `admin` (all permissions), `storemgr1` (no finance/integration permissions).
