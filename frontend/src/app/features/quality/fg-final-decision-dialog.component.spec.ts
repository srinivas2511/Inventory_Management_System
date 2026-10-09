import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FgFinalDecision, FgFinalDecisionDialogComponent } from './fg-final-decision-dialog.component';

describe('FgFinalDecisionDialogComponent (replaces the native prompt() of the FG final decision)', () => {
  let fixture: ComponentFixture<FgFinalDecisionDialogComponent>;
  const result: { value: FgFinalDecision | undefined } = { value: undefined };
  const outcome = (): FgFinalDecision | undefined => result.value;

  beforeEach(async () => {
    result.value = undefined;
    await TestBed.configureTestingModule({
      imports: [FgFinalDecisionDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { batchNumber: 'FG/26/00007', producedQty: 10 } },
        { provide: MatDialogRef, useValue: { close: (v?: FgFinalDecision) => (result.value = v) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(FgFinalDecisionDialogComponent);
    fixture.detectChanges();
  });

  const approved = (): HTMLInputElement => fixture.nativeElement.querySelectorAll('input[type="number"]')[0];
  const rejected = (): HTMLInputElement => fixture.nativeElement.querySelectorAll('input[type="number"]')[1];

  function type(el: HTMLInputElement | HTMLTextAreaElement, value: string): void {
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  const text = (): string => fixture.nativeElement.textContent as string;
  const hold = (): string =>
    (fixture.nativeElement.querySelector('[data-testid="hold-preview"]') as HTMLElement).textContent as string;

  it('names the batch and the produced quantity, and starts with everything on hold', () => {
    expect(text()).toContain('FG/26/00007');
    expect(text()).toContain('10.000 produced');
    expect(hold()).toContain('10.000');
  });

  it('refuses to close until the approved quantity is entered', () => {
    submit();
    expect(outcome()).toBeUndefined();
    expect(text()).toContain('Enter the quantity approved');
  });

  it('previews the remainder as hold: produced 10, approved 6, rejected 2 leaves 2', () => {
    type(approved(), '6');
    type(rejected(), '2');
    expect(hold()).toContain('2.000');
    submit();
    expect(outcome()).toEqual({ approvedQty: 6, rejectedQty: 2, holdQty: 2, remarks: '' });
  });

  it('refuses approved plus rejected above what was produced and says so', () => {
    type(approved(), '7');
    type(rejected(), '4');
    expect(fixture.nativeElement.querySelector('[data-testid="over-allocated"]')).not.toBeNull();
    submit();
    expect(outcome()).toBeUndefined();
  });

  it('refuses a negative quantity and a quantity above what was produced', () => {
    type(approved(), '-1');
    submit();
    expect(outcome()).toBeUndefined();
    type(approved(), '11');
    submit();
    expect(outcome()).toBeUndefined();
  });

  it('works in thousandths without floating point drift', () => {
    type(approved(), '0.1');
    type(rejected(), '0.2');
    submit();
    expect(outcome()).toEqual({ approvedQty: 0.1, rejectedQty: 0.2, holdQty: 9.7, remarks: '' });
  });

  it('allows approving everything, with no hold', () => {
    type(approved(), '10');
    const remarks = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    type(remarks, '  all within tolerance  ');
    submit();
    expect(outcome()).toEqual({ approvedQty: 10, rejectedQty: 0, holdQty: 0, remarks: 'all within tolerance' });
  });
});
