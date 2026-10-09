import { admin, apiFor } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { demoIds, receiveViaApi, releasedOrder } from '../support/flow';
import { dialog, fill, pick, today, typeInto } from '../support/ui';
import { U } from '../support/users';

/** Expected 4xx responses are PASS here: the business rule requires the server to refuse. */
test.describe('Negative tests and business rules', () => {
  test('NEG-01 a QUARANTINE batch cannot be issued to production (INV-03, workflow step 4)', async ({ guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const { material, warehouse } = await demoIds();
    const rec = await receiveViaApi(10);
    const order = await releasedOrder(10);
    // 10 pieces plan 0.45 kg of material (0.045 each). 0.4 kg is inside the plan whichever BOM is active, so only the
    // quarantine rule can refuse it. 0.5 kg is not safe: with the BOM the workflow spec activates (0% scrap) the cap is
    // plan + 10% tolerance = 0.495 and the plan check refuses it first; it only passed the plan check while the
    // demo loader's 3% scrap BOM was still active (cap 0.50985).
    const r = await apiFor(U.storeMgr).post('/api/production/issues', {
      productionOrderId: order.id,
      warehouseId: warehouse.id,
      lines: [{ materialId: material.id, quantity: 0.4, explicitBatchId: rec.batchId }],
    });
    expect([400, 409, 422], JSON.stringify(r.body).slice(0, 300)).toContain(r.status);
    expect(JSON.stringify(r.body)).toMatch(/QUARANTINE/i);
  });

  test('NEG-02 issuing more than the available stock is refused with INSUFFICIENT_STOCK (INV-01)', async ({ guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const { material, warehouse } = await demoIds();
    // Plan a quantity whose material need exceeds all stock so the plan-tolerance check passes and the stock check decides.
    const order = await releasedOrder(10_000_000);
    const r = await admin().post('/api/production/issues', {
      productionOrderId: order.id,
      warehouseId: warehouse.id,
      lines: [{ materialId: material.id, quantity: 450_000 }],
    });
    expect([400, 409, 422], JSON.stringify(r.body).slice(0, 300)).toContain(r.status);
    expect(JSON.stringify(r.body)).toMatch(/INSUFFICIENT|available/i);
  });

  test('NEG-03 an illegal state transition is refused (approve a DRAFT PO)', async ({ guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const { supplier, material } = await demoIds();
    const po = await apiFor(U.purchase).must('POST', '/api/procurement/purchase-orders', {
      supplierId: supplier.id,
      poDate: today(),
      items: [{ materialId: material.id, orderedQty: 1, rate: 1, taxPct: 0 }],
    });
    const r = await admin().post(`/api/procurement/purchase-orders/${po.id}/approve`);
    expect([409, 422, 400], JSON.stringify(r.body).slice(0, 200)).toContain(r.status);
  });

  test('NEG-04 zero and negative quantities are rejected on PO, sales order and production order', async ({ guard }) => {
    guard.allow({ status: 400 });
    const { supplier, material, customer, product } = await demoIds();
    for (const qty of [0, -5]) {
      const po = await apiFor(U.purchase).post('/api/procurement/purchase-orders', {
        supplierId: supplier.id,
        poDate: today(),
        items: [{ materialId: material.id, orderedQty: qty, rate: 10, taxPct: 0 }],
      });
      expect(po.status, `PO qty ${qty}`).toBe(400);
      const so = await apiFor(U.sales).post('/api/sales/orders', {
        customerId: customer.id,
        orderDate: today(),
        items: [{ productId: product.id, orderedQty: qty, unitPrice: 10 }],
      });
      expect(so.status, `SO qty ${qty}`).toBe(400);
    }
    const bom = await admin().must('GET', `/api/products/${product.id}/bom`);
    const prod = await apiFor(U.prodMgr).post('/api/production-orders', { productId: product.id, bomId: bom.id, plannedQty: 0 });
    expect(prod.status).toBe(400);
  });

  test('NEG-05 an operation cannot be started before the previous one is complete', async ({ guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const order = await releasedOrder(5);
    const second = order.ops.find((o: any) => o.sequenceNo === 20);
    expect(second, 'second operation exists').toBeTruthy();
    const r = await admin().post(`/api/production/operations/${second.id}/start`);
    expect([400, 409, 422], JSON.stringify(r.body).slice(0, 200)).toContain(r.status);
  });

  test('NEG-06 a PO that is fully received cannot be received again (PRC-07)', async ({ guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const { warehouse } = await demoIds();
    const rec = await receiveViaApi(4);
    const stockLoc = (await admin().list<any>('/api/inventory/balances?size=1000')).find((b) => b.stockStatus === 'AVAILABLE' && b.materialId);
    const again = await apiFor(U.storeMgr).post('/api/procurement/goods-receipts', {
      purchaseOrderId: rec.poId,
      receiptDate: today(),
      warehouseId: warehouse.id,
      items: [{ poItemId: rec.poItemId, receivedQty: 1, locationId: stockLoc.locationId }],
    });
    expect([400, 409, 422], JSON.stringify(again.body).slice(0, 200)).toContain(again.status);
  });

  test('NEG-07 the stock ledger is immutable: no update or delete exists, and the screen offers no edit controls (STK-06)', async ({ browser, guard }) => {
    guard.allow({ status: [403, 404, 405] });
    for (const method of ['PUT', 'DELETE', 'PATCH']) {
      const r = await admin().call(method, '/api/inventory/ledger/1', method === 'DELETE' ? undefined : {});
      expect([403, 404, 405], `${method} on a ledger entry`).toContain(r.status);
    }
    const a = await openAs(browser, guard, U.admin, '/inventory/ledger');
    await expect(a.page.getByRole('heading', { name: 'Stock Ledger' })).toBeVisible();
    await expect(a.page.getByRole('button', { name: /edit|delete|remove/i })).toHaveCount(0);
    await a.close();
  });

  test('VAL-01 forms show clear validation messages for bad input (PO, user)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.purchase, '/procurement/purchase-orders');
    const p = a.page;
    await p.getByRole('button', { name: /new po/i }).click();
    const d = dialog(p);
    await fill(d, 'Quantity', 0);
    await fill(d, 'Rate', 0);
    await d.getByRole('button', { name: 'Create PO' }).click();
    await expect(d.getByText('Choose a supplier')).toBeVisible();
    await expect(d.getByText('Must be above 0').first()).toBeVisible();
    await expect(d).toBeVisible();
    await a.close();
  });

  test('NEG-08 creating a user with an existing username shows the server error in the form (duplicate master data)', async ({ browser, guard }) => {
    guard.allow({ status: [400, 409, 422] });
    const a = await openAs(browser, guard, U.admin, '/admin/users');
    const p = a.page;
    await p.getByTestId('new-user').click();
    await typeInto(p.getByTestId('user-username'), 'operator1');
    await typeInto(p.getByTestId('user-full-name'), 'Duplicate Operator');
    await typeInto(p.getByTestId('user-email'), 'duplicate.operator@e2e.local');
    await typeInto(p.getByTestId('user-password'), 'Qa#Plant-2026x!');
    await pick(dialog(p), 'Roles', /operator/i);
    await p.keyboard.press('Escape');
    await p.getByTestId('user-save').click();
    await expect(p.getByTestId('user-form-error')).toBeVisible();
    await expect(dialog(p)).toBeVisible();
    await a.close();
  });
});
