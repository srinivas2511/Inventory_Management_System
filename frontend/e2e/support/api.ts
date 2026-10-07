import { APIRequestContext, request } from '@playwright/test';
import { ADMIN_PASSWORD, ADMIN_USER, API, ROLE_PASSWORD } from './env';

export interface ApiResult<T = any> {
  status: number;
  ok: boolean;
  body: T;
  headers: Record<string, string>;
}

let sharedCtx: APIRequestContext | undefined;

export async function rawApi(): Promise<APIRequestContext> {
  sharedCtx ??= await request.newContext({ baseURL: API, extraHTTPHeaders: { Accept: 'application/json' } });
  return sharedCtx;
}

export function passwordFor(username: string): string {
  return username === ADMIN_USER ? ADMIN_PASSWORD : ROLE_PASSWORD;
}

async function parse(res: { status(): number; ok(): boolean; headers(): Record<string, string>; text(): Promise<string> }): Promise<ApiResult> {
  const text = await res.text();
  let body: any = text;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    /* keep raw text */
  }
  return { status: res.status(), ok: res.ok(), body, headers: res.headers() };
}

export async function login(username: string, password: string): Promise<ApiResult> {
  const ctx = await rawApi();
  return parse(await ctx.post('/api/auth/login', { data: { username, password } }));
}

/** An authenticated API client acting as one user; re-logs in once on a 401 (15 minute tokens). */
export class Api {
  private token = '';

  constructor(
    readonly username: string,
    private readonly password: string = passwordFor(username),
  ) {}

  async signIn(): Promise<void> {
    const r = await login(this.username, this.password);
    if (r.status !== 200) {
      throw new Error(`API login failed for ${this.username}: HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    }
    this.token = r.body.accessToken;
  }

  async call(method: string, path: string, body?: unknown, opts: { token?: string | null } = {}): Promise<ApiResult> {
    const ctx = await rawApi();
    const send = async () => {
      if (opts.token === undefined && !this.token) await this.signIn();
      const bearer = opts.token === undefined ? this.token : opts.token;
      return parse(
        await ctx.fetch(path, {
          method,
          data: body === undefined ? undefined : body,
          headers: bearer ? { Authorization: `Bearer ${bearer}` } : {},
        }),
      );
    };
    let r = await send();
    if (r.status === 401 && opts.token === undefined) {
      await this.signIn();
      r = await send();
    }
    return r;
  }

  get = (path: string) => this.call('GET', path);
  post = (path: string, body?: unknown) => this.call('POST', path, body ?? {});
  put = (path: string, body?: unknown) => this.call('PUT', path, body ?? {});
  del = (path: string) => this.call('DELETE', path);

  /** GET that must succeed; returns the body (unwrapping a page's `content`). */
  async list<T = any>(path: string): Promise<T[]> {
    const r = await this.get(path);
    if (!r.ok) throw new Error(`GET ${path} -> ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    return Array.isArray(r.body) ? r.body : (r.body?.content ?? []);
  }

  /** Request that must succeed; fails loudly with the server's ProblemDetail. */
  async must(method: string, path: string, body?: unknown): Promise<any> {
    const r = await this.call(method, path, body);
    if (!r.ok) {
      throw new Error(`${method} ${path} -> HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 400)}`);
    }
    return r.body;
  }
}

const cache = new Map<string, Api>();

export function apiFor(username: string): Api {
  let a = cache.get(username);
  if (!a) {
    a = new Api(username);
    cache.set(username, a);
  }
  return a;
}

export const admin = () => apiFor(ADMIN_USER);

export function unique(prefix: string): string {
  return `${prefix}${Date.now().toString(36).toUpperCase()}${Math.floor(Math.random() * 1000)}`;
}
