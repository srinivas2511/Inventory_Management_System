/**
 * UAT rows that no earlier spec drove by name: QCF-05 (an FG batch on HOLD cannot be reserved), FIN-02 (production cost
 * summary of a closed order), PRC-04 (a submitter cannot approve their own PO), PRD-06 (production order / operation audit
 * trail), STK-01 (stock balances on screen) and INS-02 (inventory value trend chart). Each spec makes its own data through
 * the real API (a whole production run, a sales order, a purchase order) so it does not consume the demo workflow's records,
 * then checks the screen against the API and the ledger.
 */
import { admin, apiFor } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { approvedSalesOrder, balances, demoIds, produceViaApi } from '../support/flow';
import { dialog, fill, iconButton, orderPanel, pick, row, typeInto } from '../support/ui';
import { U } from '../support/users';

const num = (v: unknown): number => Number(String(v ?? '').replace(/[^0-9.\-]/g, ''));

test.describe('UAT QCF-05: HOLD finished goods cannot be reserved', () => {
  test('QCF-05 an FG batch with units on HOLD cannot be reserved (not offered, refused by the API); approving the hold makes it reservable', async ({
    browser,
    guard,
  }) => {
    guard.allow({ status: [400, 409, 422], url: /\/api\/sales\/reservations$/ });
    const made = await produceViaApi(10);

    // quality1 records the final inspection through the screen: 6 approved, 4 on HOLD
    const q = await openAs(browser, guard, U.quality, `/quality/inspections/${made.inspectionId}`);
    const qp = q.page;
    await fill(qp.locator('body'), 'Approved qty', 6);
    await fill(qp.locator('body'), 'Hold qty', 4);
    const done = qp.waitForResponse((r) => /final-decision$/.test(r.url()) && r.request().method() === 'POST');
    await qp.getByRole('button', { name: /complete final inspection/i }).click();
    const res = await done;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    await q.close();

    // state after the decision, from the API and the ledger
    const fg = await admin().must('GET', `/api/quality/fg-batches/${made.fgBatchId}`);
    expect(JSON.stringify(fg), 'the batch carries a HOLD status').toMatch(/HOLD/);
    let rows = (await balances()).filter((b) => b.fgBatchId === made.fgBatchId);
    expect(rows.filter((b) => b.stockStatus === 'AVAILABLE').reduce((n, b) => n + Number(b.qtyOnHand), 0), 'the 6 approved units are AVAILABLE stock').toBe(6);
    expect(rows.filter((b) => b.stockStatus === 'HOLD').reduce((n, b) => n + Number(b.qtyOnHand), 0), 'the 4 held units sit in HOLD stock').toBe(4);

    // a sales order for all 10 pieces. The rule is batch level: a batch with units on HOLD is not approved for dispatch,
    // so the reserve dialog must not offer it, and a reservation attempted anyway is refused with a clear message.
    const so = await approvedSalesOrder(10);
    const a = await openAs(browser, guard, U.sales, '/sales/orders');
    const p = a.page;
    await iconButton(row(p, so.orderNumber), 'inventory').click();
    let d = dialog(p);
    await pick(d, 'Order item', 0);
    await d.getByRole('combobox', { name: /Batch and location/i }).click();
    const offered = (await p.getByRole('option').allInnerTexts()).join(' | ');
    expect(offered, `the held batch ${made.fgBatchNumber} must not be offered for reservation (options: ${offered})`).not.toContain(made.fgBatchNumber);
    await p.keyboard.press('Escape');
    await d.getByRole('button', { name: 'Cancel' }).click();
    await expect(d).toBeHidden();
    const refused = await apiFor(U.sales).call('POST', '/api/sales/reservations', {
      salesOrderItemId: so.itemId,
      fgBatchId: made.fgBatchId,
      warehouseId: rows.find((b) => b.stockStatus === 'AVAILABLE')!.warehouseId,
      locationId: rows.find((b) => b.stockStatus === 'AVAILABLE')!.locationId,
      qty: 6,
    });
    expect(refused.ok, 'the API refuses to reserve a batch that has units on HOLD').toBe(false);
    expect(JSON.stringify(refused.body)).toMatch(/not approved for dispatch|FG_NOT_APPROVED/i);
    let order = await admin().must('GET', `/api/sales/orders/${so.id}`);
    expect(Number(order.items[0].reservedQty), 'nothing was reserved').toBe(0);

    // the Quality Manager approves the hold through the screen's dialog (4 approved, none rejected): the batch is approved, all 10 are available
    const qm = await openAs(browser, guard, U.quality, '/quality/fg-batches');
    const hp = qm.page;
    await iconButton(row(hp, made.fgBatchNumber), 'lock_open').click();
    const hd = dialog(hp);
    await expect(hd.getByText(/4\.000 on hold/)).toBeVisible();
    await fill(hd, /^Quantity to approve/, 4);
    await expect(hd.getByTestId('rejected-preview')).toContainText('0.000');
    await fill(hd, /^Reason for the decision/, 'E2E QCF-05: re-inspected and accepted');
    const resolved = hp.waitForResponse((r) => /approve-hold$/.test(r.url()) && r.request().method() === 'POST');
    await hd.getByRole('button', { name: 'Resolve hold' }).click();
    const rr = await resolved;
    expect(rr.status(), (await rr.text()).slice(0, 300)).toBeLessThan(300);
    await expect(hd).toBeHidden();
    await qm.close();
    rows = (await balances()).filter((b) => b.fgBatchId === made.fgBatchId);
    expect(rows.filter((b) => b.stockStatus === 'HOLD').reduce((n, b) => n + Number(b.qtyOnHand), 0), 'no units remain on HOLD').toBe(0);
    expect(rows.filter((b) => b.stockStatus === 'AVAILABLE').reduce((n, b) => n + Number(b.qtyOnHand), 0), 'all 10 are available').toBe(10);
    await iconButton(row(p, so.orderNumber), 'inventory').click();
    d = dialog(p);
    await pick(d, 'Order item', 0);
    await pick(d, 'Batch and location', made.fgBatchNumber);
    await fill(d, 'Quantity', 10);
    await d.getByRole('button', { name: 'Reserve stock' }).click();
    await expect(d).toBeHidden();
    order = await admin().must('GET', `/api/sales/orders/${so.id}`);
    expect(Number(order.items[0].reservedQty), 'once the hold is approved the whole batch reserves').toBe(10);
    await a.close();
  });
});

