/**
 * Plant scoping (ENT-002 .. ENT-005, R9-7.1 / R9-7.2) through the screens: an administrator maintains plants and their
 * setting overrides, limits a user to a plant from the Users screen, and that user then sees only that plant's documents
 * and none of the screens that cover all plants. The scoping rules themselves are proved by the backend PlantScopeIT.
 */
import { Api, admin, login, rawApi } from '../support/api';
import { ROLE_PASSWORD, TEMP_PASSWORD } from '../support/env';
import { expect, openAs, test } from '../support/fixtures';
import { dialog, row, typeInto } from '../support/ui';
import { U } from '../support/users';

const short = (prefix: string): string => `${prefix}${Math.random().toString(36).slice(2, 7)}`;

async function newPlantViaApi(): Promise<{ id: number; code: string }> {
  const code = short('E').toUpperCase().slice(0, 8);
  const plant = await admin().must('POST', '/api/plants', { plantCode: code, name: `E2E ${code}`, address: null, active: true });
  return { id: plant.id, code };
}

/** A fresh user holding one role, past the forced first password change so the API accepts it. */
async function freshUser(role: string): Promise<{ id: number; username: string }> {
  const username = short('e2e-pl-');
  const created = await admin().must('POST', '/api/admin/users', {
    username,
    fullName: 'E2E Plant User',
    email: `${username}@e2e.local`,
    password: TEMP_PASSWORD,
    roleCodes: [role],
  });
  const first = await login(username, TEMP_PASSWORD);
  const ctx = await rawApi();
  const change = await ctx.post('/api/auth/change-password', {
    data: { currentPassword: TEMP_PASSWORD, newPassword: ROLE_PASSWORD },
    headers: { Authorization: `Bearer ${first.body.accessToken}` },
  });
  expect(change.status(), 'first password change').toBeLessThan(400);
  return { id: created.id, username };
}

/** One purchase order for the first supplier and material, in the given plant (the default plant when null). */
async function purchaseOrderIn(plantId: number | null): Promise<{ id: number; poNumber: string }> {
  const supplier = (await admin().list('/api/lookups/suppliers'))[0];
  const material = (await admin().list('/api/lookups/materials'))[0];
  return admin().must('POST', '/api/procurement/purchase-orders', {
    supplierId: supplier.id,
    poDate: new Date().toISOString().slice(0, 10),
    items: [{ materialId: material.id, orderedQty: 5, rate: 10, taxPct: 0 }],
    ...(plantId ? { plantId } : {}),
  });
}

