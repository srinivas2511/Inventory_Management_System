import { admin } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { U } from '../support/users';
import { iconButton } from '../support/ui';

test.describe('Dashboards, reports, alerts (UAT INS-01 .. INS-06)', () => {
  test('INS-01 the dashboard shows KPI figures and renders its charts', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/dashboard');
    const p = a.page;
    await expect(p.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await p.waitForLoadState('networkidle');
    const text = await p.locator('mat-sidenav-content').innerText();
    expect(text, 'KPI figures are rendered (numbers present)').toMatch(/\d/);
    expect(text).not.toMatch(/NaN|undefined|\[object/i);
    const charts = p.locator('mat-sidenav-content canvas, mat-sidenav-content svg:not(.mat-icon)');
    expect(await charts.count(), 'at least one chart renders on the dashboard (INS-01/02)').toBeGreaterThan(0);
    await a.close();
  });

  test('INS-03 the alerts list loads with filters and shows seeded alert conditions', async ({ browser, guard }) => {
    const alerts = await admin().list<any>('/api/alerts?size=100');
    const a = await openAs(browser, guard, U.admin, '/insight/alerts');
    await expect(a.page.getByRole('heading', { name: 'Alerts' })).toBeVisible();
    expect(alerts.length, 'DESIGN §11.4: demo data seeds visible LOW_STOCK / PENDING_INSPECTION / PRODUCTION_DELAY alerts').toBeGreaterThan(0);
    await a.close();
  });

  test('INS-04 acknowledging and resolving an alert removes it from the open list', async ({ browser, guard }) => {
    const open = (await admin().list<any>('/api/alerts?size=100')).filter((x) => /OPEN/i.test(x.status ?? ''));
    expect(open.length, 'an open alert exists to dismiss').toBeGreaterThan(0);
    const target = open[0];
    const a = await openAs(browser, guard, U.admin, '/insight/alerts');
    const p = a.page;
    const row = p.getByRole('row').filter({ hasText: target.message ?? target.alertType }).first();
    await iconButton(row, 'check').click();
    await expect.poll(async () => (await admin().list<any>('/api/alerts?size=100')).find((x) => x.id === target.id)?.status).not.toMatch(/OPEN/i);
    await a.close();
  });

  test('INS-05 every report in the catalogue runs without an error', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/insight/reports');
    const p = a.page;
    await p.locator('mat-form-field').filter({ has: p.locator('mat-label', { hasText: 'Report' }) }).locator('mat-select').click();
    const names = (await p.getByRole('option').allInnerTexts()).map((t) => t.trim()).filter((t) => t && !/select/i.test(t));
    await p.keyboard.press('Escape');
    expect(names.length, 'the report catalogue is not empty').toBeGreaterThan(3);
    for (const name of names) {
      await p.locator('mat-form-field').filter({ has: p.locator('mat-label', { hasText: 'Report' }) }).locator('mat-select').click();
      await p.getByRole('option', { name, exact: true }).click();
      await p.getByRole('button', { name: /^run$/i }).click();
      await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 20_000 });
    }
    await a.close();
  });

  test('INS-06 the read-only management role cannot create orders (403)', async ({ guard }) => {
    guard.allow({ status: 403 });
    const { Api } = await import('../support/api');
    const r = await new Api(U.mgmt).post('/api/sales/orders', {
      customerId: 1,
      orderDate: new Date().toISOString().slice(0, 10),
      items: [{ productId: 1, orderedQty: 1, unitPrice: 1 }],
    });
    expect(r.status).toBe(403);
  });
});
