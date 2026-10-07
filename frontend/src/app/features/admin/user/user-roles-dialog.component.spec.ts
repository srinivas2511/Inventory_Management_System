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

const role = (code: string, name: string, permissions = 3): RoleDto => ({
  id: code.length,
  code,
  name,
  description: null,
  systemRole: true,
  permissions: Array.from({ length: permissions }, (_, i) => ({
    id: i,
    code: `${code}_P${i}`,
    module: 'X',
    description: null,
  })),
});

describe('UserRolesDialogComponent', () => {
  let fixture: ComponentFixture<UserRolesDialogComponent>;
  let http: HttpTestingController;
  let close: jasmine.Spy;

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
    http
      .expectOne({ method: 'GET', url: '/api/admin/roles' })
      .flush([role('OPERATOR', 'Operator'), role('SALES', 'Sales', 5)]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const options = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('mat-list-option'));

  it('lists the roles with their permission counts and ticks the ones the user already has', () => {
    const text = options().map((o) => o.textContent ?? '');
    expect(text.length).toBe(2);
    expect(text[1]).toContain('5 permissions');
    expect(options()[0].getAttribute('aria-selected')).toBe('true');
    expect(options()[1].getAttribute('aria-selected')).toBe('false');
  });

  it('saves the chosen set of role codes and closes with the updated user', () => {
    (options()[1] as HTMLElement).click();
    fixture.detectChanges();
    ((fixture.nativeElement as HTMLElement).querySelector('[data-testid="roles-save"]') as HTMLButtonElement).click();

    const req = http.expectOne({ method: 'PUT', url: '/api/admin/users/7/roles' });
    expect((req.request.body as { roleCodes: string[] }).roleCodes.sort()).toEqual(['OPERATOR', 'SALES']);
    const updated = { ...USER, roles: ['OPERATOR', 'SALES'] };
    req.flush(updated);
    expect(close).toHaveBeenCalledWith(updated);
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
