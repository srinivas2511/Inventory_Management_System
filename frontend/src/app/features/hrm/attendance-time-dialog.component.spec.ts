import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AttendanceTimeDialogComponent } from './attendance-time-dialog.component';

describe('AttendanceTimeDialogComponent', () => {
  let fixture: ComponentFixture<AttendanceTimeDialogComponent>;
  let component: AttendanceTimeDialogComponent;
  let closeSpy: jasmine.Spy;

  beforeEach(async () => {
    closeSpy = jasmine.createSpy('close');
    await TestBed.configureTestingModule({
      imports: [AttendanceTimeDialogComponent, NoopAnimationsModule],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: { employeeName: 'EMP/26/00001 Test User', attendanceDate: '2026-10-27', status: 'PRESENT' },
        },
        { provide: MatDialogRef, useValue: { close: closeSpy } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AttendanceTimeDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the employee name and date', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('EMP/26/00001 Test User');
    expect(text).toContain('2026-10-27');
  });

  it('save button is disabled when check-in is empty', () => {
    component.form.setValue({ checkIn: '', checkOut: '', remarks: '' });
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button[color="primary"]') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
  });

  it('save() closes dialog with correct IST timestamps', () => {
    component.form.setValue({ checkIn: '09:00', checkOut: '18:00', remarks: 'On time' });
    component.save();
    expect(closeSpy).toHaveBeenCalledWith({
      checkIn: '2026-10-27T09:00:00+05:30',
      checkOut: '2026-10-27T18:00:00+05:30',
      remarks: 'On time',
    });
  });

  it('save() sends null check-out when left empty', () => {
    component.form.setValue({ checkIn: '08:30', checkOut: '', remarks: '' });
    component.save();
    expect(closeSpy).toHaveBeenCalledWith({
      checkIn: '2026-10-27T08:30:00+05:30',
      checkOut: null,
      remarks: null,
    });
  });
});
