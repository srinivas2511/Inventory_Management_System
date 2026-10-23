/**
 * WCAG 2.1 AA contract tests for shared components (NFR-007, R19-7.3).
 *
 * These tests verify structural accessibility properties that would otherwise
 * only be caught by manual auditing or a browser-based axe scan.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { StatusBadgeComponent } from './status-badge.component';

describe('WCAG audit — StatusBadgeComponent (NFR-007)', () => {
  let fixture: ComponentFixture<StatusBadgeComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(StatusBadgeComponent);
    el = fixture.nativeElement;
  });

  function render(status: string, label?: string): void {
    fixture.componentInstance.status = status;
    if (label !== undefined) fixture.componentInstance.label = label;
    fixture.detectChanges();
  }

  it('SC 1.1.1 — badge span has role="img" so screen readers treat it as a labelled image', () => {
    render('IN_PROGRESS');
    const badge = el.querySelector('.status-badge') as HTMLElement;
    expect(badge.getAttribute('role')).toBe('img');
  });

  it('SC 1.1.1 — badge span has an aria-label equal to the display label', () => {
    render('IN_PROGRESS');
    const badge = el.querySelector('.status-badge') as HTMLElement;
    expect(badge.getAttribute('aria-label')).toBe('In Progress');
  });

  it('SC 1.1.1 — aria-label uses a custom label input when provided', () => {
    render('AVAILABLE', 'In stock');
    const badge = el.querySelector('.status-badge') as HTMLElement;
    expect(badge.getAttribute('aria-label')).toBe('In stock');
  });

  it('SC 1.1.1 — mat-icon inside the badge is aria-hidden (decorative)', () => {
    render('REJECTED');
    const icon = el.querySelector('mat-icon') as HTMLElement;
    expect(icon.getAttribute('aria-hidden')).toBe('true');
  });

  it('SC 1.1.1 — unknown status falls back to a sanitised label, not an empty aria-label', () => {
    render('CUSTOM_STATE');
    const badge = el.querySelector('.status-badge') as HTMLElement;
    const label = badge.getAttribute('aria-label') ?? '';
    expect(label.length).toBeGreaterThan(0);
    expect(label).not.toContain('_');
  });
});
