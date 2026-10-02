import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { ShellComponent } from './shell.component';
import { NAV_GROUPS, visibleGroups } from './nav.config';

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), provideNoopAnimations(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
  });

  it('renders the top bar with the application name', () => {
    expect(fixture.nativeElement.querySelector('mat-toolbar').textContent).toContain('Spring IMS');
  });

  it('shows only enabled navigation items', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Dashboard');
    expect(text).not.toContain('Audit Logs');
  });

  it('hides groups that have no enabled items', () => {
    // With no logged-in user, permission-gated items are hidden — only 'Overview' (no permission) remains.
    const groups = visibleGroups(NAV_GROUPS);
    expect(groups.map((g) => g.label)).toEqual(['Overview']);
  });
});
