import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CustomFieldsPanelComponent } from './custom-fields-panel.component';

describe('CustomFieldsPanelComponent', () => {
  let fixture: ComponentFixture<CustomFieldsPanelComponent>;
  let component: CustomFieldsPanelComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomFieldsPanelComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomFieldsPanelComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should fetch definitions when entityType and entityId are set', () => {
    fixture.componentRef.setInput('entityType', 'MATERIAL');
    fixture.componentRef.setInput('entityId', 1);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/admin/custom-fields?entityType=MATERIAL');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should not fetch when entityId is 0', () => {
    fixture.componentRef.setInput('entityType', 'MATERIAL');
    fixture.componentRef.setInput('entityId', 0);
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url.includes('/api/admin/custom-fields'));
  });

  it('should render TEXT fields from definitions', () => {
    fixture.componentRef.setInput('entityType', 'SUPPLIER');
    fixture.componentRef.setInput('entityId', 5);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/admin/custom-fields?entityType=SUPPLIER');
    req.flush([
      {
        id: 1,
        entityType: 'SUPPLIER',
        fieldKey: 'notes',
        label: 'Notes',
        fieldType: 'TEXT',
        options: null,
        mandatory: false,
        showInList: false,
        sortOrder: 1,
        active: true,
      },
    ]);

    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Notes');
  });

  it('should parse comma-separated options', () => {
    fixture.detectChanges();
    // Access the protected method via cast
    const comp = component as unknown as { parseOptions: (o: string | null) => string[] };
    expect(comp.parseOptions('A,B,C')).toEqual(['A', 'B', 'C']);
    expect(comp.parseOptions(null)).toEqual([]);
    expect(comp.parseOptions('')).toEqual([]);
  });
});
