/**
 * D-41: text typed into a Material dialog right after it opens must land in the field it was meant for.
 *
 * Material captures a dialog's initial focus (the first tabbable field) in afterNextRender, one render after the open
 * animation ends. On a loaded machine that move can arrive after typing has started, and `locator.fill()` (which focuses
 * the field and then inserts the text into whichever element has focus) then sends the text to the first field: the Reason
 * of the FG hold dialog once landed in "Quantity to approve" as "4E205". The shared `fill` helper therefore waits until
 * Material has moved focus into the dialog. This spec makes the late capture deterministic by delaying the focus trap's
 * own focus() call, then checks the contract of the helper: once `fill` has returned, no pending initial-focus move can
 * take focus away from the field that was filled.
 */
import { expect, openAs, test } from '../support/fixtures';
import { dialog, fill } from '../support/ui';
import { U } from '../support/users';

const GST = '27AABCS1234A1Z5';
const CAPTURE_DELAY_MS = 800;

test.describe('Typing into a dialog right after it opens (D-41)', () => {
  test('a late initial-focus move cannot hijack a field that was just filled', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/master/customers');
    const p = a.page;
    await p.evaluate((delay) => {
      const w = window as any;
      w.__initialFocusMoves = 0;
      const original = HTMLElement.prototype.focus;
      HTMLElement.prototype.focus = function (this: HTMLElement, ...args: any[]) {
        if ((new Error().stack || '').includes('focusFirstTabbableElement')) {
          setTimeout(() => {
            original.apply(this, args as any);
            w.__initialFocusMoves++;
          }, delay);
          return;
        }
        return original.apply(this, args as any);
      };
    }, CAPTURE_DELAY_MS);

    for (let i = 0; i < 3; i++) {
      await p.getByTestId('add-customer').click();
      const d = dialog(p);
      // The second field first: with an early fill the late capture would move focus to Code after this returns.
      await fill(d, 'Name', `Late focus ${i}`);
      await expect
        .poll(() => p.evaluate(() => (window as any).__initialFocusMoves), {
          message: `opening ${i}: Material's initial focus move happened`,
        })
        .toBeGreaterThan(i);
      await expect(
        d.getByLabel('Name', { exact: true }).first(),
        `opening ${i}: focus stays on the field that was filled`,
      ).toBeFocused();
      await fill(d, 'Code', `L${i}-D41`);
      await fill(d, 'GST number', GST);

      await expect(d.getByLabel('Name', { exact: true }).first()).toHaveValue(`Late focus ${i}`);
      await expect(d.getByLabel('Code', { exact: true }).first()).toHaveValue(`L${i}-D41`);
      await expect(d.getByLabel('GST number', { exact: true }).first()).toHaveValue(GST);

      await d.getByRole('button', { name: 'Cancel' }).click();
      await expect(dialog(p)).toBeHidden();
    }
    await a.close();
  });
});
