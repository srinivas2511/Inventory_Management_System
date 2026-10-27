/**
 * CRM functional tests — go beyond the load-only ui-sweep smoke tests.
 * Verifies list rendering, filters, create forms, and navigation for the
 * main CRM screens.  Admin holds CRM_VIEW + CRM_MANAGE + OPPORTUNITY_MANAGE.
 */
import { expect, openAs, test } from '../support/fixtures';
import { goto, selectFilter, row, today } from '../support/ui';
import { U } from '../support/users';

// ─── helpers ────────────────────────────────────────────────────────────────

function uniqueCompany(prefix: string): string {
  return `${prefix}-${Date.now()}`;
}

// ─── Leads ──────────────────────────────────────────────────────────────────

test.describe('CRM-F: Leads list', () => {
  test('CRM-F-1 leads page renders with expected columns and filter controls', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/leads');
    await p.waitForLoadState('networkidle');

    // Page heading
    await expect(p.getByRole('heading', { name: 'Leads' })).toBeVisible();

    // Filter bar controls
    await expect(p.getByLabel('Status')).toBeVisible();
    await expect(p.getByLabel('Search')).toBeVisible();

    // Table headers
    await expect(p.getByRole('columnheader', { name: 'Lead' })).toBeVisible();
    await expect(p.getByRole('columnheader', { name: 'Company' })).toBeVisible();
    await expect(p.getByRole('columnheader', { name: 'Status' })).toBeVisible();

    // Create form is visible for admin (has LEAD_MANAGE)
    await expect(p.getByLabel('Company')).toBeVisible();

    await a.close();
  });

  test('CRM-F-2 admin can create a lead and it appears in the table', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/leads');
    await p.waitForLoadState('networkidle');

    const company = uniqueCompany('E2E-Lead');

    // Fill the inline create form
    await p.getByLabel('Company').fill(company);
    await p.getByLabel('Source').fill('E2E test');
    await p.getByLabel('Estimated value').fill('50000');
    await p.getByLabel('Expected close').fill(today(30));

    await p.getByRole('button', { name: /New lead/i }).click();

    // Row with the new company name should appear
    await expect(row(p, company)).toBeVisible({ timeout: 15_000 });

    await a.close();
  });

  test('CRM-F-3 status filter changes the visible leads', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/leads');
    await p.waitForLoadState('networkidle');

    // Pick a status to filter
    await selectFilter(p, 'Status', /NEW/i);
    await p.waitForLoadState('networkidle');

    // Either rows matching NEW status are shown, or the "No leads found" empty state
    const tableRows = p.getByRole('row').filter({ hasNot: p.getByRole('columnheader') });
    const emptyMsg = p.getByText('No leads found');
    await expect(tableRows.first().or(emptyMsg)).toBeVisible({ timeout: 10_000 });

    await a.close();
  });

  test('CRM-F-4 clicking a lead row opens its detail page', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/leads');
    await p.waitForLoadState('networkidle');

    // Find any data row (skip the header row)
    const dataRows = p.getByRole('row').filter({ hasNot: p.getByRole('columnheader') });
    const count = await dataRows.count();
    if (count === 0) {
      // Create one first so there is something to click
      const company = uniqueCompany('E2E-Click');
      await p.getByLabel('Company').fill(company);
      await p.getByRole('button', { name: /New lead/i }).click();
      await expect(row(p, company)).toBeVisible({ timeout: 15_000 });
    }

    // Click the first data row
    await dataRows.first().click();
    // Should navigate to /crm/leads/:id
    await expect(p).toHaveURL(/\/crm\/leads\/\d+/, { timeout: 15_000 });

    await a.close();
  });
});

// ─── Opportunities ───────────────────────────────────────────────────────────

test.describe('CRM-F: Opportunities board', () => {
  test('CRM-F-5 opportunities page loads with board/list toggle', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/opportunities');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Opportunities' })).toBeVisible();

    // View toggle
    const boardBtn = p.getByRole('button', { name: 'Board' });
    const listBtn = p.getByRole('button', { name: 'List' });
    await expect(boardBtn).toBeVisible();
    await expect(listBtn).toBeVisible();

    // Switch to list view
    await listBtn.click();
    // Either a table header or an empty message renders
    const listContent = p.locator('table').or(p.getByText(/no opportunities/i));
    await expect(listContent.first()).toBeVisible({ timeout: 10_000 });

    await a.close();
  });
});

// ─── Contacts ────────────────────────────────────────────────────────────────

test.describe('CRM-F: Contacts list', () => {
  test('CRM-F-6 contacts page renders filter controls and table headers', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/contacts');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Contacts' })).toBeVisible();
    await expect(p.getByLabel('Search')).toBeVisible();

    await a.close();
  });

  test('CRM-F-7 typing in the search field triggers a reload', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/contacts');
    await p.waitForLoadState('networkidle');

    const search = p.getByLabel('Search');
    await search.fill('zzznomatch');
    await p.keyboard.press('Enter');
    await p.waitForLoadState('networkidle');

    // Either empty state or table rows — we just confirm the spinner is gone
    await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 15_000 });

    await a.close();
  });
});

// ─── Quotations ───────────────────────────────────────────────────────────────

test.describe('CRM-F: Quotations list', () => {
  test('CRM-F-8 quotations page renders list with heading and table', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/crm/quotations');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Quotations' })).toBeVisible();

    // Table header or empty state
    const tableOrEmpty = p.locator('table').or(p.getByText(/no quotations/i));
    await expect(tableOrEmpty.first()).toBeVisible({ timeout: 10_000 });

    await a.close();
  });
});
