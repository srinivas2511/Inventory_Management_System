import { ElementHandle, expect, Locator, Page } from '@playwright/test';

export const dialog = (page: Page) => page.getByRole('dialog');

/**
 * D-41. Material captures a dialog's initial focus (the first tabbable field) in afterNextRender, one render AFTER the
 * open animation ends, so the moment is unpredictable on a loaded machine. `locator.fill()` focuses the field and then
 * inserts the text into whichever element has focus, so a capture that lands between the two sends the text to the
 * first field (the Reason of the FG hold dialog once landed in "Quantity to approve" as "4E205"). Before the first
 * interaction with a field inside a dialog, wait until the dialog has finished opening and Material has moved focus into
 * it, and remember that per dialog so later fields do not wait again. Dialogs that take no initial focus fall through
 * after a bounded wait. Returns the dialog container element, or null when the target is not inside a dialog.
 */
async function waitForDialogFocus(target: Locator): Promise<ElementHandle | null> {
  const page = target.page();
  const handle = await target.evaluateHandle((el) => el.closest('.mat-mdc-dialog-container'));
  const container = handle.asElement();
  if (!container) return null;
  const settled = await page
    .waitForFunction(
      (c) => {
        const el = c as HTMLElement;
        if (el.dataset['dialogFocusReady'] === '1') return true;
        const opened = el.classList.contains('mdc-dialog--open') && !el.classList.contains('mdc-dialog--opening');
        if (opened && el.contains(document.activeElement)) {
          el.dataset['dialogFocusReady'] = '1';
          return true;
        }
        return false;
      },
      container,
      { timeout: 4_000 },
    )
    .then(() => true)
    .catch(() => false);
  if (!settled) await container.evaluate((el) => ((el as HTMLElement).dataset['dialogFocusReady'] = '1'));
  return container;
}

/** The values of every text-like field inside a dialog, keyed by element id (or position). */
async function fieldValues(container: ElementHandle): Promise<Record<string, string>> {
  return container.evaluate((root) => {
    const out: Record<string, string> = {};
    (root as HTMLElement).querySelectorAll('input, textarea').forEach((el, i) => {
      const f = el as HTMLInputElement;
      out[f.id || `#${i}`] = f.value;
    });
    return out;
  });
}

/** Open the mat-select inside the mat-form-field carrying `label`, then choose an option. */
export async function pick(scope: Locator, label: string | RegExp, option: string | RegExp | number): Promise<void> {
  const page = scope.page();
  const field = scope.locator('mat-form-field').filter({ has: page.locator('mat-label', { hasText: label }) });
  const select = field.first().locator('mat-select');
  await select.waitFor({ state: 'visible' });
  await waitForDialogFocus(select);
  // Material selects sometimes never report "stable" while their panel animates; the click itself is reliable.
  await select.click({ force: true });
  const options = page.getByRole('option');
  if (typeof option === 'number') await options.nth(option).click();
  else await options.filter({ hasText: option }).first().click();
}

/**
 * Type a value into one field. Every typed value in the specs goes through this, so a field inside a dialog is never
 * typed into before Material has captured the dialog's initial focus (D-41). Outside a dialog it is a plain fill.
 */
export async function typeInto(target: Locator, value: string | number): Promise<void> {
  const container = await waitForDialogFocus(target);
  const before = container ? await fieldValues(container) : null;
  await target.fill(String(value));
  if (container && before) {
    // Tripwire: if the text did not land in the target and some other field of the dialog changed, name that field
    // instead of letting the spec time out later with an opaque message.
    const landed = await target.inputValue().catch(() => String(value));
    if (landed !== String(value)) {
      const after = await fieldValues(container);
      const strays = Object.keys(after).filter((k) => after[k] !== before[k]);
      if (strays.length > 0) {
        throw new Error(
          `typing ${JSON.stringify(String(value))} did not reach its field (it holds ${JSON.stringify(landed)}); ` +
            `these other dialog fields changed instead: ${strays.map((k) => `${k}=${JSON.stringify(after[k])}`).join(', ')} (D-41)`,
        );
      }
    }
  }
}

export async function fill(scope: Locator, label: string | RegExp, value: string | number): Promise<void> {
  await typeInto(scope.getByLabel(label, { exact: true }).first(), value);
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
