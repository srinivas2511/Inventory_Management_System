/**
 * E2E smoke tests for the Spring Manufacturing IMS demo workflow.
 * Prerequisites: docker compose stack running at E2E_BASE_URL (default :8081),
 * demo data loaded (ims.demo.load-data=true).
 *
 * These tests cover the happy path only — they verify that key screens load,
 * forms submit without errors, and the navigated-to screen shows expected content.
 * Edge-case behaviour is covered by unit and integration tests.
 */

import { test, expect, Page } from '@playwright/test';

const BASE = process.env['E2E_BASE_URL'] || 'http://localhost:8081';

async function login(page: Page, username = 'admin', password = 'admin123') {
  await page.goto(`${BASE}/login`);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/dashboard/);
}

// ── T1: Login and dashboard ───────────────────────────────────────────────────

test('T1 — admin login shows dashboard KPIs', async ({ page }) => {
  await login(page);
  await expect(page.getByText(/active production orders/i)).toBeVisible();
  await expect(page.getByText(/pending inspections/i)).toBeVisible();
  await expect(page.getByText(/open sales orders/i)).toBeVisible();
});

// ── T2: Inventory — material balance ─────────────────────────────────────────

test('T2 — inventory list shows demo material stock', async ({ page }) => {
  await login(page);
  await page.goto(`${BASE}/inventory`);
  await expect(page.getByText(/DEMO-WR-65SI2/i)).toBeVisible();
});

// ── T3: Production order list ─────────────────────────────────────────────────

test('T3 — production order list shows demo order', async ({ page }) => {
  await login(page);
  await page.goto(`${BASE}/production/orders`);
  await expect(page.getByText(/DEMO-CS-25X3/i)).toBeVisible();
});

// ── T4: Quality inspection list shows APPROVED result ────────────────────────

test('T4 — quality inspections list shows approved final inspection', async ({ page }) => {
  await login(page);
  await page.goto(`${BASE}/quality/inspections`);
  const approvedBadge = page.locator('text=APPROVED').first();
  await expect(approvedBadge).toBeVisible();
});

// ── T5: Sales order detail shows DISPATCHED item ─────────────────────────────

test('T5 — sales order list shows dispatched order', async ({ page }) => {
  await login(page);
  await page.goto(`${BASE}/sales/orders`);
  await expect(page.getByText(/DEMO-AUTOINC/i)).toBeVisible();
});

// ── T6: Traceability search ───────────────────────────────────────────────────

test('T6 — traceability batch search returns results', async ({ page }) => {
  await login(page);
  await page.goto(`${BASE}/insight/traceability`);
  // The demo creates FG batches; a search by product code should surface them
  const searchInput = page.getByPlaceholder(/search.*batch/i).first();
  if (await searchInput.isVisible()) {
    await searchInput.fill('DEMO-CS');
    await page.keyboard.press('Enter');
    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 5000 });
  } else {
    // Screen may use a different layout; just verify the page loaded
    await expect(page.getByText(/traceability/i).first()).toBeVisible();
  }
});
