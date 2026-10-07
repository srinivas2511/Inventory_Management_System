/**
 * Role-based UI access sweep (brief section 12): for every role user, every navigation entry the role is shown
 * must open without redirecting away, without a failed API call and without a blank or broken page; admin-only
 * screens are not reachable; and representative unauthorised API operations return 403 with that role's own token.
 * rbac.spec.ts holds the exhaustive endpoint probes; this spec proves the screens agree with the grants.
 */
import { admin, Api } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { ROLE_USERS, U } from '../support/users';

test.describe('Every role: navigation entries open cleanly', () => {
  for (const u of ROLE_USERS) {
    test(`ROLE-NAV ${u.role} (${u.username}) can open every entry in its own navigation`, async ({ browser, guard }) => {
      test.setTimeout(240_000);
      const a = await openAs(browser, guard, u.username);
      const p = a.page;
      await p.waitForLoadState('networkidle');
      const hrefs = await p.locator('mat-nav-list a[href]').evaluateAll((els) => els.map((e) => (e as HTMLAnchorElement).getAttribute('href') as string));
      expect(hrefs.length, `${u.role} sees at least one navigation entry`).toBeGreaterThan(0);

      for (const href of [...new Set(hrefs)]) {
        await p.locator(`mat-nav-list a[href="${href}"]`).first().click();
        await expect(p, `${u.role}: ${href} stays on the entry it was shown for`).toHaveURL(new RegExp(href.replace(/[/]/g, '\\/')));
        await expect(p.locator('mat-spinner')).toHaveCount(0, { timeout: 20_000 });
        const body = (await p.locator('mat-sidenav-content').innerText()).trim();
        expect(body.length, `${u.role}: ${href} is not blank`).toBeGreaterThan(10);
        expect(body, `${u.role}: ${href} shows no raw template artefacts`).not.toMatch(/NaN|\[object Object\]|undefined/);
        await p.waitForLoadState('networkidle');
        expect(guard.errors, `${u.role}: the entry ${href} produced a failed API call or console error`).toEqual([]);
      }
      await a.close();
    });
  }
});

test.describe('Every role: admin-only screens and operations are closed', () => {
  const nonAdmin = ROLE_USERS.filter((u) => u.username !== U.admin);

  for (const u of nonAdmin) {
    test(`ROLE-403 ${u.role} (${u.username}): admin screens redirect away and admin writes return 403`, async ({ browser, guard }) => {
      guard.allow({ status: 403 });
      const a = await openAs(browser, guard, u.username);
      const p = a.page;
      for (const route of ['/admin/settings', '/admin/roles', '/admin/attribute-definitions']) {
        await p.goto(route);
        await p.waitForLoadState('networkidle');
        await expect(p, `${u.role} must not stay on ${route}`).not.toHaveURL(new RegExp(route.replace(/[/]/g, '\\/')));
      }
      await a.close();

      const api = new Api(u.username);
      const create = await api.post('/api/admin/users', {
        username: `no-${u.username}`,
        fullName: 'Must Not Exist',
        email: `no-${u.username}@e2e.local`,
        password: 'Demo@12345!',
        roleCodes: ['STORE_OPERATOR'],
      });
      expect(create.status, `${u.role} cannot create users (got ${create.status})`).toBe(403);
      const roles = await api.get('/api/admin/roles');
      expect([403, 404], `${u.role} cannot read the role administration API`).toContain(roles.status);
      const settings = await api.put('/api/admin/settings/e2e-nonexistent', { value: 'x' });
      expect([403, 404, 405], `${u.role} cannot change settings (got ${settings.status})`).toContain(settings.status);
    });
  }

  test('ROLE-403 nobody could create a user through the probes above', async () => {
    const users = await admin().list<any>('/api/admin/users?size=500');
    expect(users.filter((x) => /^no-/.test(x.username)), 'the forbidden create calls must not have created anything').toHaveLength(0);
  });
});
