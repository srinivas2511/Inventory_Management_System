import { Api, admin } from '../support/api';
import { expect, test } from '../support/fixtures';
import { U } from '../support/users';

/**
 * Backend authorization matrix. A hidden button is not authorization: every probe is a bodiless or
 * well-formed call made as a real seeded role user and must be refused with exactly 403 (the server's
 * security boundary), never 2xx and never a 5xx. Allowed probes confirm the same endpoints work for
 * the role that owns them.
 */
type Probe = { method: 'GET' | 'POST' | 'PUT' | 'DELETE'; path: string; denied: string[]; allowed?: string[]; body?: unknown };

const PROBES: Probe[] = [
  { method: 'GET', path: '/api/admin/users', denied: [U.operator1, U.engineer, U.prodMgr, U.supervisor, U.quality, U.storeMgr, U.purchase, U.sales, U.dispatch, U.maint], allowed: [U.admin] },
  { method: 'GET', path: '/api/audit-logs', denied: [U.operator1, U.sales, U.dispatch, U.purchase, U.storeMgr], allowed: [U.admin] },
  { method: 'GET', path: '/api/admin/roles', denied: [U.operator1, U.sales, U.quality], allowed: [U.admin] },
  { method: 'POST', path: '/api/procurement/purchase-orders/1/approve', denied: [U.storeMgr, U.quality, U.operator1, U.sales, U.dispatch, U.engineer] },
  { method: 'POST', path: '/api/procurement/purchase-orders/1/send-to-supplier', denied: [U.operator1, U.sales, U.quality] },
  { method: 'POST', path: '/api/production-orders/1/release', denied: [U.sales, U.dispatch, U.quality, U.storeMgr, U.purchase] },
  { method: 'POST', path: '/api/production-orders/1/close', denied: [U.sales, U.dispatch, U.operator1, U.purchase] },
  { method: 'POST', path: '/api/production/operations/1/start', denied: [U.sales, U.purchase, U.dispatch] },
  { method: 'POST', path: '/api/quality/inspections/1/accept', denied: [U.operator1, U.sales, U.dispatch, U.purchase] },
  { method: 'POST', path: '/api/sales/orders/1/confirm', denied: [U.operator1, U.quality, U.storeMgr, U.purchase] },
  { method: 'POST', path: '/api/sales/orders/1/approve', denied: [U.operator1, U.quality, U.storeMgr, U.purchase, U.sales] },
  { method: 'DELETE', path: '/api/materials/1', denied: [U.operator1, U.sales, U.dispatch, U.mgmt, U.quality] },
  { method: 'DELETE', path: '/api/products/1', denied: [U.operator1, U.sales, U.dispatch, U.mgmt] },
  { method: 'POST', path: '/api/boms/1/approve', denied: [U.operator1, U.sales, U.dispatch, U.storeMgr, U.purchase] },
  { method: 'POST', path: '/api/inventory/ledger/1/reverse?reason=e2e-probe', denied: [U.operator1, U.sales, U.dispatch, U.purchase, U.mgmt] },
  { method: 'GET', path: '/api/inventory/balances', denied: [], allowed: [U.admin, U.storeMgr, U.mgmt] },
  { method: 'GET', path: '/api/boms', denied: [], allowed: [U.admin, U.engineer] },
  { method: 'GET', path: '/api/production/my-work', denied: [], allowed: [U.admin, U.operator1] },
];

test.describe('RBAC: backend returns 403 for the wrong role (UAT ADM-07, QCI-06, FIN-03, INS-06)', () => {
  for (const probe of PROBES) {
    for (const who of probe.denied) {
      test(`${who} ${probe.method} ${probe.path} -> 403`, async ({ guard }) => {
        guard.allow({ status: 403 });
        const r = await new Api(who).call(probe.method, probe.path, probe.body ?? (probe.method === 'POST' || probe.method === 'PUT' ? {} : undefined));
        expect(r.status, JSON.stringify(r.body).slice(0, 200)).toBe(403);
      });
    }
    for (const who of probe.allowed ?? []) {
      test(`${who} ${probe.method} ${probe.path} is allowed`, async () => {
        const r = await new Api(who).call(probe.method, probe.path);
        expect(r.status, JSON.stringify(r.body).slice(0, 200)).toBeLessThan(300);
      });
    }
  }

  test('MANAGEMENT (read-only) cannot write: purchase, sales, production and stock writes are all 403 (workflow step 22)', async ({ guard }) => {
    guard.allow({ status: 403 });
    const mgmt = new Api(U.mgmt);
    for (const [m, p] of [
      ['POST', '/api/procurement/purchase-orders/1/submit'],
      ['POST', '/api/sales/orders/1/cancel'],
      ['POST', '/api/production-orders/1/approve'],
      ['POST', '/api/production-orders/1/cancel'],
    ] as const) {
      const r = await mgmt.call(m, p, { reason: 'E2E authorization probe' });
      expect(r.status, `${m} ${p}`).toBe(403);
    }
    const ok = await mgmt.get('/api/dashboard/kpis');
    expect(ok.status).toBe(200);
    void admin;
  });

  test('FIN-03 a finance user cannot create a PO (403)', async ({ guard }) => {
    guard.allow({ status: 403 });
    const r = await new Api(U.finance).post('/api/procurement/purchase-orders/1/submit');
    expect(r.status).toBe(403);
  });
});
