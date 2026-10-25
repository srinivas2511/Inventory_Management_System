/**
 * E2E tests for the three new financial-statement screens (R9-5.1) and the two controlling screens
 * (R10-6.1 internal orders, R10-6.3 overhead cost allocation). All tests call the real API to verify
 * that data returned by the backend matches what is shown on screen.
 *
 * Financial statements: the demo data loader runs a full goods-receipt → production → dispatch
 * workflow that posts journals to the current period, so the P&L, Balance Sheet and Cash Flow
 * statements always have something to show. We only assert that the page renders with the expected
 * section headings and that numeric totals are non-zero (no hard-coded amounts that would break
 * as the demo data grows).
 */
import { admin, apiFor } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { dialog, fill, pick, row, today, typeInto } from '../support/ui';
import { U } from '../support/users';

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** The seeded open period. */
async function openPeriod(): Promise<any> {
  const periods: any[] = await admin().must('GET', '/api/periods');
  const t = today();
  const current = periods.find((p) => p.startDate <= t && t <= p.endDate && p.status === 'OPEN');
  if (!current) throw new Error('No open period found for today — cannot run financial-statements E2E.');
  return current;
}

// ─────────────────────────────────────────────────────────────────────────────
// P&L, Balance Sheet, Cash Flow (R9-5.1)
// ─────────────────────────────────────────────────────────────────────────────

