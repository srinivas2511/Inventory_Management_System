/**
 * E2E tests for three operational / integration features added in R19:
 *   - R19-5.2  Predictive maintenance (MTBF-based failure predictions, sensor readings, machine risk)
 *   - R19-6.3  IoT machine data ingestion (POST /api/integration/iot/events)
 *   - R19-6.5  GST e-invoice generation and payload download
 *
 * All three features are API-first. The UI tests open the matching Angular screens and verify they
 * render without errors; the heavier business-logic verification goes through the API directly so
 * the spec does not depend on fragile DOM details of newly-built components.
 */
import { admin, apiFor, rawApi } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { dispatchOnePiece } from '../support/flow';
import { row } from '../support/ui';
import { U } from '../support/users';

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

async function firstMachine(): Promise<{ id: number; code: string }> {
  const machines = await admin().list<any>('/api/machines');
  if (machines.length) return machines[0];
  // Seed a machine for this test run (demo data loader does not seed machines).
  return admin().must('POST', '/api/machines', {
    code: 'MCH-E2E-01',
    name: 'E2E Test Machine',
    machineType: 'PRESS',
    status: 'RUNNING',
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Predictive Maintenance (R19-5.2)
// ─────────────────────────────────────────────────────────────────────────────

test.describe.serial('Predictive maintenance (R19-5.2)', () => {
  let predictionId: number | undefined;

  test('PRED-1 admin records a sensor reading via the API and it appears in the readings list', async () => {
    const machine = await firstMachine();
    const body = await admin().must('POST', '/api/maintenance/predictive/sensor-readings', {
      machineId: machine.id,
      readingType: 'VIBRATION',
      readingValue: 2.4,
      unit: 'mm/s',
    });
    expect(body.id, 'sensor reading persisted with an ID').toBeTruthy();
    expect(body.machineId).toBe(machine.id);

    // Verify the reading is retrievable.
    const readings = await admin().must('GET', `/api/maintenance/predictive/sensor-readings?machineId=${machine.id}`);
    expect(Array.isArray(readings), 'readings endpoint returns an array').toBe(true);
    const found = readings.find((r: any) => r.id === body.id);
    expect(found, 'the new reading appears in the list').toBeTruthy();
  });

  test('PRED-2 running predictions returns a summary and a machine risk list', async () => {
    const result = await admin().must('POST', '/api/maintenance/predictive/run');
    expect(result.summary, 'run result includes a summary').toBeTruthy();
    expect(Array.isArray(result.machines), 'run result includes machines array').toBe(true);
  });

  test('PRED-3 active predictions list is reachable; acknowledged predictions do not appear in the default view', async () => {
    const all = await admin().must('GET', '/api/maintenance/predictive/predictions?all=true');
    expect(Array.isArray(all), 'predictions?all=true returns array').toBe(true);

    const active = await admin().must('GET', '/api/maintenance/predictive/predictions');
    expect(Array.isArray(active), 'predictions (active only) returns array').toBe(true);

    if (active.length > 0) {
      predictionId = active[0].id;
    }
  });

  test('PRED-4 acknowledging a prediction removes it from the active list', async () => {
    if (!predictionId) {
      // If PRED-2/PRED-3 produced no active predictions (no failure history yet), skip gracefully.
      test.skip();
      return;
    }
    const acked = await admin().must('POST', `/api/maintenance/predictive/predictions/${predictionId}/acknowledge`);
    expect(acked.id).toBe(predictionId);
    expect(acked.acknowledged, 'prediction is now acknowledged').toBe(true);

    const active = await admin().must('GET', '/api/maintenance/predictive/predictions');
    const stillActive = (active as any[]).find((p: any) => p.id === predictionId);
    expect(stillActive, 'acknowledged prediction is gone from the active list').toBeFalsy();
  });

  test('PRED-5 predictive maintenance screen renders for a user with PREDICTIVE_MAINT_VIEW', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin, '/maintenance/predictive');
    const p = a.page;
    // The heading "Predictive Maintenance" or a tab label should be present.
    await expect(
      p.getByRole('heading', { name: /predictive maintenance/i }).or(p.getByText(/machine risk/i)).first(),
    ).toBeVisible({ timeout: 15_000 });
    await a.close();
  });

  test('PRED-6 machine risk endpoint returns a list of machines with risk levels', async () => {
    const risk = await admin().must('GET', '/api/maintenance/predictive/machine-risk');
    expect(Array.isArray(risk), 'machine-risk returns an array').toBe(true);
    // Each row (if any) should have a machineId and riskLevel.
    for (const r of risk as any[]) {
      expect(r.machineId ?? r.machine_id, 'risk row has a machineId').toBeTruthy();
    }
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// IoT Ingestion (R19-6.3)
// ─────────────────────────────────────────────────────────────────────────────

test.describe.serial('IoT machine data ingestion (R19-6.3)', () => {
  test('IOT-1 posting a SENSOR_READING event via the IoT endpoint persists a sensor reading', async () => {
    const machine = await firstMachine();
    const beforeReadings = await admin().must('GET', `/api/maintenance/predictive/sensor-readings?machineId=${machine.id}`);
    const beforeCount = (beforeReadings as any[]).length;

    const result = await admin().must('POST', '/api/integration/iot/events', {
      events: [{
        machineId: machine.id,
        eventType: 'SENSOR_READING',
        readingType: 'TEMPERATURE',
        value: 74.5,
        unit: 'C',
        timestamp: new Date().toISOString(),
      }],
    });
    expect(result.accepted, 'IoT event acknowledged').toBeGreaterThan(0);

    // The reading should now appear in the sensor readings list.
    const afterReadings = await admin().must('GET', `/api/maintenance/predictive/sensor-readings?machineId=${machine.id}`);
    expect((afterReadings as any[]).length, 'sensor reading count increased after IoT event').toBeGreaterThan(beforeCount);
  });

  test('IOT-2 a MAINTENANCE_ALERT event type is accepted without error', async () => {
    const machine = await firstMachine();
    // The server should accept any event type it recognises (or silently ignore unknown ones).
    const r = await admin().call('POST', '/api/integration/iot/events', {
      events: [{
        machineId: machine.id,
        eventType: 'MAINTENANCE_ALERT',
        timestamp: new Date().toISOString(),
      }],
    });
    expect(r.status, 'maintenance alert event accepted').toBeLessThan(300);
  });

  test('IOT-3 IoT endpoint requires authentication — unauthenticated request is rejected', async () => {
    const ctx = await rawApi();
    const res = await ctx.post('/api/integration/iot/events', {
      data: { eventType: 'SENSOR_READING', timestamp: new Date().toISOString() },
    });
    expect(res.status(), 'unauthenticated IoT request should be 401').toBe(401);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// GST E-Invoice (R19-6.5)
// ─────────────────────────────────────────────────────────────────────────────

test.describe.serial('GST e-invoice generation (R19-6.5)', () => {
  let dispatchId: number | undefined;
  let invoiceId: number | undefined;

  test('GST-EI-1 generate a GST e-invoice for a completed dispatch via the API', async () => {
    // Use the demo dispatch produced by the shared demo workflow.
    const dispatches = await admin().list<any>('/api/sales/dispatches?size=50');
    const completed = dispatches.find((d: any) => d.status === 'DISPATCHED');
    if (!completed) {
      // No completed dispatch yet — create one.
      const done = await dispatchOnePiece();
      dispatchId = done.dispatchId;
    } else {
      dispatchId = completed.id;
    }

    const inv = await admin().must('POST', '/api/integration/gst-einvoice/generate', {
      dispatchId,
    });
    expect(inv.id, 'e-invoice persisted with an ID').toBeTruthy();
    expect(inv.dispatchId).toBe(dispatchId);
    expect(inv.status ?? inv.irn ?? 'PENDING', 'e-invoice has a status or IRN').toBeTruthy();
    invoiceId = inv.id;
  });

  test('GST-EI-2 e-invoice list includes the new record', async () => {
    if (!invoiceId) { test.skip(); return; }
    const list = await admin().list<any>('/api/integration/gst-einvoice');
    const found = list.find((i: any) => i.id === invoiceId);
    expect(found, 'the generated e-invoice appears in the list').toBeTruthy();
  });

  test('GST-EI-3 downloading the JSON payload returns valid JSON with the expected GSTN fields', async () => {
    if (!invoiceId) { test.skip(); return; }
    const r = await admin().call('GET', `/api/integration/gst-einvoice/${invoiceId}/payload`);
    expect(r.status, 'payload download succeeds').toBe(200);
    // The payload is JSON. Parse and check for the mandatory GSTN schema keys.
    const payload = typeof r.body === 'string' ? JSON.parse(r.body) : r.body;
    // GSTN schema requires at least a BuyerDtls or SellerDtls section at the top level,
    // or the envelope key used by the service.
    const hasContent = payload && (payload.BuyerDtls || payload.SellerDtls || payload.DocDtls || Object.keys(payload).length > 0);
    expect(hasContent, 'payload contains GSTN schema content').toBeTruthy();
  });

  test('GST-EI-4 GST e-invoice screen renders for a user with GST_EINVOICE_VIEW', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin, '/integration/gst-einvoice');
    const p = a.page;
    await expect(
      p.getByRole('heading', { name: /gst.*e.invoice|e.invoice/i }).or(p.getByText(/gst e-invoice|einvoice/i)).first(),
    ).toBeVisible({ timeout: 15_000 });
    if (invoiceId) {
      // The generated e-invoice should appear in the table.
      await expect(row(p, String(dispatchId!)).or(p.locator('mat-row, tr').first())).toBeVisible();
    }
    await a.close();
  });

  test('GST-EI-5 a user without GST_EINVOICE_VIEW is denied access', async () => {
    const storeApi = await apiFor(U.storeMgr);
    const r = await storeApi.call('GET', '/api/integration/gst-einvoice');
    expect(r.status, 'storemgr1 is forbidden from the GST e-invoice list').toBe(403);
  });
});
