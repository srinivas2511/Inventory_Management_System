import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TallyLedgerMappingComponent } from './tally-ledger-mapping.component';

describe('TallyLedgerMappingComponent', () => {
  let fixture: ComponentFixture<TallyLedgerMappingComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TallyLedgerMappingComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(TallyLedgerMappingComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create and load mappings', () => {
    fixture.detectChanges();
    const req = http.expectOne('/api/integration/tally/ledger-mappings');
    req.flush([{ id: 1, glAccountCode: '4001', tallyLedger: 'Sales', notes: '', createdAt: new Date().toISOString() }]);
    fixture.detectChanges();
    expect(fixture.componentInstance.mappings().length).toBe(1);
  });

  it('should show empty list when no mappings', () => {
    fixture.detectChanges();
    http.expectOne('/api/integration/tally/ledger-mappings').flush([]);
    fixture.detectChanges();
    expect(fixture.componentInstance.mappings().length).toBe(0);
  });

  it('save should be disabled with empty form', () => {
    fixture.detectChanges();
    http.expectOne('/api/integration/tally/ledger-mappings').flush([]);
    expect(fixture.componentInstance.form.invalid).toBeTrue();
  });
});
