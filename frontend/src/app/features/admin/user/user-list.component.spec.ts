import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { SessionStore } from '../../../core/auth/session.store';
import { ToastService } from '../../../core/notification/toast.service';
import { Page, UserDto } from '../admin.api';
import { UserListComponent } from './user-list.component';

const user = (over: Partial<UserDto> = {}): UserDto => ({
  id: 2,
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
  version: 3,
  ...over,
});

const page = (content: UserDto[]): Page<UserDto> => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  size: 20,
  number: 0,
});

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let http: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;
  let dialog: MatDialog;

  const listCall =
    (active: string | null = 'true') =>
    (r: { url: string; params: { get(k: string): string | null } }) =>
      r.url === '/api/admin/users' && r.params.get('active') === active;

  function setup(permissions: string[], rows: UserDto[] = [user()]): void {
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    TestBed.configureTestingModule({
      imports: [UserListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ToastService, useValue: toast }],
    });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'admin',
      fullName: 'Admin',
      roles: ['ADMIN'],
      permissions,
      mustChangePassword: false,
    });
    http = TestBed.inject(HttpTestingController);
    dialog = TestBed.inject(MatDialog);
    fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    http.expectOne('/api/admin/users/capacity').flush({ total: rows.length, limit: 0 });
    http.expectOne(listCall()).flush(page(rows));
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const buttons = (testId: string) => el().querySelectorAll(`[data-testid="${testId}"]`);

  afterEach(() => http.verify());

  it('lists the users the server returns, with their roles and status', () => {
    setup(['USER_VIEW'], [user(), user({ id: 3, username: 'meena', roles: [], mustChangePassword: true })]);

    const text = el().textContent ?? '';
    expect(text).toContain('Ravi Kumar');
    expect(text).toContain('OPERATOR');
    expect(text).toContain('No roles yet');
    expect(text).toContain('Password change pending');
    expect(text).toContain('Never');
  });

  it('offers only the actions the signed-in user holds permission for', () => {
    setup(['USER_VIEW']);
    expect(buttons('new-user').length).toBe(0);
    expect(buttons('edit-user').length).toBe(0);
    expect(buttons('roles-user').length).toBe(0);
    expect(buttons('deactivate-user').length).toBe(0);
  });

  it('shows every action to an administrator', () => {
    setup(['USER_VIEW', 'USER_CREATE', 'USER_UPDATE', 'ROLE_MANAGE', 'USER_DELETE']);
    expect(buttons('new-user').length).toBe(1);
    expect(buttons('edit-user').length).toBe(1);
    expect(buttons('roles-user').length).toBe(1);
    expect(buttons('deactivate-user').length).toBe(1);
  });

  it('does not let you deactivate your own account', () => {
    setup(['USER_VIEW', 'USER_DELETE'], [user({ id: 1, username: 'admin' })]);
    expect((buttons('deactivate-user')[0] as HTMLButtonElement).disabled).toBeTrue();
  });

  it('asks before deactivating, then deactivates and reloads', () => {
    setup(['USER_VIEW', 'USER_DELETE']);
    const open = spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown>);

    (buttons('deactivate-user')[0] as HTMLButtonElement).click();
    expect(open).toHaveBeenCalled();
    http
      .expectOne({ method: 'DELETE', url: '/api/admin/users/2' })
      .flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(listCall()).flush(page([]));

    expect(toast.success).toHaveBeenCalledWith('ravi deactivated');
  });

  it('does nothing when the confirmation is cancelled', () => {
    setup(['USER_VIEW', 'USER_DELETE']);
    spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(undefined) } as MatDialogRef<unknown>);

    (buttons('deactivate-user')[0] as HTMLButtonElement).click();

    http.expectNone({ method: 'DELETE', url: '/api/admin/users/2' });
  });

  it('explains why the last administrator cannot be deactivated', () => {
    setup(['USER_VIEW', 'USER_DELETE']);
    spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown>);

    (buttons('deactivate-user')[0] as HTMLButtonElement).click();
    http
      .expectOne({ method: 'DELETE', url: '/api/admin/users/2' })
      .flush(
        { code: 'LAST_ADMIN', status: 400, detail: 'Cannot deactivate the last active administrator' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(toast.error).toHaveBeenCalledWith('Cannot deactivate the last active administrator');
  });

  it('reactivates an inactive user straight from the list', () => {
    setup(['USER_VIEW', 'USER_UPDATE'], [user({ active: false })]);

    (buttons('reactivate-user')[0] as HTMLButtonElement).click();
    http.expectOne({ method: 'POST', url: '/api/admin/users/2/reactivate' }).flush(user());
    http.expectOne(listCall()).flush(page([]));

    expect(toast.success).toHaveBeenCalledWith('ravi reactivated');
  });

  it('searches the server after a short pause in typing', fakeAsync(() => {
    setup(['USER_VIEW']);
    const input = el().querySelector('[data-testid="user-search"]') as HTMLInputElement;
    input.value = 'rav';
    input.dispatchEvent(new Event('input'));
    tick(299);
    http.expectNone((r) => r.url === '/api/admin/users' && r.params.get('q') === 'rav');
    tick(1);

    http.expectOne((r) => r.url === '/api/admin/users' && r.params.get('q') === 'rav').flush(page([]));
  }));
  describe('password reset and unlock', () => {
    it('offers Reset password only to holders of USER_PASSWORD_RESET, and never on your own row', () => {
      setup(['USER_VIEW', 'USER_PASSWORD_RESET'], [user(), user({ id: 1, username: 'admin' })]);
      expect(buttons('reset-password-user').length).toBe(1);
    });

    it('hides Reset password from someone who can only update users', () => {
      setup(['USER_VIEW', 'USER_UPDATE']);
      expect(buttons('reset-password-user').length).toBe(0);
    });

    it('reloads and confirms once the reset dialog reports the password was reset', () => {
      setup(['USER_VIEW', 'USER_PASSWORD_RESET']);
      const open = spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown>);

      (buttons('reset-password-user')[0] as HTMLButtonElement).click();

      expect(open).toHaveBeenCalledTimes(1);
      http.expectOne(listCall()).flush(page([user()]));
      expect(toast.success).toHaveBeenCalledWith('Password reset for ravi');
    });

    it('does not reload when the reset dialog is cancelled', () => {
      setup(['USER_VIEW', 'USER_PASSWORD_RESET']);
      spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(undefined) } as MatDialogRef<unknown>);

      (buttons('reset-password-user')[0] as HTMLButtonElement).click();

      http.expectNone(listCall());
      expect(toast.success).not.toHaveBeenCalled();
    });

    it('shows Unlock only on a locked account, and unlocks it', () => {
      setup(
        ['USER_VIEW', 'USER_UPDATE'],
        [user({ locked: true, lockedUntil: '2026-10-07T10:00:00Z' }), user({ id: 3, username: 'meena' })],
      );
      expect(buttons('unlock-user').length).toBe(1);
      expect(buttons('locked-caption').length).toBe(1);

      (buttons('unlock-user')[0] as HTMLButtonElement).click();

      const req = http.expectOne('/api/admin/users/2/unlock');
      expect(req.request.method).toBe('POST');
      req.flush(user());
      http.expectOne(listCall()).flush(page([user()]));
      expect(toast.success).toHaveBeenCalledWith('ravi unlocked');
    });

    it('hides Unlock from someone who cannot update users', () => {
      setup(['USER_VIEW'], [user({ locked: true, lockedUntil: '2026-10-07T10:00:00Z' })]);
      expect(buttons('unlock-user').length).toBe(0);
    });
  });
});
