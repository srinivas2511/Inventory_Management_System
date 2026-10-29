import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ReportBuilderComponent } from './report-builder.component';

describe('ReportBuilderComponent', () => {
  let fixture: ComponentFixture<ReportBuilderComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportBuilderComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(ReportBuilderComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    http.expectOne('/api/report-builder/catalogue').flush([]);
    http.expectOne('/api/report-builder/saved').flush([]);
  });

  it('should load catalogue on init', () => {
    fixture.detectChanges();
    const entry = {
      viewName: 'bi_sales',
      columnName: 'doc_no',
      dataType: 'text',
      label: 'Doc No',
      aggregations: null,
      permissionCode: 'BI_SALES',
    };
    http.expectOne('/api/report-builder/catalogue').flush([entry]);
    http.expectOne('/api/report-builder/saved').flush([]);
    fixture.detectChanges();
    expect(fixture.componentInstance.catalogue().length).toBe(1);
  });

  it('should load saved reports on init', () => {
    fixture.detectChanges();
    http.expectOne('/api/report-builder/catalogue').flush([]);
    const saved = {
      id: 1,
      name: 'My Report',
      sourceView: 'bi_sales',
      definition: {},
      shareScope: 'PRIVATE',
      ownerId: 1,
      createdAt: null,
    };
    http.expectOne('/api/report-builder/saved').flush([saved]);
    fixture.detectChanges();
    expect(fixture.componentInstance.savedReports().length).toBe(1);
  });
});
