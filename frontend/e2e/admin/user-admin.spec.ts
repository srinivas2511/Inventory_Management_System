/**
 * User administration screens built after the first walkthrough: role assignment dialog, unlocking a locked
 * account, the self-deactivation guard, and the change-password screen with its policy checklist. Existing
 * ADM-02..07 specs cover create/reset/disable/audit/settings; the last-administrator invariant is covered
 * by the backend LastAdminGuardIT (an e2e check would risk removing the only demo administrator).
 */
import { admin, login, unique } from '../support/api';
import { TEMP_PASSWORD } from '../support/env';
import { expect, openAs, test, uiLogin, uiLogout } from '../support/fixtures';
import { dialog, row, typeInto } from '../support/ui';
import { U } from '../support/users';

const short = (prefix: string): string => `${prefix}${Math.random().toString(36).slice(2, 7)}`;

async function createUser(roleCodes: string[]): Promise<{ id: number; username: string }> {
  const username = short('e2e-u-');
  const u = await admin().must('POST', '/api/admin/users', {
    username,
    fullName: 'E2E Admin Screen User',
    email: `${username}@e2e.local`,
    password: TEMP_PASSWORD,
    roleCodes,
  });
  return { id: u.id, username };
}

const userRow = async (p: import('@playwright/test').Page, username: string) => {
  await typeInto(p.getByTestId('user-search'), username);
  const r = row(p, username);
  await expect(r).toBeVisible();
  return r;
};

test.describe('User and role administration through the UI', () => {
  test('ADM-08 admin assigns an extra role through the roles dialog and the user holds both roles', async ({ browser, guard }) => {
    const u = await createUser(['STORE_OPERATOR']);
    const a = await openAs(browser, guard, U.admin, '/admin/users');
    const p = a.page;
    const r = await userRow(p, u.username);
    await r.getByTestId('roles-user').click();
    const d = dialog(p);
    await expect(d.getByTestId('role-QUALITY_MANAGER')).toBeVisible();
    await d.locator('[data-testid="role-QUALITY_MANAGER"] input[type="checkbox"]').click();
    await d.getByTestId('roles-save').click();
    await expect(d).toBeHidden();
    await expect
      .poll(async () => {
        const got = await admin().must('GET', `/api/admin/users/${u.id}`);
        const codes: string[] = (got.roles ?? got.roleCodes ?? []).map((x: any) => (typeof x === 'string' ? x : x.code ?? x.roleCode));
        return codes.sort().join(',');
      })
      .toBe('QUALITY_MANAGER,STORE_OPERATOR');
    await a.close();
  });

  test('ADM-09 an account locked by repeated failed sign-ins can be unlocked from the list and signs in again', async ({ browser, guard }) => {
    guard.allow({ status: [401, 423, 429], url: /\/api\/auth\/login/ });
    const u = await createUser(['STORE_OPERATOR']);
    for (let i = 0; i < 12; i++) await login(u.username, 'Wrong#Password-1');
    const locked = await login(u.username, TEMP_PASSWORD);
    expect([401, 403, 423], `a locked account must refuse the correct password, got ${locked.status}`).toContain(locked.status);

    const a = await openAs(browser, guard, U.admin, '/admin/users');
    const p = a.page;
    const r = await userRow(p, u.username);
    await expect(r.getByTestId('locked-caption')).toBeVisible();
    await r.getByTestId('unlock-user').click();
    const confirm = p.getByRole('dialog');
    if (await confirm.isVisible({ timeout: 1500 }).catch(() => false)) await confirm.getByRole('button', { name: /unlock|confirm|yes/i }).click();
    await expect(r.getByTestId('locked-caption')).toBeHidden();
    const ok = await login(u.username, TEMP_PASSWORD);
    expect(ok.status, 'the unlocked user can sign in again').toBe(200);
    await a.close();
  });

  test('ADM-10 an administrator cannot deactivate their own account from the list', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.admin, '/admin/users');
    const p = a.page;
    await typeInto(p.getByTestId('user-search'), 'admin');
    const r = p.getByRole('row').filter({ has: p.getByRole('cell', { name: 'admin', exact: true }) });
    await expect(r).toHaveCount(1);
    await expect(r.getByTestId('deactivate-user')).toBeDisabled();
    await a.close();
  });

  test('ADM-11 a user with a temporary password changes it on the forced screen with the policy checklist, then signs in with the new password', async ({ page, guard }) => {
    const u = await createUser(['STORE_OPERATOR']);
    const next = `Qa#Plant-${unique('n')}!`;
    await uiLogin(page, u.username, TEMP_PASSWORD);
    await expect(page).toHaveURL(/change-password/);
    await expect(page.getByTestId('forced-note')).toBeVisible();

    await typeInto(page.getByTestId('current-password'), TEMP_PASSWORD);
    await typeInto(page.getByTestId('new-password'), 'weak');
    await typeInto(page.getByTestId('confirm-password'), 'weak');
    await page.getByTestId('change-submit').click();
    await expect(page, 'a password that breaks the policy is not accepted').toHaveURL(/change-password/);
    guard.allow({ status: 400, url: /\/api\/auth\/change-password/ });
    expect((await login(u.username, TEMP_PASSWORD)).status, 'the temporary password still works: nothing was changed').toBe(200);

    await typeInto(page.getByTestId('new-password'), next);
    await typeInto(page.getByTestId('confirm-password'), next);
    await page.getByTestId('change-submit').click();
    await expect(page).not.toHaveURL(/change-password/);
    await uiLogout(page);

    guard.allow({ status: 401, url: /\/api\/auth\/login/ });
    const old = await login(u.username, TEMP_PASSWORD);
    expect(old.status, 'the temporary password no longer works').toBe(401);
    const fresh = await login(u.username, next);
    expect(fresh.status).toBe(200);
    expect(fresh.body.mustChangePassword).toBeFalsy();
  });

  test('ADM-12 a wrong current password on the change-password screen shows an error and keeps the user signed in', async ({ page, guard }) => {
    guard.allow({ status: [400, 401], url: /\/api\/auth\/change-password/ });
    const u = await createUser(['STORE_OPERATOR']);
    const next = `Qa#Plant-${unique('m')}!`;
    await uiLogin(page, u.username, TEMP_PASSWORD);
    await expect(page).toHaveURL(/change-password/);
    await typeInto(page.getByTestId('current-password'), 'Not#My-Password-1');
    await typeInto(page.getByTestId('new-password'), next);
    await typeInto(page.getByTestId('confirm-password'), next);
    await page.getByTestId('change-submit').click();
    await expect(page.getByText(/not your current password/i)).toBeVisible();
    await expect(page, 'a wrong current password must not sign the user out').toHaveURL(/change-password/);
  });
});
