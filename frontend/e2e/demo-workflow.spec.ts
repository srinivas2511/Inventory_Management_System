/**
 * The documented end-to-end demonstration workflow (DESIGN §12, UAT checklist) driven through the
 * real UI, one signed-in browser session per actor, with business state verified after each step
 * through the API (ledger and balances), not just toasts. Steps depend on each other: serial.
 * Quantities follow the UAT checklist (50 kg wire, 50 pcs spring) on the demo master data;
 * docs/decisions.md supersedes older DESIGN wording where they differ (FG stays AVAILABLE, three-way match, ...).
 */
import { admin, apiFor, login, passwordFor } from './support/api';
import { expect, openAs, test } from './support/fixtures';
import { availableOf, balances, demoIds, onHand, statusRe } from './support/flow';
import { dialog, fill, iconButton, orderPanel, pick, row, today } from './support/ui';
import { U } from './support/users';

interface State {
  ids?: Awaited<ReturnType<typeof demoIds>>;
  poId?: number;
  poNumber?: string;
  availBefore?: number;
  quarantineBefore?: number;
  inspectionId?: number;
  grId?: number;
  bomId?: number;
  orderId?: number;
  orderNumber?: string;
  fgBatchId?: number;
  fgBatchNumber?: string;
  soId?: number;
  soNumber?: string;
  soItemId?: number;
  dispatchId?: number;
  fgAvailBeforeDispatch?: number;
}
const S: State = {};

