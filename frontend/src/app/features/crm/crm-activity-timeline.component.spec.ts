import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { CrmActivityTimelineComponent } from './crm-activity-timeline.component';
import { ActivityDto, Page } from './crm.api';

const activity: ActivityDto = {
  id: 11,
  activityType: 'MEETING',
  customerId: 5,
  customerName: 'Acme Springs',
  contactId: 3,
  contactName: 'Meena Iyer',
  leadId: null,
  opportunityId: null,
  subject: 'Site visit',
  notes: 'Toured the plant',
  outcome: 'Positive',
  activityDate: '2026-10-01T09:30:00Z',
  durationMinutes: 90,
  ownerId: 2,
  ownerName: 'Asha',
  version: 0,
};

const page: Page<ActivityDto> = { content: [activity], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('CrmActivityTimelineComponent', () => {
  let fixture: ComponentFixture<CrmActivityTimelineComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'agent',
      fullName: 'Agent',
      email: 'a@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(CrmActivityTimelineComponent);
    backend = TestBed.inject(HttpTestingController);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmActivityTimelineComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('loads the log for the given customer and renders it', () => {
    setup(['CRM_VIEW']);
    fixture.componentRef.setInput('filter', { customerId: 5 });
    fixture.detectChanges();
    const req = backend.expectOne((r) => r.url === '/api/crm/activities');
    expect(req.request.params.get('customerId')).toBe('5');
    req.flush(page);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Site visit');
    expect(text).toContain('Toured the plant');
    expect(text).toContain('Outcome: Positive');
    expect(text).toContain('Meena Iyer');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('does not call the server without a customer, lead, opportunity or contact', () => {
    setup(['CRM_VIEW']);
    fixture.componentRef.setInput('filter', {});
    fixture.detectChanges();
    backend.expectNone((r) => r.url === '/api/crm/activities');
    expect(fixture.nativeElement.textContent).toContain('No activity logged yet');
  });

  it('logs an activity against the lead being viewed', () => {
    setup(['CRM_VIEW', 'CUSTOMER_ACTIVITY']);
    fixture.componentRef.setInput('filter', { leadId: 7 });
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/crm/activities').flush(page);

    fixture.componentInstance.form.patchValue({ activityType: 'CALL', subject: 'Intro call', durationMinutes: 15 });
    fixture.componentInstance.log();
    const req = backend.expectOne((r) => r.url === '/api/crm/activities' && r.method === 'POST');
    expect(req.request.body).toEqual(
      jasmine.objectContaining({
        activityType: 'CALL',
        leadId: 7,
        customerId: null,
        subject: 'Intro call',
        durationMinutes: 15,
      }),
    );
    req.flush(activity);
    backend.expectOne((r) => r.url === '/api/crm/activities' && r.method === 'GET').flush(page);
  });

  it('deletes an activity and reloads', () => {
    setup(['CRM_VIEW', 'CUSTOMER_ACTIVITY']);
    fixture.componentRef.setInput('filter', { customerId: 5 });
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/crm/activities').flush(page);

    fixture.componentInstance.remove(activity);
    const req = backend.expectOne((r) => r.url === '/api/crm/activities/11');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    backend.expectOne((r) => r.url === '/api/crm/activities' && r.method === 'GET').flush({ ...page, content: [] });
  });
});
