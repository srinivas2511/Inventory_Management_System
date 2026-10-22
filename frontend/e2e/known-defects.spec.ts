/**
 * Tests that assert DOCUMENTED behaviour which the application currently violates. They are expected to FAIL
 * until the application is fixed (each id matches scratchpad e2e-defects.md). They are never skipped or weakened:
 * a green run of this file means the defects are fixed.
 */
import { admin, apiFor, login, passwordFor } from './support/api';
import { balances, demoIds, receiveViaApi } from './support/flow';
import { expect, openAs, test } from './support/fixtures';
import { U } from './support/users';
import { today, typeInto } from './support/ui';

test.describe('Known defects (documented behaviour not yet met)', () => {
  test('D-04 every role lands on its first screen without a failed API call (no dashboard 403)', async ({ browser, guard }) => {
    const roles = [U.purchase, U.storeMgr, U.quality, U.operator1, U.sales, U.dispatch, U.engineer, U.prodMgr, U.supervisor];
    for (const who of roles) {
      const perms = (await login(who, passwordFor(who))).body.user.permissions as string[];
      if (perms.includes('DASHBOARD_VIEW')) continue;
      const ctx = await browser.newContext();
      const page = await ctx.newPage();
      const seen: string[] = [];
      page.on('response', (r) => {
        if (r.status() >= 400 && r.url().includes('/api/dashboard')) seen.push(`${who}: HTTP ${r.status()} ${new URL(r.url()).pathname}`);
      });
      guard.attach(page);
      await page.goto('/login');
      await typeInto(page.getByTestId('username'), who);
      await typeInto(page.getByTestId('password'), passwordFor(who));
      await page.getByTestId('login-submit').click();
      await expect(page).not.toHaveURL(/\/login/);
      await page.waitForLoadState('networkidle');
      expect(seen, 'a role without DASHBOARD_VIEW must not be sent to a screen that calls the dashboard API').toEqual([]);
      await ctx.close();
    }
  });

  test('D-05 storemgr1 (INVENTORY_ISSUE) can reach the production-order screen to issue material (DESIGN §12 step 8)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.storeMgr, '/production/orders');
    await expect(a.page.getByRole('heading', { name: 'Production Orders' })).toBeVisible();
    await a.close();
  });

  test('D-07 rejected final-inspection quantity is REJECTED stock and is not counted as available (DESIGN §12 step 14, QCF-04)', async () => {
    const { product } = await demoIds();
    const fgBatches = await admin().list<any>('/api/quality/fg-batches?size=200');
    const partial = fgBatches.filter((b) => /PARTIALLY_APPROVED/.test(JSON.stringify(b))).sort((x, y) => y.id - x.id)[0];
    expect(partial, 'a partially approved FG batch exists (run the workflow spec first)').toBeTruthy();
    const rows = (await balances(apiFor(U.quality))).filter((b) => b.fgBatchId === partial.id);
    const rejected = rows.filter((b) => b.stockStatus === 'REJECTED').reduce((n, b) => n + Number(b.qtyOnHand), 0);
    const availableRejectLoc = rows.filter((b) => b.stockStatus === 'AVAILABLE' && Number(b.qtyOnHand) === 2);
    expect(availableRejectLoc, 'rejected pieces must not sit in AVAILABLE status').toHaveLength(0);
    expect(rejected).toBe(2);
    void product;
  });

  test('D-08 reserving stock moves the sales order to ALLOCATED (DESIGN §12 step 15)', async ({ guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const { customer, product } = await demoIds();
    const fgList = await admin().list<any>(`/api/lookups/fg-stock?productId=${product.id}`);
    const stock = fgList.find((b) => Number(b.available) >= 1);
    expect(stock, 'available FG exists (run the workflow spec first)').toBeTruthy();
    const sales = apiFor(U.sales);
    const so = await sales.must('POST', '/api/sales/orders', {
      customerId: customer.id,
      orderDate: today(),
      requiredDate: today(10),
      deliveryAddress: 'E2E D-08 address',
      items: [{ productId: product.id, orderedQty: 1, unitPrice: 100 }],
    });
    await sales.must('POST', `/api/sales/orders/${so.id}/confirm`);
    await admin().must('POST', `/api/sales/orders/${so.id}/approve`);
    const full = await admin().must('GET', `/api/sales/orders/${so.id}`);
    await sales.must('POST', '/api/sales/reservations', {
      salesOrderItemId: full.items[0].id,
      fgBatchId: stock!.fgBatchId,
      warehouseId: stock!.warehouseId,
      locationId: stock!.locationId,
      qty: 1,
    });
    const after = await admin().must('GET', `/api/sales/orders/${so.id}`);
    expect(after.status).toBe('ALLOCATED');
    await sales.post(`/api/sales/orders/${so.id}/cancel`);
  });

  test('D-10 a stock adjustment compares the counted quantity with the real on-hand quantity (UAT STK-02/STK-03)', async () => {
    const { material } = await demoIds();
    const rows = (await balances()).filter((b) => b.materialId === material.id && b.stockStatus === 'AVAILABLE' && Number(b.qtyOnHand) > 10);
    const bal = rows.sort((x, y) => Number(y.qtyOnHand) - Number(x.qtyOnHand))[0];
    expect(bal, 'material stock exists (run the workflow spec first)').toBeTruthy();
    const onHand = Number(bal.qtyOnHand);
    const r = await apiFor(U.storeMgr).post('/api/inventory/adjustments', {
      itemType: 'MATERIAL',
      materialId: material.id,
      warehouseId: bal.warehouseId,
      locationId: bal.locationId,
      stockStatus: 'AVAILABLE',
      countedQty: onHand - 5,
      reason: 'E2E D-10 probe',
    });
    expect(r.ok, JSON.stringify(r.body).slice(0, 200)).toBe(true);
    expect(Number(r.body.systemQty)).toBeCloseTo(onHand, 3);
    expect(Number(r.body.varianceQty)).toBeCloseTo(-5, 3);
    expect(Number(r.body.varianceValue)).not.toBe(0);
  });

  test('D-11 quality1 can open the traceability screen (DESIGN §12 steps 20-21)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.quality, '/insight/traceability');
    await expect(a.page).toHaveURL(/insight\/traceability/);
    await expect(a.page.getByRole('heading', { name: 'Traceability' })).toBeVisible();
    await a.close();
  });

  test('D-13 a list shows (or pages to) the newest records once more than 20 exist', async ({ browser, guard }) => {
    const { supplier, material } = await demoIds();
    const buyer = apiFor(U.purchase);
    const existing = await buyer.list<any>('/api/procurement/purchase-orders?size=500');
    const created: number[] = [];
    for (let i = existing.length; i < 22; i++) {
      const po = await buyer.must('POST', '/api/procurement/purchase-orders', {
        supplierId: supplier.id,
        poDate: today(),
        items: [{ materialId: material.id, orderedQty: 1, rate: 1, taxPct: 0 }],
      });
      created.push(po.id);
    }
    const all = await buyer.list<any>('/api/procurement/purchase-orders?size=500');
    const newest = all.sort((x, y) => y.id - x.id)[0];
    const a = await openAs(browser, guard, U.purchase, '/procurement/purchase-orders');
    // Wait for the list to finish loading (the first body row, after the header row) before looking for the newest PO.
    await expect(a.page.getByRole('row').nth(1)).toBeVisible();
    const paginator = a.page.locator('mat-paginator');
    const visible = await a.page.getByRole('row').filter({ hasText: newest.poNumber }).count();
    const reachable = visible > 0 || (await paginator.count()) > 0;
    await a.close();
    // Keep the shared database usable for the workflow spec: cancel the drafts this probe created.
    for (const id of created) await buyer.post(`/api/procurement/purchase-orders/${id}/cancel`, { reason: 'E2E D-13 cleanup' });
    expect(reachable, `newest PO ${newest.poNumber} unreachable on the list (no row, no paginator)`).toBe(true);
  });

  test('D-14 an incoming inspection can be put on HOLD and re-inspected (UAT QCI-04, QCI-05)', async ({ browser, guard }) => {
    const rec = await receiveViaApi(3);
    const a = await openAs(browser, guard, U.quality, `/quality/inspections/${rec.inspectionId}`);
    await expect(a.page.getByRole('button', { name: /hold/i }), 'a Hold action exists on the incoming inspection').toBeVisible({ timeout: 5000 });
    await a.close();
  });
});
