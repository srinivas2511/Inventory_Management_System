import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { StockBalanceListComponent } from './stock-balance-list.component';
import { InventoryApi, InventoryBalanceDto } from './inventory.api';
import { LookupApi, LookupOption } from '../../shared/lookup.api';

const WAREHOUSES: LookupOption[] = [
  { id: 1, code: 'WH-01', name: 'Main Warehouse' },
  { id: 2, code: 'WH-02', name: 'Stores' },
];

const BALANCES: InventoryBalanceDto[] = [
  {
    id: 1,
    warehouseId: 1,
    warehouseName: 'Main Warehouse',
    locationId: 10,
    locationName: 'Raw Materials Store',
    itemType: 'MATERIAL',
    materialId: 5,
    productId: undefined,
    materialBatchId: undefined,
    fgBatchId: undefined,
    stockStatus: 'AVAILABLE',
    qtyOnHand: 100,
    qtyReserved: 0,
    qtyAvailable: 100,
    avgUnitCost: 50,
    totalValue: 5000,
    updatedAt: '2026-10-01T00:00:00Z',
  },
  {
    id: 2,
    warehouseId: 2,
    warehouseName: 'Stores',
    locationId: 20,
    locationName: 'FG Store',
    itemType: 'PRODUCT',
    materialId: undefined,
    productId: 3,
    materialBatchId: undefined,
    fgBatchId: 7,
    stockStatus: 'AVAILABLE',
    qtyOnHand: 50,
    qtyReserved: 10,
    qtyAvailable: 40,
    avgUnitCost: 200,
    totalValue: 10000,
    updatedAt: '2026-10-01T00:00:00Z',
  },
];

describe('StockBalanceListComponent', () => {
  let fixture: ComponentFixture<StockBalanceListComponent>;
  let apiSpy: jasmine.SpyObj<InventoryApi>;
  let lookupSpy: jasmine.SpyObj<LookupApi>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('InventoryApi', ['getBalances']);
    lookupSpy = jasmine.createSpyObj('LookupApi', ['warehouses']);
    apiSpy.getBalances.and.returnValue(of(BALANCES));
    lookupSpy.warehouses.and.returnValue(of(WAREHOUSES));

    await TestBed.configureTestingModule({
      imports: [StockBalanceListComponent, NoopAnimationsModule],
      providers: [
        { provide: InventoryApi, useValue: apiSpy },
        { provide: LookupApi, useValue: lookupSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StockBalanceListComponent);
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads warehouses into the filter dropdown on init', () => {
    expect(lookupSpy.warehouses).toHaveBeenCalled();
    expect(fixture.componentInstance.warehouses()).toEqual(WAREHOUSES);
  });

  it('displays warehouse and location names in the table (not IDs)', () => {
    const cells = fixture.nativeElement.querySelectorAll('td[mat-cell]');
    const cellTexts = Array.from(cells).map((c) => (c as HTMLElement).textContent?.trim());
    expect(cellTexts.some((t) => t?.includes('Main Warehouse'))).toBeTrue();
    expect(cellTexts.some((t) => t?.includes('Raw Materials Store'))).toBeTrue();
    expect(cellTexts.some((t) => t?.includes('Stores'))).toBeTrue();
    expect(cellTexts.some((t) => t?.includes('FG Store'))).toBeTrue();
  });

  it('calls getBalances with warehouseId when warehouse filter changes', () => {
    fixture.componentInstance.warehouseCtrl.setValue(1);
    fixture.detectChanges();
    expect(apiSpy.getBalances).toHaveBeenCalledWith(1, undefined, undefined);
  });

  it('calls getBalances with stockStatus when status filter changes', () => {
    fixture.componentInstance.statusCtrl.setValue('QUARANTINE');
    fixture.detectChanges();
    expect(apiSpy.getBalances).toHaveBeenCalledWith(undefined, undefined, 'QUARANTINE');
  });

  it('renders the warehouse filter dropdown', () => {
    const select = fixture.nativeElement.querySelector('[data-testid="warehouse-filter"]');
    expect(select).toBeTruthy();
  });
});
