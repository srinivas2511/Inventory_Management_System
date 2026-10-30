import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BusyExportComponent } from './busy-export.component';

describe('BusyExportComponent', () => {
  let fixture: ComponentFixture<BusyExportComponent>;
  let comp: BusyExportComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusyExportComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(BusyExportComponent);
    comp = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates', () => {
    fixture.detectChanges();
    http.expectOne('/api/integration/busy-export/queue').flush([]);
    http.expectOne('/api/integration/busy/sync-log').flush([]);
    expect(comp).toBeTruthy();
  });

  it('queue starts empty', () => {
    fixture.detectChanges();
    http.expectOne('/api/integration/busy-export/queue').flush([]);
    http.expectOne('/api/integration/busy/sync-log').flush([]);
    expect(comp.queue()).toEqual([]);
  });

  it('syncNow button exists', () => {
    fixture.detectChanges();
    http.expectOne('/api/integration/busy-export/queue').flush([]);
    http.expectOne('/api/integration/busy/sync-log').flush([]);
    fixture.detectChanges();
    expect(comp.syncing()).toBeFalse();
  });
});
