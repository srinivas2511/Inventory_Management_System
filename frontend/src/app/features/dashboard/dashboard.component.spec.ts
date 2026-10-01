import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let backend: HttpTestingController;

  const status = () => fixture.nativeElement.querySelector('[data-testid="api-status"]').textContent as string;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(DashboardComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows a checking state first', () => {
    expect(status()).toContain('Checking');
    backend.expectOne('/api/system/ping').flush({ status: 'UP', application: 'ims', version: '0.1.0', time: '' });
  });

  it('shows the server version when the API is up', () => {
    backend.expectOne('/api/system/ping').flush({ status: 'UP', application: 'ims', version: '0.1.0', time: '' });
    fixture.detectChanges();
    expect(status()).toContain('Server is running');
    expect(fixture.nativeElement.textContent).toContain('Version 0.1.0');
  });

  it('shows an unreachable state when the call fails', () => {
    backend.expectOne('/api/system/ping').flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(status()).toContain('not reachable');
  });
});
