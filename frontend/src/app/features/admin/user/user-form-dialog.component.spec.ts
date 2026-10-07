import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../../core/auth/session.store';
import { RoleDto, UserDto } from '../admin.api';
import { UserFormData, UserFormDialogComponent } from './user-form-dialog.component';

const SAVED: UserDto = {
  id: 9,
  username: 'meena',
  employeeCode: 'E-9',
  fullName: 'Meena Iyer',
  email: 'meena@example.com',
  phone: null,
  active: true,
  mustChangePassword: true,
  lastLoginAt: null,
  passwordChangedAt: null,
  roles: [],
  version: 0,
};

const ROLES: RoleDto[] = [
  { id: 1, code: 'OPERATOR', name: 'Operator', description: null, systemRole: true, permissions: [] },
  { id: 2, code: 'SALES', name: 'Sales', description: null, systemRole: true, permissions: [] },
];

describe('UserFormDialogComponent', () => {
  let fixture: ComponentFixture<UserFormDialogComponent>;
  let http: HttpTestingController;
  let close: jasmine.Spy;

  function setup(permissions: string[], data: UserFormData = {}): void {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [UserFormDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'admin',
      fullName: 'Admin',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UserFormDialogComponent);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const input = (testId: string) => el().querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
  const type = (testId: string, value: string) => {
    input(testId).value = value;
    input(testId).dispatchEvent(new Event('input'));
  };
  const form = () => fixture.componentInstance['form'];
  const submit = () => {
    (el().querySelector('[data-testid="user-save"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  };
  const fillValid = () => {
    type('user-username', 'meena');
    type('user-full-name', 'Meena Iyer');
    type('user-email', 'meena@example.com');
    type('user-employee-code', 'E-9');
    type('user-password', 'Fresh#Start-2026x');
  };

  afterEach(() => http.verify());

  it('creates a user with exactly the fields the API takes, and leaves roles out when none are chosen', () => {
    setup(['USER_CREATE']);
    fillValid();
    submit();

    const req = http.expectOne({ method: 'POST', url: '/api/admin/users' });
    expect(req.request.body).toEqual({
      username: 'meena',
      employeeCode: 'E-9',
      fullName: 'Meena Iyer',
      email: 'meena@example.com',
      phone: null,
      password: 'Fresh#Start-2026x',
    });
    req.flush(SAVED);
    expect(close).toHaveBeenCalledWith(SAVED);
  });

  it('shows the roles control only to someone who may assign roles, and sends the chosen roles', () => {
    setup(['USER_CREATE', 'ROLE_MANAGE']);
    http.expectOne({ method: 'GET', url: '/api/admin/roles' }).flush(ROLES);
    fixture.detectChanges();
    expect(input('user-roles')).not.toBeNull();

    fillValid();
    form().controls.roleCodes.setValue(['OPERATOR', 'SALES']);
    submit();

    const req = http.expectOne({ method: 'POST', url: '/api/admin/users' });
    expect((req.request.body as { roleCodes: string[] }).roleCodes).toEqual(['OPERATOR', 'SALES']);
    req.flush(SAVED);
  });

  it('does not offer roles, or even ask for them, to a holder of USER_CREATE alone', () => {
    setup(['USER_CREATE']);
    http.expectNone({ method: 'GET', url: '/api/admin/roles' });
    expect(input('user-roles')).toBeNull();

    form().controls.roleCodes.setValue(['ADMIN']);
    fillValid();
    submit();

    const req = http.expectOne({ method: 'POST', url: '/api/admin/users' });
    expect('roleCodes' in (req.request.body as object)).toBeFalse();
    req.flush(SAVED);
  });

  it('will not submit a password that breaks the rules, and ticks the rules off as they are met', () => {
    setup(['USER_CREATE']);
    fillValid();
    type('user-password', 'alllowercase');
    fixture.detectChanges();

    const ticked = () => el().querySelectorAll('[data-testid="password-hint"].ok').length;
    expect(ticked()).toBe(3); // length, a lowercase letter, and no username in it
    submit();
    http.expectNone({ method: 'POST', url: '/api/admin/users' });

    type('user-password', 'Fresh#Start-2026x');
    fixture.detectChanges();
    expect(ticked()).toBe(6);
  });

  it('rejects a password that contains the username', () => {
    setup(['USER_CREATE']);
    fillValid();
    type('user-password', 'Meena#Pass-2026');
    fixture.detectChanges();

    expect(form().controls.password.invalid).toBeTrue();
    submit();
    http.expectNone({ method: 'POST', url: '/api/admin/users' });
  });

  it('shows the server explanation inside the dialog and keeps it open', () => {
    setup(['USER_CREATE']);
    fillValid();
    submit();

    http
      .expectOne({ method: 'POST', url: '/api/admin/users' })
      .flush(
        { code: 'DUPLICATE_KEY', status: 409, detail: 'Username already taken: meena' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="user-form-error"]')?.textContent).toContain(
      'Username already taken: meena',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('edits the details only, sending the version the list showed', () => {
    setup(['USER_UPDATE'], { user: { ...SAVED, version: 4 } });
    expect(input('user-password')).toBeNull();
    expect(input('user-username')).toBeNull();
    expect(input('user-full-name').value).toBe('Meena Iyer');

    type('user-full-name', 'Meena I. Iyer');
    submit();

    const req = http.expectOne({ method: 'PUT', url: '/api/admin/users/9' });
    expect(req.request.body).toEqual({
      employeeCode: 'E-9',
      fullName: 'Meena I. Iyer',
      email: 'meena@example.com',
      phone: null,
      version: 4,
    });
    req.flush({ ...SAVED, fullName: 'Meena I. Iyer' });
    expect(close).toHaveBeenCalled();
  });
});
