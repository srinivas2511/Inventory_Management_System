import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { WarehouseApi } from '../warehouse/warehouse.api';
import { MaintenanceCostDto, MaintenanceRequestDto, SparePartDto } from './maintenance.api';
import { MaintenanceRequestDetailComponent, NEXT_STATUSES } from './maintenance-request-detail.component';

function request(status: string): MaintenanceRequestDto {
  return {
    id: 7,
    requestNumber: 'MR/26/00007',
    machineId: 3,
    requestType: 'CORRECTIVE',
    priority: 2,
    description: 'Spindle bearing noise',
    status,
    requestedBy: 1,
    assignedTo: null,
    scheduledDate: null,
    completedAt: null,
    resolutionNotes: null,
    createdAt: '2026-10-05T09:00:00Z',
  };
}

function cost(over: Partial<MaintenanceCostDto> = {}): MaintenanceCostDto {
  return {
    requestId: 7,
    requestNumber: 'MR/26/00007',
    machineId: 3,
    status: 'COMPLETED',
    costCentreId: 1,
    labourMinutes: 120,
    labourHours: 2,
    labourRatePerHour: 300,
    labourCost: 600,
    sparePartsCost: 120,
    totalCost: 720,
    labourCostFinal: true,
    labourJournalHeaderId: null,
    costPostedAt: null,
    ...over,
  };
}

const part: SparePartDto = {
  id: 1,
  sparePartCode: 'SP-BRG-01',
  name: 'Bearing 6204',
  description: null,
  uom: 'PCS',
  materialId: 40,
  compatibleMachineTypes: null,
  minStock: 2,
  reorderLevel: 5,
  standardCost: 40,
  active: true,
  onHandQty: 10,
  belowMinStock: false,
  version: 0,
};

