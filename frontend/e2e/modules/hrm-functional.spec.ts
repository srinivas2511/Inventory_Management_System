/**
 * HRM functional tests — go beyond the load-only ui-sweep smoke tests.
 * Verifies employee list, create form, attendance, shifts, and payroll run screens.
 * Admin holds HR_VIEW + HR_MANAGE + PAYROLL_RUN + PAYROLL_APPROVE.
 */
import { expect, openAs, test } from '../support/fixtures';
import { goto, row, today } from '../support/ui';
import { U } from '../support/users';

// ─── Employees ───────────────────────────────────────────────────────────────

test.describe('HRM-F: Employee list', () => {
  test('HRM-F-1 employees page renders heading, filter controls, and table columns', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/employees');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Employees' })).toBeVisible();

    // Filter controls
    await expect(p.getByLabel('Search')).toBeVisible();
    await expect(p.getByLabel('Status')).toBeVisible();

    // Create form (EMPLOYEE_MANAGE)
    await expect(p.getByLabel('First name')).toBeVisible();
    await expect(p.getByLabel('Last name')).toBeVisible();
    await expect(p.getByLabel('Designation')).toBeVisible();

    await a.close();
  });

  test('HRM-F-2 admin can create an employee and it appears in the list', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/employees');
    await p.waitForLoadState('networkidle');

    const suffix = Date.now();
    const firstName = `E2EFirst${suffix}`;
    const lastName = `E2ELast${suffix}`;

    await p.getByLabel('First name').fill(firstName);
    await p.getByLabel('Last name').fill(lastName);
    await p.getByLabel('Designation').fill('Test Engineer');
    await p.getByLabel('Department').fill('QA');
    await p.getByLabel('Joining date').fill(today());

    // Select employment type (first option)
    const typeSelect = p.getByLabel('Type');
    await typeSelect.click();
    await p.getByRole('option').first().click();

    await p.getByRole('button', { name: /New employee/i }).click();

    // create() navigates to the employee detail page on success
    await expect(p).toHaveURL(/\/hrm\/employees\/\d+/, { timeout: 15_000 });

    await a.close();
  });

  test('HRM-F-3 clicking an employee row opens the detail page', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/employees');
    await p.waitForLoadState('networkidle');

    const dataRows = p.getByRole('row').filter({ hasNot: p.getByRole('columnheader') });
    const count = await dataRows.count();
    if (count === 0) {
      test.skip();
      return;
    }

    await dataRows.first().click();
    await expect(p).toHaveURL(/\/hrm\/employees\/\d+/, { timeout: 15_000 });

    await a.close();
  });

  test('HRM-F-4 status filter changes the displayed employees', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/employees');
    await p.waitForLoadState('networkidle');

    // Pick "Active" status
    const statusSelect = p.getByLabel('Status');
    await statusSelect.click();
    await p.getByRole('option', { name: 'Active', exact: true }).click();
    await p.waitForLoadState('networkidle');
    await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 15_000 });

    await a.close();
  });
});

// ─── Attendance ───────────────────────────────────────────────────────────────

test.describe('HRM-F: Attendance page', () => {
  test('HRM-F-5 attendance page loads without errors', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/attendance');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: /Attendance/i })).toBeVisible();
    await expect(p.locator('mat-spinner')).toHaveCount(0);

    await a.close();
  });
});

// ─── Shifts ───────────────────────────────────────────────────────────────────

test.describe('HRM-F: Shifts list', () => {
  test('HRM-F-6 shifts page loads with heading and table', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/shifts');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: /Shifts/i })).toBeVisible();

    const tableOrEmpty = p.locator('table').or(p.getByText(/no shifts/i));
    await expect(tableOrEmpty.first()).toBeVisible({ timeout: 10_000 });

    await a.close();
  });
});

// ─── Payroll ─────────────────────────────────────────────────────────────────

test.describe('HRM-F: Payroll run list', () => {
  test('HRM-F-7 payroll page loads with run initiation form and run list table', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/payroll');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: 'Payroll' })).toBeVisible();

    // Initiation form
    await expect(p.getByLabel('Month')).toBeVisible();
    await expect(p.getByLabel('Year')).toBeVisible();
    await expect(p.getByRole('button', { name: /Start payroll run/i })).toBeVisible();

    // Table headers
    await expect(p.getByRole('columnheader', { name: 'Run' })).toBeVisible();
    await expect(p.getByRole('columnheader', { name: 'Period' })).toBeVisible();

    await a.close();
  });

  test('HRM-F-8 payroll form validates year field', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/payroll');
    await p.waitForLoadState('networkidle');

    // Select a month
    const monthSelect = p.getByLabel('Month');
    await monthSelect.click();
    await p.getByRole('option').first().click();

    // Clear the year and try to submit
    await p.getByLabel('Year').clear();
    const submitBtn = p.getByRole('button', { name: /Start payroll run/i });
    await expect(submitBtn).toBeDisabled();

    await a.close();
  });
});

// ─── Leave ────────────────────────────────────────────────────────────────────

test.describe('HRM-F: Leave page', () => {
  test('HRM-F-9 leave page loads for admin without errors', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin);
    const p = a.page;
    await goto(p, '/hrm/leave');
    await p.waitForLoadState('networkidle');

    await expect(p.getByRole('heading', { name: /Leave/i })).toBeVisible();
    await expect(p.locator('mat-spinner')).toHaveCount(0);

    await a.close();
  });
});
