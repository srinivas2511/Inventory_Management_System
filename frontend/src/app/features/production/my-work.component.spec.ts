import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
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
};

describe('MyWorkComponent', () => {
  let fixture: ComponentFixture<MyWorkComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyWorkComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(MyWorkComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/production/my-work').flush([op]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('titles each card with the production order number', () => {
    expect(fixture.nativeElement.textContent).toContain('PO/26/00009');
    expect(fixture.nativeElement.textContent).toContain('Coiling');
  });

  it('starts the production operation by its own id, not the master-data operation id', () => {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    buttons.find((b) => (b.textContent ?? '').includes('Start'))?.click();
    const req = backend.expectOne('/api/production/operations/55/start');
    expect(req.request.method).toBe('POST');
    req.flush({});
    backend.expectOne('/api/production/my-work').flush([]);
  });
});