test.describe('Plants and plant scoping', () => {
  test('PLANT-E2E-1 an administrator adds a plant, overrides a setting for it, and clears the override', async ({ browser, guard }) => {
    const code = short('E').toUpperCase().slice(0, 8);
    const a = await openAs(browser, guard, U.admin, '/admin/plants');
    const p = a.page;

    await p.getByTestId('add-plant').click();
    await typeInto(p.getByTestId('plant-code'), code);
    await typeInto(p.getByTestId('plant-name'), `E2E plant ${code}`);
    await p.getByTestId('save-plant').click();
    await expect(row(p, code)).toBeVisible();
    const plants = await admin().list('/api/plants');
    const created = plants.find((x: any) => x.plantCode === code);
    expect(created, 'the plant exists on the server').toBeTruthy();

    // The settings follow the level chosen: the new plant starts on the system default.
    await p.getByTestId('settings-level').click();
    await p.getByRole('option', { name: new RegExp(code) }).click();
    const calendar = p.getByTestId('setting-production_calendar');
    await expect(calendar).toContainText('system default');

    await typeInto(calendar.locator('input'), 'NIGHT_SHIFT');
    await p.getByTestId('save-production_calendar').click();
    await expect(calendar).toContainText('this plant');
    const effective = await admin().list(`/api/plants/${created.id}/settings`);
    expect(effective.find((s: any) => s.key === 'production_calendar')).toMatchObject({ value: 'NIGHT_SHIFT', source: 'PLANT' });

    await p.getByTestId('clear-production_calendar').click();
    await expect(calendar).toContainText('system default');
    await a.close();
  });

  test('PLANT-E2E-2 a user limited to a plant sees only its purchase orders and no all-plant screens, and the server refuses the rest', async ({ browser, guard }) => {
    const plantB = await newPlantViaApi();
    const poDefault = await purchaseOrderIn(null);
    const poB = await purchaseOrderIn(plantB.id);
    const user = await freshUser('PURCHASE_MANAGER');

    // The administrator limits the user to plant B from the Users screen.
    const a = await openAs(browser, guard, U.admin, '/admin/users');
    await typeInto(a.page.getByTestId('user-search'), user.username);
    await row(a.page, user.username).getByTestId('plants-user').click();
    const d = dialog(a.page);
    await expect(d.getByTestId('plants-list')).toBeVisible();
    await d.getByRole('option').filter({ hasText: plantB.code }).click();
    await d.getByTestId('plants-save').click();
    await expect(d).toBeHidden();
    await a.close();

    // The user lands on a screen of their own, not the dashboard that aggregates all plants.
    const u = await openAs(browser, guard, user.username);
    const p = u.page;
    await expect(p).not.toHaveURL(/\/dashboard/);
    await expect(p.getByRole('link', { name: 'Dashboard' })).toHaveCount(0);

    await p.goto('/procurement/purchase-orders');
    await expect(row(p, poB.poNumber)).toBeVisible();
    await expect(row(p, poDefault.poNumber)).toHaveCount(0);
    await u.close();

    // The same through the API: another plant's order is not found, company-level functions and other plants are refused.
    const api = new Api(user.username, ROLE_PASSWORD);
    expect((await api.get(`/api/procurement/purchase-orders/${poB.id}`)).status).toBe(200);
    expect((await api.get(`/api/procurement/purchase-orders/${poDefault.id}`)).status, "another plant's order is reported as not found").toBe(404);
    const dashboard = await api.get('/api/dashboard/kpis');
    expect(dashboard.status).toBe(403);
    expect(dashboard.body.code).toBe('PLANT_SCOPE_COMPANY_LEVEL');
    const otherPlant = (await admin().list('/api/plants')).find((x: any) => x.id !== plantB.id);
    const refused = await api.post('/api/procurement/purchase-orders', {
      supplierId: (await admin().list('/api/lookups/suppliers'))[0].id,
      poDate: new Date().toISOString().slice(0, 10),
      items: [{ materialId: (await admin().list('/api/lookups/materials'))[0].id, orderedQty: 1, rate: 1, taxPct: 0 }],
      plantId: otherPlant.id,
    });
    expect(refused.status, 'creating a document in a plant the user is not assigned to').toBe(403);
  });

  test('PLANT-E2E-3 removing the restriction lifts it at once, with no new sign-in', async () => {
    const plantB = await newPlantViaApi();
    const poDefault = await purchaseOrderIn(null);
    const poB = await purchaseOrderIn(plantB.id);
    const user = await freshUser('PURCHASE_MANAGER');
    const api = new Api(user.username, ROLE_PASSWORD);
    const listed = async (): Promise<number[]> =>
      ((await api.get('/api/procurement/purchase-orders?size=500')).body.content as { id: number }[]).map((x) => x.id);

    expect(await listed(), 'before any restriction the user sees both plants').toEqual(expect.arrayContaining([poDefault.id, poB.id]));

    await admin().must('PUT', `/api/plants/users/${user.id}`, { plantIds: [plantB.id] });
    const limited = await listed();
    expect(limited).toContain(poB.id);
    expect(limited).not.toContain(poDefault.id);

    await admin().must('PUT', `/api/plants/users/${user.id}`, { plantIds: [] });
    expect(await listed(), 'the restriction is gone on the next request').toEqual(expect.arrayContaining([poDefault.id, poB.id]));
  });
});
