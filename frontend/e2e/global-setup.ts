import { Api, login, rawApi } from './support/api';
import { ADMIN_PASSWORD, ADMIN_USER, API, ROLE_PASSWORD, TEMP_PASSWORD } from './support/env';
import { ROLE_USERS } from './support/users';

/**
 * Verifies the stack and creates the DESIGN §11.2 role users through the admin API
 * (the demo loader only creates `admin`; see e2e-defects D-01). Idempotent.
 */
export default async function globalSetup(): Promise<void> {
  const ctx = await rawApi();
  const health = await ctx.get('/actuator/health');
  if (health.status() !== 200) {
    throw new Error(`Backend at ${API} is not healthy (HTTP ${health.status()}). Start the stack first.`);
  }

  const adminLogin = await login(ADMIN_USER, ADMIN_PASSWORD);
  if (adminLogin.status !== 200) {
    throw new Error(`Cannot sign in as ${ADMIN_USER}: HTTP ${adminLogin.status}`);
  }
  const admin = new Api(ADMIN_USER);
  const existing = await admin.list<{ username: string; active?: boolean; enabled?: boolean; locked?: boolean; id: number }>(
    '/api/admin/users?size=500',
  );
  const byName = new Map(existing.map((u) => [u.username, u]));

  for (const u of ROLE_USERS) {
    const found = byName.get(u.username);
    if (found) {
      if (found.active === false || found.enabled === false) await admin.post(`/api/admin/users/${found.id}/reactivate`);
      if (found.locked) await admin.post(`/api/admin/users/${found.id}/unlock`);
      continue;
    }
    await admin.must('POST', '/api/admin/users', {
      username: u.username,
      fullName: u.fullName,
      email: `${u.username}@e2e.local`,
      password: TEMP_PASSWORD,
      roleCodes: [u.role],
    });
    const first = await login(u.username, TEMP_PASSWORD);
    if (first.status !== 200) throw new Error(`First login failed for ${u.username}: HTTP ${first.status}`);
    const change = await ctx.post('/api/auth/change-password', {
      data: { currentPassword: TEMP_PASSWORD, newPassword: ROLE_PASSWORD },
      headers: { Authorization: `Bearer ${first.body.accessToken}` },
    });
    if (change.status() >= 400) {
      throw new Error(`Password change failed for ${u.username}: HTTP ${change.status()} ${(await change.text()).slice(0, 200)}`);
    }
  }

  // Every role user must be able to sign in with the final password.
  for (const u of ROLE_USERS) {
    const r = await login(u.username, ROLE_PASSWORD);
    if (r.status !== 200 || r.body.mustChangePassword) {
      throw new Error(`Role user ${u.username} is not ready: HTTP ${r.status} mustChange=${r.body?.mustChangePassword}`);
    }
  }
}