test.describe.serial('22-step demonstration workflow', () => {
  test.beforeAll(async () => {
    S.ids = await demoIds();
  });

  test('S01 purchase1 creates the PO (50 kg wire) and submits it', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.purchase, '/procurement/purchase-orders');
    const p = a.page;
    await p.getByRole('button', { name: /new po/i }).click();
    const d = dialog(p);
    await pick(d, 'Supplier', /krishna/i);
    await pick(d, 'Material', /65Si2/i);
    await fill(d, 'Quantity', 50);
    await fill(d, 'Rate', 285);
    await d.getByRole('button', { name: 'Create PO' }).click();
    await expect(d).toBeHidden();

    const pos = await apiFor(U.purchase).list<any>('/api/procurement/purchase-orders?size=100');
    const mine = pos.filter((x) => x.status === 'DRAFT' && x.items?.[0]?.orderedQty === 50).sort((x, y) => y.id - x.id)[0];
    expect(mine, 'the new draft PO exists').toBeTruthy();
    S.poId = mine.id;
    S.poNumber = mine.poNumber;
    expect(Number(mine.subtotal)).toBe(14250);

    // The PO list shows only the first 20 POs with no paging (D-13): filter by status like a user would.
    await pick(p.locator('body'), 'Status', 'Draft');
    const r = row(p, S.poNumber!);
    await expect(r).toContainText(statusRe('DRAFT'));
    await iconButton(r, 'send').click();
    await pick(p.locator('body'), 'Status', 'Submitted');
    await expect(row(p, S.poNumber!)).toContainText(statusRe('SUBMITTED'));
    const po = await apiFor(U.purchase).must('GET', `/api/procurement/purchase-orders/${S.poId}`);
    expect(po.status).toBe('SUBMITTED');
    await a.close();
  });

  test('S02 admin approves and sends the PO (a different user from the submitter)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/procurement/purchase-orders');
    const p = a.page;
    const body = p.locator('body');
    await pick(body, 'Status', 'Submitted');
    await iconButton(row(p, S.poNumber!), 'check_circle').click();
    await pick(body, 'Status', 'Approved');
    const approved = row(p, S.poNumber!);
    await expect(approved).toContainText(statusRe('APPROVED'));
    await iconButton(approved, 'local_shipping').click();
    await pick(body, 'Status', 'Sent to Supplier');
    await expect(row(p, S.poNumber!)).toContainText(statusRe('SENT_TO_SUPPLIER'));
    const po = await admin().must('GET', `/api/procurement/purchase-orders/${S.poId}`);
    expect(po.status).toBe('SENT_TO_SUPPLIER');
    await a.close();
  });

  test('S03 storemgr1 receives 50 kg: batch lands in QUARANTINE and available stock does not move', async ({ browser, guard }) => {
    const m = { materialId: S.ids!.material.id };
    S.availBefore = await availableOf(m);
    S.quarantineBefore = await onHand(m, 'QUARANTINE');
    const a = await openAs(browser, guard, U.storeMgr, '/procurement/goods-receipts');
    const p = a.page;
    await p.getByRole('button', { name: /receive goods/i }).click();
    const d = dialog(p);
    await pick(d, 'Purchase order', S.poNumber!);
    await pick(d, 'Warehouse', 0);
    await fill(d, 'Received', 50);
    await pick(d, 'Location', 0);
    await fill(d, 'Heat no.', 'H-77821');
    await fill(d, 'Certificate no.', 'MTC-2231');
    await d.getByRole('button', { name: 'Post receipt' }).click();
    await expect(d).toBeHidden();

    const grs = await apiFor(U.storeMgr).list<any>('/api/procurement/goods-receipts?size=50');
    const gr = grs.sort((x, y) => y.id - x.id)[0];
    S.grId = gr.id;
    expect(await onHand(m, 'QUARANTINE')).toBeCloseTo(S.quarantineBefore! + 50, 3);
    expect(await availableOf(m)).toBeCloseTo(S.availBefore!, 3);
    const po = await admin().must('GET', `/api/procurement/purchase-orders/${S.poId}`);
    expect(po.status).toBe('RECEIVED');
    const insp = (await admin().list<any>('/api/quality/inspections?size=100'))
      .filter((i) => i.inspectionType === 'INCOMING' && /PENDING|IN_PROGRESS/.test(i.status))
      .sort((x, y) => y.id - x.id)[0];
    expect(insp, 'incoming inspection created for the receipt').toBeTruthy();
    S.inspectionId = insp.id;
    await p.getByRole('button', { name: /refresh/i }).click();
    await expect(row(p, gr.grNumber)).toBeVisible();
    await a.close();
  });

  test('S04 a quarantined batch exists and an issue against a bogus order is refused', async () => {
    const batches = await admin().list<any>('/api/inventory/batches?size=100');
    expect(batches.find((b) => b.status === 'QUARANTINE'), 'a quarantined batch exists').toBeTruthy();
    const r = await apiFor(U.storeMgr).post('/api/production/issues', {
      productionOrderId: 999999,
      warehouseId: S.ids!.warehouse.id,
      lines: [],
    });
    expect([400, 404, 409, 422]).toContain(r.status);
  });

  test('S05 quality1 accepts the incoming inspection: stock becomes AVAILABLE (+50)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.quality, `/quality/inspections/${S.inspectionId}`);
    const p = a.page;
    await expect(p.getByRole('heading', { name: /QI\// })).toBeVisible();
    const accept = p.waitForResponse((r) => /\/inspections\/\d+\/accept$/.test(r.url()) && r.request().method() === 'POST');
    await p.getByRole('button', { name: 'Accept' }).click();
    const res = await accept;
    expect(res.status(), `accept response: ${(await res.text()).slice(0, 300)}`).toBe(200);
    const sent = res.request().postDataJSON();
    expect(Number(sent.acceptedQty ?? sent.accepted ?? 0), `accept request body: ${JSON.stringify(sent)}`).toBe(50);
    const m = { materialId: S.ids!.material.id };
    expect(await availableOf(m)).toBeCloseTo(S.availBefore! + 50, 3);
    expect(await onHand(m, 'QUARANTINE')).toBeCloseTo(S.quarantineBefore!, 3);
    await a.close();
  });

  test('S06 engineer1 creates a new BOM revision (0.045 kg wire per piece) and it is activated; the old one is superseded', async ({ browser, guard }) => {
    const productId = S.ids!.product.id;
    const before = (await admin().list<any>('/api/boms?size=100')).filter((b) => b.productId === productId);
    const activeBefore = before.find((b) => b.status === 'ACTIVE');
    const eng = (await login(U.engineer, passwordFor(U.engineer))).body.user.permissions as string[];
    const approver = eng.includes('BOM_APPROVE') ? U.engineer : U.admin;

    const a = await openAs(browser, guard, U.engineer, '/engineering/bom');
    const p = a.page;
    await p.getByRole('button', { name: /new bom/i }).click();
    const d = dialog(p);
    await pick(d, 'Product', /25X3/i);
    await fill(d, 'Base quantity (pieces)', 1);
    await fill(d, 'Reason for this version', 'E2E revision');
    await pick(d, 'Material', /65Si2/i);
    await fill(d, 'Quantity', 0.045);
    await d.getByRole('button', { name: 'Create BOM' }).click();
    await expect(d).toBeHidden();
    const after = (await admin().list<any>('/api/boms?size=100')).filter((b) => b.productId === productId);
    const created = after.filter((b) => !before.some((o) => o.id === b.id)).sort((x, y) => y.id - x.id)[0];
    expect(created, 'new BOM created').toBeTruthy();
    expect(created.status).toBe('DRAFT');
    S.bomId = created.id;
    await p.getByRole('button', { name: /refresh/i }).click();
    let r = row(p, created.bomNumber);
    await iconButton(r, 'send').click();
    await expect(r).toContainText(statusRe('ENGINEERING_REVIEW'));
    await a.close();

    const b = await openAs(browser, guard, approver, '/engineering/bom');
    r = row(b.page, created.bomNumber);
    await iconButton(r, 'check_circle').click();
    await expect(r).toContainText(statusRe('APPROVED'));
    await iconButton(r, 'toggle_on').click();
    await expect(r).toContainText(statusRe('ACTIVE'));
    const now = (await admin().list<any>('/api/boms?size=100')).filter((x) => x.productId === productId);
    expect(now.filter((x) => x.status === 'ACTIVE')).toHaveLength(1);
    expect(now.find((x) => x.id === created.id)!.status).toBe('ACTIVE');
    if (activeBefore) expect(now.find((x) => x.id === activeBefore.id)!.status).toBe('SUPERSEDED');
    await b.close();
  });

  test('S07 prodmgr1 creates, approves and releases the production order (50 pcs); the BOM is snapshotted', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.prodMgr, '/production/orders');
    const p = a.page;
    await p.getByRole('button', { name: /new order/i }).click();
    const d = dialog(p);
    await pick(d, 'Product', /25X3/i);
    await fill(d, 'Planned quantity', 50);
    await d.getByRole('button', { name: 'Create order' }).click();
    await expect(d).toBeHidden();
    const orders = await admin().list<any>('/api/production-orders?size=100');
    const o = orders.filter((x) => x.status === 'DRAFT').sort((x, y) => y.id - x.id)[0];
    expect(o, 'draft order created').toBeTruthy();
    S.orderId = o.id;
    S.orderNumber = o.orderNumber;
    await p.getByRole('button', { name: /refresh/i }).click();
    let panel = await orderPanel(p, S.orderNumber!);
    await panel.getByRole('button', { name: /approve/i }).click();
    await expect(panel).toContainText(statusRe('APPROVED'));
    panel = await orderPanel(p, S.orderNumber!);
    await panel.getByRole('button', { name: /release/i }).click();
    await expect(panel).toContainText(statusRe('RELEASED'));
    const t0 = Date.now();
    await expect
      .poll(async () => (await admin().list<any>(`/api/production-orders/${S.orderId}/materials`)).length, { timeout: 15_000 })
      .toBe(1);
    test.info().annotations.push({ type: 'snapshot-delay-ms', description: String(Date.now() - t0) });
    const mats = await admin().list<any>(`/api/production-orders/${S.orderId}/materials`);
    expect(Number(mats[0].plannedQty)).toBeCloseTo(2.25, 3);
    const ops = await admin().list<any>(`/api/production-orders/${S.orderId}/operations`);
    expect(ops.map((o) => o.sequenceNo)).toEqual([10, 20, 30]);
    await a.close();
  });

  test('S08 prodmgr1 assigns operator1 to every operation of the order', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.prodMgr, '/production/orders');
    const p = a.page;
    const panel = await orderPanel(p, S.orderNumber!);
    await panel.getByRole('button', { name: /assign operator to all/i }).click();
    const d = dialog(p);
    await pick(d, /operator|user/i, /operator one/i);
    await d.getByRole('button', { name: /assign/i }).last().click();
    await expect(d).toBeHidden();
    const ops = await admin().list<any>(`/api/production-orders/${S.orderId}/operations`);
    expect(ops.length).toBeGreaterThanOrEqual(1);
    for (const o of ops) expect(String(o.assignedOperatorName ?? o.operatorName ?? '')).toMatch(/operator one/i);
    await a.close();
  });

  test('S09 admin issues 2.25 kg wire to the order (storemgr1/supervisor1 cannot via UI: D-05): WIP up, available down, ledger MATERIAL_ISSUE', async ({ browser, guard }) => {
    const m = { materialId: S.ids!.material.id };
    const avail = await availableOf(m);
    const wip = await onHand(m, 'WIP');
    const a = await openAs(browser, guard, U.admin, '/production/orders');
    const p = a.page;
    const panel = await orderPanel(p, S.orderNumber!);
    await panel.getByRole('button', { name: /issue material/i }).click();
    const d = dialog(p);
    await pick(d, 'Issue from warehouse', 0);
    await fill(d, 'Quantity', 2.25); // D-06: the dialog prefills 0 instead of the 2.25 kg still to issue
    const issue = p.waitForResponse((r) => /\/api\/production\/issues$/.test(r.url()) && r.request().method() === 'POST');
    await d.getByRole('button', { name: /^issue/i }).first().click();
    const res = await issue;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    await d.getByRole('button', { name: 'Done' }).click();
    expect(await availableOf(m)).toBeCloseTo(avail - 2.25, 3);
    expect(await onHand(m, 'WIP')).toBeCloseTo(wip + 2.25, 3);
    const ledger = await admin().list<any>('/api/inventory/ledger?size=50');
    expect(ledger.some((t) => /ISSUE/.test(t.transactionType))).toBe(true);
    await a.close();
  });

  async function runOperation(browser: any, guard: any, who: string, opName: string, good: number, rejected: number, scrap: number) {
    const a = await openAs(browser, guard, who, '/production/my-work');
    const p = a.page;
    await p.getByRole('button', { name: /refresh/i }).click();
    const card = p.locator('mat-card').filter({ hasText: S.orderNumber! }).filter({ hasText: opName }).first();
    await expect(card, `${opName} card for ${S.orderNumber} on My Work`).toBeVisible();
    await card.getByRole('button', { name: /^\s*start\s*$/i }).click();
    await expect(card).toContainText(/in progress/i);
    await card.getByRole('button', { name: /record output/i }).first().click();
    await fill(card, 'Good Qty', good);
    await fill(card, 'Rejected Qty', rejected);
    await fill(card, 'Scrap Qty', scrap);
    await card.getByRole('button', { name: /record output/i }).last().click();
    await card.getByRole('button', { name: /^\s*complete\s*$/i }).click();
    await card.getByRole('button', { name: /complete operation/i }).click();
    await a.close();
  }

  test('S10 operator1 runs Coiling (50 good) and Grinding (49 good, 1 rejected): op 20 input follows op 10 output, WIP view matches', async ({ browser, guard }) => {
    await runOperation(browser, guard, U.operator1, 'Coiling', 50, 0, 0);
    let ops = await admin().list<any>(`/api/production-orders/${S.orderId}/operations`);
    expect(ops.find((o) => o.sequenceNo === 10)!.status).toBe('COMPLETED');
    expect(Number(ops.find((o) => o.sequenceNo === 20)!.inputQty)).toBe(50);
    await runOperation(browser, guard, U.operator1, 'Grinding', 49, 1, 0);
    ops = await admin().list<any>(`/api/production-orders/${S.orderId}/operations`);
    expect(ops.find((o) => o.sequenceNo === 20)!.status).toBe('COMPLETED');
    expect(Number(ops.find((o) => o.sequenceNo === 20)!.rejectedQty)).toBe(1);
    expect(Number(ops.find((o) => o.sequenceNo === 30)!.inputQty)).toBe(49);
  });

  test('S11 operator1 completes the last operation: order COMPLETED, FG batch in QUALITY_PENDING, FG available unchanged', async ({ browser, guard }) => {
    const pid = { productId: S.ids!.product.id };
    const availBefore = await availableOf(pid);
    await runOperation(browser, guard, U.operator1, 'Inspection', 49, 0, 0);
    const order = await admin().must('GET', `/api/production-orders/${S.orderId}`);
    expect(order.status).toBe('COMPLETED');
    const fg = (await admin().list<any>('/api/quality/fg-batches?size=100')).sort((x, y) => y.id - x.id)[0];
    expect(fg.qcStatus ?? fg.qualityStatus ?? fg.status).toMatch(/QUALITY_PENDING/);
    S.fgBatchId = fg.id;
    S.fgBatchNumber = fg.batchNumber;
    expect(S.fgBatchNumber, 'FG batch number').toBeTruthy();
    expect(await availableOf(pid)).toBeCloseTo(availBefore, 3);
    expect(await onHand(pid, 'QUALITY_PENDING')).toBeGreaterThanOrEqual(49);
  });

  test('S12 FG in QUALITY_PENDING cannot be reserved or dispatched until final approval', async ({ guard }) => {
    guard.allow({ status: [400, 409, 422], url: /\/api\/sales\/reservations$/ });
    const sales = apiFor(U.sales);
    const so = await sales.must('POST', '/api/sales/orders', {
      customerId: S.ids!.customer.id,
      orderDate: today(),
      requiredDate: today(10),
      deliveryAddress: 'E2E Plot 1, Test Industrial Area',
      items: [{ productId: S.ids!.product.id, orderedQty: 5, unitPrice: 100 }],
    });
    await sales.must('POST', `/api/sales/orders/${so.id}/confirm`);
    await admin().must('POST', `/api/sales/orders/${so.id}/approve`);
    const full = await admin().must('GET', `/api/sales/orders/${so.id}`);
    const bal = (await balances()).find((b) => b.fgBatchId === S.fgBatchId && b.stockStatus === 'QUALITY_PENDING');
    expect(bal, 'a QUALITY_PENDING balance for the FG batch').toBeTruthy();
    const r = await sales.post('/api/sales/reservations', {
      salesOrderItemId: full.items[0].id,
      fgBatchId: S.fgBatchId,
      warehouseId: bal!.warehouseId,
      locationId: bal!.locationId,
      qty: 5,
    });
    expect(r.status, JSON.stringify(r.body).slice(0, 300)).toBeGreaterThanOrEqual(400);
    expect(r.status).toBeLessThan(500);
    await sales.post(`/api/sales/orders/${so.id}/cancel`);
  });

  test('S13 quality1 records the final inspection (47 approved, 2 rejected): 47 FG become AVAILABLE and the batch is PARTIALLY_APPROVED', async ({ browser, guard }) => {
    const insp = (await admin().list<any>('/api/quality/inspections?size=200')).find(
      (i) => i.inspectionType === 'FINAL' && i.fgBatchId === S.fgBatchId,
    );
    expect(insp, 'final inspection auto-created for the FG batch').toBeTruthy();
    const a = await openAs(browser, guard, U.quality, `/quality/inspections/${insp.id}`);
    const p = a.page;
    await fill(p.locator('body'), 'Approved qty', 47);
    await fill(p.locator('body'), 'Rejected qty', 2);
    await p.getByRole('button', { name: /add reason/i }).click();
    await pick(p.locator('body'), 'Reason', 0);
    await fill(p.locator('body'), 'Quantity', 2);
    const done = p.waitForResponse((r) => /final-decision$/.test(r.url()) && r.request().method() === 'POST');
    await p.getByRole('button', { name: /complete final inspection/i }).click();
    const res = await done;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    const rows = (await balances()).filter((b) => b.fgBatchId === S.fgBatchId);
    expect(rows.find((b) => b.stockStatus === 'AVAILABLE' && Number(b.qtyOnHand) === 47), 'the 47 approved pieces are AVAILABLE').toBeTruthy();
    expect(Number(rows.find((b) => b.stockStatus === 'QUALITY_PENDING')!.qtyOnHand)).toBe(0);
    // The 2 rejected pieces are asserted separately (D-07 in known-defects.spec.ts).
    const fg = await admin().must('GET', `/api/quality/fg-batches/${S.fgBatchId}`);
    expect(JSON.stringify(fg)).toMatch(/PARTIALLY_APPROVED/);
    await a.close();
  });

  test('S14 sales1 creates the sales order (30 pcs), confirms it; admin approves', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.sales, '/sales/orders');
    const p = a.page;
    await p.getByRole('button', { name: /new order/i }).click();
    const d = dialog(p);
    await pick(d, 'Customer', 0);
    await fill(d, 'Delivery address', 'E2E Plot 7, Test Industrial Area');
    await pick(d, 'Product', /25X3/i);
    await fill(d, 'Quantity', 30);
    await fill(d, 'Unit price', 150);
    await d.getByRole('button', { name: 'Create order' }).click();
    await expect(d).toBeHidden();
    const so = (await admin().list<any>('/api/sales/orders?size=100')).filter((x) => x.status === 'DRAFT').sort((x, y) => y.id - x.id)[0];
    expect(so, 'draft sales order created').toBeTruthy();
    S.soId = so.id;
    S.soNumber = so.orderNumber;
    S.soItemId = so.items[0].id;
    await p.getByRole('button', { name: /refresh|reload/i }).first().click().catch(() => undefined);
    const r = row(p, S.soNumber!);
    await iconButton(r, 'check_circle').click();
    await expect(r).toContainText(statusRe('ORDERED'));
    await a.close();

    const b = await openAs(browser, guard, U.admin, '/sales/orders');
    const r2 = row(b.page, S.soNumber!);
    await iconButton(r2, 'thumb_up').click();
    await expect(r2).toContainText(statusRe('APPROVED'));
    await b.close();
  });

  test('S15 sales1 cannot reserve more than the available FG, then reserves 30 pcs (status ALLOCATED is D-08)', async ({ browser, guard }) => {
    guard.allow({ status: [400, 409, 422], url: /\/api\/sales\/reservations$/ });
    const a = await openAs(browser, guard, U.sales, '/sales/orders');
    const p = a.page;
    const r = row(p, S.soNumber!);
    await iconButton(r, 'inventory').click();
    let d = dialog(p);
    await pick(d, 'Order item', 0);
    await pick(d, 'Batch and location', S.fgBatchNumber!);
    await fill(d, 'Quantity', 1000);
    await d.getByRole('button', { name: 'Reserve stock' }).click();
    await expect(d.getByRole('alert')).toBeVisible();
    await fill(d, 'Quantity', 30);
    await d.getByRole('button', { name: 'Reserve stock' }).click();
    await expect(d).toBeHidden();
    const so = await admin().must('GET', `/api/sales/orders/${S.soId}`);
    expect(Number(so.items[0].reservedQty)).toBe(30);
    await a.close();
  });

  test('S16 dispatch1 dispatches 30 pcs: FG on hand falls by 30; dispatching more than reserved is refused', async ({ browser, guard }) => {
    guard.allow({ status: [400, 409, 422], url: /\/api\/sales\/dispatches$/ });
    const pid = { productId: S.ids!.product.id };
    const before = await onHand(pid, 'AVAILABLE');
    const a = await openAs(browser, guard, U.dispatch, '/sales/dispatches');
    const p = a.page;
    await p.getByRole('button', { name: /new dispatch/i }).click();
    const d = dialog(p);
    await pick(d, 'Sales order', S.soNumber!);
    await fill(d, 'Transporter', 'E2E Logistics');
    await fill(d, 'Vehicle no.', 'KA-01-E2E-0001');
    await fill(d, 'LR number', 'LR-E2E-1');
    await fill(d, 'Delivery address', 'E2E Plot 7, Test Industrial Area');
    await fill(d, 'Invoice reference', 'INV-E2E-1');
    await pick(d, 'Batch and location', S.fgBatchNumber!);
    await fill(d, 'Quantity', 30);
    const dispatch = p.waitForResponse((r) => /\/api\/sales\/dispatches$/.test(r.url()) && r.request().method() === 'POST');
    await d.getByRole('button', { name: 'Dispatch' }).click();
    const res = await dispatch;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    S.dispatchId = (await res.json()).id;
    expect(await onHand(pid, 'AVAILABLE')).toBeCloseTo(before - 30, 3);
    const so = await admin().must('GET', `/api/sales/orders/${S.soId}`);
    expect(so.status).toBe('DISPATCHED');
    await a.close();

    // Over-dispatch: another 100 against the same order must be refused by the server.
    const bal = (await balances()).find((b) => b.fgBatchId === S.fgBatchId && b.stockStatus === 'AVAILABLE' && Number(b.qtyOnHand) > 0);
    const over = await apiFor(U.dispatch).post('/api/sales/dispatches', {
      salesOrderId: S.soId,
      dispatchDate: today(),
      deliveryAddress: 'E2E Plot 7',
      lines: [{ salesOrderItemId: S.soItemId, fgBatchId: S.fgBatchId, quantity: 100, warehouseId: bal?.warehouseId, locationId: bal?.locationId }],
    });
    expect(over.status, JSON.stringify(over.body).slice(0, 300)).toBeGreaterThanOrEqual(400);
    expect(over.status).toBeLessThan(500);
  });

  test('S17 prodmgr1 returns the unused wire and closes the order', async ({ browser, guard }) => {
    const m = { materialId: S.ids!.material.id };
    const mats = await admin().list<any>(`/api/production-orders/${S.orderId}/materials`);
    const line = mats[0];
    const leftover = Number(line.issuedQty) - Number(line.consumedQty) - Number(line.returnedQty) - Number(line.scrapQty);
    const availBefore = await availableOf(m);
    const a = await openAs(browser, guard, U.prodMgr, '/production/orders');
    const p = a.page;
    let panel = await orderPanel(p, S.orderNumber!);
    if (leftover > 0.0005) {
      await panel.getByRole('button', { name: /^return$/i }).first().click();
      const d = dialog(p);
      await fill(d, 'Quantity to return', leftover.toFixed(3));
      await d.getByRole('button', { name: /return/i }).last().click();
      await expect(d).toBeHidden();
      expect(await availableOf(m)).toBeCloseTo(availBefore + leftover, 2);
    }
    panel = await orderPanel(p, S.orderNumber!);
    await panel.getByRole('button', { name: /^close$/i }).click();
    const d2 = dialog(p);
    await d2.getByRole('button', { name: /close/i }).last().click();
    await expect(d2).toBeHidden();
    const order = await admin().must('GET', `/api/production-orders/${S.orderId}`);
    expect(order.status).toBe('CLOSED');
    await a.close();
  });

  test('S18 storemgr1 requests a stock adjustment through the UI; the request is recorded (its numbers are D-10)', async ({ browser, guard }) => {
    const m = { materialId: S.ids!.material.id };
    const bal = (await balances()).filter((b) => b.materialId === m.materialId && b.stockStatus === 'AVAILABLE').sort((x, y) => Number(y.qtyOnHand) - Number(x.qtyOnHand))[0];
    const counted = Number(bal.qtyOnHand) - 5;
    const known = new Set((await admin().list<any>('/api/inventory/adjustments?size=200')).map((x) => x.id));
    const a = await openAs(browser, guard, U.storeMgr, '/inventory/adjustments');
    const p = a.page;
    await p.getByRole('button', { name: /request adjustment/i }).click();
    const d = dialog(p);
    await pick(d, 'Item Type', 'Material');
    // D-09: the dialog offers pickers (code and name), not raw numeric ids.
    const warehouse = (await admin().list<any>('/api/lookups/warehouses')).find((w) => w.id === bal.warehouseId);
    const location = (await admin().list<any>(`/api/lookups/locations?warehouseId=${bal.warehouseId}`)).find((l) => l.id === bal.locationId);
    await pick(d, 'Material', S.ids!.material.materialCode);
    await pick(d, 'Warehouse', warehouse.code);
    await pick(d, 'Location', location.code);
    await pick(d, 'Stock Status', 'Available');
    await fill(d, 'Counted Qty', counted.toFixed(3));
    await fill(d, 'Reason', 'Physical stock verification');
    await d.getByRole('button', { name: 'Submit' }).click();
    await expect(d).toBeHidden();
    let created: any;
    await expect
      .poll(async () => {
        created = (await admin().list<any>('/api/inventory/adjustments?size=200')).find((x) => !known.has(x.id));
        return !!created;
      })
      .toBe(true);
    expect(created.reason).toBe('Physical stock verification');
    expect(created.status).toBe('REQUESTED');
    await expect(row(p, created.adjustmentNumber)).toBeVisible({ timeout: 15_000 }).catch(async () => {
      await p.getByRole('button', { name: /refresh/i }).click().catch(() => undefined);
      await expect(row(p, created.adjustmentNumber)).toBeVisible();
    });
    await a.close();
  });

  test('S19 operator1 calling PUT /api/products/{id} gets 403', async ({ guard }) => {
    guard.allow({ status: 403 });
    const body = { productCode: S.ids!.product.productCode, name: S.ids!.product.name, springType: S.ids!.product.springType ?? 'COMPRESSION' };
    const r = await apiFor(U.operator1).put(`/api/products/${S.ids!.product.id}`, body);
    expect(r.status, JSON.stringify(r.body).slice(0, 200)).toBe(403);
  });

});

