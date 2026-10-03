import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CrmPipelineReportComponent, csvField, toCsv } from './crm-pipeline-report.component';
import { ReportResult } from './crm.api';

const result: ReportResult = {
  reportCode: 'CRM_PIPELINE',
  displayName: 'Sales Pipeline by Stage',
  headers: ['Stage', 'Opportunities', 'Total Value'],
  rows: [
    { Stage: 'PROSPECT', Opportunities: 2, 'Total Value': 4000 },
    { Stage: 'PROPOSAL', Opportunities: 1, 'Total Value': 2000 },
  ],
  totalRows: 2,
};

describe('CSV helpers', () => {
  it('quotes fields containing commas, quotes or newlines', () => {
    expect(csvField('plain')).toBe('plain');
    expect(csvField('a,b')).toBe('"a,b"');
    expect(csvField('say "hi"')).toBe('"say ""hi"""');
    expect(csvField('two\nlines')).toBe('"two\nlines"');
  });

  it('writes empty cells for null and undefined', () => {
    expect(csvField(null)).toBe('');
    expect(csvField(undefined)).toBe('');
  });

  it('builds a header row and one line per result row', () => {
    expect(toCsv(result.headers, result.rows)).toBe(
      'Stage,Opportunities,Total Value\nPROSPECT,2,4000\nPROPOSAL,1,2000',
    );
  });
});

describe('CrmPipelineReportComponent', () => {
  let fixture: ComponentFixture<CrmPipelineReportComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmPipelineReportComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(CrmPipelineReportComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('runs the pipeline report with the date range and renders the rows', () => {
    fixture.componentInstance.fromCtrl.setValue('2026-10-01');
    fixture.componentInstance.toCtrl.setValue('2026-12-31');
    fixture.componentInstance.run();
    const req = backend.expectOne((r) => r.url === '/api/reports/CRM_PIPELINE');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-12-31');
    req.flush(result);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('PROSPECT');
    expect(text).toContain('PROPOSAL');
    expect(fixture.componentInstance.running()).toBe(false);
  });

  it('keeps the export button disabled until a report has run', () => {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (b) => (b.textContent ?? '').includes('Export CSV'),
    );
    expect(button?.disabled).toBe(true);
  });
});
