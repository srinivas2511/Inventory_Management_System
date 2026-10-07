import { Api, admin, unique, login } from '../support/api';
import { ADMIN_PASSWORD, ADMIN_USER, TEMP_PASSWORD } from '../support/env';
import { expect, test, uiLogin, uiLogout } from '../support/fixtures';
import { U } from '../support/users';

test.describe('Authentication and session', () => {
  test('ADM-01 admin signs in and lands on the dashboard', async ({ page }) => {
    await uiLogin(page, ADMIN_USER, ADMIN_PASSWORD);
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByTestId('user-menu-trigger')).toContainText(/administrator/i);
  });

  test('AUTH-02 wrong password is refused with a clear message', async ({ page, guard }) => {
    guard.allow({ status: 401, url: /\/api\/auth\/login$/ });
    await page.goto('/login');
    await page.getByTestId('username').fill(ADMIN_USER);
    await page.getByTestId('password').fill('definitely-wrong-1A!');
    await page.getByTestId('login-submit').click();
    await expect(page.getByTestId('login-error')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('AUTH-03 empty form shows required-field messages', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('login-submit').click();
    await expect(page.getByText('Username is required')).toBeVisible();
    await expect(page.getByText('Password is required')).toBeVisible();
  });

  test('AUTH-04 a page reload keeps the session', async ({ page }) => {
    await uiLogin(page, ADMIN_USER);
    await page.goto('/inventory/stock');
    await expect(page.getByRole('heading', { name: 'Current Stock' })).toBeVisible();
    await page.reload();
    await expect(page).toHaveURL(/inventory\/stock/);
    await expect(page.getByRole('heading', { name: 'Current Stock' })).toBeVisible();
    await expect(page.getByTestId('user-menu-trigger')).toBeVisible();
  });

  test('AUTH-05 a deep link survives sign-in', async ({ page }) => {
    await page.goto('/inventory/ledger');
    await expect(page).toHaveURL(/\/login/);
    await page.getByTestId('username').fill(ADMIN_USER);
    await page.getByTestId('password').fill(ADMIN_PASSWORD);
    await page.getByTestId('login-submit').click();
    await expect(page).toHaveURL(/inventory\/ledger/);
  });

  test('AUTH-06 sign out ends the session and protects routes', async ({ page }) => {
    await uiLogin(page, ADMIN_USER);
    await uiLogout(page);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
    await page.reload();
    await expect(page).toHaveURL(/\/login/);
  });

  test('AUTH-07 back/forward after sign-out does not reveal protected pages', async ({ page }) => {
    await uiLogin(page, ADMIN_USER);
    await page.goto('/inventory/stock');
    await uiLogout(page);
    await page.goBack();
    await expect(page).toHaveURL(/\/login/);
  });

  test('AUTH-08 a user without the permission cannot open an admin screen (UI)', async ({ page, guard }) => {
    guard.allow({ status: [401, 403] });
    await uiLogin(page, U.operator1);
    await page.goto('/admin/users');
    await expect(page).not.toHaveURL(/admin\/users/);
  });

  test('AUTH-09 temporary password forces a change before anything else (UI and server)', async ({ page, guard }) => {
    guard.allow({ status: 403 });
    const name = unique('tmp').toLowerCase().slice(0, 12);
    await admin().must('POST', '/api/admin/users', {
      username: name,
      fullName: 'Forced Change',
      email: `${name}@e2e.local`,
      password: TEMP_PASSWORD,
      roleCodes: ['STORE_OPERATOR'],
    });
    // Server side: every normal call is refused with the stable code until the password is changed.
    const first = await login(name, TEMP_PASSWORD);
    expect(first.body.mustChangePassword).toBe(true);
    const blocked = await fetchAs(first.body.accessToken, '/api/materials');
    expect(blocked.status).toBe(403);
    expect(JSON.stringify(blocked.body)).toContain('PASSWORD_CHANGE_REQUIRED');

    // UI: sign-in is held on the change-password screen.
    await uiLogin(page, name, TEMP_PASSWORD).catch(() => undefined);
    await expect(page).toHaveURL(/change-password/);
    await page.getByTestId('current-password').fill(TEMP_PASSWORD);
    await page.getByTestId('new-password').fill('Fresh#Passw0rd-77');
    await page.getByTestId('confirm-password').fill('Fresh#Passw0rd-77');
    await page.getByTestId('change-submit').click();
    await expect(page).not.toHaveURL(/change-password/);
  });
});

async function fetchAs(token: string, path: string) {
  const r = await new Api('forced-change-probe', TEMP_PASSWORD).call('GET', path, undefined, { token });
  return { status: r.status, body: r.body };
}
