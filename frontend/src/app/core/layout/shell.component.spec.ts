import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../auth/session.store';
import { ShellComponent } from './shell.component';
import { NAV_GROUPS, visibleGroups } from './nav.config';

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), provideNoopAnimations(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  /** The menu is decided when the shell is created, so the user signs in first. */
  function open(permissions: string[]): string {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'asha',
      fullName: 'Asha Rao',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    return fixture.nativeElement.textContent as string;
  }

  it('renders the top bar with the application name', () => {
    open([]);
    expect(fixture.nativeElement.querySelector('mat-toolbar').textContent).toContain('Spring IMS');
  });

  it('shows only enabled navigation items', () => {
    const text = open(['DASHBOARD_VIEW']);
    expect(text).toContain('Dashboard');
    expect(text).not.toContain('Audit Logs');
  });

  it('does not offer the dashboard to someone who may not open it', () => {
    const text = open(['PRODUCTION_EXECUTE']);
    expect(text).not.toContain('Dashboard');
    expect(text).toContain('My Work');
  });

  it('hides groups that have no enabled items', () => {
    // With no logged-in user, permission-gated items are hidden — only 'Overview' (Notifications needs none) remains.
    const groups = visibleGroups(NAV_GROUPS);
    expect(groups.map((g) => g.label)).toEqual(['Overview']);
    expect(groups[0].items.map((i) => i.label)).toEqual(['Notifications']);
  });
});
