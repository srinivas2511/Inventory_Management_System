import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps in-app paths, with their query string', () => {
    expect(safeReturnUrl('/inventory/stock')).toBe('/inventory/stock');
    expect(safeReturnUrl('/quality/inspections/5?tab=results')).toBe('/quality/inspections/5?tab=results');
  });

  it('drops anything that could leave the app', () => {
    expect(safeReturnUrl('https://evil.example.com/')).toBeNull();
    expect(safeReturnUrl('//evil.example.com/')).toBeNull();
    expect(safeReturnUrl('/\\evil.example.com')).toBeNull();
    expect(safeReturnUrl('javascript:alert(1)')).toBeNull();
    expect(safeReturnUrl('/ok\nSet-Cookie: x=1')).toBeNull();
  });

  it('drops the sign-in pages and the bare root, which would loop or say nothing', () => {
    expect(safeReturnUrl('/login')).toBeNull();
    expect(safeReturnUrl('/login?returnUrl=%2Fx')).toBeNull();
    expect(safeReturnUrl('/forgot-password')).toBeNull();
    expect(safeReturnUrl('/reset-password/abc')).toBeNull();
    expect(safeReturnUrl('/')).toBeNull();
  });

  it('copes with nothing at all and with absurd lengths', () => {
    expect(safeReturnUrl(null)).toBeNull();
    expect(safeReturnUrl(undefined)).toBeNull();
    expect(safeReturnUrl('')).toBeNull();
    expect(safeReturnUrl(`/${'a'.repeat(600)}`)).toBeNull();
  });
});
