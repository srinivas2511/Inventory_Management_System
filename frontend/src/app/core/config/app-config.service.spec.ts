import { TestBed } from '@angular/core/testing';
import { AppConfigService } from './app-config.service';

describe('AppConfigService', () => {
  let service: AppConfigService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AppConfigService);
  });

  it('uses defaults before load', () => {
    expect(service.apiBaseUrl).toBe('');
    expect(service.appName).toBe('ERP');
    expect(service.timezone).toBe('Asia/Kolkata');
  });

  it('merges runtime config over defaults and trims trailing slashes', async () => {
    spyOn(window, 'fetch').and.resolveTo(
      new Response(JSON.stringify({ apiBaseUrl: 'https://ims.example.com/' }), { status: 200 }),
    );
    await service.load();
    expect(service.apiBaseUrl).toBe('https://ims.example.com');
    expect(service.appName).toBe('ERP');
    expect(service.timezone).toBe('Asia/Kolkata');
  });

  it('reads timezone from config.json when provided', async () => {
    spyOn(window, 'fetch').and.resolveTo(
      new Response(JSON.stringify({ timezone: 'America/New_York' }), { status: 200 }),
    );
    await service.load();
    expect(service.timezone).toBe('America/New_York');
  });

  it('falls back to defaults when the file cannot be loaded', async () => {
    spyOn(window, 'fetch').and.rejectWith(new Error('network'));
    await service.load();
    expect(service.apiBaseUrl).toBe('');
  });
});
