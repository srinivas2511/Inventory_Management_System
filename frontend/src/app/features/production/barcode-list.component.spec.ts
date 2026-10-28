import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BarcodeListComponent, BarcodeLabelDto } from './barcode-list.component';

const label: BarcodeLabelDto = {
  id: 1,
  labelType: 'MATERIAL',
  refType: 'MATERIAL',
  refId: 42,
  barcodeValue: 'MATERIAL-42-1234567890',
  qrData: null,
  printedAt: null,
  printCount: 0,
  createdAt: '2026-10-28T08:00:00Z',
};

describe('BarcodeListComponent', () => {
  let fixture: ComponentFixture<BarcodeListComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BarcodeListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(BarcodeListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/production/barcodes').flush([label]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('renders the table with one row', () => {
    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(1);
  });

  it('shows label type', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('MATERIAL');
  });

  it('shows barcode value', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('MATERIAL-42-1234567890');
  });
});
