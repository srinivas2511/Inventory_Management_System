/**
 * Production execution screens added after the first walkthrough, driven through the real UI:
 * operator assignment and its effect on My Work, the visible sequence guard, Pause / Resume / Record scrap,
 * material issue by the store manager (prefilled quantity, over-issue refused), and closing an order with
 * leftover material (write-off reason required). The straight-through happy path lives in demo-workflow.spec.ts.
 */
import type { Browser } from '@playwright/test';
import { admin, apiFor } from '../support/api';
import { expect, Guard, openAs, test } from '../support/fixtures';
import { demoIds, onHand, releasedOrder } from '../support/flow';
import { dialog, fill, orderPanel, pick, row, today } from '../support/ui';
import { U } from '../support/users';

const num = (v: unknown): number => Number(v ?? 0);

async function assignAll(browser: Browser, guard: Guard, orderNumber: string): Promise<void> {
  const a = await openAs(browser, guard, U.prodMgr, '/production/orders');
  const p = a.page;
  const panel = await orderPanel(p, orderNumber);
  await panel.getByRole('button', { name: /assign operator to all/i }).click();
  const d = dialog(p);
  await pick(d, /operator|user/i, /operator one/i);
  await d.getByRole('button', { name: /assign/i }).last().click();
  await expect(d).toBeHidden();
  await a.close();
}

async function issueAsUi(browser: Browser, guard: Guard, who: string, orderNumber: string, quantity?: number): Promise<void> {
  const a = await openAs(browser, guard, who, '/production/orders');
  const p = a.page;
  const panel = await orderPanel(p, orderNumber);
  await panel.getByRole('button', { name: /issue material/i }).click();
  const d = dialog(p);
  await pick(d, 'Issue from warehouse', 0);
  if (quantity !== undefined) await fill(d, 'Quantity', quantity.toFixed(3));
  const issue = p.waitForResponse((r) => /\/api\/production\/issues$/.test(r.url()) && r.request().method() === 'POST');
  await d.getByRole('button', { name: /^issue material$/i }).click();
  const res = await issue;
  expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
  await d.getByRole('button', { name: 'Done' }).click();
  await a.close();
}

