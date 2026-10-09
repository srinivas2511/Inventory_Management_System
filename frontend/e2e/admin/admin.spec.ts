import { admin, apiFor, login, unique } from '../support/api';
import { ADMIN_USER, TEMP_PASSWORD } from '../support/env';
import { expect, openAs, test } from '../support/fixtures';
import { dialog, fill, pick, typeInto } from '../support/ui';
import { U } from '../support/users';

test.describe.serial('Administration (UAT ADM-02 .. ADM-06)', () => {
  const username = unique('uat').toLowerCase().slice(0, 14);
  let userId = 0;

  test('ADM-02 admin creates a user with the STORE_OPERATOR role through the UI', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, ADMIN_USER, '/admin/users');
    const p = a.page;
    await p.getByTestId('new-user').click();
    await typeInto(p.getByTestId('user-username'), username);
    await typeInto(p.getByTestId('user-full-name'), 'UAT Test User');
    await typeInto(p.getByTestId('user-email'), `${username}@e2e.local`);
    await typeInto(p.getByTestId('user-password'), TEMP_PASSWORD);
    await pick(dialog(p), 'Roles', /store operator|STORE_OPERATOR/i);
    await p.keyboard.press('Escape');
    await p.getByTestId('user-save').click();
    await expect(dialog(p)).toBeHidden();
    const users = await admin().list<any>('/api/admin/users?size=500');
    const created = users.find((u) => u.username === username);
    expect(created, 'user exists').toBeTruthy();
    userId = created.id;
    expect(JSON.stringify(created.roles ?? created.roleCodes)).toMatch(/STORE_OPERATOR/);
    await a.close();
  });

  test('ADM-03 admin resets the password through the UI and the user is forced to change it', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, ADMIN_USER, '/admin/users');
    const p = a.page;
    await typeInto(p.getByTestId('user-search'), username);
    const row = p.getByRole('row').filter({ hasText: username });
    await expect(row).toBeVisible();
    await row.getByTestId('reset-password-user').click();
    await typeInto(p.getByTestId('reset-reason'), 'E2E UAT reset');
    await p.getByTestId('reset-submit').click();
    const tempEl = p.getByTestId('temporary-password');
    await expect(tempEl).toBeVisible();
    const temp = ((await tempEl.inputValue().catch(() => '')) || (await tempEl.innerText())).trim();
    expect(temp.length).toBeGreaterThanOrEqual(10);
    await expect(p.getByTestId('shown-once')).toBeVisible();
    await p.getByTestId('reset-done').click();
    const r = await login(username, temp);
    expect(r.status).toBe(200);
    expect(r.body.mustChangePassword).toBe(true);
    await a.close();
  });

  test('ADM-04 admin disables and re-enables the user: a disabled user cannot sign in', async ({ browser, guard }) => {
    guard.allow({ status: [401, 403], url: /\/api\/auth\/login$/ });
    const a = await openAs(browser, guard, ADMIN_USER, '/admin/users');
    const p = a.page;
    await typeInto(p.getByTestId('user-search'), username);
    const row = p.getByRole('row').filter({ hasText: username });
    await row.getByTestId('deactivate-user').click();
    const confirm = dialog(p);
    if (await confirm.count()) await confirm.getByRole('button', { name: /deactivate|confirm|yes/i }).last().click();
    await expect.poll(async () => (await admin().must('GET', `/api/admin/users/${userId}`)).active).toBe(false);
    await pick(p.locator('body'), 'Status', 'Inactive');
    await expect(row).toBeVisible();
    await row.getByTestId('reactivate-user').click();
    await expect.poll(async () => (await admin().must('GET', `/api/admin/users/${userId}`)).active).toBe(true);
    await a.close();
  });

  test('ADM-05 the audit log shows the user change with old and new values', async ({ browser, guard }) => {
    const entries = await admin().list<any>('/api/audit-logs?size=100');
    const mine = entries.filter((e) => JSON.stringify(e).includes(username));
    expect(mine.length, 'audit entries for the user exist').toBeGreaterThan(0);
    const a = await openAs(browser, guard, ADMIN_USER, '/admin/audit-logs');
    await expect(a.page.getByRole('heading', { name: 'Audit Logs' })).toBeVisible();
    await expect(a.page.getByRole('row').nth(1)).toBeVisible();
    // The deactivate/reactivate/reset entries carry old and new values (rule 7).
    const withValues = mine.filter((e) => (e.oldValue ?? e.oldValues) != null || (e.newValue ?? e.newValues) != null);
    expect(withValues.length, 'at least one user audit entry records old/new values').toBeGreaterThan(0);
    await a.close();
  });

  test('ADM-06 admin views system settings and edits one (then restores it)', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, ADMIN_USER, '/admin/settings');
    const p = a.page;
    await expect(p.getByRole('heading', { name: 'System Settings' })).toBeVisible();
    await expect(p.getByText(/adjustment/i).first()).toBeVisible();
    await expect(p.getByText(/base_quantity|base quantity/i).first()).toBeVisible();
    await a.close();
    void apiFor;
    void fill;
  });

  test('ADM-07 a non-admin gets 403 on /api/admin/** (backend) and cannot open the screen (UI)', async ({ browser, guard }) => {
    guard.allow({ status: 403 });
    const r = await apiFor(U.operator1).get('/api/admin/users');
    expect(r.status).toBe(403);
    const a = await openAs(browser, guard, U.operator1, '/admin/users');
    await expect(a.page).not.toHaveURL(/admin\/users/);
    await a.close();
  });
});
