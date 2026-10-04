import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { NotificationPreferencesComponent } from './notification-preferences.component';
import { PreferenceDto } from './notifications.api';

const prefs = (lowStockInApp: boolean): PreferenceDto[] => [
  {
    templateCode: 'LOW_STOCK',
    description: 'Alert: stock below its reorder level',
    channels: [
      { channel: 'EMAIL', enabled: true },
      { channel: 'IN_APP', enabled: lowStockInApp },
    ],
  },
  {
    templateCode: 'CAPA_ASSIGNED',
    description: 'A CAPA has been assigned to you',
    channels: [{ channel: 'IN_APP', enabled: true }],
  },
];

describe('NotificationPreferencesComponent', () => {
  let fixture: ComponentFixture<NotificationPreferencesComponent>;
  let backend: HttpTestingController;

  function setup(): void {
    fixture = TestBed.createComponent(NotificationPreferencesComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/notification-preferences').flush(prefs(true));
    fixture.detectChanges();
  }

  const box = (code: string, channel: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="pref-${code}-${channel}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationPreferencesComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists every event once, with a checkbox only for the channels it uses', () => {
    setup();
    expect(fixture.nativeElement.textContent).toContain('Alert: stock below its reorder level');
    expect(box('LOW_STOCK', 'IN_APP')).not.toBeNull();
    expect(box('LOW_STOCK', 'EMAIL')).not.toBeNull();
    expect(box('CAPA_ASSIGNED', 'IN_APP')).not.toBeNull();
    expect(box('CAPA_ASSIGNED', 'EMAIL')).toBeNull();
  });

  it('reflects what is currently enabled', () => {
    setup();
    expect(fixture.componentInstance.enabled(prefs(false)[0], 'IN_APP')).toBe(false);
    expect(fixture.componentInstance.enabled(prefs(true)[0], 'IN_APP')).toBe(true);
    expect(fixture.componentInstance.enabled(prefs(true)[1], 'EMAIL')).toBe(false);
  });

  it('saves a change and shows what the server returns', () => {
    setup();
    fixture.componentInstance.set(prefs(true)[0], 'IN_APP', false);
    const req = backend.expectOne('/api/notification-preferences');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ templateCode: 'LOW_STOCK', channel: 'IN_APP', enabled: false });
    req.flush(prefs(false));
    expect(fixture.componentInstance.enabled(fixture.componentInstance.rows()[0], 'IN_APP')).toBe(false);
  });

  it('keeps the previous state when saving fails', () => {
    setup();
    fixture.componentInstance.set(prefs(true)[0], 'EMAIL', false);
    backend.expectOne('/api/notification-preferences').flush({}, { status: 500, statusText: 'Server error' });
    expect(fixture.componentInstance.enabled(fixture.componentInstance.rows()[0], 'EMAIL')).toBe(true);
  });
});
