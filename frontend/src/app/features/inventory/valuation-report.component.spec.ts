import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { ValuationReportComponent } from './valuation-report.component';
import { InventoryApi, ValuationReportDto } from './inventory.api';
import { LookupApi } from '../../shared/lookup.api';

const MOVING_AVG_REPORT: ValuationReportDto = {
  costMethod: 'MOVING_AVERAGE',
  lines: [
    {
      itemType: 'MATERIAL',
      itemId: 1,
      itemCode: 'RM-001',
      itemName: 'Steel Wire',
      uom: 'KG',
      qtyOnHand: 100,
      avgUnitCost: 50,
      standardCost: 50,
      totalValue: 5000,
    },
    {
      itemType: 'PRODUCT',
      itemId: 2,
      itemCode: 'FG-001',
      itemName: 'Spring 30mm',
      uom: 'PC',
      qtyOnHand: 20,
      avgUnitCost: 200,
      standardCost: 200,
      totalValue: 4000,
    },
  ],
  rmTotal: 5000,
  fgTotal: 4000,
  grandTotal: 9000,
};

const STANDARD_REPORT: ValuationReportDto = { ...MOVING_AVG_REPORT, costMethod: 'STANDARD' };

describe('ValuationReportComponent', () => {
  let fixture: ComponentFixture<ValuationReportComponent>;
  let component: ValuationReportComponent;
  let apiSpy: jasmine.SpyObj<InventoryApi>;
  let lookupSpy: jasmine.SpyObj<LookupApi>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('InventoryApi', ['getValuationReport']);
    lookupSpy = jasmine.createSpyObj('LookupApi', ['warehouses']);
    apiSpy.getValuationReport.and.returnValue(of(MOVING_AVG_REPORT));
    lookupSpy.warehouses.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ValuationReportComponent, NoopAnimationsModule],
      providers: [
        { provide: InventoryApi, useValue: apiSpy },
        { provide: LookupApi, useValue: lookupSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ValuationReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('VR-1: loads report on init and displays grand total', () => {
    expect(apiSpy.getValuationReport).toHaveBeenCalledWith(undefined);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('9,000');
    expect(compiled.textContent).toContain('Moving Average');
  });

  it('VR-2: MOVING_AVERAGE columns do not include Std Cost', () => {
    expect(component.displayedColumns()).not.toContain('standardCost');
  });

  it('VR-3: STANDARD costing adds Std Cost column', () => {
    apiSpy.getValuationReport.and.returnValue(of(STANDARD_REPORT));
    component.load();
    fixture.detectChanges();
    expect(component.displayedColumns()).toContain('standardCost');
  });

  it('VR-4: reloads when warehouse selection changes', () => {
    apiSpy.getValuationReport.calls.reset();
    component.selectedWarehouseId = 1;
    component.load();
    expect(apiSpy.getValuationReport).toHaveBeenCalledWith(1);
  });

  it('VR-5: exportCsv generates a CSV blob without error', () => {
    const anchor = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(anchor);
    spyOn(anchor, 'click');
    component.exportCsv();
    expect(anchor.click).toHaveBeenCalled();
    expect(anchor.download).toMatch(/inventory-valuation-.+\.csv/);
  });
});