test.describe('UAT FIN-02: production cost summary of a closed order', () => {
  test('FIN-02 the cost summary of a closed order shows the figures the API reports and they add up', async ({ browser, guard }) => {
    const made = await produceViaApi(8);
    const mats = await admin().list<any>(`/api/production-orders/${made.orderId}/materials`);
    const leftover = mats.reduce(
      (n, m) => n + Number(m.issuedQty) - Number(m.consumedQty) - Number(m.returnedQty ?? 0) - Number(m.scrapQty ?? 0),
      0,
    );

    // prodmgr1 closes the order through the screen
    const pm = await openAs(browser, guard, U.prodMgr, '/production/orders');
    const panel = await orderPanel(pm.page, made.orderNumber);
    await panel.getByRole('button', { name: /^close$/i }).click();
    const cd = dialog(pm.page);
    const reason = cd.getByLabel(/Reason for writing the material off/i);
    if (await reason.isVisible().catch(() => false)) {
      await typeInto(reason, 'E2E FIN-02 leftover scrapped');
    }
    await cd.getByRole('button', { name: /close/i }).last().click();
    await expect(cd).toBeHidden();
    expect((await admin().must('GET', `/api/production-orders/${made.orderId}`)).status).toBe('CLOSED');
    await pm.close();

    // the variance / cost summary is produced by the close event (after commit)
    let v: any;
    for (let i = 0; i < 40 && !v; i++) {
      v = (await admin().list<any>('/api/controlling/variances?size=200')).find((x) => x.productionOrderId === made.orderId);
      if (!v) await new Promise((r) => setTimeout(r, 500));
    }
    expect(v, `a cost summary exists for the closed order ${made.orderNumber}`).toBeTruthy();

    // the figures add up
    const components = num(v.actualMaterialCost) + num(v.actualLabourCost) + num(v.actualMachineCost) + num(v.actualOverheadCost);
    expect(num(v.actualCost), 'actual cost = material + labour + machine + overhead').toBeCloseTo(components, 2);
    expect(num(v.actualMaterialCost), 'the order consumed material, so material cost is above zero').toBeGreaterThan(0);
    if (v.status !== 'NO_ESTIMATE') {
      expect(num(v.totalVariance), 'total variance = actual - standard').toBeCloseTo(num(v.actualCost) - num(v.standardCost), 2);
      const parts = num(v.priceVariance) + num(v.quantityVariance) + num(v.efficiencyVariance) + num(v.otherVariance);
      expect(parts, 'the variance parts add up to the total').toBeCloseTo(num(v.totalVariance), 2);
    }
    // hand-worked check of the material cost when the issue response carries unit costs and nothing is left over
    const issueLines: any[] = made.issue.lines ?? made.issue.items ?? [];
    if (leftover < 0.0005 && issueLines.length > 0 && issueLines.every((l) => l.unitCost != null && l.quantity != null)) {
      const expected = issueLines.reduce((n, l) => n + num(l.quantity) * num(l.unitCost), 0);
      expect(num(v.actualMaterialCost), 'material cost = issued quantity x unit cost').toBeCloseTo(expected, 1);
    }

    // finance1 (read-only costing user, UAT Role 8) sees the same figures on the screen
    const f = await openAs(browser, guard, U.finance, '/controlling/variances');
    const r = row(f.page, made.orderNumber);
    await expect(r, 'the closed order appears in the cost summary list').toBeVisible();
    const cells = (await r.getByRole('cell').allInnerTexts()).map((t) => t.trim());
    const asNumbers = cells.map(num).filter((n) => Number.isFinite(n) && n !== 0);
    expect(asNumbers.some((n) => Math.abs(n - num(v.actualCost)) < 0.06), `the screen shows the actual cost ${v.actualCost}: ${cells.join(' | ')}`).toBe(true);
    if (v.status !== 'NO_ESTIMATE' && num(v.standardCost) !== 0) {
      expect(asNumbers.some((n) => Math.abs(n - num(v.standardCost)) < 0.06), `the screen shows the standard cost ${v.standardCost}`).toBe(true);
    }
    await f.close();
  });
});

