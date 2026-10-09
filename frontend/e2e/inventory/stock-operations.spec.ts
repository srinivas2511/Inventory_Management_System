/**
 * UAT STK-04 (opening balance), STK-05 (transfer between locations), SAL-01 / PRC-01 (customer and supplier
 * created through the UI) and FIN-01 / FIN-04 (inventory valuation). Each spec is independent of the
 * workflow spec: it builds its own master data and bins through the API and verifies the resulting
 * ledger/balance state, never a toast alone.
 */
import type { Page } from '@playwright/test';
import { admin, apiFor } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { balances, demoIds } from '../support/flow';
import { dialog, fill, pick, row } from '../support/ui';
import { U } from '../support/users';

/** Short unique code (master-data codes have tight length limits). */
const GST = '27AABCS1234A1Z5';
const short = (prefix: string): string => `${prefix}${Math.random().toString(36).slice(2, 7).toUpperCase()}`;

async function createBins(): Promise<{ a: any; b: any; warehouseId: number; materialId: number }> {
  const { warehouse, material } = await demoIds();
  const zone = await admin().must('POST', '/api/wms/zones', {
    warehouseId: warehouse.id,
    zoneCode: short('Z'),
    name: 'E2E transfer zone',
    zoneType: 'BULK',
    active: true,
    version: null,
  });
  const mk = (code: string) =>
    admin().must('POST', '/api/wms/bins', {
      zoneId: zone.id,
      binCode: code,
      aisle: null,
      rack: null,
      level: null,
      binPosition: null,
      binType: 'STANDARD',
      maxWeightKg: null,
      maxVolumeM3: null,
      allowedMaterialTypes: null,
      locationType: null,
      active: true,
      version: null,
    });
  const a = await mk(short('A-'));
  const b = await mk(short('B-'));
  return { a, b, warehouseId: warehouse.id, materialId: material.id };
}

async function fillTransferForm(p: Page, materialId: number, batchId: number, fromBin: number, toBin: number, qty: number): Promise<void> {
  await p.getByRole('combobox', { name: /warehouse/i }).click({ force: true });
  await p.getByRole('option').first().click();
  await p.getByLabel(/^Item id/).fill(String(materialId));
  await p.getByLabel(/^Batch id/).fill(String(batchId));
  await p.getByLabel(/^From bin id/).fill(String(fromBin));
  await p.getByLabel(/^To bin id/).fill(String(toBin));
  await p.getByLabel(/^Quantity/).fill(String(qty));
}

const onHandAt = async (materialId: number, locationId: number, status = 'AVAILABLE'): Promise<number> =>
  (await balances())
    .filter((x) => x.materialId === materialId && x.locationId === locationId && x.stockStatus === status)
    .reduce((n, x) => n + Number(x.qtyOnHand), 0);

