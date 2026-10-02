import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { UserProfile } from '../auth/auth.models';
import { SessionStore } from '../auth/session.store';
import { NAV_GROUPS, visibleGroups } from './nav.config';
import { ShellComponent } from './shell.component';

function profile(permissions: string[]): UserProfile {
  return {
    id: 1,
    username: 'asha',
    fullName: 'Asha Rao',
    roles: ['ADMIN'],
    permissions,
    primaryDashboard: 'ADMIN',
  };
}

describe('ShellComponent', () => {
  const user = signal<UserProfile | null>(null);
  let session: { user: typeof user; permissions: ReturnType<typeof signal<Set<string>>>; logout: jasmine.Spy };
  let fixture: ComponentFixture<ShellComponent>;

  function render(permissions: string[]): void {
    user.set(profile(permissions));
    session.permissions.set(new Set(permissions));
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    session = {
      user,
      permissions: signal(new Set<string>()),
      logout: jasmine.createSpy('logout').and.returnValue(of(undefined)),
    };
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: SessionStore, useValue: session }],
    }).compileComponents();
  });

  it('renders the top bar with the application name and the user', () => {
    render([]);
    expect(fixture.nativeElement.querySelector('mat-toolbar').textContent).toContain('Spring IMS');
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
  });

  it('shows only the items the user is permitted to see', () => {
    render(['USER_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Dashboard');
    expect(text).toContain('Users');
    expect(text).not.toContain('Roles');
    expect(text).not.toContain('Audit Logs');
  });

  it('hides the administration group without permissions', () => {
    render([]);
    expect(fixture.nativeElement.textContent).not.toContain('Administration');
  });

  it('signs out and returns to the login page', () => {
    render([]);
    (fixture.nativeElement.querySelector('[data-testid="user-menu"]') as HTMLElement).click();
    fixture.detectChanges();
    (document.querySelector('[data-testid="sign-out"]') as HTMLElement).click();
    expect(session.logout).toHaveBeenCalled();
  });

  it('hides groups that have no enabled items', () => {
    const groups = visibleGroups(NAV_GROUPS);
    expect(groups.map((g) => g.label)).toEqual(['Overview', 'Master Data', 'Administration']);
  });

  it('filters items by permission', () => {
    const groups = visibleGroups(NAV_GROUPS, (p) => p === undefined || p === 'ROLE_MANAGE');
    expect(groups.flatMap((g) => g.items.map((i) => i.label))).toEqual(['Dashboard', 'Roles']);
  });
});
