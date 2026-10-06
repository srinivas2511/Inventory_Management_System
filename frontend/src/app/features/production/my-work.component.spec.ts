import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { MyWorkComponent, MyWorkItemDto } from './my-work.component';

const op: MyWorkItemDto = {
  id: 55,
  operationId: 3,
  productionOrderId: 9,
  productionOrderNumber: 'PO/26/00009',
  productId: 4,
  sequenceNo: 2,
  operationName: 'Coiling',
  machineId: 6,
  status: 'PENDING',
  inputQty: 100,
  goodQty: 0,
  rejectedQty: 0,
  scrapQty: 0,
  finalOperation: false,
};

describe('MyWorkComponent', () => {
  let fixture: ComponentFixture<MyWorkComponent>;
  let backend: HttpTestingController;

  async function show(items: MyWorkItemDto[]): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [MyWorkComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(MyWorkComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/production/my-work').flush(items);
    fixture.detectChanges();
  }

  function button(label: string): HTMLButtonElement | undefined {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    return buttons.find((b) => (b.textContent ?? '').includes(label));
  }

  function click(label: string): void {
    button(label)?.click();
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  it('titles each card with the production order number', async () => {
    await show([op]);
    expect(fixture.nativeElement.textContent).toContain('PO/26/00009');
    expect(fixture.nativeElement.textContent).toContain('Coiling');
  });

  it('starts the production operation by its own id, not the master-data operation id', async () => {
    await show([op]);
    click('Start');
    const req = backend.expectOne('/api/production/operations/55/start');
    expect(req.request.method).toBe('POST');
    req.flush({});
    backend.expectOne('/api/production/my-work').flush([]);
  });

  it('offers Resume, and nothing else to act on, for a paused operation', async () => {
    await show([{ ...op, status: 'PAUSED' }]);
    expect(button('Resume')).toBeDefined();
    expect(button('Complete')).toBeUndefined();
    expect(button('Start')).toBeUndefined();
    click('Resume');
    const req = backend.expectOne('/api/production/operations/55/resume');
    expect(req.request.method).toBe('POST');
    req.flush({});
    backend.expectOne('/api/production/my-work').flush([]);
  });

  it('offers Complete only while the operation is in progress', async () => {
    await show([{ ...op, status: 'PENDING' }]);
    expect(button('Complete')).toBeUndefined();
  });

  it('asks for confirmation before completing and warns about pieces with no output recorded', async () => {
    await show([{ ...op, status: 'IN_PROGRESS', goodQty: 90, rejectedQty: 4, scrapQty: 0 }]);
    click('Complete');
    backend.expectNone('/api/production/operations/55/complete');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Recorded 94 of 100');
    expect(text).toContain('6 pieces have no output recorded');
    expect(text).not.toContain('last operation');
  });

  it('completes the operation by its own id and refreshes the list', async () => {
    await show([{ ...op, status: 'IN_PROGRESS', goodQty: 100 }]);
    click('Complete');
    click('Complete operation');
    const req = backend.expectOne('/api/production/operations/55/complete');
    expect(req.request.method).toBe('POST');
    req.flush({});
    backend.expectOne('/api/production/my-work').flush([]);
  });

  it('says the final inspection is requested after the last operation, and links to the inspections', async () => {
    await show([{ ...op, status: 'IN_PROGRESS', goodQty: 100, finalOperation: true }]);
    click('Complete');
    expect(fixture.nativeElement.textContent).toContain('This is the last operation');

    const onAction = new Subject<void>();
    const open = spyOn(fixture.debugElement.injector.get(MatSnackBar), 'open').and.returnValue({
      onAction: () => onAction.asObservable(),
    } as never);
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);

    click('Complete operation');
    backend.expectOne('/api/production/operations/55/complete').flush({});
    backend.expectOne('/api/production/my-work').flush([]);

    const [message, action] = open.calls.mostRecent().args;
    expect(message).toContain('Final inspection requested');
    expect(action).toBe('Open inspections');
    onAction.next();
    expect(navigate).toHaveBeenCalledWith('/quality/inspections');
  });

  it('shows the server error text when completing is refused and keeps the operation on screen', async () => {
    await show([{ ...op, status: 'IN_PROGRESS', goodQty: 100 }]);
    const open = spyOn(fixture.debugElement.injector.get(MatSnackBar), 'open').and.callThrough();
    click('Complete');
    click('Complete operation');
    backend
      .expectOne('/api/production/operations/55/complete')
      .flush({ detail: 'Operation cannot be completed in status: COMPLETED' }, { status: 409, statusText: 'Conflict' });
    expect(open.calls.mostRecent().args[0]).toBe('Operation cannot be completed in status: COMPLETED');
  });
});
