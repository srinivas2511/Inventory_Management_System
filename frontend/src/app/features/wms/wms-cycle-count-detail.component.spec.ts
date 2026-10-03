import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { CycleCountDto } from './wms.api';
import { WmsCycleCountDetailComponent } from './wms-cycle-count-detail.component';

const blindCount: CycleCountDto = {
  id: 8,
  countNumber: 'CC/26/00008',
  warehouseId: 1,
  countType: 'ZONE',
  zoneId: 4,
  abcClass: null,
  sampleSize: null,
  status: 'CREATED',
  notes: null,
  reviewedAt: null,
  postedAt: null,
  lineCount: 1,
  countedCount: 0,
  varianceLineCount: 0,
  version: 0,
  allowedActions: ['COUNT', 'CANCEL'],
  lines: [
    {
      id: 21,
      lineNo: 1,
      binId: 100,
      binCode: 'P-01',
      locationId: 50,
      itemType: 'PRODUCT',
      itemId: 3,
      itemCode: 'SPR-3',
      itemName: 'Compression spring',
      batchId: 5,
      batchNumber: 'FG-5',
      stockStatus: 'APPROVED',
      systemQty: null,
      countedQty: null,
      varianceQty: null,
      adjustmentId: null,
    },
  ],
};

const reviewed: CycleCountDto = {
  ...blindCount,
  status: 'REVIEW',
  countedCount: 1,
  varianceLineCount: 1,
  allowedActions: ['REOPEN', 'POST', 'CANCEL'],
  lines: [{ ...blindCount.lines[0], systemQty: 30, countedQty: 28, varianceQty: -2 }],
};

describe('WmsCycleCountDetailComponent', () => {
  let fixture: ComponentFixture<WmsCycleCountDetailComponent>;
  let backend: HttpTestingController;

  function setup(): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'wm',
      fullName: 'Warehouse Manager',
      email: 'wm@example.com',
      roles: [],
      permissions: ['WMS_VIEW', 'CYCLE_COUNT_MANAGE'],
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(WmsCycleCountDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/wms/cycle-counts/8').flush(blindCount);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WmsCycleCountDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '8' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('counts blind: no system quantity or variance column while the count is open', () => {
    setup();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="system"]')).toBeNull();
    expect(el.querySelector('[data-test="variance"]')).toBeNull();
    expect(el.querySelector('[data-test="counted"]')).not.toBeNull();
    expect(el.textContent).not.toContain('Variance');
  });

  it('saves only the counts that were entered', () => {
    setup();
    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector<HTMLInputElement>('[data-test="counted"]')!;
    input.value = '28';
    input.dispatchEvent(new Event('input'));

    el.querySelector<HTMLButtonElement>('[data-test="save"]')!.click();

    const req = backend.expectOne('/api/wms/cycle-counts/8/lines');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ lines: [{ lineId: 21, countedQty: 28 }] });
    req.flush({
      ...blindCount,
      status: 'IN_PROGRESS',
      countedCount: 1,
      lines: [{ ...blindCount.lines[0], countedQty: 28 }],
    });
  });

  it('saves unsaved counts before sending the count to review', () => {
    setup();
    const component = fixture.componentInstance as unknown as { apply: (c: CycleCountDto) => void };
    component.apply({ ...blindCount, status: 'IN_PROGRESS', allowedActions: ['COUNT', 'REVIEW', 'CANCEL'] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector<HTMLInputElement>('[data-test="counted"]')!;
    input.value = '28';
    input.dispatchEvent(new Event('input'));

    el.querySelector<HTMLButtonElement>('[data-test="review"]')!.click();

    backend.expectOne('/api/wms/cycle-counts/8/lines').flush({ ...blindCount, status: 'IN_PROGRESS' });
    const review = backend.expectOne('/api/wms/cycle-counts/8/review');
    expect(review.request.method).toBe('POST');
    review.flush(reviewed);
    fixture.detectChanges();
    expect(el.querySelector('[data-test="variance"]')?.textContent?.trim()).toBe('-2');
  });

  it('shows system quantity, variance and the post action in review', () => {
    setup();
    // load the reviewed state through the same component by reloading its data
    const component = fixture.componentInstance;
    (component as unknown as { apply: (c: CycleCountDto) => void }).apply(reviewed);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="system"]')?.textContent?.trim()).toBe('30');
    expect(el.querySelector('[data-test="variance"]')?.textContent?.trim()).toBe('-2');
    expect(el.querySelector('[data-test="counted"]')).toBeNull();

    el.querySelector<HTMLButtonElement>('[data-test="post"]')!.click();
    const req = backend.expectOne('/api/wms/cycle-counts/8/post');
    expect(req.request.method).toBe('POST');
    req.flush({ ...reviewed, status: 'POSTED', allowedActions: [] });
  });
});