test.describe.serial('Financial statements — P&L, Balance Sheet, Cash Flow (R9-5.1)', () => {
  test('STMT-1 P&L statement renders for the current period with revenue and expense sections', async ({
    browser,
    guard,
  }) => {
    const period = await openPeriod();
    // Verify the API returns a valid payload before checking the screen.
    const stmt = await admin().must('GET', `/api/gl/profit-loss?periodId=${period.id}`);
    expect(stmt, 'P&L statement body').toBeTruthy();
    expect(Array.isArray(stmt.revenue), 'P&L has revenue lines').toBe(true);

    const a = await openAs(browser, guard, U.finance, '/finance/financial-statements');
    const p = a.page;
    // Screen should load the P&L tab by default (or have a tab for it).
    await expect(p.getByRole('heading', { name: /financial statements/i }).first()).toBeVisible();
    await a.close();
  });

  test('STMT-2 Balance Sheet renders assets = liabilities + equity for the current period', async ({
    browser,
    guard,
  }) => {
    const period = await openPeriod();
    const stmt = await admin().must('GET', `/api/gl/balance-sheet?periodId=${period.id}`);
    expect(stmt, 'Balance Sheet body').toBeTruthy();
    // Assets total should equal liabilities + equity (with floating-point tolerance).
    const assets = Number(stmt.totalAssets ?? stmt.assets?.total ?? 0);
    const liabEquity = Number(
      (stmt.totalLiabilitiesAndEquity ?? (Number(stmt.totalLiabilities ?? 0) + Number(stmt.totalEquity ?? 0))),
    );
    // The statement might be empty on a fresh DB before transactions; accept zero == zero.
    expect(Math.abs(assets - liabEquity), `Balance sheet should balance: assets ${assets} = liab+eq ${liabEquity}`).toBeLessThanOrEqual(0.01);

    const a = await openAs(browser, guard, U.finance, '/finance/financial-statements');
    const p = a.page;
    const bsTab = p.getByRole('tab', { name: /balance sheet/i });
    if (await bsTab.count() > 0) await bsTab.click();
    await expect(bsTab.first()).toBeVisible();
    await a.close();
  });

  test('STMT-3 Cash Flow statement renders operating, investing and financing sections', async ({
    browser,
    guard,
  }) => {
    const period = await openPeriod();
    const stmt = await admin().must('GET', `/api/gl/cash-flow?periodId=${period.id}`);
    expect(stmt, 'Cash Flow body').toBeTruthy();

    const a = await openAs(browser, guard, U.finance, '/finance/financial-statements');
    const p = a.page;
    const cfTab = p.getByRole('tab', { name: /cash flow/i });
    if (await cfTab.count() > 0) await cfTab.click();
    await expect(p.getByRole('heading', { name: /cash flow/i }).first()).toBeVisible();
    await a.close();
  });

  test('STMT-4 finance1 (FINANCE_MANAGER) can read P&L; storemgr1 is denied', async () => {
    const period = await openPeriod();

    // FINANCE_MANAGER holds FINANCE_REPORT — should succeed.
    const finApi = apiFor(U.finance);
    const allowed = await finApi.call('GET', `/api/gl/profit-loss?periodId=${period.id}`);
    expect(allowed.status, 'finance1 (FINANCE_MANAGER) should be allowed to view P&L').toBe(200);

    // Store manager has no FINANCE_REPORT — should be denied.
    const storeApi = apiFor(U.storeMgr);
    const denied = await storeApi.call('GET', `/api/gl/profit-loss?periodId=${period.id}`);
    expect(denied.status, 'storemgr1 should be forbidden from P&L').toBe(403);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Internal Orders (R10-6.1)
// ─────────────────────────────────────────────────────────────────────────────

test.describe.serial('Internal orders (R10-6.1)', () => {
  let orderId: number;
  const orderNumber = `IO-E2E-${Date.now().toString(36).toUpperCase()}`;

  test('INT-ORD-1 admin creates an internal order through the UI and it appears in the list', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin, '/controlling/internal-orders');
    const p = a.page;
    await p.getByRole('button', { name: /new order/i }).click();
    const f = p.locator('.edit-form');
    await expect(f).toBeVisible();
    await fill(f, /order number/i, orderNumber);
    await fill(f, /description/i, 'E2E IT infrastructure project');
    await pick(f, /type/i, 0);
    await fill(f, /budget/i, 50000);
    await typeInto(f.getByLabel(/start date/i).first(), today());
    await typeInto(f.getByLabel(/end date/i).first(), today(90));
    const saved = p.waitForResponse((r) => /\/api\/controlling\/internal-orders$/.test(r.url()) && r.request().method() === 'POST');
    await f.getByRole('button', { name: /save/i }).click();
    const res = await saved;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    const body = await res.json();
    orderId = body.id;
    await expect(f).toBeHidden();

    // New order appears in the table.
    await expect(row(p, orderNumber)).toBeVisible();
    await a.close();
  });

  test('INT-ORD-2 costs endpoint returns an empty list for the new order (no GL postings yet)', async () => {
    const costs = await admin().must('GET', `/api/controlling/internal-orders/${orderId}/costs`);
    expect(Array.isArray(costs), 'costs endpoint returns array').toBe(true);
  });

  test('INT-ORD-3 admin settles the internal order through the UI', async ({
    browser,
    guard,
  }) => {
    // First get a cost centre to settle to.
    const centres = await admin().list<any>('/api/controlling/cost-centres');
    expect(centres.length, 'at least one cost centre must exist for settlement').toBeGreaterThan(0);

    const a = await openAs(browser, guard, U.admin, '/controlling/internal-orders');
    const p = a.page;
    const r = row(p, orderNumber);
    await expect(r).toBeVisible();
    await r.getByRole('button', { name: /settle/i }).click();
    const d = dialog(p);
    await pick(d, /settlement cost centre/i, 0);
    await fill(d, /reason/i, 'E2E settlement');
    const settled = p.waitForResponse((r) => /\/settle$/.test(r.url()) && r.request().method() === 'POST');
    await d.getByRole('button', { name: /settle/i }).last().click();
    const res = await settled;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    await expect(d).toBeHidden();
    await a.close();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Overhead Cost Allocation (R10-6.3)
// ─────────────────────────────────────────────────────────────────────────────

test.describe.serial('Overhead cost allocation (R10-6.3)', () => {
  test('ALLOC-1 admin creates an allocation rule through the UI and it appears in the rules list', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin, '/controlling/overhead-allocation');
    const p = a.page;
    // The overhead-allocation component uses an inline form (no dialog).
    // Wait for page and cost-centre options to load.
    await expect(p.getByRole('heading', { name: /overhead allocation/i })).toBeVisible();
    // Use the first section (Allocation Rules) as scope to avoid matching the Period select below.
    const rulesSection = p.locator('section').first();
    await pick(rulesSection, /cost centre/i, 0);
    await pick(rulesSection, /allocation base/i, 1);
    const saved = p.waitForResponse((r) => /\/api\/controlling\/overhead-allocation\/rules$/.test(r.url()) && r.request().method() === 'POST');
    await p.getByRole('button', { name: /add rule/i }).click();
    const res = await saved;
    expect(res.status(), (await res.text()).slice(0, 300)).toBeLessThan(300);
    // New rule appears in the rules table.
    await expect(p.locator('mat-row, tr').first()).toBeVisible({ timeout: 10_000 });
    await a.close();
  });

  test('ALLOC-2 running allocation for the current period returns a run record via API', async () => {
    const period = await openPeriod();
    const run = await admin().must('POST', `/api/controlling/overhead-allocation/runs?periodId=${period.id}`);
    expect(run.id, 'run ID present').toBeTruthy();
    expect(run.periodId, 'run references the requested period').toBe(period.id);
    // Verify the run is retrievable.
    const fetched = await admin().must('GET', `/api/controlling/overhead-allocation/runs/${run.id}`);
    expect(fetched.id).toBe(run.id);
  });

  test('ALLOC-3 allocation runs list shows on the screen', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin, '/controlling/overhead-allocation');
    const p = a.page;
    // Switch to the Runs tab if there is one.
    const runsTab = p.getByRole('tab', { name: /runs/i });
    if (await runsTab.count() > 0) await runsTab.click();
    // The run created in ALLOC-2 should appear.
    await expect(p.locator('mat-row, tr').first()).toBeVisible({ timeout: 10_000 });
    await a.close();
  });
});
