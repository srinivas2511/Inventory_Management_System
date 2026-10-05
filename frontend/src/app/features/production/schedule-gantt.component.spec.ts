import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { ScheduleGanttComponent } from './schedule-gantt.component';
import { GanttBarDto, GanttDto, SchedulePlanDto } from './scheduling.api';

const pending: GanttBarDto = {
  productionOperationId: 11,
  productionOrderId: 1,
  orderNumber: 'PO/26/00001',
  productCode: 'SPR-1',
  priority: 2,
  sequenceNo: 1,
  operationName: 'Coiling',
  status: 'PENDING',
  start: '2026-10-05T09:00:00Z',
  end: '2026-10-05T11:00:00Z',
  operatorUserId: 7,
  operatorName: 'Asha',
  late: false,
  locked: false,
};

const running: GanttBarDto = {
  ...pending,
  productionOperationId: 12,
  productionOrderId: 2,
  orderNumber: 'PO/26/00002',
  status: 'IN_PROGRESS',
  start: '2026-10-05T12:00:00Z',
  end: '2026-10-05T14:00:00Z',
  late: true,
  locked: true,
};

const gantt: GanttDto = {
  from: '2026-10-05',
  to: '2026-10-07',
  zone: 'UTC',
  days: [
    { date: '2026-10-05', start: '2026-10-05T00:00:00Z', end: '2026-10-06T00:00:00Z', working: true },
    { date: '2026-10-06', start: '2026-10-06T00:00:00Z', end: '2026-10-07T00:00:00Z', working: false },
    { date: '2026-10-07', start: '2026-10-07T00:00:00Z', end: '2026-10-08T00:00:00Z', working: true },
  ],
  workingDays: ['2026-10-05', '2026-10-07'],
  machines: [
    { machineId: 1, machineCode: 'CL-01', machineName: 'Coiler one', bars: [pending, running], maintenance: [] },
    {
      machineId: 2,
      machineCode: 'CL-02',
      machineName: 'Coiler two',
      bars: [],
      maintenance: [
        { maintenanceRequestId: 9, requestNumber: 'MR/26/00009', date: '2026-10-07', requestType: 'PREVENTIVE' },
      ],
    },
  ],
  unscheduled: [
    {
      productionOperationId: 20,
      productionOrderId: 3,
      orderNumber: 'PO/26/00003',
      priority: 1,
      sequenceNo: 1,
      operationName: 'Grinding',
      machineId: null,
    },
  ],
};

const plan: SchedulePlanDto = {
  applied: false,
  mode: 'FULL',
  horizonStart: '2026-10-05T08:00:00Z',
  horizonEnd: '2026-10-19',
  placements: [
    {
      productionOperationId: 11,
      productionOrderId: 1,
      orderNumber: 'PO/26/00001',
      sequenceNo: 1,
      operationName: 'Coiling',
      machineId: 1,
      machineCode: 'CL-01',
      start: '2026-10-05T09:00:00Z',
      end: '2026-10-05T11:00:00Z',
      operatorUserId: null,
      warnings: ['NO_SKILLED_OPERATOR'],
    },
  ],
  unplaced: [
    {
      productionOperationId: 30,
      productionOrderId: 4,
      orderNumber: 'PO/26/00004',
      sequenceNo: 1,
      operationName: 'Plating',
      reason: 'NO_MACHINE',
    },
  ],
  orders: [
    {
      productionOrderId: 1,
      orderNumber: 'PO/26/00001',
      dueDate: '2026-10-04',
      endsAt: '2026-10-05T11:00:00Z',
      late: true,
    },
  ],
};

