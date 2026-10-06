import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { DataExchangeComponent } from './data-exchange.component';
import { ImportResult } from './integration.api';

const dryRunResult: ImportResult = {
  entity: 'customers',
  dryRun: true,
  totalRows: 3,
  valid: 2,
  created: 0,
  failed: 1,
  rowsTruncated: false,
  rows: [
    { rowNumber: 2, key: 'C-1', status: 'OK', message: null },
    { rowNumber: 3, key: 'C-2', status: 'OK', message: null },
    { rowNumber: 4, key: 'C-3', status: 'ERROR', message: 'gstNumber Invalid GST number format' },
  ],
};

describe('DataExchangeComponent', () => {
  let fixture: ComponentFixture<DataExchangeComponent>;
  let backend: HttpTestingController;

  const q = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const all = (id: string): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${id}"]`));

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'admin',
      fullName: 'Admin',
      email: 'a@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(DataExchangeComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    if (permissions.includes('DATA_IMPORT')) {
      backend.expectOne('/api/integration/import').flush(['materials', 'products', 'customers', 'suppliers']);
    }
    backend.expectOne('/api/integration/export').flush(['customers', 'materials']);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataExchangeComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('validates a file first and shows what would happen row by row', () => {
    setup(['DATA_IMPORT', 'DATA_EXPORT']);
    const c = fixture.componentInstance;
    expect(c.importEntity.value).toBe('materials');
    c.importEntity.setValue('customers');
    expect((q('run') as HTMLButtonElement).disabled).toBeTrue();

    const file = new File(['customerCode,name\nC-1,Alpha'], 'customers.csv', { type: 'text/csv' });
    c.onFile({ target: { files: [file] } } as unknown as Event);
    fixture.detectChanges();
    expect(q('run')!.textContent).toContain('Validate');
    (q('run') as HTMLButtonElement).click();

    const req = backend.expectOne((r) => r.method === 'POST' && r.url === '/api/integration/import/customers');
    expect(req.request.params.get('dryRun')).toBe('true');
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(dryRunResult);
    fixture.detectChanges();

    expect(q('summary')!.textContent).toContain('3 rows');
    expect(q('summary')!.textContent).toContain('2 valid');
    expect(q('summary')!.textContent).toContain('1 with errors');
    expect(all('result-row')).toHaveSize(3);
    expect(all('result-row')[2].textContent).toContain('Invalid GST number format');
  });

  it('imports for real only after the dry-run switch is turned off', () => {
    setup(['DATA_IMPORT', 'DATA_EXPORT']);
    const c = fixture.componentInstance;
    c.importEntity.setValue('suppliers');
    c.onFile({ target: { files: [new File(['x'], 's.csv')] } } as unknown as Event);
    c.dryRun.set(false);
    fixture.detectChanges();
    expect(q('run')!.textContent).toContain('Import');

    (q('run') as HTMLButtonElement).click();
    const req = backend.expectOne('/api/integration/import/suppliers?dryRun=false');
    req.flush({ ...dryRunResult, entity: 'suppliers', dryRun: false, created: 2, valid: 0 });
    fixture.detectChanges();
    expect(q('summary')!.textContent).toContain('2 created');
  });

  it('clears an old result when a different file is chosen', () => {
    setup(['DATA_IMPORT', 'DATA_EXPORT']);
    const c = fixture.componentInstance;
    c.result.set(dryRunResult);
    c.onFile({ target: { files: [new File(['x'], 'next.csv')] } } as unknown as Event);
    expect(c.result()).toBeNull();
  });

  it('exports the chosen entity in the chosen format and range', () => {
    setup(['DATA_EXPORT']);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');
    const c = fixture.componentInstance;
    expect((q('export') as HTMLButtonElement).disabled).toBeTrue();

    c.exportEntity.setValue('customers');
    c.format.setValue('json');
    c.from.setValue('2026-01-01');
    fixture.detectChanges();
    (q('export') as HTMLButtonElement).click();

    const req = backend.expectOne((r) => r.url === '/api/integration/export/customers');
    expect(req.request.params.get('format')).toBe('json');
    expect(req.request.params.get('from')).toBe('2026-01-01');
    expect(req.request.params.has('to')).toBeFalse();
    req.flush(new Blob(['[]']));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('hides import and Tally from someone who may only export', () => {
    setup(['DATA_EXPORT']);
    expect(q('import-entity')).toBeNull();
    expect(q('tally-ledgers')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Column template');
  });

  it('downloads Tally ledgers and vouchers for the chosen period', () => {
    setup(['DATA_EXPORT', 'TALLY_EXPORT']);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');
    const c = fixture.componentInstance;

    c.tallyLedgers();
    backend.expectOne('/api/integration/tally/ledgers').flush(new Blob(['<ENVELOPE/>']));

    c.tallyFrom.setValue('2026-10-01');
    c.tallyTo.setValue('2026-10-31');
    c.tallyVouchers();
    const req = backend.expectOne((r) => r.url === '/api/integration/tally/vouchers');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-10-31');
    req.flush(new Blob(['<ENVELOPE/>']));
    expect(URL.createObjectURL).toHaveBeenCalledTimes(2);
  });
});
