/* eslint-disable @typescript-eslint/no-explicit-any */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ReasonDialogComponent } from './reason-dialog.component';

describe('ReasonDialogComponent', () => {
  let fixture: ComponentFixture<ReasonDialogComponent>;
  let close: jasmine.Spy;
  let instance: Record<string, any>;

  beforeEach(() => {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [ReasonDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { title: 'Reverse JNL/1?', message: 'm', label: 'Reason for reversing', confirmLabel: 'Reverse' },
        },
      ],
    });
    fixture = TestBed.createComponent(ReasonDialogComponent);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
  });

  it('shows the title and the label it was given', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Reverse JNL/1?');
    expect(text).toContain('Reason for reversing');
  });

  it('refuses an empty or blank reason and closes nothing', () => {
    instance['confirm']();
    instance['form'].controls.reason.setValue('    ');
    instance['confirm']();

    expect(close).not.toHaveBeenCalled();
    expect(instance['form'].invalid).toBe(true);
  });

  it('closes with the trimmed reason', () => {
    instance['form'].controls.reason.setValue('  Entered against the wrong account ');

    instance['confirm']();

    expect(close).toHaveBeenCalledWith('Entered against the wrong account');
  });

  it('allows 200 characters unless told otherwise', () => {
    expect(instance['maxLength']).toBe(200);
  });
});
