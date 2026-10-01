import { TestBed } from '@angular/core/testing';
import { AppConfigService } from './app-config.service';

describe('AppConfigService', () => {
  let service: AppConfigService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AppConfigService);
  });

  it('uses defaults before load', () => {
    expect(service.apiBaseUrl).toBe('/api');
    expect(service.appName).toBe('Spring IMS');
  });

  it('merges runtime config over defaults and trims trailing slashes', async () => {
    spyOn(window, 'fetch').and.resolveTo(
      new Response(JSON.stringify({ apiBaseUrl: 'https://ims.example.com/api/' }), { status: 200 }),
    );
    await service.load();
    expect(service.apiBaseUrl).toBe('https://ims.example.com/api');
    expect(service.appName).toBe('Spring IMS');
  });

  it('falls back to defaults when the file cannot be loaded', async () => {
    spyOn(window, 'fetch').and.rejectWith(new Error('network'));
    await service.load();
    expect(service.apiBaseUrl).toBe('/api');
  });
});
