import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FgHoldDecision, FgHoldDecisionDialogComponent } from './fg-hold-decision-dialog.component';

describe('FgHoldDecisionDialogComponent (D-39)', () => {
  let fixture: ComponentFixture<FgHoldDecisionDialogComponent>;
  const result: { value: FgHoldDecision | undefined } = { value: undefined };
  const outcome = (): FgHoldDecision | undefined => result.value;

  beforeEach(async () => {
    result.value = undefined;
    await TestBed.configureTestingModule({
      imports: [FgHoldDecisionDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { batchNumber: 'FG/26/00012', heldQty: 4 } },
        { provide: MatDialogRef, useValue: { close: (v?: FgHoldDecision) => (result.value = v) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(FgHoldDecisionDialogComponent);
    fixture.detectChanges();
  });

  const input = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLInputElement;

  function type(selector: string, value: string): void {
    const el = input(selector);
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('shows the held quantity and the batch', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('FG/26/00012');
    expect(text).toContain('4.000 on hold');
  });

  it('refuses to close without a quantity or a reason', () => {
    submit();
    expect(outcome()).toBeUndefined();
    type('input[type="number"]', '4');
    submit();
    expect(outcome()).toBeUndefined();
  });

  it('refuses more than is on hold and a blank reason', () => {
    type('input[type="number"]', '5');
    type('textarea', 'ok');
    submit();
    expect(outcome()).toBeUndefined();
    type('input[type="number"]', '3');
    type('textarea', '   ');
    submit();
    expect(outcome()).toBeUndefined();
  });

  it('rejects whatever is not approved and previews it', () => {
    type('input[type="number"]', '1.5');
    expect(fixture.nativeElement.querySelector('[data-testid="rejected-preview"]').textContent).toContain('2.500');
    type('textarea', '  re-inspected  ');
    submit();
    expect(outcome()).toEqual({ approvedQty: 1.5, rejectedQty: 2.5, reason: 're-inspected' });
  });

  it('allows approving everything and allows rejecting everything', () => {
    type('input[type="number"]', '4');
    type('textarea', 'all good');
    submit();
    expect(outcome()).toEqual({ approvedQty: 4, rejectedQty: 0, reason: 'all good' });

    result.value = undefined;
    type('input[type="number"]', '0');
    submit();
    expect(outcome()).toEqual({ approvedQty: 0, rejectedQty: 4, reason: 'all good' });
  });
});
