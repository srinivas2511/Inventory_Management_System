import { expect, Page, test as base } from '@playwright/test';
import { ROLE_PASSWORD, ADMIN_PASSWORD, ADMIN_USER } from './env';

interface AllowRule {
  status?: number | number[];
  method?: string;
  url?: RegExp;
}

/**
 * Collects browser console errors, uncaught page errors and failed API calls for
 * every page of a test and fails the test at the end if any are unexpected.
 * Intentional negative tests opt in with guard.allow({status: 403}) etc.
 */
export class Guard {
  readonly errors: string[] = [];
  private readonly rules: AllowRule[] = [
    // The session-restore initializer asks the server for a refresh on every cold start; 401/400 = "no session".
    { url: /\/api\/auth\/refresh$/, status: [400, 401] },
  ];
  private readonly consoleAllow: RegExp[] = [/Failed to load resource/];

  allow(rule: AllowRule): void {
    this.rules.push(rule);
  }

  /** Allow a known application defect so later steps can still be verified; the defect keeps its own failing test. */
  allowKnown(defectId: string, rule: AllowRule): void {
    this.known.add(defectId);
    this.rules.push(rule);
  }

  readonly known = new Set<string>();

  allowConsole(re: RegExp): void {
    this.consoleAllow.push(re);
  }

  attach(page: Page): void {
    page.on('pageerror', (e) => this.errors.push(`pageerror: ${e.message}`.slice(0, 300)));
    page.on('console', (m) => {
      if (m.type() !== 'error') return;
      const t = m.text();
      if (this.consoleAllow.some((re) => re.test(t))) return;
      this.errors.push(`console.error: ${t}`.slice(0, 300));
    });
    page.on('response', (r) => {
      const url = r.url();
      const s = r.status();
      if (s < 400 || !/\/(api|actuator)\//.test(url)) return;
      const method = r.request().method();
      const ok = this.rules.some(
        (rule) =>
          (rule.status === undefined || [rule.status].flat().includes(s)) &&
          (!rule.method || rule.method === method) &&
          (!rule.url || rule.url.test(url)),
      );
      if (!ok) this.errors.push(`HTTP ${s} ${method} ${new URL(url).pathname}`);
    });
  }
}

export const test = base.extend<{ guard: Guard }>({
  guard: [
    async ({ context }, use) => {
      const g = new Guard();
      context.pages().forEach((p) => g.attach(p));
      context.on('page', (p) => g.attach(p));
      await use(g);
      expect(g.errors, 'unexpected browser console errors or failed API calls').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };

/** Sign in through the real login form. */
export async function uiLogin(page: Page, username: string, password?: string): Promise<void> {
  const pw = password ?? (username === ADMIN_USER ? ADMIN_PASSWORD : ROLE_PASSWORD);
  await page.goto('/login');
  await page.getByTestId('username').fill(username);
  await page.getByTestId('password').fill(pw);
  await page.getByTestId('login-submit').click();
  await expect(page).not.toHaveURL(/\/login/);
}

export async function uiLogout(page: Page): Promise<void> {
  await page.getByTestId('user-menu-trigger').click();
  await page.getByTestId('logout-btn').click();
  await expect(page).toHaveURL(/\/login/);
}

import type { Browser, BrowserContext } from '@playwright/test';
import { BASE } from './env';

export interface Actor {
  page: Page;
  context: BrowserContext;
  close(): Promise<void>;
}

/** A fresh, separately signed-in browser session for one user (guard attached). */
export async function openAs(browser: Browser, guard: Guard, username: string, path?: string): Promise<Actor> {
  const context = await browser.newContext({ baseURL: BASE });
  const page = await context.newPage();
  guard.attach(page);
  guard.allowKnown('D-04 dashboard KPI call returns 403 for roles without DASHBOARD_VIEW', { status: 403, url: /\/api\/dashboard\/kpis/ });
  await uiLogin(page, username);
  if (path) await page.goto(path);
  return { page, context, close: () => context.close() };
}
