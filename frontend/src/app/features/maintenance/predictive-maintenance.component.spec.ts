import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PredictiveMaintenanceComponent } from './predictive-maintenance.component';
import { PredictionDto, RunPredictionsResponse } from './maintenance.api';

function makePred(id: number): PredictionDto {
  return {
    id,
    machineId: 1,
    machineCode: 'MCH-01',
    machineName: 'Press',
    predictedFailureAt: '2026-11-10T00:00:00Z',
    confidencePct: 75,
    mtbfHours: 240,
    failureCount: 3,
    lastFailureAt: '2026-10-20T00:00:00Z',
    algorithmVersion: '1.0',
    generatedAt: '2026-10-24T00:00:00Z',
    acknowledged: false,
    acknowledgedAt: null,
    notes: null,
  };
}

function makeRunResp(): RunPredictionsResponse {
  return {
    summary: { machinesAnalyzed: 2, predictionsGenerated: 1, highRiskCount: 1, acknowledgedCount: 0 },
    machines: [
      {
        machineId: 1,
        machineCode: 'MCH-01',
        machineName: 'Press',
        failureCountLast90Days: 3,
        mtbfHours: 240,
        nextPredictedFailure: '2026-11-10T00:00:00Z',
        confidencePct: 75,
        riskLevel: 'HIGH',
      },
    ],
  };
}

describe('PredictiveMaintenanceComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PredictiveMaintenanceComponent],
      providers: [provideAnimationsAsync(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('PM-1: loads active predictions on init', () => {
    const fixture = TestBed.createComponent(PredictiveMaintenanceComponent);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url.includes('/api/maintenance/predictive/predictions'));
    req.flush([makePred(1), makePred(2)]);
    fixture.detectChanges();
    expect(fixture.componentInstance.predictions().length).toBe(2);
  });

  it('PM-2: runPredictions posts to /run and updates machineRows', () => {
    const fixture = TestBed.createComponent(PredictiveMaintenanceComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/api/maintenance/predictive/predictions')).flush([]);

    fixture.componentInstance.runPredictions();
    fixture.detectChanges();

    const runReq = http.expectOne((r) => r.url.includes('/api/maintenance/predictive/run'));
    expect(runReq.request.method).toBe('POST');
    runReq.flush(makeRunResp());
    http.expectOne((r) => r.url.includes('/api/maintenance/predictive/predictions')).flush([makePred(1)]);
    fixture.detectChanges();

    expect(fixture.componentInstance.machineRows().length).toBe(1);
    expect(fixture.componentInstance.machineRows()[0].riskLevel).toBe('HIGH');
  });

  it('PM-3: running signal is true during runPredictions', () => {
    const fixture = TestBed.createComponent(PredictiveMaintenanceComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/api/maintenance/predictive/predictions')).flush([]);

    fixture.componentInstance.runPredictions();
    fixture.detectChanges();
    expect(fixture.componentInstance.running()).toBeTrue();

    http.expectOne((r) => r.url.includes('/api/maintenance/predictive/run')).flush(makeRunResp());
    http.expectOne((r) => r.url.includes('/api/maintenance/predictive/predictions')).flush([]);
    expect(fixture.componentInstance.running()).toBeFalse();
  });

  it('PM-4: acknowledge removes prediction from list', () => {
    const fixture = TestBed.createComponent(PredictiveMaintenanceComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/api/maintenance/predictive/predictions')).flush([makePred(10)]);
    fixture.detectChanges();
    expect(comp.predictions().length).toBe(1);

    comp.acknowledge(makePred(10));
    http.expectOne((r) => r.url.includes('/predictions/10/acknowledge')).flush({ ...makePred(10), acknowledged: true });
    expect(comp.predictions().length).toBe(0);
  });

  it('PM-5: shows no-predictions message when list is empty', () => {
    const fixture = TestBed.createComponent(PredictiveMaintenanceComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/api/maintenance/predictive/predictions')).flush([]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No active predictions');
  });
});
