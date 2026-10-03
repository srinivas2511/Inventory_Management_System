import { netUnitPrice } from './crm-quotation-form.component';

describe('netUnitPrice (quotation preview arithmetic)', () => {
  it('keeps the list price when there is no discount', () => {
    expect(netUnitPrice(12.5, 0)).toBe(12.5);
  });

  it('applies a percentage discount', () => {
    expect(netUnitPrice(200, 10)).toBe(180);
    expect(netUnitPrice(12.5, 10)).toBe(11.25);
    expect(netUnitPrice(200, 100)).toBe(0);
  });

  it('rounds to 4 places like the server', () => {
    expect(netUnitPrice(10.3333, 15)).toBe(8.7833);
  });
});
