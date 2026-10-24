import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RoleDto, UserDto } from '../admin.api';
import { UserRolesDialogComponent } from './user-roles-dialog.component';

const USER: UserDto = {
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

const role = (code: string, name: string, module = 'OPS', permissions = 3): RoleDto => ({
  id: code.length,
  code,
  name,
  description: `${name} description`,
  systemRole: true,
  permissions: Array.from({ length: permissions }, (_, i) => ({
    id: i,
    code: `${code}_P${i}`,
    module,
    description: `${code} permission ${i}`,
  })),
});

describe('UserRolesDialogComponent', () => {
  let fixture: ComponentFixture<UserRolesDialogComponent>;
  let http: HttpTestingController;
  let close: jasmine.Spy;

  const flushRoles = () => {
    http
      .expectOne({ method: 'GET', url: '/api/admin/roles' })
      // OPERATOR is in the Operations group; SALES is in Sales & Dispatch
      .flush([role('OPERATOR', 'Operator', 'PRODUCTION', 3), role('SALES', 'Sales', 'SALES', 5)]);
    fixture.detectChanges();
  };

  beforeEach(() => {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [UserRolesDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { user: USER } },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UserRolesDialogComponent);
    fixture.detectChanges();
    flushRoles();
  });

  afterEach(() => http.verify());

  const checkbox = (code: string): HTMLInputElement =>
    (fixture.nativeElement as HTMLElement).querySelector(
      `[data-testid="role-${code}"] input[type=checkbox]`,
    ) as HTMLInputElement;

  const expandBtn = (code: string): HTMLButtonElement =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="expand-${code}"]`) as HTMLButtonElement;

  const permPanel = (code: string): HTMLElement | null =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="perms-${code}"]`);

  it('pre-checks the roles the user already holds and leaves others unchecked', () => {
    expect(checkbox('OPERATOR')?.checked).toBeTrue();
    expect(checkbox('SALES')?.checked).toBeFalse();
  });

  it('shows the permission count on the expand button', () => {
    expect(expandBtn('OPERATOR')?.textContent).toContain('3 permissions');
    expect(expandBtn('SALES')?.textContent).toContain('5 permissions');
  });

  it('shows group section labels for the roles in the list', () => {
    const labels = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.group-label')).map((el) =>
      el.textContent?.trim(),
    );
    expect(labels).toContain('Operations');
    expect(labels).toContain('Sales & Dispatch');
  });

  it('expands and collapses the permission panel when the expand button is clicked', () => {
    expect(permPanel('OPERATOR')).toBeNull();

    expandBtn('OPERATOR').click();
    fixture.detectChanges();

    const panel = permPanel('OPERATOR');
    expect(panel).not.toBeNull();
    expect(panel?.textContent).toContain('OPERATOR permission 0');

    expandBtn('OPERATOR').click();
    fixture.detectChanges();
    expect(permPanel('OPERATOR')).toBeNull();
  });

  it('groups permissions by module inside the permission panel', () => {
    expandBtn('SALES').click();
    fixture.detectChanges();

    const panel = permPanel('SALES');
    const moduleHeaders = Array.from(panel?.querySelectorAll('.perm-module') ?? []).map((el) => el.textContent?.trim());
    expect(moduleHeaders).toContain('SALES');
  });

  it('saves the chosen set of role codes and closes with the updated user', () => {
    checkbox('SALES').click();
    fixture.detectChanges();

    ((fixture.nativeElement as HTMLElement).querySelector('[data-testid="roles-save"]') as HTMLButtonElement).click();

    const req = http.expectOne({ method: 'PUT', url: '/api/admin/users/7/roles' });
    expect((req.request.body as { roleCodes: string[] }).roleCodes.sort()).toEqual(['OPERATOR', 'SALES']);
    const updated = { ...USER, roles: ['OPERATOR', 'SALES'] };
    req.flush(updated);
    expect(close).toHaveBeenCalledWith(updated);
  });

  it('unchecking a pre-selected role removes it from the saved set', () => {
    checkbox('OPERATOR').click();
    fixture.detectChanges();

    ((fixture.nativeElement as HTMLElement).querySelector('[data-testid="roles-save"]') as HTMLButtonElement).click();

    const req = http.expectOne({ method: 'PUT', url: '/api/admin/users/7/roles' });
    expect((req.request.body as { roleCodes: string[] }).roleCodes).toEqual([]);
    req.flush({ ...USER, roles: [] });
    expect(close).toHaveBeenCalled();
  });

  it('shows why a save was refused and stays open', () => {
    ((fixture.nativeElement as HTMLElement).querySelector('[data-testid="roles-save"]') as HTMLButtonElement).click();
    http
      .expectOne({ method: 'PUT', url: '/api/admin/users/7/roles' })
      .flush(
        { code: 'NOT_FOUND', status: 404, detail: 'Unknown role codes: [X]' },
        { status: 404, statusText: 'Not Found' },
      );
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="roles-error"]')?.textContent).toContain(
      'Unknown role codes',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