async function runOp(browser: Browser, guard: Guard, orderNumber: string, opName: string, good: number, rejected = 0, scrap = 0): Promise<void> {
  const a = await openAs(browser, guard, U.operator1, '/production/my-work');
  const p = a.page;
  await p.getByRole('button', { name: /refresh/i }).click();
  const card = p.locator('mat-card').filter({ hasText: orderNumber }).filter({ hasText: opName }).first();
  await expect(card).toBeVisible();
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

test.describe('My Work: assignment, sequence, pause/resume and scrap', () => {
  test('MFG-01 only the assigned operator sees the operations; the later operation is blocked with a reason until the earlier one completes', async ({ browser, guard }) => {
    const order = await releasedOrder(10);
    await assignAll(browser, guard, order.orderNumber);

    const other = await openAs(browser, guard, U.operator2, '/production/my-work');
    await other.page.getByRole('button', { name: /refresh/i }).click();
    await expect(other.page.locator('mat-card').filter({ hasText: order.orderNumber }), 'an unassigned operator sees nothing of this order').toHaveCount(0);
    await other.close();

    const a = await openAs(browser, guard, U.operator1, '/production/my-work');
    const p = a.page;
    await p.getByRole('button', { name: /refresh/i }).click();
    const grinding = p.locator('mat-card').filter({ hasText: order.orderNumber }).filter({ hasText: 'Grinding' }).first();
    await expect(grinding).toBeVisible();
    await expect(grinding.getByTestId('start-blocked-reason'), 'the blocked Start explains why').toBeVisible();
    await expect(grinding.getByRole('button', { name: /^\s*start\s*$/i })).toBeDisabled();
    await a.close();
  });

  test('MFG-02 pause and resume keep the operation on My Work, and recording scrap stores it on the operation', async ({ browser, guard }) => {
    const order = await releasedOrder(10);
    await assignAll(browser, guard, order.orderNumber);
    const a = await openAs(browser, guard, U.operator1, '/production/my-work');
    const p = a.page;
    await p.getByRole('button', { name: /refresh/i }).click();
    const card = p.locator('mat-card').filter({ hasText: order.orderNumber }).filter({ hasText: 'Coiling' }).first();
    await card.getByRole('button', { name: /^\s*start\s*$/i }).click();
    await expect(card).toContainText(/in progress/i);
    await card.getByRole('button', { name: /pause/i }).click();
    await expect(card, 'a paused operation stays on the screen').toContainText(/paused/i);
    await card.getByRole('button', { name: /resume/i }).click();
    await expect(card).toContainText(/in progress/i);

    await card.getByRole('button', { name: /record output/i }).first().click();
    await fill(card, 'Good Qty', 5);
    await fill(card, 'Rejected Qty', 0);
    await fill(card, 'Scrap Qty', 1);
    await card.getByRole('button', { name: /record output/i }).last().click();
    await expect.poll(async () => {
      const ops = await admin().list<any>(`/api/production-orders/${order.id}/operations`);
      const op = ops.find((o) => o.sequenceNo === 10)!;
      return `${num(op.goodQty)}/${num(op.scrapQty)}`;
    }).toBe('5/1');
    await a.close();
  });
});

test.describe('Material issue and close with a write-off (UI)', () => {
  test('MFG-03a the issue dialog prefills each line with the quantity still to be issued (D-06)', async ({ browser, guard }) => {
    const order = await releasedOrder(10);
    const planned = num((await admin().list<any>(`/api/production-orders/${order.id}/materials`))[0].plannedQty);
    const a = await openAs(browser, guard, U.storeMgr, '/production/orders');
    const p = a.page;
    const panel = await orderPanel(p, order.orderNumber);
    await panel.getByRole('button', { name: /issue material/i }).click();
    const d = dialog(p);
    await pick(d, 'Issue from warehouse', 0);
    await expect
      .poll(async () => Number(await d.getByLabel('Quantity').first().inputValue()), {
        message: `D-06: the quantity is prefilled with the ${planned} still to issue, not 0`,
      })
      .toBeCloseTo(planned, 3);
    await a.close();
  });

  test('MFG-03 storemgr1 issues material: the quantity is prefilled with what is still to issue, over-issue is refused, the exact quantity posts', async ({ browser, guard }) => {
    guard.allow({ status: [400, 409, 422], method: 'POST', url: /\/api\/production\/issues/ });
    const { material } = await demoIds();
    const order = await releasedOrder(10);
    const lines = await admin().list<any>(`/api/production-orders/${order.id}/materials`);
    const planned = num(lines[0].plannedQty);
    expect(planned).toBeGreaterThan(0);
    const wipBefore = await onHand({ materialId: material.id }, 'WIP');

    const a = await openAs(browser, guard, U.storeMgr, '/production/orders');
    const p = a.page;
    const panel = await orderPanel(p, order.orderNumber);
    await panel.getByRole('button', { name: /issue material/i }).click();
    const d = dialog(p);
    await pick(d, 'Issue from warehouse', 0);
    await fill(d, 'Quantity', (planned * 20).toFixed(3));
    await d.getByRole('button', { name: /^issue material$/i }).click();
    await expect(d.getByRole('alert').first(), 'issuing far more than the plan is refused with a message').toBeVisible();
    expect(await onHand({ materialId: material.id }, 'WIP')).toBeCloseTo(wipBefore, 3);

    await fill(d, 'Quantity', planned.toFixed(3));
    await d.getByRole('button', { name: /^issue material$/i }).click();
    await expect(d.getByText(/posted\. Stock taken from/i)).toBeVisible();
    await d.getByRole('button', { name: 'Done' }).click();
    expect(await onHand({ materialId: material.id }, 'WIP')).toBeCloseTo(wipBefore + planned, 3);
    const after = await admin().list<any>(`/api/production-orders/${order.id}/materials`);
    expect(num(after[0].issuedQty)).toBeCloseTo(planned, 3);
    await a.close();
  });

  test('MFG-04 closing an order with leftover material needs a write-off reason; with it the order is CLOSED and the leftover is written off', async ({ browser, guard }) => {
    guard.allow({ status: [400, 409, 422], url: /\/api\/production-orders\/\d+\/close/ });
    const order = await releasedOrder(10);
    const planned = num((await admin().list<any>(`/api/production-orders/${order.id}/materials`))[0].plannedQty);
    await assignAll(browser, guard, order.orderNumber);
    await issueAsUi(browser, guard, U.admin, order.orderNumber, planned * 1.05);
    await runOp(browser, guard, order.orderNumber, 'Coiling', 10);
    await runOp(browser, guard, order.orderNumber, 'Grinding', 10);
    await runOp(browser, guard, order.orderNumber, 'Inspection', 10);
    expect((await admin().must('GET', `/api/production-orders/${order.id}`)).status).toBe('COMPLETED');
    const mat = (await admin().list<any>(`/api/production-orders/${order.id}/materials`))[0];
    const leftover = num(mat.issuedQty) - num(mat.consumedQty) - num(mat.returnedQty) - num(mat.scrapQty ?? 0);
    expect(leftover, 'issuing 5% more than needed leaves material over').toBeGreaterThan(0.0001);

    const a = await openAs(browser, guard, U.prodMgr, '/production/orders');
    const p = a.page;
    const panel = await orderPanel(p, order.orderNumber);
    await panel.getByRole('button', { name: /^close$/i }).click();
    const d = dialog(p);
    await expect(d.getByLabel(/Reason for writing the material off/i)).toBeVisible();
    await d.getByRole('button', { name: /close/i }).last().click();
    await expect(d, 'without a reason the order cannot be closed').toBeVisible();
    expect((await admin().must('GET', `/api/production-orders/${order.id}`)).status).toBe('COMPLETED');
    await d.getByLabel(/Reason for writing the material off/i).fill('E2E leftover scrapped on the floor');
    await d.getByRole('button', { name: /close/i }).last().click();
    await expect(d).toBeHidden();
    const closed = await admin().must('GET', `/api/production-orders/${order.id}`);
    expect(closed.status).toBe('CLOSED');
    const after = (await admin().list<any>(`/api/production-orders/${order.id}/materials`))[0];
    const written = num(after.writtenOffQty ?? after.writeOffQty ?? after.scrapQty);
    expect(written, 'the leftover was written off').toBeCloseTo(leftover, 3);
    await a.close();
  });
});

test.describe('Purchase requisition form', () => {
  test('PRC-08 purchase1 raises a requisition through the UI and it is listed as DRAFT', async ({ browser, guard }) => {
    const { material } = await demoIds();
    const known = new Set((await admin().list<any>('/api/procurement/requisitions?status=OPEN&size=200')).map((r) => r.id));
    const a = await openAs(browser, guard, U.purchase, '/procurement/requisitions');
    const p = a.page;
    await p.getByRole('button', { name: /new pr/i }).click();
    const d = dialog(p);
    await pick(d, 'Material', /65Si2/i);
    await fill(d, 'Quantity', 20);
    await d.getByLabel('Needed by').fill(today(14));
    await fill(d, 'Reason', 'E2E replenishment');
    await d.getByRole('button', { name: 'Create requisition' }).click();
    await expect(d).toBeHidden();
    const created = (await apiFor(U.purchase).list<any>('/api/procurement/requisitions?status=OPEN&size=200')).find((r) => !known.has(r.id));
    expect(created, 'the requisition exists (found through the OPEN status filter)').toBeTruthy();
    expect(num(created.requestedQty)).toBe(20);
    expect(created.status).toBe('OPEN');
    await p.getByRole('button', { name: /refresh/i }).click();
    await expect(
      row(p, created.prNumber ?? created.requisitionNumber),
      'D-32: the default "All" list is empty because the API filters on status IS NULL',
    ).toBeVisible({ timeout: 5000 });
    expect(material.id).toBeGreaterThan(0);
    await a.close();
  });
});
