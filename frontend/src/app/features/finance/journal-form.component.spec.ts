/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { JournalFormComponent } from './journal-form.component';
import { GlAccountDto } from './finance.api';

function account(over: Partial<GlAccountDto>): GlAccountDto {
  return {
    id: 1,
    accountCode: '1201',
    name: 'Main Bank Account',
    accountType: 'ASSET',
    normalBalance: 'DEBIT',
    allowDirectPosting: true,
    active: true,
    ...over,
  };
}

const bank = account({});
const revenue = account({
  id: 2,
  accountCode: '4001',
  name: 'Sales Revenue',
  accountType: 'REVENUE',
  normalBalance: 'CREDIT',
});
const control = account({ id: 3, accountCode: '2402', name: 'Accounts Payable Control', allowDirectPosting: false });
const retired = account({ id: 4, accountCode: '9999', name: 'Retired account', active: false });

describe('JournalFormComponent', () => {
  let fixture: ComponentFixture<JournalFormComponent>;
  let backend: HttpTestingController;
  let instance: Record<string, any>;
  let navigate: jasmine.Spy;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const saveButton = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Save draft'),
    ) as HTMLButtonElement;
  const line = (i: number) => instance['lines'].at(i) as any;

  /** Fills both lines of a balanced journal: Dr bank, Cr revenue. */
  function fillBalanced(amount = 1500): void {
    line(0).patchValue({ account: bank, debit: amount });
    line(1).patchValue({ account: revenue, credit: amount });
    instance['form'].patchValue({ description: 'Opening balance correction' });
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [JournalFormComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(JournalFormComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend.expectOne('/api/gl/accounts').flush([bank, revenue, control, retired]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('offers only active accounts that accept a manual posting', () => {
    const offered = instance['accountOptions']('').map((a: GlAccountDto) => a.accountCode);

    expect(offered).toEqual(['1201', '4001']);
  });

  it('filters the account list by code or by name', () => {
    expect(instance['accountOptions']('4001').map((a: GlAccountDto) => a.accountCode)).toEqual(['4001']);
    expect(instance['accountOptions']('bank').map((a: GlAccountDto) => a.accountCode)).toEqual(['1201']);
    expect(instance['accountOptions'](bank).length).toBe(2); // a picked account shows the whole list again
  });

  it('starts with two lines and never goes below two', () => {
    expect(instance['lines'].length).toBe(2);

    instance['removeLine'](0);
    expect(instance['lines'].length).toBe(2);

    instance['addLine']();
    expect(instance['lines'].length).toBe(3);
    instance['removeLine'](2);
    expect(instance['lines'].length).toBe(2);
  });

  it('keeps a line to a debit or a credit: typing one clears the other', () => {
    line(0).controls.debit.setValue(500);
    line(0).controls.credit.setValue(300);
    expect(line(0).value.debit).toBeNull();
    expect(line(0).value.credit).toBe(300);

    line(0).controls.debit.setValue(200);
    expect(line(0).value.credit).toBeNull();
    expect(line(0).value.debit).toBe(200);
  });

  it('shows the running totals and the difference until the journal balances', () => {
    line(0).patchValue({ account: bank, debit: 1500 });
    line(1).patchValue({ account: revenue, credit: 1000 });
    fixture.detectChanges();

    expect(text()).toContain('Debits 1,500.00');
    expect(text()).toContain('Credits 1,000.00');
    expect(text()).toContain('Out of balance by 500.00');
    expect(instance['isBalanced']()).toBe(false);

    line(1).patchValue({ credit: 1500 });
    fixture.detectChanges();

    expect(text()).toContain('Balanced');
    expect(instance['isBalanced']()).toBe(true);
  });

  it('treats 0.1 + 0.2 as equal to 0.3 when comparing the totals', () => {
    line(0).patchValue({ account: bank, debit: 0.1 });
    line(1).patchValue({ account: revenue, credit: 0.3 });
    instance['addLine']();
    line(2).patchValue({ account: bank, debit: 0.2 });

    expect(instance['isBalanced']()).toBe(true);
  });

  it('keeps Save off, and says why, until every line has a picked account and one amount', () => {
    expect(saveButton().disabled).toBe(true);

    line(0).patchValue({ account: 'bank', debit: 100 }); // typed text is not a picked account
    line(1).patchValue({ account: revenue, credit: 100 });
    instance['form'].patchValue({ description: 'x' });
    fixture.detectChanges();

    expect(instance['canSave']()).toBe(false);
    expect(text()).toContain('Every line needs an account from the list');
    expect(saveButton().disabled).toBe(true);
  });

  it('keeps Save off while the debits and credits differ, and while the description is empty', () => {
    line(0).patchValue({ account: bank, debit: 100 });
    line(1).patchValue({ account: revenue, credit: 90 });
    instance['form'].patchValue({ description: 'x' });
    fixture.detectChanges();
    expect(instance['canSave']()).toBe(false);
    expect(text()).toContain('Debits and credits must be equal');

    line(1).patchValue({ credit: 100 });
    instance['form'].patchValue({ description: '   ' });
    expect(instance['canSave']()).toBe(false);

    instance['form'].patchValue({ description: 'Accrual' });
    expect(instance['canSave']()).toBe(true);
  });

  it('rejects an empty line even when the others balance', () => {
    fillBalanced();
    instance['addLine']();

    expect(instance['canSave']()).toBe(false);
  });

  it('posts the JournalCreateRequest shape, then opens the new draft', () => {
    fillBalanced(1500);
    instance['form'].patchValue({ postingDate: '2026-10-07', description: '  Opening balance correction ' });
    line(0).patchValue({ description: ' Dr bank ' });

    instance['save']();

    const req = backend.expectOne('/api/journals');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      journalType: 'GL',
      postingDate: '2026-10-07',
      description: 'Opening balance correction',
      currencyCode: 'INR',
      lines: [
        { glAccountId: 1, debitAmount: 1500, creditAmount: 0, description: 'Dr bank' },
        { glAccountId: 2, debitAmount: 0, creditAmount: 1500, description: null },
      ],
    });
    req.flush({ id: 42, journalNumber: 'JNL/26/00042' });
    expect(navigate).toHaveBeenCalledWith(['/finance/journals', 42]);
  });

  it('posts nothing when the journal does not balance', () => {
    line(0).patchValue({ account: bank, debit: 100 });
    line(1).patchValue({ account: revenue, credit: 50 });
    instance['form'].patchValue({ description: 'x' });

    instance['save']();

    backend.expectNone('/api/journals');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the server explanation when the period is closed and stays on the form', () => {
    fillBalanced();
    instance['save']();

    backend
      .expectOne('/api/journals')
      .flush(
        { status: 422, code: 'PERIOD_CLOSED', detail: 'No open fiscal period for date 2026-10-07' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(text()).toContain('No open fiscal period for date 2026-10-07');
    expect(navigate).not.toHaveBeenCalled();
    expect(instance['saving']()).toBe(false);
  });

  it('tells the user when the chart of accounts could not be loaded', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [JournalFormComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const failing = TestBed.createComponent(JournalFormComponent);
    const http = TestBed.inject(HttpTestingController);
    failing.detectChanges();

    http.expectOne('/api/gl/accounts').flush({}, { status: 403, statusText: 'Forbidden' });
    failing.detectChanges();

    expect((failing.nativeElement as HTMLElement).textContent).toContain('The chart of accounts could not be loaded');
  });
});
