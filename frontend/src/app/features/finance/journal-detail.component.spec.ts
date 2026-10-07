/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { FiscalPeriodDto, JournalHeaderDto } from './finance.api';
import { JournalDetailComponent } from './journal-detail.component';

function journal(over: Partial<JournalHeaderDto>): JournalHeaderDto {
  return {
    id: 5,
    journalNumber: 'JNL/26/00005',
    journalType: 'GL',
    postingDate: '2026-10-07',
    periodId: 7,
    description: 'Opening balance correction',
    currencyCode: 'INR',
    status: 'DRAFT',
    preparedBy: 11,
    lines: [
      {
        id: 1,
        lineNumber: 1,
        glAccountId: 1,
        accountCode: '1201',
        accountName: 'Main Bank Account',
        debitAmount: 1500,
        creditAmount: 0,
        description: 'Dr bank',
      },
      {
        id: 2,
        lineNumber: 2,
        glAccountId: 2,
        accountCode: '4001',
        accountName: 'Sales Revenue',
        debitAmount: 0,
        creditAmount: 1500,
        description: 'Cr revenue',
      },
    ],
    ...over,
  };
}

const openPeriod: FiscalPeriodDto = {
  id: 7,
  periodName: 'OCT-2026',
  fiscalYear: 2026,
  periodNumber: 7,
  startDate: '2026-10-01',
  endDate: '2026-10-31',
  status: 'OPEN',
};