test.describe('Master data created through the UI', () => {
  test('SAL-01 a customer is created through the UI with only code and name (UAT as written), and appears in the list and the API', async ({ browser, guard }) => {
    const code = short('TEST-CUST-');
    const a = await openAs(browser, guard, U.admin, '/master/customers');
    const p = a.page;
    await p.getByTestId('add-customer').click();
    const d = dialog(p);
    await fill(d, 'Code', code);
    await fill(d, 'Name', 'E2E Test Customer');
    await fill(d, 'Email', 'cust@e2e.local');
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(d, 'D-27: leaving the GST number blank sends gstNumber "" and the API answers 400 Invalid GST number format').toBeHidden();
    await p.getByTestId('search').fill(code);
    await expect(row(p, code)).toBeVisible();
    const found = (await admin().list<any>(`/api/customers?q=${code}&size=20`)).find((c) => c.customerCode === code);
    expect(found, 'the customer exists in the API').toBeTruthy();
    expect(found.name).toBe('E2E Test Customer');
    await a.close();
  });

  test('PRC-01 a supplier is created through the UI with only code and name (UAT as written)', async ({ browser, guard }) => {
    const code = short('TEST-SUP-');
    const a = await openAs(browser, guard, U.admin, '/master/suppliers');
    const p = a.page;
    await p.getByTestId('add-supplier').click();
    const d = dialog(p);
    await fill(d, 'Code', code);
    await fill(d, 'Name', 'E2E Test Supplier');
    await fill(d, 'Email', 'sup@e2e.local');
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(d, 'D-27: leaving the GST number blank sends gstNumber "" and the API answers 400 Invalid GST number format').toBeHidden();
    await p.getByTestId('search').fill(code);
    await expect(row(p, code)).toBeVisible();
    const found = (await admin().list<any>(`/api/suppliers?q=${code}&size=20`)).find((s) => s.supplierCode === code);
    expect(found, 'the supplier exists in the API').toBeTruthy();
    await a.close();
  });

  test('SAL-01 a customer with a valid GST number is created through the UI', async ({ browser, guard }) => {
    const code = short('TEST-CUST-');
    const a = await openAs(browser, guard, U.admin, '/master/customers');
    const p = a.page;
    await p.getByTestId('add-customer').click();
    const d = dialog(p);
    await fill(d, 'Code', code);
    await fill(d, 'Name', 'E2E GST Customer');
    await fill(d, 'GST number', GST);
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(d).toBeHidden();
    await p.getByTestId('search').fill(code);
    await expect(row(p, code)).toBeVisible();
    expect((await admin().list<any>(`/api/customers?q=${code}&size=20`)).some((c) => c.customerCode === code)).toBe(true);
    await a.close();
  });

  test('PRC-01 a supplier with a valid GST number is created through the UI', async ({ browser, guard }) => {
    const code = short('TEST-SUP-');
    const a = await openAs(browser, guard, U.admin, '/master/suppliers');
    const p = a.page;
    await p.getByTestId('add-supplier').click();
    const d = dialog(p);
    await fill(d, 'Code', code);
    await fill(d, 'Name', 'E2E GST Supplier');
    await fill(d, 'GST number', GST);
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(d).toBeHidden();
    await p.getByTestId('search').fill(code);
    await expect(row(p, code)).toBeVisible();
    expect((await admin().list<any>(`/api/suppliers?q=${code}&size=20`)).some((s) => s.supplierCode === code)).toBe(true);
    await a.close();
  });

  test('a rejected material form names the field that failed, not just "Validation failed"', async ({ browser, guard }) => {
    guard.allow({ status: [400, 422], method: 'POST', url: /\/api\/materials/ });
    const a = await openAs(browser, guard, U.admin, '/master/materials');
    const p = a.page;
    await p.getByTestId('add-material').click();
    const d = dialog(p);
    await p.getByTestId('code').fill(short('TEST-RM-'));
    await p.getByTestId('name').fill('E2E incomplete material');
    await p.getByTestId('materialType').fill('WIRE');
    await p.getByTestId('uom').click();
    await p.getByRole('option').first().click();
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(d, 'the dialog stays open while the form is incomplete').toBeVisible();
    await expect(
      d.locator('mat-error, .form-error, [role="alert"]').filter({ hasText: /min stock|minStock|reorder|standard cost|max stock|must not be (null|empty)/i }).first(),
      'D-31: the server answers with field errors but the dialog only shows "Validation failed"',
    ).toBeVisible({ timeout: 5000 });
    await a.close();
  });

  test('duplicate customer codes are refused with a readable message', async ({ browser, guard }) => {
    guard.allow({ status: [400, 409, 422], method: 'POST', url: /\/api\/customers/ });
    const code = short('DUP-C-');
    await admin().must('POST', '/api/customers', { customerCode: code, name: 'First', email: 'a@e2e.local', gstNumber: GST });
    const a = await openAs(browser, guard, U.admin, '/master/customers');
    const p = a.page;
    await p.getByTestId('add-customer').click();
    const d = dialog(p);
    // Material moves focus to the first field when the dialog has finished opening. Typing before that moment let a late
    // focus change send the GST number into the Code field once in a full run, so wait for it, then check the values
    // really are in their own fields before saving.
    await expect(d.getByLabel('Code', { exact: true }).first(), 'the dialog has finished opening').toBeFocused();
    await fill(d, 'Code', code);
    await fill(d, 'Name', 'Second');
    await fill(d, 'GST number', GST);
    await expect(d.getByLabel('Code', { exact: true }).first()).toHaveValue(code);
    await expect(d.getByLabel('GST number', { exact: true }).first()).toHaveValue(GST);
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(dialog(p), 'the dialog stays open on a duplicate').toBeVisible();
    await expect(p.locator('mat-error, mat-snack-bar-container, .error, [role="alert"]').first()).toContainText(/exist|duplicate|already|unique/i);
    await a.close();
  });
});