test.describe('UAT PRC-04: segregation of duties on purchase order approval', () => {
  test('PRC-04 a user who submitted a PO cannot approve it; a different approver can', async ({ browser, guard }) => {
    guard.allow({ status: [400, 403, 409, 422], url: /\/api\/procurement\/purchase-orders\/\d+\/approve$/ });
    const { supplier, material } = await demoIds();
    const today = new Date().toISOString().slice(0, 10);
    // admin raises and submits the PO themselves
    const po = await admin().must('POST', '/api/procurement/purchase-orders', {
      supplierId: supplier.id,
      poDate: today,
      items: [{ materialId: material.id, orderedQty: 5, rate: 285, taxPct: 0 }],
    });
    await admin().must('POST', `/api/procurement/purchase-orders/${po.id}/submit`);
    const full = await admin().must('GET', `/api/procurement/purchase-orders/${po.id}`);

    const a = await openAs(browser, guard, U.admin, '/procurement/purchase-orders');
    const p = a.page;
    await pick(p.locator('body'), 'Status', 'Submitted');
    // D-37: the submitter is not offered a working Approve (disabled, with the reason); the server stays the authority
    const approveButton = iconButton(row(p, full.poNumber), 'check_circle');
    await expect(approveButton, 'Approve is disabled for the user who submitted the PO').toBeDisabled();
    await approveButton.locator('xpath=ancestor::span[contains(@class,"approve-wrap")]').hover();
    await expect(p.locator('.mat-mdc-tooltip')).toContainText(/another approver must approve/i);
    expect((await admin().must('GET', `/api/procurement/purchase-orders/${po.id}`)).status, 'the PO stays SUBMITTED').toBe('SUBMITTED');
    const direct = await admin().call('POST', `/api/procurement/purchase-orders/${po.id}/approve`);
    expect(direct.ok, 'the API refuses too').toBe(false);
    expect(JSON.stringify(direct.body)).toMatch(/SEGREGATION_OF_DUTIES|approve own purchase order/i);
    await a.close();

    // purchase1 does not hold the approval permission; a different approver is needed - an administrator who did NOT submit it
    const other = await apiFor(U.purchase).must('POST', '/api/procurement/purchase-orders', {
      supplierId: supplier.id,
      poDate: today,
      items: [{ materialId: material.id, orderedQty: 5, rate: 285, taxPct: 0 }],
    });
    await apiFor(U.purchase).must('POST', `/api/procurement/purchase-orders/${other.id}/submit`);
    const approved = await admin().must('POST', `/api/procurement/purchase-orders/${other.id}/approve`);
    expect(approved.status, 'a different user approves it').toBe('APPROVED');
  });
});

