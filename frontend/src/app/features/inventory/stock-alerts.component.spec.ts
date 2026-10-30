import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { StockAlertsComponent, BelowMinimumDto } from './stock-alerts.component';

const item: BelowMinimumDto = {
  materialId: 1,
  materialCode: 'RM-001',
  name: 'Steel Wire',
  minStock: 100,
  onHand: 30,
  uom: 'KG',
  shortage: 70,
};

describe('StockAlertsComponent', () => {
  let fixture: ComponentFixture<StockAlertsComponent>;
  let component: StockAlertsComponent;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockAlertsComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(StockAlertsComponent);
    component = fixture.componentInstance;
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/inventory/below-minimum').flush([item]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show items below minimum', () => {
    expect(component.items().length).toBe(1);
    expect(component.items()[0].materialCode).toBe('RM-001');
  });

  it('should show shortage quantity', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('RM-001');
  });
});
