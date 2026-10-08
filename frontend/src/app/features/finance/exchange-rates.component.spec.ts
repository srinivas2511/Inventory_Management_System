/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { ExchangeRatesComponent } from './exchange-rates.component';

const periods = [
  {
    id: 21,
    periodName: 'FX-JAN-2012',
    fiscalYear: 2011,
    periodNumber: 21,
    startDate: '2012-01-01',
    endDate: '2012-01-31',
    status: 'OPEN',
  },
];

describe('ExchangeRatesComponent', () => {
  let fixture: ComponentFixture<ExchangeRatesComponent>;
  let backend: HttpTestingController;
  let instance: Record<string, any>;

  function setup(permissions: string[]): void {
    TestBed.configureTestingModule({
      imports: [ExchangeRatesComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'fin',
      fullName: 'Finance',
      email: 'f@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ExchangeRatesComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend.expectOne('/api/periods').flush(periods);
    backend
      .expectOne((r) => r.url === '/api/finance/exchange-rates')
      .flush([{ id: 1, fromCurrency: 'USD', toCurrency: 'INR', periodId: 21, periodName: 'FX-JAN-2012', rate: 80 }]);
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => backend.verify());

  it('lists the rates, and hides the entry form from a user who cannot maintain them', () => {
    setup(['GL_VIEW']);

    expect(text()).toContain('USD to INR');
    expect(text()).toContain('FX-JAN-2012');
    expect(text()).toContain('80.00');
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
  });

  it('saves a rate with an upper-case currency and reloads the list', () => {
    setup(['GL_VIEW', 'EXCHANGE_RATE_MANAGE']);
    instance['form'].setValue({ fromCurrency: 'eur', periodId: 21, rate: 88.5 });

    instance['save']();

    const put = backend.expectOne('/api/finance/exchange-rates');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ fromCurrency: 'EUR', periodId: 21, rate: 88.5 });
    put.flush({ id: 2, fromCurrency: 'EUR', toCurrency: 'INR', periodId: 21, periodName: 'FX-JAN-2012', rate: 88.5 });
    backend.expectOne((r) => r.url === '/api/finance/exchange-rates').flush([]);
  });

  it('refuses a currency that is not three letters and posts nothing', () => {
    setup(['GL_VIEW', 'EXCHANGE_RATE_MANAGE']);
    instance['form'].setValue({ fromCurrency: 'US', periodId: 21, rate: 80 });

    instance['save']();

    backend.expectNone('/api/finance/exchange-rates');
    expect(instance['form'].controls.fromCurrency.hasError('pattern')).toBe(true);
  });
});
