import { readFileSync } from 'fs';
import { resolve } from 'path';
import { expect, openAs, test } from '../support/fixtures';
import { U } from '../support/users';

/**
 * UI quality sweep: every navigation entry loads for the administrator with no console errors, no failed
 * API calls, no stuck spinner and no blank screen. Routes are read from the app's own nav configuration so
 * new screens are covered automatically.
 */
const navSource = readFileSync(resolve(__dirname, '..', '..', 'src', 'app', 'core', 'layout', 'nav.config.ts'), 'utf-8');
const ROUTES = [...new Set([...navSource.matchAll(/route:\s*'([^']+)'/g)].map((m) => m[1]))];

test.describe('UI sweep: every screen loads cleanly', () => {
  test('the navigation configuration is not empty', () => {
    expect(ROUTES.length).toBeGreaterThan(60);
  });

  for (const route of ROUTES) {
    test(`NAV ${route}`, async ({ browser, guard }) => {
      const a = await openAs(browser, guard, U.admin);
      const p = a.page;
      await p.goto(route);
      await expect(p).toHaveURL(new RegExp(route.replace(/[/]/g, '\\/')));
      await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 20_000 });
      await p.waitForLoadState('networkidle');
      const body = (await p.locator('mat-sidenav-content').innerText()).trim();
      expect(body.length, `${route} is not blank`).toBeGreaterThan(15);
      expect(body, `${route} shows no raw template/JS artefacts`).not.toMatch(/NaN|\[object Object\]|undefined/);
      await a.close();
    });
  }

  test('browser back and forward keep the app state intact', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/inventory/stock');
    const p = a.page;
    await p.goto('/inventory/ledger');
    await p.goto('/quality/inspections');
    await p.goBack();
    await expect(p).toHaveURL(/inventory\/ledger/);
    await expect(p.getByRole('heading', { name: 'Stock Ledger' })).toBeVisible();
    await p.goBack();
    await expect(p.getByRole('heading', { name: 'Current Stock' })).toBeVisible();
    await p.goForward();
    await expect(p).toHaveURL(/inventory\/ledger/);
    await a.close();
  });
});
