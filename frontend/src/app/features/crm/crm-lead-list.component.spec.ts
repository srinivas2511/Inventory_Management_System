import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { CrmLeadListComponent } from './crm-lead-list.component';
import { LeadDto, Page } from './crm.api';

const lead: LeadDto = {
  id: 7,
  leadNumber: 'LD/26/00007',
  companyName: 'Acme Springs Pvt Ltd',
  contactId: null,
  contactName: 'Ravi Kumar',
  source: 'WEBSITE',
  status: 'CONTACTED',
  ownerId: 2,
  ownerName: 'Asha Rao',
  estimatedValue: 250000,
  expectedCloseDate: '2026-12-01',
  convertedCustomerId: null,
  convertedAt: null,
  disqualifyReason: null,
  notes: null,
  version: 0,
  allowedActions: ['EDIT', 'QUALIFY', 'DISQUALIFY'],
};

const page: Page<LeadDto> = { content: [lead], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('CrmLeadListComponent', () => {
  let fixture: ComponentFixture<CrmLeadListComponent>;
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
    fixture = TestBed.createComponent(CrmLeadListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/crm/leads').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmLeadListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists leads without a create form for read-only users', () => {
    setup(['CRM_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('LD/26/00007');
    expect(text).toContain('Acme Springs Pvt Ltd');
    expect(text).toContain('Ravi Kumar');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('creates a lead for users with LEAD_MANAGE and reloads', () => {
    setup(['CRM_VIEW', 'LEAD_MANAGE']);
    expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
    fixture.componentInstance.form.patchValue({
      companyName: 'Beta Wires',
      source: 'REFERRAL',
      estimatedValue: 1000,
      expectedCloseDate: '2026-12-31',
      notes: '',
    });
    fixture.componentInstance.create();

    const req = backend.expectOne((r) => r.url === '/api/crm/leads' && r.method === 'POST');
    expect(req.request.body).toEqual({
      companyName: 'Beta Wires',
      contactId: null,
      source: 'REFERRAL',
      ownerId: null,
      estimatedValue: 1000,
      expectedCloseDate: '2026-12-31',
      notes: null,
      version: null,
    });
    req.flush(lead);
    backend.expectOne((r) => r.url === '/api/crm/leads' && r.method === 'GET').flush(page);
  });

  it('does not submit a lead without a company name', () => {
    setup(['CRM_VIEW', 'LEAD_MANAGE']);
    fixture.componentInstance.create();
    backend.expectNone((r) => r.method === 'POST');
  });

  it('filters by status when the status control changes', () => {
    setup(['CRM_VIEW']);
    fixture.componentInstance.statusCtrl.setValue('QUALIFIED');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/crm/leads');
    expect(req.request.params.get('status')).toBe('QUALIFIED');
    req.flush(page);
  });
});