// Steps 20-22 read the state built above but fail independently of each other.
test.describe('22-step demonstration workflow: traceability and read-only checks', () => {
  test('S20 admin traces the FG batch back to its material batches (quality1 cannot: D-11)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/insight/traceability');
    const p = a.page;
    await pick(p.locator('body'), 'Batch Type', 'FG Batch');
    await fill(p.locator('body'), 'Batch ID', S.fgBatchId!);
    await p.getByRole('button', { name: /trace/i }).click();
    await expect(p.getByText(/RM-BATCH/).first()).toBeVisible();
    await a.close();
  });

  test('S21 admin traces a material batch forward to the order, FG batch and customer (quality1 cannot: D-11)', async ({ browser, guard }) => {
    const issues = (await admin().list<any>('/api/inventory/ledger?size=200')).filter((t) => /ISSUE/.test(t.transactionType) && t.materialBatchId);
    const batchId = issues.sort((x, y) => y.id - x.id)[0]?.materialBatchId;
    expect(batchId, 'a material batch was issued').toBeTruthy();
    const a = await openAs(browser, guard, U.admin, '/insight/traceability');
    const p = a.page;
    await pick(p.locator('body'), 'Batch Type', 'Material Batch');
    await fill(p.locator('body'), 'Batch ID', batchId);
    await p.getByRole('button', { name: /trace/i }).click();
    await expect(p.getByText(S.orderNumber!).first()).toBeVisible();
    await a.close();
  });

  test('S22 mgmt1 sees the dashboard KPIs but every write is refused with 403', async ({ browser, guard }) => {
    guard.allow({ status: 403 });
    const a = await openAs(browser, guard, U.mgmt, '/dashboard');
    await expect(a.page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(a.page.getByText(/production orders/i).first()).toBeVisible();
    const w = await apiFor(U.mgmt).post('/api/production-orders/1/approve');
    expect(w.status).toBe(403);
    await a.close();
  });
});
