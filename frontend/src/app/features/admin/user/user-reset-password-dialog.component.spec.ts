import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserDto } from '../admin.api';
import { UserResetPasswordDialogComponent } from './user-reset-password-dialog.component';

const user: UserDto = {
  id: 7,
  username: 'ravi',
  employeeCode: null,
  fullName: 'Ravi Kumar',
  email: 'ravi@example.com',
  phone: null,
  active: true,
  mustChangePassword: false,
  locked: false,
  lockedUntil: null,
  lastLoginAt: null,
  passwordChangedAt: null,
  roles: ['OPERATOR'],
  version: 1,
};

describe('UserResetPasswordDialogComponent', () => {
  let fixture: ComponentFixture<UserResetPasswordDialogComponent>;
  let http: HttpTestingController;
  let dialogRef: { close: jasmine.Spy; disableClose: boolean };

  const el = () => fixture.nativeElement as HTMLElement;
  const byId = <T extends HTMLElement>(id: string) => el().querySelector(`[data-testid="${id}"]`) as T | null;

  function type(id: string, value: string): void {
    const input = byId<HTMLInputElement>(id) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    dialogRef = { close: jasmine.createSpy('close'), disableClose: false };
    TestBed.configureTestingModule({
      imports: [UserResetPasswordDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { user } },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UserResetPasswordDialogComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('cannot be submitted without a reason', () => {
    expect(byId<HTMLButtonElement>('reset-submit')?.disabled).toBeTrue();
    type('reset-reason', '   ');
    expect(byId<HTMLButtonElement>('reset-submit')?.disabled).toBeTrue();
    type('reset-reason', 'Forgot it');
    expect(byId<HTMLButtonElement>('reset-submit')?.disabled).toBeFalse();
  });

  it('asks the server to generate the password and shows it once, with a warning and no accidental dismissal', () => {
    type('reset-reason', 'Forgot it');
    byId<HTMLButtonElement>('reset-submit')?.click();

    const req = http.expectOne('/api/admin/users/7/reset-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Forgot it' });
    req.flush({ temporaryPassword: 'Gk7#mQ2!vNx9Ztp4', generated: true });
    fixture.detectChanges();

    expect(byId<HTMLInputElement>('temporary-password')?.value).toBe('Gk7#mQ2!vNx9Ztp4');
    expect(byId('shown-once')?.textContent).toContain('shown once');
    expect(dialogRef.disableClose).toBeTrue();

    byId<HTMLButtonElement>('reset-done')?.click();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('keeps Reset disabled until a password the administrator chose meets the policy, then sends it', () => {
    type('reset-reason', 'Handing over a laptop');
    (el().querySelector('[data-testid="reset-own"] input') as HTMLInputElement).click();
    fixture.detectChanges();

    type('reset-password-input', 'short');
    expect(byId<HTMLButtonElement>('reset-submit')?.disabled).toBeTrue();
    type('reset-password-input', 'Given#Temp-2026');
    expect(byId<HTMLButtonElement>('reset-submit')?.disabled).toBeFalse();

    byId<HTMLButtonElement>('reset-submit')?.click();
    const req = http.expectOne('/api/admin/users/7/reset-password');
    expect(req.request.body).toEqual({ reason: 'Handing over a laptop', temporaryPassword: 'Given#Temp-2026' });
    req.flush({ temporaryPassword: null, generated: false });
    fixture.detectChanges();

    expect(byId('temporary-password')).toBeNull();
    expect(el().textContent).toContain('The password you chose is set.');
  });

  it('shows the server message and stays on the form when the reset is refused', () => {
    type('reset-reason', 'x');
    byId<HTMLButtonElement>('reset-submit')?.click();

    http
      .expectOne('/api/admin/users/7/reset-password')
      .flush(
        { code: 'SELF_PASSWORD_RESET', status: 422, detail: 'Use Change password for your own account' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(byId('reset-error')?.textContent).toContain('Use Change password for your own account');
    expect(byId('reset-submit')).not.toBeNull();
    expect(dialogRef.disableClose).toBeFalse();
  });
});
