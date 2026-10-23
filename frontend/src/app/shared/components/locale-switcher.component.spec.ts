import { activeLocale, switchedUrl } from './locale-switcher.component';

describe('activeLocale', () => {
  it('returns en-IN for root path', () => {
    expect(activeLocale('/')).toBe('en-IN');
  });

  it('returns en-IN for an arbitrary English path', () => {
    expect(activeLocale('/inventory/stock')).toBe('en-IN');
  });

  it('returns hi for /hi/', () => {
    expect(activeLocale('/hi/')).toBe('hi');
  });

  it('returns hi for /hi/some/path', () => {
    expect(activeLocale('/hi/dashboard')).toBe('hi');
  });

  it('returns kn for /kn/', () => {
    expect(activeLocale('/kn/')).toBe('kn');
  });

  it('returns kn for /kn/some/path', () => {
    expect(activeLocale('/kn/inventory/stock')).toBe('kn');
  });
});

describe('switchedUrl', () => {
  const origin = 'http://localhost:4200';

  it('switches from en-IN to hi', () => {
    const result = switchedUrl(`${origin}/dashboard`, '/hi/');
    expect(result).toBe(`${origin}/hi/dashboard`);
  });

  it('switches from en-IN to kn', () => {
    const result = switchedUrl(`${origin}/inventory/stock`, '/kn/');
    expect(result).toBe(`${origin}/kn/inventory/stock`);
  });

  it('switches from hi to kn', () => {
    const result = switchedUrl(`${origin}/hi/dashboard`, '/kn/');
    expect(result).toBe(`${origin}/kn/dashboard`);
  });

  it('switches from kn to en-IN', () => {
    const result = switchedUrl(`${origin}/kn/inventory`, '/');
    expect(result).toBe(`${origin}/inventory`);
  });

  it('returns same URL when switching to the current locale (en-IN to en-IN)', () => {
    const href = `${origin}/dashboard`;
    expect(switchedUrl(href, '/')).toBe(href);
  });

  it('preserves query string when switching', () => {
    const result = switchedUrl(`${origin}/hi/reports?year=2026`, '/kn/');
    expect(result).toBe(`${origin}/kn/reports?year=2026`);
  });
});
