import { createHmac } from 'crypto';
import { readFileSync } from 'fs';
import { resolve } from 'path';
import { Api, login, rawApi } from '../support/api';
import { ADMIN_PASSWORD, ADMIN_USER, ROLE_PASSWORD, TEMP_PASSWORD } from '../support/env';
import { expect, test } from '../support/fixtures';

const repo = (p: string) => resolve(__dirname, '..', '..', '..', p);
const b64u = (v: string | Buffer) => Buffer.from(v).toString('base64url');

function sign(payload: Record<string, unknown>, secret: string, alg = 'HS256'): string {
  const h = b64u(JSON.stringify({ alg, typ: 'JWT' }));
  const p = b64u(JSON.stringify(payload));
  const sig = createHmac('sha256', secret).update(`${h}.${p}`).digest('base64url');
  return `${h}.${p}.${sig}`;
}

function claimsOf(token: string): Record<string, any> {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf-8'));
}

function devSecret(): string {
  const yml = readFileSync(repo('backend/src/main/resources/application-dev.yml'), 'utf-8');
  const m = yml.match(/secret:\s*(\S+)/);
  if (!m) throw new Error('dev JWT secret not found in application-dev.yml');
  return m[1];
}

async function me(token: string | null) {
  const ctx = await rawApi();
  const res = await ctx.get('/api/auth/me', { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  return res.status();
}

test.describe('Security (SEC-01 .. SEC-09)', () => {
  test('SEC-01 API calls without an Authorization header are rejected with 401', async ({ guard }) => {
    guard.allow({ status: 401 });
    const ctx = await rawApi();
    for (const path of ['/api/materials', '/api/products', '/api/procurement/purchase-orders', '/api/inventory/balances', '/api/admin/users', '/api/auth/me']) {
      const res = await ctx.get(path);
      expect(res.status(), path).toBe(401);
    }
  });

  test('SEC-02/03/04 security headers are present on responses', async () => {
    const ctx = await rawApi();
    for (const path of ['/actuator/health', '/api/materials']) {
      const res = await ctx.get(path);
      const h = res.headers();
      expect(h['strict-transport-security'], `${path} HSTS`).toBeTruthy();
      expect(h['x-xss-protection'], `${path} X-XSS-Protection`).toMatch(/1;\s*mode=block/i);
      expect(h['x-content-type-options'], `${path} nosniff`).toBe('nosniff');
    }
  });

  test('SEC-05 a JWT signed with the wrong secret is rejected (control: the right secret is accepted)', async ({ guard }) => {
    guard.allow({ status: 401 });
    const real = (await login(ADMIN_USER, ADMIN_PASSWORD)).body.accessToken as string;
    const claims = claimsOf(real);
    expect(await me(sign(claims, devSecret())), 'control: re-signed token with the dev secret is accepted').toBe(200);
    expect(await me(sign(claims, 'this-is-not-the-secret-this-is-not-the-secret'))).toBe(401);
  });

  test('SEC-06 an expired JWT is rejected', async ({ guard }) => {
    guard.allow({ status: 401 });
    const real = (await login(ADMIN_USER, ADMIN_PASSWORD)).body.accessToken as string;
    const claims = claimsOf(real);
    const past = Math.floor(Date.now() / 1000) - 3600;
    expect(await me(sign({ ...claims, iat: past - 900, exp: past }, devSecret()))).toBe(401);
  });

  test('SEC-05b malformed, unsigned (alg none) and tampered tokens are rejected', async ({ guard }) => {
    guard.allow({ status: 401 });
    const real = (await login(ADMIN_USER, ADMIN_PASSWORD)).body.accessToken as string;
    const claims = claimsOf(real);
    const none = `${b64u(JSON.stringify({ alg: 'none', typ: 'JWT' }))}.${b64u(JSON.stringify(claims))}.`;
    expect(await me(none)).toBe(401);
    expect(await me('not-a-jwt')).toBe(401);
    const [h, , s] = real.split('.');
    const tampered = `${h}.${b64u(JSON.stringify({ ...claims, roles: ['ADMIN'], usr: 'someone-else' }))}.${s}`;
    expect(await me(tampered)).toBe(401);
  });

  test('SEC-07 production profile disables Swagger/OpenAPI (configuration check)', async () => {
    const prod = readFileSync(repo('backend/src/main/resources/application-prod.yml'), 'utf-8');
    expect(prod).toMatch(/api-docs\.enabled:\s*\$\{SWAGGER_ENABLED:false\}/);
    expect(prod).toMatch(/swagger-ui\.enabled:\s*\$\{SWAGGER_ENABLED:false\}/);
  });

  test('SEC-08 /actuator/health is public; /actuator/prometheus needs authentication', async ({ guard }) => {
    guard.allow({ status: [401, 403, 404] });
    const ctx = await rawApi();
    expect((await ctx.get('/actuator/health')).status()).toBe(200);
    const prom = await ctx.get('/actuator/prometheus');
    expect([401, 403]).toContain(prom.status());
    const env = await ctx.get('/actuator/env');
    expect([401, 403, 404]).toContain(env.status());
  });

  test('SEC-09 no passwords or the JWT secret appear in the backend log', async () => {
    const logPath = process.env['E2E_BACKEND_LOG'];
    expect(logPath, 'set E2E_BACKEND_LOG to the backend log file so SEC-09 can scan it').toBeTruthy();
    const log = readFileSync(logPath!, 'utf-8');
    for (const secret of [ADMIN_PASSWORD, ROLE_PASSWORD, TEMP_PASSWORD, 'Fresh#Passw0rd-77', devSecret()]) {
      expect(log.includes(secret), 'a secret value is present in the backend log').toBe(false);
    }
    const token = (await login(ADMIN_USER, ADMIN_PASSWORD)).body.accessToken as string;
    expect(log.includes(token.split('.')[2]), 'a JWT signature is present in the log').toBe(false);
  });

  test('SEC-08b the operational endpoints need SYSTEM_MONITOR: a signed-in non-admin gets 403 on /actuator/metrics and /actuator/prometheus', async ({ guard }) => {
    guard.allow({ status: [401, 403, 404] });
    const store = new Api('storemgr1');
    for (const path of ['/actuator/metrics', '/actuator/prometheus']) {
      expect((await store.get(path)).status, `${path} for a non-admin`).toBe(403);
      const anon = await (await rawApi()).get(path);
      expect(anon.status(), `${path} anonymously`).toBe(401);
      // the dev profile exposes only health and info, so for the administrator the path answers 404, never 401/403
      const adm = (await new Api(ADMIN_USER, ADMIN_PASSWORD).get(path)).status;
      expect([200, 404], `${path} for the administrator`).toContain(adm);
    }
  });

  test('SEC-10 a signed-in user cannot read another route-level secret: /api/admin/** is 403 for a non-admin', async ({ guard }) => {
    guard.allow({ status: 403 });
    const op = new Api('operator1');
    for (const path of ['/api/admin/users', '/api/audit-logs', '/api/admin/roles']) {
      const r = await op.get(path);
      expect(r.status, path).toBe(403);
    }
  });
});
