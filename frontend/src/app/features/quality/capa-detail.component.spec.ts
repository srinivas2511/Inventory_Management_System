import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { CapaDetailComponent } from './capa-detail.component';
import { Capa } from './quality-plus.api';

function capa(overrides: Partial<Capa>): Capa {
  return {
    id: 4,
    capaNumber: 'CAPA/26/00004',
    title: 'Coil rejects',
    description: 'Rising reject rate',
    sourceType: 'INTERNAL_AUDIT',
    sourceId: null,
    sourceLabel: null,
    rootCause: 'Worn tool',
    correctiveAction: null,
    preventiveAction: null,
    verificationNotes: null,
    targetCompletionDate: null,
    status: 'ROOT_CAUSE_ANALYSIS',
    nextStatuses: ['ACTION_IN_PROGRESS'],
    assignedTo: 7,
    verifiedBy: null,
    openedAt: '2026-10-02T08:00:00Z',
    closedAt: null,
    ...overrides,
  };
}

describe('CapaDetailComponent', () => {
  let fixture: ComponentFixture<CapaDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[], loaded: Capa): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'qm',
      fullName: 'Quality Manager',
      email: 'q@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(CapaDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/capa/4').flush(loaded);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CapaDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '4' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the state steps and a move button for each allowed next step', () => {
    setup(['QUALITY_VIEW', 'CAPA_MANAGE'], capa({}));
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CAPA/26/00004');
    expect(fixture.nativeElement.querySelectorAll('ol.steps li').length).toBe(5);
    expect(fixture.nativeElement.querySelector('li.current')?.textContent).toContain('ROOT_CAUSE_ANALYSIS');
    expect(text).toContain('Move to ACTION_IN_PROGRESS');
  });

  it('hides the workflow controls from read-only users', () => {
    setup(['QUALITY_VIEW'], capa({}));
    expect((fixture.nativeElement.textContent as string).includes('Move to')).toBe(false);
    expect((fixture.nativeElement.textContent as string).includes('Save')).toBe(false);
  });

  it('never offers CLOSED as a move; closing is its own action with its own permission', () => {
    setup(
      ['QUALITY_VIEW', 'CAPA_MANAGE'],
      capa({ status: 'VERIFICATION', nextStatuses: ['CLOSED', 'ACTION_IN_PROGRESS'] }),
    );
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Move to ACTION_IN_PROGRESS');
    expect(text.includes('Move to CLOSED')).toBe(false);
    expect(text.includes('Verify and close')).toBe(false);
  });

  it('lets a CAPA closer verify and close a CAPA in verification', () => {
    setup(
      ['QUALITY_VIEW', 'CAPA_CLOSE'],
      capa({ status: 'VERIFICATION', nextStatuses: ['CLOSED', 'ACTION_IN_PROGRESS'] }),
    );
    expect(fixture.nativeElement.textContent).toContain('Verify and close');
    fixture.componentInstance.verification.setValue('Reject rate back under 0.5%');
    fixture.componentInstance.close();
    const req = backend.expectOne('/api/capa/4/close');
    expect(req.request.body).toEqual({ verificationNotes: 'Reject rate back under 0.5%' });
    req.flush(capa({ status: 'CLOSED', nextStatuses: [], verificationNotes: 'Reject rate back under 0.5%' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Reject rate back under 0.5%');
  });

  it('sends the reason with a move back to action', () => {
    setup(
      ['QUALITY_VIEW', 'CAPA_MANAGE'],
      capa({ status: 'VERIFICATION', nextStatuses: ['CLOSED', 'ACTION_IN_PROGRESS'] }),
    );
    fixture.componentInstance.reason.setValue('Rejects persisted');
    fixture.componentInstance.advance('ACTION_IN_PROGRESS');
    const req = backend.expectOne('/api/capa/4/advance');
    expect(req.request.body).toEqual({ target: 'ACTION_IN_PROGRESS', reason: 'Rejects persisted' });
    req.flush(capa({ status: 'ACTION_IN_PROGRESS', nextStatuses: ['VERIFICATION'] }));
  });

  it('saves the edited fields and passes the assignee through unchanged', () => {
    setup(['QUALITY_VIEW', 'CAPA_MANAGE'], capa({}));
    fixture.componentInstance.form.patchValue({ correctiveAction: 'Replace the tool every 5,000 pieces' });
    fixture.componentInstance.save();
    const req = backend.expectOne('/api/capa/4');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.correctiveAction).toBe('Replace the tool every 5,000 pieces');
    expect(req.request.body.assignedTo).toBe(7);
    expect(req.request.body.sourceType).toBe('INTERNAL_AUDIT');
    req.flush(capa({ correctiveAction: 'Replace the tool every 5,000 pieces' }));
  });
});