describe('JournalDetailComponent', () => {
  let fixture: ComponentFixture<JournalDetailComponent>;
  let backend: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let navigate: jasmine.Spy;
  let instance: Record<string, any>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const labels = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('mat-card-actions button')).map((b) =>
      (b.textContent ?? '').trim(),
    );

  function setup(permissions: string[], j: JournalHeaderDto = journal({}), period: FiscalPeriodDto = openPeriod) {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'fm',
      fullName: 'Finance Manager',
      email: 'f@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(JournalDetailComponent);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend.expectOne('/api/journals/5').flush(j);
    backend.expectOne('/api/periods').flush([period]);
    fixture.detectChanges();
  }

  function dialogReturning(value: unknown) {
    return spyOn(instance['dialog'], 'open').and.returnValue({
      afterClosed: () => of(value),
    } as unknown as MatDialogRef<unknown>);
  }

  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({ id: '5' }));
    TestBed.configureTestingModule({
      imports: [JournalDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  afterEach(() => backend.verify());

  it('shows the header, the period, the lines and their totals', () => {
    setup(['GL_VIEW']);

    expect(text()).toContain('JNL/26/00005');
    expect(text()).toContain('Opening balance correction');
    expect(text()).toContain('OCT-2026');
    expect(text()).toContain('1201 – Main Bank Account');
    expect(text()).toContain('Dr bank');
    expect(instance['totalDebit']()).toBe(1500);
    expect(instance['totalCredit']()).toBe(1500);
    expect(text()).toContain('Prepared by user #11');
  });

  it('warns that posting is refused when the period is not open', () => {
    setup(['GL_VIEW'], journal({}), { ...openPeriod, status: 'CLOSED' });

    expect(text()).toContain('This period is closed.');
    expect(text()).toContain('refused until a finance manager reopens it');
  });

  it('does not warn about an open period', () => {
    setup(['GL_VIEW']);

    expect(text()).not.toContain('This period is');
  });

  it('links a reversal to the journal it reverses', () => {
    setup(['GL_VIEW'], journal({ reversalOfId: 3, journalType: 'REVERSAL', status: 'POSTED' }));

    expect(text()).toContain('journal #3');
  });

  describe('which action each status offers', () => {
    it('a draft can be submitted by a GL_POST holder, and by nobody else', () => {
      setup(['GL_POST']);
      expect(labels()).toEqual(['Submit for approval']);
    });

    it('a draft shows no action to a read-only user', () => {
      setup(['GL_VIEW']);
      expect(labels()).toEqual([]);
    });

    it('a submitted journal is approved by JOURNAL_APPROVE, not by GL_POST', () => {
      setup(['GL_POST'], journal({ status: 'SUBMITTED' }));
      expect(labels()).toEqual([]);
    });

    it('a submitted journal offers Approve to a JOURNAL_APPROVE holder', () => {
      setup(['JOURNAL_APPROVE'], journal({ status: 'SUBMITTED' }));
      expect(labels()).toEqual(['Approve']);
    });

    it('an approved journal offers Post to the ledger to a GL_POST holder', () => {
      setup(['GL_POST'], journal({ status: 'APPROVED' }));
      expect(labels()).toEqual(['Post to ledger']);
    });

    it('a posted journal offers Reverse to a GL_POST holder', () => {
      setup(['GL_POST'], journal({ status: 'POSTED' }));
      expect(labels()).toEqual(['Reverse']);
    });

    it('a reversed journal offers nothing, so it cannot be reversed twice', () => {
      setup(['GL_POST', 'JOURNAL_APPROVE'], journal({ status: 'REVERSED' }));
      expect(labels()).toEqual([]);
    });
  });

  it('submits through the real endpoint and shows the new status', () => {
    setup(['GL_POST']);

    instance['submit']();

    const req = backend.expectOne('/api/journals/5/submit');
    expect(req.request.method).toBe('POST');
    req.flush(journal({ status: 'SUBMITTED' }));
    fixture.detectChanges();
    expect(instance['journal']().status).toBe('SUBMITTED');
    expect(instance['working']()).toBe(false);
  });

  it('approves and posts through their endpoints', () => {
    setup(['JOURNAL_APPROVE', 'GL_POST'], journal({ status: 'SUBMITTED' }));

    instance['approve']();
    const approve = backend.expectOne('/api/journals/5/approve');
    expect(approve.request.method).toBe('POST');
    approve.flush(journal({ status: 'APPROVED' }));

    instance['post']();
    const post = backend.expectOne('/api/journals/5/post');
    expect(post.request.method).toBe('POST');
    post.flush(journal({ status: 'POSTED' }));
    expect(instance['journal']().status).toBe('POSTED');
  });

  it('shows the server explanation when posting is refused', () => {
    setup(['GL_POST'], journal({ status: 'APPROVED' }));

    instance['post']();
    backend
      .expectOne('/api/journals/5/post')
      .flush(
        { status: 422, code: 'PERIOD_CLOSED', detail: 'No open fiscal period for date 2026-10-07' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(text()).toContain('No open fiscal period for date 2026-10-07');
    expect(instance['journal']().status).toBe('APPROVED');
    expect(instance['working']()).toBe(false);
  });

  it('asks for a reason, reverses with it, and opens the reversing journal', () => {
    setup(['GL_POST'], journal({ status: 'POSTED' }));
    const dialog = dialogReturning('Entered against the wrong account');

    instance['reverse']();

    expect((dialog.calls.mostRecent().args[1] as { data: unknown }).data).toEqual(
      jasmine.objectContaining({ label: 'Reason for reversing', confirmLabel: 'Reverse journal' }),
    );
    const req = backend.expectOne(
      (r) => r.url === '/api/journals/5/reverse?reason=Entered%20against%20the%20wrong%20account',
    );
    expect(req.request.method).toBe('POST');
    req.flush(journal({ id: 9, journalNumber: 'JNL/26/00009', journalType: 'REVERSAL', reversalOfId: 5 }));
    expect(navigate).toHaveBeenCalledWith(['/finance/journals', 9]);
  });

  it('does not reverse when the reason dialog is dismissed', () => {
    setup(['GL_POST'], journal({ status: 'POSTED' }));
    const dialog = dialogReturning(undefined);

    instance['reverse']();

    expect(dialog).toHaveBeenCalledTimes(1);
    backend.expectNone((r) => r.url.startsWith('/api/journals/5/reverse'));
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the server explanation when the reversal is refused', () => {
    setup(['GL_POST'], journal({ status: 'POSTED' }));
    dialogReturning('wrong account');

    instance['reverse']();
    backend
      .expectOne((r) => r.url.startsWith('/api/journals/5/reverse'))
      .flush(
        { status: 422, code: 'ILLEGAL_STATE_TRANSITION', detail: 'Journal JNL/26/00005 is REVERSED, expected POSTED' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(text()).toContain('Journal JNL/26/00005 is REVERSED, expected POSTED');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('loads the other journal when the id in the address changes', () => {
    setup(['GL_VIEW']);

    params.next(convertToParamMap({ id: '9' }));

    backend.expectOne('/api/journals/9').flush(journal({ id: 9, journalNumber: 'JNL/26/00009' }));
    fixture.detectChanges();
    expect(text()).toContain('JNL/26/00009');
  });

  it('says so when the journal cannot be loaded', () => {
    fixture = TestBed.createComponent(JournalDetailComponent);
    fixture.detectChanges();

    backend
      .expectOne('/api/journals/5')
      .flush(
        { status: 404, code: 'NOT_FOUND', detail: 'Journal 5 was not found' },
        { status: 404, statusText: 'Not Found' },
      );
    backend.expectOne('/api/periods').flush([]);
    fixture.detectChanges();

    expect(text()).toContain('Journal 5 was not found');
  });
});
