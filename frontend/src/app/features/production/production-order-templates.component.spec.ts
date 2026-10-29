import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ProductionOrderTemplatesComponent } from './production-order-templates.component';

const TEMPLATE = {
  id: 1,
  code: 'T001',
  name: 'Widget Order',
  productId: 5,
  bomId: null,
  plannedQty: 100,
  leadTimeDays: 3,
  notes: null,
  active: true,
};

describe('ProductionOrderTemplatesComponent', () => {
  let fixture: ComponentFixture<ProductionOrderTemplatesComponent>;
  let component: ProductionOrderTemplatesComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductionOrderTemplatesComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(ProductionOrderTemplatesComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should render the templates title', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/production/templates').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Production Order Templates');
  });

  it('should display loaded templates in the table', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/production/templates').flush([TEMPLATE]);
    fixture.detectChanges();
    expect(component.templates.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('T001');
    expect(fixture.nativeElement.textContent).toContain('Widget Order');
  });

  it('should open the create form when New Template is clicked', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/production/templates').flush([]);
    fixture.detectChanges();
    component.openForm();
    fixture.detectChanges();
    expect(component.formOpen).toBeTrue();
    expect(component.editing).toBeNull();
  });

  it('should populate form when editing an existing template', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/production/templates').flush([]);
    fixture.detectChanges();
    component.openForm(TEMPLATE);
    fixture.detectChanges();
    expect(component.editing).toBe(TEMPLATE);
    expect(component.form.get('code')?.value).toBe('T001');
  });

  it('should close the form on cancel', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/production/templates').flush([]);
    fixture.detectChanges();
    component.openForm();
    component.closeForm();
    expect(component.formOpen).toBeFalse();
  });
});
