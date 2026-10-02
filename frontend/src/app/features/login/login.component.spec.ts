import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let session: jasmine.SpyObj<SessionStore>;
  let router: Router;

  const el = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const type = (id: string, value: string) => {
    const input = el(id) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  beforeEach(async () => {
    session = jasmine.createSpyObj<SessionStore>('SessionStore', ['login', 'changePassword']);
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: SessionStore, useValue: session }],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  function signIn(): void {
    type('username', 'asha');
    type('password', 'pw');
    (el('sign-in') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('does not call the server with an empty form', () => {
    (el('sign-in') as HTMLButtonElement).click();
    expect(session.login).not.toHaveBeenCalled();
  });

  it('signs in and goes to the dashboard by default', () => {
    session.login.and.returnValue(of('authenticated'));
    signIn();
    expect(session.login).toHaveBeenCalledWith('asha', 'pw');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('follows a same-site return url but not an external one', () => {
    session.login.and.returnValue(of('authenticated'));
    fixture.componentRef.setInput('returnUrl', '//evil.example.com');
    signIn();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    fixture.componentRef.setInput('returnUrl', '/admin/users');
    (el('sign-in') as HTMLButtonElement).click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/users');
  });

  it('shows a generic message for bad credentials', () => {
    session.login.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401, error: { code: 'BAD_CREDENTIALS', status: 401 } })),
    );
    signIn();
    expect(el('login-error')?.textContent).toContain('Invalid username or password.');
  });

  it('asks for a new password when the account requires one, then signs in', () => {
    session.login.and.returnValue(of('mustChangePassword'));
    session.changePassword.and.returnValue(of('authenticated'));
    signIn();
    expect(el('new-password')).not.toBeNull();
    type('new-password', 'Brand-New-Pass1!');
    type('confirm-password', 'Brand-New-Pass1!');
    (el('change-submit') as HTMLButtonElement).click();
    expect(session.changePassword).toHaveBeenCalledWith('asha', 'pw', 'Brand-New-Pass1!');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });
});
