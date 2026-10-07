import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { AuthService, LoginResponse } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let navigateByUrl: jasmine.Spy;

  function setup(returnUrl: string | null): void {
    const auth = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    auth.login.and.returnValue(of({} as LoginResponse));
    TestBed.configureTestingModule({
      imports: [LoginComponent, NoopAnimationsModule],
      providers: [
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) } },
        },
      ],
    });
    navigateByUrl = spyOn(TestBed.inject(Router), 'navigateByUrl');
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  }

  function type(testId: string, value: string): void {
    const input = (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    type('username', 'asha');
    type('password', 'secret');
    ((fixture.nativeElement as HTMLElement).querySelector('[data-testid="login-submit"]') as HTMLButtonElement).click();
  }

  it('returns to the page the user was on when the session ended', () => {
    setup('/inventory/stock?warehouse=2');
    submit();
    expect(navigateByUrl).toHaveBeenCalledWith('/inventory/stock?warehouse=2');
  });

  it('goes to the dashboard when there is nothing to return to', () => {
    setup(null);
    submit();
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('never follows a return URL that leaves the app', () => {
    setup('https://evil.example.com/');
    submit();
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });
});