test.describe('Opening balance and transfers (UAT STK-04, STK-05)', () => {
  test('STK-04 a new material can be created through the UI and given an opening balance of 100 kg AVAILABLE through the UI', async ({ browser, guard }) => {
    const code = short('TEST-RM-');
    const a = await openAs(browser, guard, U.admin, '/master/materials');
    const p = a.page;
    await p.getByTestId('add-material').click();
    const d = dialog(p);
    await p.getByTestId('code').fill(code);
    await p.getByTestId('name').fill('E2E opening balance wire');
    await p.getByTestId('materialType').fill('WIRE');
    await p.getByTestId('uom').click();
    await p.getByRole('option').first().click();
    await fill(d, 'Min stock', 10);
    await fill(d, 'Reorder level', 20);
    await fill(d, 'Max stock', 500);
    await fill(d, 'Standard cost (₹)', 85);
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(d).toBeHidden();
    const mat = (await admin().list<any>(`/api/materials?q=${code}&size=20`)).find((m) => m.materialCode === code);
    expect(mat, 'the material was created').toBeTruthy();

    // UAT STK-04: "Record opening balance for a new material (100 kg AVAILABLE)" — a user must be able to do it in the app.
    await p.goto('/inventory/stock');
    const control = p.getByRole('button', { name: /opening balance/i }).or(p.getByRole('link', { name: /opening balance/i }));
    await expect(control.first(), 'D-28: there is no screen or button to record an opening balance').toBeVisible({ timeout: 5000 });
    await a.close();
  });

  test('STK-04 (API behaviour) an opening balance posts 100 kg AVAILABLE, a ledger row, and journals against Opening Balance Equity', async () => {
    const { warehouse } = await demoIds();
    const code = short('OB-RM-');
    const mat = await admin().must('POST', '/api/materials', {
      materialCode: code,
      name: 'E2E OB wire',
      materialType: 'WIRE',
      uom: 'KG',
      standardCost: 85,
      minStock: 0,
      reorderLevel: 0,
    });
    const { a: bin } = await createBins();
    const locationId = bin.locationId;
    const res = await admin().must('POST', '/api/inventory/opening-balances', [
      {
        itemType: 'MATERIAL',
        materialId: mat.id,
        productId: null,
        batchId: null,
        warehouseId: warehouse.id,
        locationId,
        stockStatus: 'AVAILABLE',
        quantity: 100,
        uom: 'KG',
        unitCost: 85,
        remarks: 'E2E opening balance',
      },
    ]);
    expect(res).toHaveLength(1);
    const rows = (await balances()).filter((x) => x.materialId === mat.id);
    expect(rows.reduce((n, x) => n + Number(x.qtyOnHand), 0)).toBeCloseTo(100, 3);
    expect(rows.every((x) => x.stockStatus === 'AVAILABLE')).toBe(true);
    const ledger = await admin().list<any>(`/api/inventory/ledger?materialId=${mat.id}&size=20`);
    expect(ledger.some((l) => l.transactionType === 'OPENING_BALANCE' && Number(l.quantity) === 100)).toBe(true);
    await expect
      .poll(
        async () => {
          const journals = await admin().list<any>('/api/journals?size=50');
          return journals.some((j) => /opening/i.test(`${j.description ?? ''} ${j.sourceDocumentType ?? ''}`));
        },
        { message: 'D-17: an opening-balance journal exists' },
      )
      .toBe(true);
  });

  test('STK-05 admin transfers 8 kg from bin A to bin B through the WMS transfer screen; source and destination balances move', async ({ browser, guard }) => {
    const { a: binA, b: binB, warehouseId, materialId } = await createBins();
    await admin().must('POST', '/api/inventory/opening-balances', [
      {
        itemType: 'MATERIAL', materialId, productId: null, batchId: null, warehouseId,
        locationId: binA.locationId, stockStatus: 'AVAILABLE', quantity: 20, uom: 'KG', unitCost: 85, remarks: 'E2E transfer stock',
      },
    ]);
    const batchId = (await balances()).find((x) => x.materialId === materialId && x.locationId === binA.locationId)!.materialBatchId!;
    expect(await onHandAt(materialId, binA.locationId)).toBeCloseTo(20, 3);

    const a = await openAs(browser, guard, U.admin, '/wms/transfers');
    const p = a.page;
    await fillTransferForm(p, materialId, batchId, binA.id, binB.id, 8);
    await p.getByRole('button', { name: /create transfer/i }).click();
    await expect(p.getByTestId('title').or(p.locator('[data-test="title"]'))).toContainText(/TO\//);
    await p.locator('[data-test="start"]').click();
    await p.locator('[data-test="qty"]').first().fill('8');
    await p.locator('[data-test="confirm"]').click();
    await expect(p.locator('[data-test="lines"]')).toContainText(/confirmed/i);

    expect(await onHandAt(materialId, binA.locationId)).toBeCloseTo(12, 3);
    expect(await onHandAt(materialId, binB.locationId)).toBeCloseTo(8, 3);
    const ledger = await admin().list<any>(`/api/inventory/ledger?materialId=${materialId}&size=50`);
    expect(ledger.filter((l) => /TRANSFER/.test(l.transactionType)).length, 'transfer ledger legs exist').toBeGreaterThanOrEqual(2);
    await a.close();
  });

  test('STK-05 a transfer larger than the stock in the source bin is refused and no stock moves', async ({ browser, guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const { a: binA, b: binB, warehouseId, materialId } = await createBins();
    await admin().must('POST', '/api/inventory/opening-balances', [
      {
        itemType: 'MATERIAL', materialId, productId: null, batchId: null, warehouseId,
        locationId: binA.locationId, stockStatus: 'AVAILABLE', quantity: 5, uom: 'KG', unitCost: 85, remarks: 'E2E short stock',
      },
    ]);
    const batchId = (await balances()).find((x) => x.materialId === materialId && x.locationId === binA.locationId)!.materialBatchId!;
    const a = await openAs(browser, guard, U.admin, '/wms/transfers');
    const p = a.page;
    await fillTransferForm(p, materialId, batchId, binA.id, binB.id, 500);
    await p.getByRole('button', { name: /create transfer/i }).click();
    // Either the order is refused at creation or its confirmation is refused; stock must not move in any case.
    if (await p.locator('[data-test="start"]').isVisible({ timeout: 4000 }).catch(() => false)) {
      await p.locator('[data-test="start"]').click();
      await p.locator('[data-test="qty"]').first().fill('500');
      await p.locator('[data-test="confirm"]').click();
    }
    await expect(p.locator('mat-snack-bar-container, [data-test="warnings"], .line-warning').first()).toContainText(/insufficient|not enough|available|exceed|cannot|could not/i);
    expect(await onHandAt(materialId, binA.locationId)).toBeCloseTo(5, 3);
    expect(await onHandAt(materialId, binB.locationId)).toBeCloseTo(0, 3);
    await a.close();
  });
});

test.describe('Inventory valuation (UAT FIN-01, FIN-04)', () => {
  test('FIN-01 the finance role can open the reports screen and run the inventory valuation report (UAT Role 8)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.finance, '/insight/reports');
    const p = a.page;
    await p.waitForLoadState('networkidle');
    await expect(p, 'D-30: the Finance role holds no REPORT_VIEW and is redirected away from the reports screen').toHaveURL(/insight\/reports/);
    await pick(p.locator('body'), 'Report', /inventory stock/i);
    await p.getByRole('button', { name: /^run$/i }).click();
    await expect(p.getByRole('columnheader', { name: /total value/i })).toBeVisible();
    await a.close();
  });

  test('FIN-01 the inventory valuation report agrees with the stock screen value per material', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/insight/reports');
    const p = a.page;
    await pick(p.locator('body'), 'Report', /inventory stock/i);
    await p.getByRole('button', { name: /^run$/i }).click();
    await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 20_000 });
    await expect(p.getByRole('columnheader', { name: /total value/i })).toBeVisible();

    const report = await admin().get('/api/reports/INV_STOCK?size=500');
    expect(report.ok, JSON.stringify(report.body).slice(0, 200)).toBe(true);
    const rows: any[] = report.body.rows ?? report.body.content ?? report.body.data ?? [];
    const bal = await balances();
    const stockValue = new Map<number, number>();
    for (const b of bal.filter((x) => x.itemType === 'MATERIAL')) {
      const v = Number((b as any).totalValue ?? 0);
      stockValue.set(b.materialId!, (stockValue.get(b.materialId!) ?? 0) + v);
    }
    const byCode = new Map<string, number>(rows.map((r) => [r['Material Code'] ?? r.material_code, Number(r['Total Value (INR)'] ?? r.total_value)]));
    const materials = await admin().list<any>('/api/materials?size=500');
    let compared = 0;
    for (const m of materials) {
      if (!stockValue.has(m.id) || !byCode.has(m.materialCode)) continue;
      compared++;
      expect(
        byCode.get(m.materialCode)!,
        `D-29: valuation report value for ${m.materialCode} must equal the stock screen value (ledger average cost)`,
      ).toBeCloseTo(stockValue.get(m.id)!, 1);
    }
    expect(compared, 'at least one material is valued in both places').toBeGreaterThan(0);
    await a.close();
  });

  test('FIN-04 the dashboard shows the inventory value trend (valuation view) for management', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.mgmt, '/dashboard');
    const p = a.page;
    await expect(p.locator('mat-sidenav-content svg:not(.mat-icon), mat-sidenav-content canvas').first()).toBeVisible();
    await expect(p.getByText(/inventory value/i).first()).toBeVisible();
    const trends = await apiFor(U.mgmt).get('/api/dashboard/trends');
    expect(trends.ok).toBe(true);
    await a.close();
  });
});
