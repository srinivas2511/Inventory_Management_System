import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { NegativeStockOverrideDto, NegativeStockOverridesComponent } from './negative-stock-overrides.component';

const rows: NegativeStockOverrideDto[] = [
  { itemType: 'MATERIAL', id: 1, code: 'WIRE-1', name: 'Wire 1mm', allowed: false },
  { itemType: 'MATERIAL', id: 2, code: 'WIRE-2', name: 'Wire 2mm', allowed: true },
  { itemType: 'PRODUCT', id: 10, code: 'SPR-01', name: 'Compression spring', allowed: false },
];

describe('NegativeStockOverridesComponent', () => {
  let fixture: ComponentFixture<NegativeStockOverridesComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NegativeStockOverridesComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NegativeStockOverridesComponent);
    fixture.detectChanges();
    http.expectOne('/api/inventory/negative-stock-overrides').flush(rows);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renders material rows in the Materials tab', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('WIRE-1');
    expect(text).toContain('WIRE-2');
  });

  it('separates materials and products into their respective signals', () => {
    const comp = fixture.componentInstance as unknown as { materials: () => unknown[]; products: () => unknown[] };
    expect(comp.materials().length).toBe(2);
    expect(comp.products().length).toBe(1);
  });
});