describe('MaintenanceRequestDetailComponent', () => {
  let fixture: ComponentFixture<MaintenanceRequestDetailComponent>;
  let backend: HttpTestingController;
  let component: MaintenanceRequestDetailComponent;

  const ALL = [
    'MAINTENANCE_VIEW',
    'MAINTENANCE_MANAGE',
    'SPARE_PART_ISSUE',
    'MAINTENANCE_COST_VIEW',
    'MAINTENANCE_COST_POST',
  ];

  function flushRequest(status: string, c: MaintenanceCostDto | null = cost()): void {
    backend.expectOne('/api/maintenance/requests/7').flush(request(status));
    backend.expectOne('/api/maintenance/requests/7/activities').flush([
      {
        id: 1,
        maintenanceRequestId: 7,
        description: 'Strip and inspect',
        performedBy: 1,
        performedAt: '2026-10-05T10:00:00Z',
        durationMinutes: 90,
        sparePartsUsed: null,
      },
    ]);
    backend.expectOne('/api/maintenance/requests/7/spare-parts').flush([
      {
        id: 5,
        maintenanceRequestId: 7,
        sparePartId: 1,
        sparePartCode: 'SP-BRG-01',
        sparePartName: 'Bearing 6204',
        warehouseId: 1,
        issuedQty: 3,
        unitCost: 40,
        totalCost: 120,
        issuedBy: 1,
        issuedAt: '2026-10-05T10:30:00Z',
        remarks: null,
      },
    ]);
    if (c) backend.expectOne('/api/maintenance/requests/7/cost').flush(c);
    fixture.detectChanges();
  }

  function setup(permissions: string[], status = 'IN_PROGRESS', c: MaintenanceCostDto | null = cost()): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'maint',
      fullName: 'Maintenance',
      email: 'm@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(MaintenanceRequestDetailComponent);
    component = fixture.componentInstance;
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushRequest(status, permissions.includes('MAINTENANCE_COST_VIEW') ? c : null);
    if (permissions.includes('SPARE_PART_ISSUE')) {
      backend
        .expectOne((r) => r.url === '/api/spare-parts')
        .flush({ content: [part], totalElements: 1, totalPages: 1, size: 100, number: 0 });
      fixture.detectChanges();
    }
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MaintenanceRequestDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } },
        {
          provide: WarehouseApi,
          useValue: {
            list: () => of([{ id: 1, warehouseCode: 'MAIN', name: 'Main warehouse', active: true, version: 0 }]),
          },
        },
      ],
    }).compileComponents();
    // Not only in setup(): a test that never calls setup() must still have a controller for afterEach.
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('offers exactly the status moves the server allows from each state', () => {
    expect(NEXT_STATUSES['OPEN'].map((n) => n.status)).toEqual(['IN_PROGRESS', 'CANCELLED']);
    expect(NEXT_STATUSES['IN_PROGRESS'].map((n) => n.status)).toEqual(['COMPLETED', 'CANCELLED']);
    expect(NEXT_STATUSES['COMPLETED']).toEqual([]);
    expect(NEXT_STATUSES['CANCELLED']).toEqual([]);
  });

  it('shows the request, the work done and the parts issued', () => {
    setup(ALL);
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-test="title"]')?.textContent).toContain('MR/26/00007');
    expect(el.querySelector('[data-test="activities"]')?.textContent).toContain('Strip and inspect');
    expect(el.querySelector('[data-test="issues"]')?.textContent).toContain('SP-BRG-01 Bearing 6204');
    expect(el.querySelector('[data-test="issues"]')?.textContent).toContain('120.00');
  });

  it('shows the cost with labour and spare parts and says when the labour is only an estimate', () => {
    setup(ALL, 'IN_PROGRESS', cost({ labourCostFinal: false, totalCost: 720 }));
    const text = (fixture.nativeElement as HTMLElement).querySelector('[data-test="cost"]')?.textContent ?? '';

    expect(text).toContain('600.00');
    expect(text).toContain('120.00');
    expect(text).toContain('estimate until the request is completed');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="cost-total"]')?.textContent).toContain(
      '720.00',
    );
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="post-cost"]')).toBeNull();
  });

  it('shows no cost to people who may not see it', () => {
    setup(['MAINTENANCE_VIEW']);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="cost"]')).toBeNull();
  });

  it('offers the next statuses of an in-progress request and moves it', () => {
    setup(ALL, 'IN_PROGRESS');
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="move-COMPLETED"]')).not.toBeNull();
    expect(el.querySelector('[data-test="move-CANCELLED"]')).not.toBeNull();
    expect(el.querySelector('[data-test="move-IN_PROGRESS"]')).toBeNull();

    component.move('COMPLETED');

    const put = backend.expectOne((r) => r.url === '/api/maintenance/requests/7/status');
    expect(put.request.method).toBe('PUT');
    expect(put.request.params.get('newStatus')).toBe('COMPLETED');
    put.flush(request('COMPLETED'));
    flushRequest('COMPLETED');
  });

  it('offers nothing on a finished request, and no way to book work or issue parts', () => {
    setup(ALL, 'COMPLETED');
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-test="move-COMPLETED"]')).toBeNull();
    expect(el.querySelector('[data-test="activity-form"]')).toBeNull();
    expect(el.querySelector('[data-test="issue-form"]')).toBeNull();
    expect(component.acceptsWork()).toBe(false);
  });

  it('books work with its minutes', () => {
    setup(ALL);
    component.activityText.setValue(' Refit ');
    component.activityMinutes.setValue(30);

    component.addActivity();

    const post = backend.expectOne('/api/maintenance/requests/7/activities');
    expect(post.request.body).toEqual({ description: 'Refit', durationMinutes: 30 });
    post.flush({});
    flushRequest('IN_PROGRESS');
    expect(component.activityText.value).toBe('');
  });

  it('will not book work with no description or no minutes', () => {
    setup(ALL);
    component.activityText.setValue('');
    component.addActivity();
    component.activityText.setValue('x');
    component.activityMinutes.setValue(0);
    component.addActivity();

    backend.expectNone('/api/maintenance/requests/7/activities');
  });

  it('issues a spare part from a warehouse and refreshes the stock shown', () => {
    setup(ALL);
    expect(component.canIssue()).toBe(false);
    component.partCtrl.setValue(1);
    component.warehouseCtrl.setValue(1);
    component.qtyCtrl.setValue(3);
    expect(component.canIssue()).toBe(true);

    component.issue();

    const post = backend.expectOne('/api/maintenance/requests/7/spare-parts');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ sparePartId: 1, warehouseId: 1, quantity: 3 });
    post.flush({});
    flushRequest('IN_PROGRESS');
    backend
      .expectOne((r) => r.url === '/api/spare-parts')
      .flush({ content: [{ ...part, onHandQty: 7 }], totalElements: 1, totalPages: 1, size: 100, number: 0 });
    expect(component.parts()[0].onHandQty).toBe(7);
    expect(component.partCtrl.value).toBeNull();
  });

  it('shows the reason when stock is short', () => {
    setup(ALL);
    component.partCtrl.setValue(1);
    component.warehouseCtrl.setValue(1);
    component.qtyCtrl.setValue(50);

    component.issue();

    backend
      .expectOne('/api/maintenance/requests/7/spare-parts')
      .flush({ detail: 'Requested 50.000; available 10.000' }, { status: 422, statusText: 'Unprocessable' });
    expect(component.partCtrl.value).toBe(1); // nothing was cleared
  });

  it('says labour is not in the GL yet and posts it on request', () => {
    setup(ALL, 'COMPLETED', cost({ costPostedAt: null }));
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="not-posted"]')).not.toBeNull();

    component.postCost();

    const post = backend.expectOne('/api/maintenance/requests/7/post-cost');
    expect(post.request.method).toBe('POST');
    post.flush(cost({ costPostedAt: '2026-10-05T12:00:00Z', labourJournalHeaderId: 55 }));
    fixture.detectChanges();
    expect(el.querySelector('[data-test="posted"]')).not.toBeNull();
    expect(el.querySelector('[data-test="not-posted"]')).toBeNull();
  });

  it('does not offer to post the cost to people who may not', () => {
    setup(['MAINTENANCE_VIEW', 'MAINTENANCE_COST_VIEW'], 'COMPLETED', cost({ costPostedAt: null }));
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-test="not-posted"]')).not.toBeNull();
    expect(el.querySelector('[data-test="post-cost"]')).toBeNull();
  });
});
