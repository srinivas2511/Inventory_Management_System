import { expect, Locator, Page } from '@playwright/test';

export const dialog = (page: Page) => page.getByRole('dialog');

/** Open the mat-select inside the mat-form-field carrying `label`, then choose an option. */
export async function pick(scope: Locator, label: string | RegExp, option: string | RegExp | number): Promise<void> {
  const page = scope.page();
  const field = scope.locator('mat-form-field').filter({ has: page.locator('mat-label', { hasText: label }) });
  const select = field.first().locator('mat-select');
  await select.waitFor({ state: 'visible' });
  // Material selects sometimes never report "stable" while their panel animates; the click itself is reliable.
  await select.click({ force: true });
  const options = page.getByRole('option');
  if (typeof option === 'number') await options.nth(option).click();
  else await options.filter({ hasText: option }).first().click();
}

export async function fill(scope: Locator, label: string | RegExp, value: string | number): Promise<void> {
  await scope.getByLabel(label, { exact: true }).first().fill(String(value));
}

export const snack = (page: Page) => page.locator('mat-snack-bar-container');

export async function expectToast(page: Page, text: string | RegExp): Promise<void> {
  await expect(snack(page).filter({ hasText: text }).first()).toBeVisible();
}

export const row = (page: Page, text: string | RegExp) => page.getByRole('row').filter({ hasText: text });

/** Icon-only buttons carry only a mat-icon ligature (no aria-label), so locate by icon name. */
export const iconButton = (scope: Locator, icon: string) =>
  scope.locator('button').filter({ has: scope.page().locator(`mat-icon:text-is("${icon}")`) });

export async function selectFilter(page: Page, label: string, option: string | RegExp): Promise<void> {
  await pick(page.locator('body'), label, option);
}

export async function goto(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await expect(page.locator('mat-spinner')).toHaveCount(0, { timeout: 20_000 });
}

export const today = (offsetDays = 0): string => {
  const d = new Date(Date.now() + offsetDays * 86_400_000);
  return d.toISOString().slice(0, 10);
};

/** The production-order accordion panel for an order number, expanded. */
export async function orderPanel(page: Page, orderNumber: string): Promise<Locator> {
  const panel = page.locator('mat-expansion-panel').filter({ hasText: orderNumber }).first();
  const header = panel.locator('mat-expansion-panel-header').first();
  if ((await header.getAttribute('aria-expanded')) !== 'true') await header.click();
  await expect(header).toHaveAttribute('aria-expanded', 'true');
  return panel;
}
