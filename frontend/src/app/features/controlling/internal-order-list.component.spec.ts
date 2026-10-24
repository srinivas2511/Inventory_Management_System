import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { ControllingApi, InternalOrderCostRow, InternalOrderDto } from './controlling.api';
import { InternalOrderListComponent } from './internal-order-list.component';

const ORDER: InternalOrderDto = {
  id: 1,
  orderNumber: 'IO-001',
  description: 'Factory expansion',
  orderType: 'CAPEX',
  responsibleCostCentreId: null,
  budgetAmount: 500000,
  actualAmount: 120000,
  status: 'OPEN',
  settlementCostCentreId: null,
  settledAmount: null,
  settledAt: null,
  settledBy: null,
  settlementJournalId: null,
  startDate: '2026-04-01',
  endDate: '2027-03-31',
};

const COST: InternalOrderCostRow = {
  glAccountId: 5,
  accountCode: '5301',
  accountName: 'Labour',
  totalDebit: 120000,
  totalCredit: 0,
  netAmount: 120000,
};

describe('InternalOrderListComponent', () => {
  let fixture: ComponentFixture<InternalOrderListComponent>;
  let api: jasmine.SpyObj<ControllingApi>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(async () => {
    api = jasmine.createSpyObj('ControllingApi', [
      'getInternalOrders',
      'getCostCentres',
      'createInternalOrder',
      'updateInternalOrder',
      'getInternalOrderCosts',
      'settleInternalOrder',
    ]);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    api.getInternalOrders.and.returnValue(of([ORDER]));
    api.getCostCentres.and.returnValue(of([]));
    api.createInternalOrder.and.returnValue(of(ORDER));
    api.updateInternalOrder.and.returnValue(of(ORDER));
    api.getInternalOrderCosts.and.returnValue(of([COST]));
    api.settleInternalOrder.and.returnValue(of({ ...ORDER, status: 'SETTLED' }));

    await TestBed.configureTestingModule({
      imports: [InternalOrderListComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ControllingApi, useValue: api },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InternalOrderListComponent);
    fixture.detectChanges();
  });

  it('should load orders on init (IO-1)', () => {
    expect(fixture.componentInstance).toBeTruthy();
    expect(api.getInternalOrders).toHaveBeenCalledOnceWith();
    expect(fixture.componentInstance.orders().length).toBe(1);
  });

  it('should show create form on new (IO-2)', () => {
    fixture.componentInstance.newOrder();
    expect(fixture.componentInstance.editing()).toBeTrue();
    expect(fixture.componentInstance.editingId()).toBeNull();
  });

  it('should populate form when editing (IO-3)', () => {
    fixture.componentInstance.editOrder(ORDER);
    expect(fixture.componentInstance.editing()).toBeTrue();
    expect(fixture.componentInstance.editingId()).toBe(1);
    expect(fixture.componentInstance.form.value.orderNumber).toBe('IO-001');
  });

  it('should load costs on viewCosts (IO-4)', () => {
    fixture.componentInstance.viewCosts(ORDER);
    expect(api.getInternalOrderCosts).toHaveBeenCalledWith(1);
    expect(fixture.componentInstance.costs().length).toBe(1);
    expect(fixture.componentInstance.selectedOrder()?.orderNumber).toBe('IO-001');
  });

  it('should keep orders empty when load fails (IO-5)', () => {
    api.getInternalOrders.and.returnValue(throwError(() => ({ status: 500 })));
    fixture.componentInstance.orders.set([]);
    fixture.componentInstance.ngOnInit();
    expect(fixture.componentInstance.orders().length).toBe(0);
  });
});