test.describe('UAT PRD-06: production order history and operation audit trail', () => {
  test('PRD-06 the audit log records the order and each operation start and completion, and the screen shows them', async ({ browser, guard }) => {
    const made = await produceViaApi(3);
    const ops = await admin().list<any>(`/api/production-orders/${made.orderId}/operations`);
    expect(ops.length, 'the order has its routing operations').toBeGreaterThanOrEqual(2);
    const audit = await admin().list<any>('/api/audit-logs?entity=ProductionOperation&size=500');
    for (const op of ops) {
      const mine = audit.filter((x) => String(x.entityId) === String(op.id)).map((x) => x.action);
      expect(mine, `operation ${op.sequenceNo} has a start entry`).toContain('OP_START');
      expect(mine, `operation ${op.sequenceNo} has a completion entry`).toContain('OP_COMPLETE');
    }
    const orderAudit = (await admin().list<any>('/api/audit-logs?size=500')).filter(
      (x) => String(x.entityId) === String(made.orderId) && /production/i.test(String(x.entity)),
    );
    expect(orderAudit.length, 'the order itself has history entries (approve / release / complete)').toBeGreaterThan(0);

    const a = await openAs(browser, guard, U.admin, '/admin/audit-logs');
    const p = a.page;
    await typeInto(p.getByLabel('Filter by entity'), 'ProductionOperation');
    const lastOp = [...ops].sort((x, y) => y.sequenceNo - x.sequenceNo)[0];
    const r = row(p, new RegExp(`OP_COMPLETE[\\s\\S]*${lastOp.id}|${lastOp.id}[\\s\\S]*OP_COMPLETE`));
    await expect(r.first(), `the audit screen lists the completion of operation ${lastOp.id}`).toBeVisible({ timeout: 15_000 });
    await a.close();
  });
});

test.describe('UAT STK-01: inventory balances on screen', () => {
  test('STK-01 the Current Stock screen lists the balances and its quantities match the ledger balances', async ({ browser, guard }) => {
    const { warehouse } = await demoIds();
    const api = await balances();
    expect(api.filter((b) => b.warehouseId === warehouse.id).length, 'the main warehouse holds balances').toBeGreaterThan(0);
    const a = await openAs(browser, guard, U.storeMgr, '/inventory/stock');
    const p = a.page;
    await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 20_000 });
    const rows = p.locator('tbody tr');
    await expect(rows.first(), 'the stock screen shows rows').toBeVisible();
    const shown = await rows.evaluateAll((trs) => trs.map((tr) => Array.from(tr.querySelectorAll('td')).map((td) => (td.textContent ?? '').trim())));
    expect(shown.length).toBeGreaterThan(0);
    let matched = 0;
    for (const cells of shown) {
      if (cells.length < 6) continue;
      const [type, itemId, whLoc, status, onHand] = cells;
      const [whId, locId] = whLoc.split('/').map((s) => Number(s.trim()));
      const candidates = api.filter(
        (b) =>
          b.itemType === type.toUpperCase() &&
          String(b.materialId ?? b.productId) === itemId &&
          b.warehouseId === whId &&
          b.locationId === locId &&
          status.toUpperCase().replace(/[^A-Z]+/g, '_').endsWith(b.stockStatus),
      );
      // the screen lists one row per batch balance, so each row must equal one of the ledger balances for that key
      if (candidates.length > 0) {
        matched++;
        expect(
          candidates.some((b) => Math.abs(Number(b.qtyOnHand) - num(onHand)) < 0.005),
          `on hand ${onHand} for ${type} ${itemId} at ${whLoc} (${status}) is one of the ledger balances ${candidates.map((b) => b.qtyOnHand).join(', ')}`,
        ).toBe(true);
      }
    }
    expect(matched, 'at least one screen row was matched to the ledger balances').toBeGreaterThan(0);
    await a.close();
  });
});

test.describe('UAT INS-02: inventory value trend chart', () => {
  test('INS-02 the dashboard draws the inventory value trend chart from the monthly closing values', async ({ browser, guard }) => {
    const trends = await apiFor(U.mgmt).get('/api/dashboard/trends');
    expect(trends.ok, JSON.stringify(trends.body).slice(0, 200)).toBe(true);
    expect((trends.body.inventoryValue ?? []).length, 'the trend has at least one month of closing value').toBeGreaterThan(0);
    const a = await openAs(browser, guard, U.mgmt, '/dashboard');
    const p = a.page;
    const card = p.locator('mat-card').filter({ hasText: 'Inventory value (INR)' });
    await expect(card, 'the Inventory value card is on the dashboard').toBeVisible();
    await expect(card.locator('svg:not(.mat-icon)').first(), 'the chart is drawn inside the card').toBeVisible();
    await a.close();
  });
});
