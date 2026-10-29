import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { IntegrationCallLogComponent } from './integration-call-log.component';

describe('IntegrationCallLogComponent', () => {
  let fixture: ComponentFixture<IntegrationCallLogComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IntegrationCallLogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(IntegrationCallLogComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create and load calls', () => {
    fixture.detectChanges();
    const req = http.expectOne('/api/integration/calls');
    req.flush([
      {
        id: 1,
        connector: 'TALLY',
        direction: 'OUT',
        requestSummary: 'test',
        statusCode: 200,
        ok: true,
        durationMs: 50,
        correlationId: 'c1',
        errorMessage: null,
        createdAt: new Date().toISOString(),
      },
    ]);
    fixture.detectChanges();
    expect(fixture.componentInstance.calls().length).toBe(1);
  });

  it('should show empty state when no calls', () => {
    fixture.detectChanges();
    http.expectOne('/api/integration/calls').flush([]);
    fixture.detectChanges();
    expect(fixture.componentInstance.calls().length).toBe(0);
  });

  it('should reload on refresh button click', () => {
    fixture.detectChanges();
    http.expectOne('/api/integration/calls').flush([]);
    fixture.componentInstance.load();
    http.expectOne('/api/integration/calls').flush([]);
    expect(fixture.componentInstance.calls().length).toBe(0);
  });
});
