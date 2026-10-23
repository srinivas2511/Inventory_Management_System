import { DOCUMENT } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';

export interface LocaleOption {
  code: string;
  label: string;
  base: string;
}

export const LOCALES: LocaleOption[] = [
  { code: 'en-IN', label: 'EN', base: '/' },
  { code: 'hi', label: 'हिं', base: '/hi/' },
  { code: 'kn', label: 'ಕನ', base: '/kn/' },
];

/** Resolves the active locale from the current URL path. */
export function activeLocale(pathname: string): string {
  if (pathname.startsWith('/hi/') || pathname === '/hi') return 'hi';
  if (pathname.startsWith('/kn/') || pathname === '/kn') return 'kn';
  return 'en-IN';
}

/** Returns the equivalent path under a different locale's base. */
export function switchedUrl(href: string, toBase: string): string {
  for (const loc of LOCALES) {
    if (loc.base !== '/' && href.includes(loc.base)) {
      return href.replace(loc.base, toBase);
    }
  }
  // Currently on en-IN: prefix the current path with the target base
  const url = new URL(href);
  if (toBase === '/') return href;
  return url.origin + toBase + url.pathname.replace(/^\//, '') + url.search + url.hash;
}

@Component({
  selector: 'app-locale-switcher',
  standalone: true,
  imports: [MatButtonModule, MatButtonToggleModule],
  template: `
    <mat-button-toggle-group [value]="current" aria-label="Select language" class="locale-group">
      @for (loc of locales; track loc.code) {
        <mat-button-toggle [value]="loc.code" (change)="navigate(loc)" [attr.lang]="loc.code">
          {{ loc.label }}
        </mat-button-toggle>
      }
    </mat-button-toggle-group>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        margin-right: 8px;
      }
      .locale-group {
        height: 32px;
        font-size: 0.8rem;
      }
      ::ng-deep .locale-group .mat-button-toggle-button {
        padding: 0 8px;
        height: 32px;
        line-height: 32px;
      }
    `,
  ],
})
export class LocaleSwitcherComponent {
  private readonly doc = inject(DOCUMENT);
  protected readonly locales = LOCALES;
  protected readonly current = activeLocale(this.doc.location.pathname);

  protected navigate(loc: LocaleOption): void {
    if (loc.code === this.current) return;
    this.doc.location.href = switchedUrl(this.doc.location.href, loc.base);
  }
}