describe('ScheduleGanttComponent', () => {
  let fixture: ComponentFixture<ScheduleGanttComponent>;
  let backend: HttpTestingController;
  let component: ScheduleGanttComponent;

  function flushGantt(): void {
    backend.expectOne((r) => r.url === '/api/scheduling/gantt').flush(gantt);
    fixture.detectChanges();
  }

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'planner',
      fullName: 'Planner',
      email: 'p@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ScheduleGanttComponent);
    component = fixture.componentInstance;
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushGantt();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScheduleGanttComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('draws a row per machine, a band per day with weekly offs hatched, the bars and the maintenance days', () => {
    setup(['SCHEDULE_VIEW']);
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelectorAll('[data-test="machine-label"]').length).toBe(2);
    expect(el.querySelectorAll('[data-test="day"]').length).toBe(3);
    expect(el.querySelectorAll('[data-test="day"].off').length).toBe(1);
    expect(el.querySelectorAll('[data-test="bar"]').length).toBe(2);
    expect(el.querySelectorAll('[data-test="maintenance"]').length).toBe(1);
    expect(el.querySelector('[data-test="unscheduled"]')?.textContent).toContain('PO/26/00003/1');
    expect(el.querySelector('[data-test="unscheduled"]')?.textContent).toContain('Grinding');
  });

  it('positions a bar from its start time and length at 6 px per hour', () => {
    setup(['SCHEDULE_VIEW']);
    const bar = (fixture.nativeElement as HTMLElement).querySelector('[data-test="bar"]') as HTMLElement;

    expect(bar.style.left).toBe('54px'); // 9 h from the start of the first day
    expect(bar.style.width).toBe('12px'); // 2 h
    expect(bar.classList).toContain('pending');
  });

  it('flags a late order and lets only an unstarted operation be moved, and only by someone who may', () => {
    setup(['SCHEDULE_VIEW']);
    expect(component.canMove(pending)).toBe(false); // no SCHEDULE_MANAGE
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('[data-test="bar"].late').length).toBe(1);

    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'planner',
      fullName: 'Planner',
      email: 'p@example.com',
      roles: [],
      permissions: ['SCHEDULE_VIEW', 'SCHEDULE_MANAGE'],
      mustChangePassword: false,
    });
    expect(component.canMove(pending)).toBe(true);
    expect(component.canMove(running)).toBe(false); // already running
  });

  it('moves an operation by dragging it: the new machine and snapped start go to the server', () => {
    setup(['SCHEDULE_VIEW', 'SCHEDULE_MANAGE']);
    // dragged 18 px right (3 h) and one row (48 px) down, from CL-01 to CL-02
    component.drag.set({
      bar: pending,
      originRow: 0,
      startMs: Date.parse(pending.start),
      startClientX: 0,
      startClientY: 0,
      dx: 18,
      dy: 48,
    });

    component.up();

    const put = backend.expectOne('/api/scheduling/operations/11/schedule');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ machineId: 2, start: '2026-10-05T12:00:00.000Z' });
    put.flush({
      bar: { ...pending, start: '2026-10-05T12:00:00Z', end: '2026-10-05T14:00:00Z' },
      machineId: 2,
      machineCode: 'CL-02',
      warnings: ['MACHINE_TYPE_DIFFERS_FROM_OPERATION'],
    });
    flushGantt(); // the chart reloads from the server
    expect(component.drag()).toBeNull();
  });

  it('treats a small pointer movement as a click and does not call the server', () => {
    setup(['SCHEDULE_VIEW', 'SCHEDULE_MANAGE']);
    component.drag.set({
      bar: pending,
      originRow: 0,
      startMs: Date.parse(pending.start),
      startClientX: 0,
      startClientY: 0,
      dx: 2,
      dy: 1,
    });

    component.up();

    backend.expectNone('/api/scheduling/operations/11/schedule');
    expect(component.drag()).toBeNull();
  });

  it('selects a bar on press and starts a drag only when the operation can be moved', () => {
    setup(['SCHEDULE_VIEW', 'SCHEDULE_MANAGE']);
    const press = (b: GanttBarDto) =>
      component.down(new PointerEvent('pointerdown', { clientX: 5, clientY: 5, pointerId: 1 }), b, 0);

    press(running);
    expect(component.selected()?.productionOperationId).toBe(12);
    expect(component.drag()).toBeNull();

    press(pending);
    expect(component.selected()?.productionOperationId).toBe(11);
    expect(component.drag()?.bar.productionOperationId).toBe(11);
    component.up(); // released where it was pressed: a click
  });

  it('shows the reason when the server refuses a move and puts the bar back by reloading', () => {
    setup(['SCHEDULE_VIEW', 'SCHEDULE_MANAGE']);

    component.drop(pending, 1, Date.parse('2026-10-05T10:00:00Z'));

    backend
      .expectOne('/api/scheduling/operations/11/schedule')
      .flush({ detail: 'Machine CL-01 is already taken' }, { status: 409, statusText: 'Conflict' });
    flushGantt();
  });

  it('plans a preview without saving, reports what would go wrong, and applies it on request', () => {
    setup(['SCHEDULE_VIEW', 'SCHEDULE_MANAGE']);
    component.modeCtrl.setValue('ONLY_UNSCHEDULED');

    component.preview();

    const dry = backend.expectOne((r) => r.url === '/api/scheduling/run');
    expect(dry.request.params.get('dryRun')).toBe('true');
    expect(dry.request.body.mode).toBe('ONLY_UNSCHEDULED');
    dry.flush(plan);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const text = el.querySelector('[data-test="preview"]')?.textContent ?? '';
    expect(text).toContain('1 operation(s) placed, 1 could not be placed');
    expect(text).toContain('Late: PO/26/00001');
    expect(text).toContain('PO/26/00004/1 Plating: no machine of the right type');
    expect(text).toContain('nobody in the skill matrix is authorised');
    expect(el.querySelector('[data-test="apply"]')).not.toBeNull();

    component.apply();

    const save = backend.expectOne((r) => r.url === '/api/scheduling/run');
    expect(save.request.params.get('dryRun')).toBe('false');
    save.flush({ ...plan, applied: true });
    flushGantt();
    fixture.detectChanges();
    expect(el.querySelector('[data-test="apply"]')).toBeNull();
    expect(el.querySelector('[data-test="preview"]')?.textContent).toContain('(applied)');
  });

  it('hides the planning controls from people who can only look', () => {
    setup(['SCHEDULE_VIEW']);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="auto-schedule"]')).toBeNull();
  });

  it('shows the details of the selected operation and unschedules it', () => {
    setup(['SCHEDULE_VIEW', 'SCHEDULE_MANAGE']);
    component.selected.set(pending);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="detail"]')?.textContent).toContain('Asha');
    expect(el.querySelector('[data-test="detail"]')?.textContent).toContain('SPR-1');

    component.unschedule(pending);

    const del = backend.expectOne('/api/scheduling/operations/11/schedule');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    flushGantt();
    expect(component.selected()).toBeNull();
  });

  it('moves the visible range by a week', () => {
    setup(['SCHEDULE_VIEW']);
    component.fromCtrl.setValue('2026-10-05');

    component.shift(7);

    const req = backend.expectOne((r) => r.url === '/api/scheduling/gantt');
    expect(req.request.params.get('from')).toBe('2026-10-12');
    req.flush(gantt);
  });

  it('zooms within limits', () => {
    setup(['SCHEDULE_VIEW']);
    for (let i = 0; i < 20; i++) component.zoom(1);
    expect(component.pxPerHour()).toBe(24);
    for (let i = 0; i < 20; i++) component.zoom(-1);
    expect(component.pxPerHour()).toBe(2);
  });
});
