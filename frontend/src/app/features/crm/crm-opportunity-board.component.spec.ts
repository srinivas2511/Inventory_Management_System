import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { CrmOpportunityBoardComponent } from './crm-opportunity-board.component';
import { CrmCustomerDto, OpportunityDto, Page } from './crm.api';

function opp(over: Partial<OpportunityDto>): OpportunityDto {
  return {
    id: 1,
    opportunityNumber: 'OPP/26/00001',
    leadId: null,
    customerId: 5,
    customerName: 'Acme Springs',
    contactId: null,
    contactName: null,
    description: 'Compression springs RFQ',
    stage: 'PROSPECT',
    probabilityPct: 10,
    estimatedValue: 1000,
    weightedValue: 100,
    expectedCloseDate: '2026-12-01',
    ownerId: 2,
    ownerName: 'Asha',
    convertedQuotationId: null,
    wonAt: null,
    lostReason: null,
    version: 0,
    allowedActions: ['EDIT', 'QUALIFIED', 'CLOSED_LOST'],
    ...over,
  };
}

const prospect = opp({});
const proposal = opp({
  id: 2,
  opportunityNumber: 'OPP/26/00002',
  description: 'Wire forms',
  stage: 'PROPOSAL',
  estimatedValue: 4000,
  allowedActions: ['EDIT', 'CLOSED_LOST', 'CLOSED_WON', 'NEGOTIATION'],
});
const won = opp({ id: 3, opportunityNumber: 'OPP/26/00003', stage: 'CLOSED_WON', allowedActions: [] });

const oppPage: Page<OpportunityDto> = {
  content: [prospect, proposal, won],
  totalElements: 3,
  totalPages: 1,
  size: 200,
  number: 0,
};
const customerPage: Page<CrmCustomerDto> = {
  content: [{ id: 5, customerCode: 'C5', name: 'Acme Springs', status: 'ACTIVE', tags: [] }],
  totalElements: 1,
  totalPages: 1,
  size: 200,
  number: 0,
};

describe('CrmOpportunityBoardComponent', () => {
  let fixture: ComponentFixture<CrmOpportunityBoardComponent>;
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
    fixture = TestBed.createComponent(CrmOpportunityBoardComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/crm/opportunities').flush(oppPage);
    backend.expectOne((r) => r.url === '/api/crm/customers').flush(customerPage);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmOpportunityBoardComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('groups opportunities into stage columns and totals each column', () => {
    setup(['CRM_VIEW']);
    const grouped = fixture.componentInstance.byStage();
    expect(grouped.PROSPECT.map((o) => o.id)).toEqual([1]);
    expect(grouped.PROPOSAL.map((o) => o.id)).toEqual([2]);
    expect(grouped.CLOSED_WON.map((o) => o.id)).toEqual([3]);
    expect(grouped.NEGOTIATION).toEqual([]);
    expect(fixture.componentInstance.total('PROPOSAL')).toBe(4000);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('OPP/26/00001');
    expect(text).toContain('Wire forms');
  });

  it('shows stage move buttons only to users who can manage opportunities', () => {
    setup(['CRM_VIEW']);
    expect(fixture.nativeElement.querySelectorAll('.moves button').length).toBe(0);
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('offers exactly the stages the server allows, ignoring EDIT', () => {
    setup(['CRM_VIEW', 'OPPORTUNITY_MANAGE']);
    expect(fixture.componentInstance.nextStages(prospect)).toEqual(['QUALIFIED', 'CLOSED_LOST']);
    expect(fixture.componentInstance.nextStages(proposal)).toEqual(['CLOSED_LOST', 'CLOSED_WON', 'NEGOTIATION']);
    expect(fixture.componentInstance.nextStages(won)).toEqual([]);
    expect(fixture.nativeElement.querySelectorAll('.moves button').length).toBe(5);
  });

  it('advances an opportunity and reloads the board', () => {
    setup(['CRM_VIEW', 'OPPORTUNITY_MANAGE']);
    fixture.componentInstance.move(prospect, 'QUALIFIED');
    const req = backend.expectOne((r) => r.url === '/api/crm/opportunities/1/advance');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ stage: 'QUALIFIED', lostReason: null });
    req.flush({ ...prospect, stage: 'QUALIFIED' });
    backend.expectOne((r) => r.url === '/api/crm/opportunities' && r.method === 'GET').flush(oppPage);
  });

  it('asks for a reason before closing as lost', () => {
    setup(['CRM_VIEW', 'OPPORTUNITY_MANAGE']);
    fixture.componentInstance.move(proposal, 'CLOSED_LOST');
    backend.expectNone((r) => r.url.endsWith('/advance'));
    expect(fixture.componentInstance.lostFor()?.id).toBe(2);

    fixture.componentInstance.lostReason.setValue('went with a competitor');
    fixture.componentInstance.confirmLost();
    const req = backend.expectOne((r) => r.url === '/api/crm/opportunities/2/advance');
    expect(req.request.body).toEqual({ stage: 'CLOSED_LOST', lostReason: 'went with a competitor' });
    req.flush({ ...proposal, stage: 'CLOSED_LOST' });
    backend.expectOne((r) => r.url === '/api/crm/opportunities' && r.method === 'GET').flush(oppPage);
    expect(fixture.componentInstance.lostFor()).toBeNull();
  });

  it('creates an opportunity for a customer', () => {
    setup(['CRM_VIEW', 'OPPORTUNITY_MANAGE']);
    fixture.componentInstance.form.patchValue({
      customerId: 5,
      description: 'New RFQ',
      estimatedValue: 9000,
      expectedCloseDate: '2027-01-15',
    });
    fixture.componentInstance.create();
    const req = backend.expectOne((r) => r.url === '/api/crm/opportunities' && r.method === 'POST');
    expect(req.request.body).toEqual(
      jasmine.objectContaining({ customerId: 5, description: 'New RFQ', estimatedValue: 9000 }),
    );
    req.flush(prospect);
    backend.expectOne((r) => r.url === '/api/crm/opportunities' && r.method === 'GET').flush(oppPage);
  });
});
