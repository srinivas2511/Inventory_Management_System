import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RegistersComponent } from './registers.component';

describe('RegistersComponent', () => {
  let fixture: ComponentFixture<RegistersComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegistersComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(RegistersComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    http.expectOne('/api/registers').flush([]);
  });

  it('should load register codes on init', () => {
    fixture.detectChanges();
    const req = http.expectOne('/api/registers');
    req.flush(['REG_SALES_INVOICE', 'REG_PAYMENT']);
    fixture.detectChanges();
    expect(fixture.componentInstance.codes()).toContain('REG_SALES_INVOICE');
  });

  it('should load register detail on selection', () => {
    fixture.detectChanges();
    http.expectOne('/api/registers').flush(['REG_PAYMENT']);
    fixture.componentInstance.loadRegister('REG_PAYMENT');
    const req = http.expectOne('/api/registers/REG_PAYMENT');
    req.flush({ code: 'REG_PAYMENT', name: 'Payment Register', rows: [] });
    expect(fixture.componentInstance.register()!.code).toBe('REG_PAYMENT');
  });
});
