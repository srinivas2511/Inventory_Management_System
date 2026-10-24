import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { GstEInvoiceComponent } from './gst-einvoice.component';
import { EInvoiceDto } from './integration.api';

function makeInvoice(id: number): EInvoiceDto {
  return {
    id,
    dispatchId: 10 + id,
    irn: null,
    ackNo: null,
    ackDate: null,
    status: 'GENERATED',
    generatedAt: '2026-10-24T00:00:00Z',
  };
}

describe('GstEInvoiceComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GstEInvoiceComponent],
      providers: [provideAnimationsAsync(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GST-1: loads existing e-invoices on init', () => {
    const fixture = TestBed.createComponent(GstEInvoiceComponent);
    fixture.detectChanges();
    http.expectOne('/api/integration/gst-einvoice').flush([makeInvoice(1), makeInvoice(2)]);
    fixture.detectChanges();
    expect(fixture.componentInstance.invoices().length).toBe(2);
  });

  it('GST-2: shows empty message when no invoices', () => {
    const fixture = TestBed.createComponent(GstEInvoiceComponent);
    fixture.detectChanges();
    http.expectOne('/api/integration/gst-einvoice').flush([]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No e-invoices generated yet');
  });

  it('GST-3: generate posts to /generate and reloads list', () => {
    const fixture = TestBed.createComponent(GstEInvoiceComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne('/api/integration/gst-einvoice').flush([]);

    comp.generateForm.setValue({ dispatchId: 5 });
    comp.generate();
    http.expectOne('/api/integration/gst-einvoice/generate').flush(makeInvoice(99));
    http.expectOne('/api/integration/gst-einvoice').flush([makeInvoice(99)]);
    expect(comp.invoices().length).toBe(1);
  });

  it('GST-4: generating signal is true during generate', () => {
    const fixture = TestBed.createComponent(GstEInvoiceComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne('/api/integration/gst-einvoice').flush([]);

    comp.generateForm.setValue({ dispatchId: 5 });
    comp.generate();
    expect(comp.generating()).toBeTrue();
    http.expectOne('/api/integration/gst-einvoice/generate').flush(makeInvoice(1));
    http.expectOne('/api/integration/gst-einvoice').flush([]);
    expect(comp.generating()).toBeFalse();
  });

  it('GST-5: generate is disabled when form is invalid', () => {
    const fixture = TestBed.createComponent(GstEInvoiceComponent);
    fixture.detectChanges();
    http.expectOne('/api/integration/gst-einvoice').flush([]);
    fixture.detectChanges();
    expect(fixture.componentInstance.generateForm.invalid).toBeTrue();
  });
});
