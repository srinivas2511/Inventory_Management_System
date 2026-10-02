import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { SessionStore } from '../../../core/auth/session.store';
import { RolesApi } from '../api/roles.api';
import { UsersApi } from '../api/users.api';
import { UsersPageComponent } from './users-page.component';

const rows = [
  { id: 1, username: 'asha', fullName: 'Asha Rao', email: 'a@x.in', active: true, roles: ['ADMIN'] },
  { id: 2, username: 'ravi', fullName: 'Ravi K', email: 'r@x.in', active: false, roles: ['STORE_OPERATOR'] },
];

describe('UsersPageComponent', () => {
  let fixture: ComponentFixture<UsersPageComponent>;
  let users: jasmine.SpyObj<UsersApi>;
  const permissions = signal(new Set<string>());

  beforeEach(async () => {
    users = jasmine.createSpyObj<UsersApi>('UsersApi', ['list', 'get', 'deactivate', 'activate']);
    users.list.and.returnValue(of({ content: rows, page: 0, size: 25, totalElements: 2, totalPages: 1 }));
    const roles = jasmine.createSpyObj<RolesApi>('RolesApi', ['list']);
    roles.list.and.returnValue(of([]));
    permissions.set(new Set());
    await TestBed.configureTestingModule({
      imports: [UsersPageComponent],
      providers: [
        provideNoopAnimations(),
        { provide: UsersApi, useValue: users },
        { provide: RolesApi, useValue: roles },
        { provide: SessionStore, useValue: { permissions } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(UsersPageComponent);
    fixture.detectChanges();
  });

  const q = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  it('lists users from the server with default paging and sort', () => {
    expect(users.list).toHaveBeenCalledWith(
      jasmine.objectContaining({ page: 0, size: 25, sort: 'username,asc', active: null }),
    );
    expect(fixture.nativeElement.querySelectorAll('[data-testid="username-cell"]').length).toBe(2);
  });

  it('hides "New user" without USER_CREATE and shows it with', () => {
    expect(q('new-user')).toBeNull();
    permissions.set(new Set(['USER_CREATE']));
    fixture.detectChanges();
    expect(q('new-user')).not.toBeNull();
  });
});
