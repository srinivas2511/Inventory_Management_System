import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { DispatchListComponent } from './dispatch-list.component';
import { DispatchDto } from './sales.api';

const mockDispatches: DispatchDto[] = [
  {
    id: 1,
    dispatchNumber: 'DN/26/00001',
    salesOrderId: 1,
    orderNumber: 'SO/26/00001',
    customerId: 10,
    customerName: 'Acme Corp',
    dispatchDate: '2026-10-15',
    transporter: 'FedEx',
    vehicleNo: 'MH01AB1234',
    lrNumber: 'LR-001',
    deliveryAddress: '123 Test St',
    invoiceReference: 'INV-001',
    status: 'DISPATCHED',
    notes: null,
    items: [],
  },
];

const pageResponse = { content: mockDispatches, totalElements: 1, totalPages: 1, size: 20, number: 0 };

describe('DispatchListComponent', () => {
  let fixture: ComponentFixture<DispatchListComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DispatchListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const session = TestBed.inject(SessionStore);
    session.setUser({
      id: 1,
      username: 'test',
      fullName: 'Test',
      email: 't@t.com',
      roles: [],
      permissions: ['DISPATCH_VIEW', 'SALES_MANAGE'],
      mustChangePassword: false,
    });

    fixture = TestBed.createComponent(DispatchListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows loading spinner initially', () => {
    backend.expectOne((r) => r.url.includes('/api/sales/dispatches'));
    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
  });

  it('renders dispatch rows after load', () => {
    backend.expectOne((r) => r.url.includes('/api/sales/dispatches')).flush(pageResponse);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('DN/26/00001');
    expect(fixture.nativeElement.textContent).toContain('Acme Corp');
    expect(fixture.nativeElement.textContent).toContain('FedEx');
  });

  it('shows empty state when no dispatches', () => {
    backend
      .expectOne((r) => r.url.includes('/api/sales/dispatches'))
      .flush({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
      });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No dispatches found');
  });
});
