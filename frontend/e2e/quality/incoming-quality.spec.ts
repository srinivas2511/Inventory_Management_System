import { admin } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { demoIds, onHand, receiveViaApi } from '../support/flow';
import { U } from '../support/users';
import { fill } from '../support/ui';

test.describe('Incoming quality (UAT QCI-01 .. QCI-06)', () => {
  test('QCI-01 the pending inspection for a new goods receipt is on the quality worklist', async ({ browser, guard }) => {
    const rec = await receiveViaApi(8);
    const insp = await admin().must('GET', `/api/quality/inspections/${rec.inspectionId}`);
    const a = await openAs(browser, guard, U.quality, '/quality/inspections');
    await expect(a.page.getByRole('heading', { name: 'Quality Inspections' })).toBeVisible();
    await expect(a.page.getByRole('row').filter({ hasText: insp.inspectionNumber }).first()).toBeVisible();
    await a.close();
  });

  test('QCI-03 a partial decision (4 accepted, 2 rejected) makes 4 AVAILABLE and 2 REJECTED stock; the batch is decided', async ({ browser, guard }) => {
    const { material } = await demoIds();
    const m = { materialId: material.id };
    const availBefore = await onHand(m, 'AVAILABLE');
    const rejectedBefore = await onHand(m, 'REJECTED');
    const rec = await receiveViaApi(6);
    const a = await openAs(browser, guard, U.quality, `/quality/inspections/${rec.inspectionId}`);
    const p = a.page;
    await fill(p.locator('body'), 'Accepted qty', 4);
    await fill(p.locator('body'), 'Rejected qty', 2);
    await fill(p.locator('body'), 'Rejection reason', 'E2E surface defect');
    const accept = p.waitForResponse((r) => /\/inspections\/\d+\/accept$/.test(r.url()) && r.request().method() === 'POST');
    await p.getByRole('button', { name: 'Accept' }).click();
    const res = await accept;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    expect(await onHand(m, 'AVAILABLE')).toBeCloseTo(availBefore + 4, 3);
    expect(await onHand(m, 'REJECTED')).toBeCloseTo(rejectedBefore + 2, 3);
    await a.close();
  });

  test('QCI-03b rejecting the whole lot leaves available stock unchanged and the batch REJECTED', async ({ browser, guard }) => {
    const { material } = await demoIds();
    const m = { materialId: material.id };
    const availBefore = await onHand(m, 'AVAILABLE');
    const rejectedBefore = await onHand(m, 'REJECTED');
    const rec = await receiveViaApi(5);
    const a = await openAs(browser, guard, U.quality, `/quality/inspections/${rec.inspectionId}`);
    const p = a.page;
    await fill(p.locator('body'), 'Rejected qty', 5);
    await fill(p.locator('body'), 'Rejection reason', 'E2E wrong grade');
    const reject = p.waitForResponse((r) => /\/inspections\/\d+\/reject$/.test(r.url()) && r.request().method() === 'POST');
    await p.getByRole('button', { name: 'Reject' }).click();
    const res = await reject;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    expect(await onHand(m, 'AVAILABLE')).toBeCloseTo(availBefore, 3);
    expect(await onHand(m, 'REJECTED')).toBeCloseTo(rejectedBefore + 5, 3);
    const batch = (await admin().list<any>('/api/inventory/batches?size=500')).find((b) => b.id === rec.batchId);
    expect(batch.status).toBe('REJECTED');
    await a.close();
  });

  test('QCI-06 a quality user cannot use production endpoints (403) but can read inspections', async ({ guard }) => {
    guard.allow({ status: 403 });
    const { Api } = await import('../support/api');
    const q = new Api(U.quality);
    expect((await q.post('/api/production-orders/1/release')).status).toBe(403);
    expect((await q.post('/api/production/operations/1/start')).status).toBe(403);
    expect((await q.get('/api/quality/inspections')).status).toBe(200);
  });
});
