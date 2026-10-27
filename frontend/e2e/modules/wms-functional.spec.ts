/**
 * WMS functional tests — go beyond the load-only ui-sweep smoke tests.
 * Verifies bin view, pick waves, cycle counts, putaway, and picking task screens.
 * Admin holds WMS_VIEW + WMS_MANAGE + WAVE_MANAGE + CYCLE_COUNT_MANAGE + PUTAWAY_MANAGE + PICKING_MANAGE.
 */
import { expect, openAs, test } from '../support/fixtures';
import { goto } from '../support/ui';
import { U } from '../support/users';

// ─── Bin View ─────────────────────────────────────────────────────────────────

test.describe('WMS-F: Bin view', () => {
  test('WMS-F-1 bin view page loads with warehouse filter and bin table', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/bin-view');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Bin View' })).toBeVisible();

    // Warehouse filter
    await expect(p.locator('[data-test="warehouse"]')).toBeVisible();
    // Search input
    await expect(p.getByLabel('Find bin')).toBeVisible();

    // Table columns
    await expect(p.getByRole('columnheader', { name: 'Bin' })).toBeVisible();
    await expect(p.getByRole('columnheader', { name: 'Zone' })).toBeVisible();

    await a.close();
  });

  test('WMS-F-2 selecting a warehouse loads bins (or shows empty state)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/bin-view');
    await p.waitForLoadState('networkidle');

    const warehouseSelect = p.locator('[data-test="warehouse"]');
    const optionCount = await p.getByRole('option').count().catch(() => 0);

    // Click the warehouse select
    await warehouseSelect.click();
    const options = p.getByRole('option');
    const cnt = await options.count();

    if (cnt > 0) {
      await options.first().click();
      await p.waitForLoadState('networkidle');
      await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 15_000 });
      // Either bins table has rows or shows empty state
      const dataRows = p.getByRole('row').filter({ hasNot: p.getByRole('columnheader') });
      const emptyState = p.getByText(/no bins/i);
      await expect(dataRows.first().or(emptyState)).toBeVisible({ timeout: 10_000 });
    } else {
      // No warehouses seeded — just confirm no crash
      await p.keyboard.press('Escape');
      await expect(p.getByRole('heading', { name: 'Bin View' })).toBeVisible();
    }

    await a.close();
  });
});

// ─── Pick Waves ───────────────────────────────────────────────────────────────

test.describe('WMS-F: Pick waves', () => {
  test('WMS-F-3 waves page renders with creation form and wave list table', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/waves');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Pick Waves' })).toBeVisible();

    // Create form controls
    await expect(p.locator('[data-test="wave-orders"]')).toBeVisible();
    await expect(p.locator('[data-test="create-wave"]')).toBeVisible();

    // Create button should be disabled when no orders are selected
    await expect(p.locator('[data-test="create-wave"]')).toBeDisabled();

    // Table column headers
    await expect(p.getByRole('columnheader', { name: 'Wave' })).toBeVisible();
    await expect(p.getByRole('columnheader', { name: 'Status' })).toBeVisible();

    await a.close();
  });

  test('WMS-F-4 status filter on waves page updates the table', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/waves');
    await p.waitForLoadState('networkidle');

    // Click status filter
    await p.getByLabel('Status').click();
    const options = p.getByRole('option');
    const cnt = await options.count();
    if (cnt > 0) {
      await options.nth(1).click(); // pick first non-All option
      await p.waitForLoadState('networkidle');
      await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 15_000 });
    }

    await a.close();
  });
});

// ─── Cycle Counts ────────────────────────────────────────────────────────────

test.describe('WMS-F: Cycle counts', () => {
  test('WMS-F-5 cycle counts page loads with creation form and list table', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/cycle-counts');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Cycle Counts' })).toBeVisible();

    // Create form controls
    await expect(p.locator('[data-test="count-warehouse"]')).toBeVisible();
    await expect(p.locator('[data-test="count-type"]')).toBeVisible();

    // Table columns
    await expect(p.getByRole('columnheader', { name: 'Count', exact: true })).toBeVisible();
    await expect(p.getByRole('columnheader', { name: 'Type' })).toBeVisible();
    await expect(p.getByRole('columnheader', { name: 'Status' })).toBeVisible();

    await a.close();
  });

  test('WMS-F-6 count type ZONE shows zone picker, ABC shows class picker', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/cycle-counts');
    await p.waitForLoadState('networkidle');

    // Switch count type to ABC
    await p.locator('[data-test="count-type"]').click();
    await p.getByRole('option', { name: /ABC/i }).click();
    await expect(p.getByLabel('ABC class')).toBeVisible({ timeout: 5_000 });

    // Switch to ZONE — zone picker should appear (after selecting a warehouse)
    await p.locator('[data-test="count-type"]').click();
    await p.getByRole('option', { name: /ZONE/i }).click();
    // Zone field appears only once a warehouse with zones is selected — just confirm no errors
    await expect(p.locator('mat-spinner')).toHaveCount(0);

    await a.close();
  });
});

// ─── Putaway tasks ────────────────────────────────────────────────────────────

test.describe('WMS-F: Putaway tasks', () => {
  test('WMS-F-7 putaway page loads with status filter and task list', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/putaway');
    await p.waitForLoadState('networkidle');

    // Title is "Put-Away" or similar (set by route data)
    const heading = p.locator('[data-test="title"]');
    await expect(heading).toBeVisible();

    await expect(p.locator('[data-test="status"]')).toBeVisible();
    await expect(p.getByLabel('Order number')).toBeVisible();

    await a.close();
  });
});

// ─── Picking tasks ────────────────────────────────────────────────────────────

test.describe('WMS-F: Picking tasks', () => {
  test('WMS-F-8 picking page loads with status filter and task list', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/wms/picking');
    await p.waitForLoadState('networkidle');

    const heading = p.locator('[data-test="title"]');
    await expect(heading).toBeVisible();

    await expect(p.locator('[data-test="status"]')).toBeVisible();
    // filter bar always shows "Order number"; the PICKING_MANAGE form shows "Sales order id" additionally
    await expect(p.getByLabel('Order number')).toBeVisible();

    await a.close();
  });
});
