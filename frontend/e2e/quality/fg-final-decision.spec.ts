/**
 * The FG batch final decision and the sales-order cancellation used to ask for their values with the browser's native
 * prompt(), which cannot be automated, cannot validate and looks nothing like the rest of the application. Both are
 * Material dialogs now. These specs drive them through the real screens, check the business state afterwards from
 * the API and the ledger, and fail if the browser ever opens a native dialog again.
 */
import { admin } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { approvedSalesOrder, balances, produceViaApi } from '../support/flow';
import { dialog, fill, iconButton, row } from '../support/ui';
import { U } from '../support/users';

const qtyOf = (rows: any[], batchId: number, status: string): number =>
  rows
    .filter((b) => b.fgBatchId === batchId && b.stockStatus === status)
    .reduce((n, b) => n + Number(b.qtyOnHand), 0);

test.describe('FG final decision dialog (UAT QCF-02 with a HOLD remainder)', () => {
  test('the final decision is entered in a dialog: produced 10, approved 6, rejected 2, the remaining 2 go on HOLD', async ({
    browser,
    guard,
  }) => {
    const made = await produceViaApi(10);

    const q = await openAs(browser, guard, U.quality, '/quality/fg-batches');
    const p = q.page;
    const nativeDialogs: string[] = [];
    p.on('dialog', (d) => {
      nativeDialogs.push(`${d.type()}: ${d.message()}`);
      void d.dismiss();
    });

    await iconButton(row(p, made.fgBatchNumber), 'fact_check').click();
    const d = dialog(p);
    await expect(d.getByText(/10\.000 produced/)).toBeVisible();
    await expect(d.getByTestId('hold-preview')).toContainText('10.000');

    // approved + rejected above what was produced is refused on the spot, with a message, and nothing is sent
    await fill(d, /^Quantity approved/, 7);
    await fill(d, /^Quantity rejected/, 4);
    await expect(d.getByTestId('over-allocated')).toBeVisible();
    await d.getByRole('button', { name: 'Record decision' }).click();
    await expect(d, 'the dialog stays open while the split is invalid').toBeVisible();

    // the real split: the hold remainder is previewed before saving
    await fill(d, /^Quantity approved/, 6);
    await fill(d, /^Quantity rejected/, 2);
    await expect(d.getByTestId('over-allocated')).toBeHidden();
    await expect(d.getByTestId('hold-preview')).toContainText('2.000');
    await fill(d, /^Remarks/, 'E2E final decision 6/2/2');
    const sent = p.waitForResponse((r) => /final-decision$/.test(r.url()) && r.request().method() === 'POST');
    await d.getByRole('button', { name: 'Record decision' }).click();
    const res = await sent;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    expect(res.request().postDataJSON(), 'the dialog sends the split it previewed').toMatchObject({
      approvedQty: 6,
      rejectedQty: 2,
      holdQty: 2,
    });
    await expect(d).toBeHidden();
    await q.close();

    expect(nativeDialogs, 'no native prompt/confirm/alert may open').toEqual([]);

    const fg = await admin().must('GET', `/api/quality/fg-batches/${made.fgBatchId}`);
    expect(Number(fg.approvedQty), 'approved quantity on the batch').toBe(6);
    expect(Number(fg.rejectedQty), 'rejected quantity on the batch').toBe(2);
    const rows = await balances();
    expect(qtyOf(rows, made.fgBatchId, 'AVAILABLE'), '6 approved units are AVAILABLE stock').toBe(6);
    expect(qtyOf(rows, made.fgBatchId, 'HOLD'), '2 held units sit in HOLD stock').toBe(2);
  });

  test('the dialog can be dismissed without recording anything', async ({ browser, guard }) => {
    const made = await produceViaApi(4);
    const q = await openAs(browser, guard, U.quality, '/quality/fg-batches');
    const p = q.page;
    await iconButton(row(p, made.fgBatchNumber), 'fact_check').click();
    const d = dialog(p);
    await fill(d, /^Quantity approved/, 4);
    await d.getByRole('button', { name: 'Cancel' }).click();
    await expect(d).toBeHidden();
    await q.close();

    const fg = await admin().must('GET', `/api/quality/fg-batches/${made.fgBatchId}`);
    expect(fg.qualityStatus, 'the batch is still waiting for its inspection').toBe('QUALITY_PENDING');
  });
});

test.describe('sales order cancellation dialog', () => {
  test('cancelling an order asks for the reason in a dialog and records it', async ({ browser, guard }) => {
    const so = await approvedSalesOrder(3);
    const s = await openAs(browser, guard, U.sales, '/sales/orders');
    const p = s.page;
    const nativeDialogs: string[] = [];
    p.on('dialog', (d) => {
      nativeDialogs.push(`${d.type()}: ${d.message()}`);
      void d.dismiss();
    });

    await iconButton(row(p, so.orderNumber), 'cancel').click();
    const d = dialog(p);
    await expect(d.getByRole('heading', { name: new RegExp(`Cancel order ${so.orderNumber}`) })).toBeVisible();

    // a blank reason is refused and nothing is sent
    await d.getByRole('button', { name: 'Cancel order' }).click();
    await expect(d.getByText('A reason is required')).toBeVisible();

    await fill(d, /^Cancellation reason/, 'E2E: customer withdrew the order');
    const sent = p.waitForResponse((r) => /\/cancel$/.test(r.url()) && r.request().method() === 'POST');
    await d.getByRole('button', { name: 'Cancel order' }).click();
    const res = await sent;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    expect(res.request().postDataJSON()).toMatchObject({ reason: 'E2E: customer withdrew the order' });
    await expect(d).toBeHidden();
    await s.close();

    expect(nativeDialogs, 'no native prompt/confirm/alert may open').toEqual([]);
    const order = await admin().must('GET', `/api/sales/orders/${so.id}`);
    expect(order.status).toBe('CANCELLED');
  });
});
