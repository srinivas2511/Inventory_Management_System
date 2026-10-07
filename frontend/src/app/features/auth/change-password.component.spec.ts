import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { SessionStore } from '../../core/auth/session.store';
import { ToastService } from '../../core/notification/toast.service';
import { ChangePasswordComponent } from './change-password.component';

const CURRENT = 'Temp#Start-2026';
const NEXT = 'Chosen#Pass-2027';

describe('ChangePasswordComponent', () => {
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let auth: jasmine.SpyObj<AuthService>;
  let toast: jasmine.SpyObj<ToastService>;
  let navigateByUrl: jasmine.Spy;

  function setup(mustChangePassword: boolean, returnUrl: string | null = null): void {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['changePassword', 'logout']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    TestBed.configureTestingModule({
      imports: [ChangePasswordComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) } },
        },
      ],
    });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'asha',
      fullName: 'Asha Rao',
      roles: [],
      permissions: ['DASHBOARD_VIEW'],
      mustChangePassword,
    });
    navigateByUrl = spyOn(TestBed.inject(Router), 'navigateByUrl');
    fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const field = (testId: string) => el().querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
  const type = (testId: string, value: string) => {
    field(testId).value = value;
    field(testId).dispatchEvent(new Event('input'));
  };
  const fill = (current = CURRENT, next = NEXT, confirm = next) => {
    type('current-password', current);
    type('new-password', next);
    type('confirm-password', confirm);
    fixture.detectChanges();
  };
  const submit = () => {
    field('change-submit').click();
    fixture.detectChanges();
  };

  it('explains a forced change and offers sign-out, not cancel', () => {
    setup(true);
    expect(el().querySelector('[data-testid="forced-note"]')?.textContent).toContain('temporary password');
    expect(el().querySelector('[data-testid="change-cancel"]')).toBeNull();

    el().querySelector<HTMLButtonElement>('[data-testid="forced-signout"]')!.click();
    expect(auth.logout).toHaveBeenCalled();
  });

  it('is an ordinary form with a way back when the user chose to change it', () => {
    setup(false);
    expect(el().querySelector('[data-testid="forced-note"]')).toBeNull();
    expect(el().querySelector('[data-testid="change-cancel"]')).not.toBeNull();
  });

  it('changes the password, tells the user, and goes to the page they were heading for', () => {
    setup(true, '/inventory/stock');
    auth.changePassword.and.returnValue(of(undefined));
    fill();
    submit();

    expect(auth.changePassword).toHaveBeenCalledWith(CURRENT, NEXT);
    expect(toast.success).toHaveBeenCalled();
    expect(navigateByUrl).toHaveBeenCalledWith('/inventory/stock');
  });

  it('goes to the dashboard when there is nowhere else to go, and never to another site', () => {
    setup(false, 'https://evil.example.com/');
    auth.changePassword.and.returnValue(of(undefined));
    fill();
    submit();

    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('does not submit a new password that breaks the rules', () => {
    setup(false);
    fill(CURRENT, 'alllowercase');
    submit();

    expect(auth.changePassword).not.toHaveBeenCalled();
  });

  it('does not submit the same password again', () => {
    setup(false);
    fill(CURRENT, CURRENT);
    submit();

    expect(auth.changePassword).not.toHaveBeenCalled();
    expect(el().querySelector('[data-testid="same-as-current"]')).not.toBeNull();
  });

  it('does not submit when the confirmation differs', () => {
    setup(false);
    fill(CURRENT, NEXT, 'Chosen#Pass-2028');
    submit();

    expect(auth.changePassword).not.toHaveBeenCalled();
  });

  it('marks the current-password field when the server says it is wrong, and stays on the page', () => {
    setup(false);
    auth.changePassword.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'CURRENT_PASSWORD_INCORRECT', status: 400, detail: 'Current password is incorrect' },
          }),
      ),
    );
    fill();
    submit();

    expect(el().textContent).toContain('That is not your current password');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows any other server explanation, such as a reused password', () => {
    setup(false);
    auth.changePassword.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'PASSWORD_POLICY',
              status: 400,
              detail: 'Password cannot be the same as your last 5 passwords',
            },
          }),
      ),
    );
    fill();
    submit();

    expect(el().querySelector('[data-testid="change-error"]')?.textContent).toContain('last 5 passwords');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
