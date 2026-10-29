import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { PlanBoardComponent } from './plan-board.component';

describe('PlanBoardComponent', () => {
  let fixture: ComponentFixture<PlanBoardComponent>;
  let component: PlanBoardComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlanBoardComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(PlanBoardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should render the plan board title', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/production/plan-board');
    req.flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Production Plan Board');
  });

  it('should display operations returned by the API', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/production/plan-board');
    req.flush([
      {
        id: 1,
        sequenceNo: 10,
        operationId: 1,
        operationName: 'Cutting',
        machineId: null,
        machineName: null,
        assignedOperatorId: null,
        assignedOperatorName: null,
        inspectionPoint: false,
        status: 'PENDING',
        inputQty: 0,
        goodQty: 0,
        rejectedQty: 0,
        scrapQty: 0,
        finalOperation: false,
        productionOrderNumber: 'PO-001',
        plannedStart: null,
        plannedEnd: null,
      },
    ]);
    fixture.detectChanges();
    expect(component.operations.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('PO-001');
  });

  it('should show empty state when no operations exist', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/production/plan-board').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No active operations scheduled');
  });
});
