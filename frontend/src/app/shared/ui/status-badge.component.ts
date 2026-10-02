import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

interface BadgeStyle {
  label: string;
  tone: 'ok' | 'neutral' | 'info' | 'warn' | 'bad';
  icon: string;
}

/** Status to colour, icon and label (DESIGN.md section 8.7). Unknown statuses fall back to a neutral badge. */
const STYLES: Record<string, BadgeStyle> = {
  ACTIVE: { label: 'Active', tone: 'ok', icon: 'check_circle' },
  INACTIVE: { label: 'Inactive', tone: 'neutral', icon: 'block' },
  DRAFT: { label: 'Draft', tone: 'info', icon: 'edit_note' },
  OBSOLETE: { label: 'Obsolete', tone: 'warn', icon: 'archive' },
  LOCKED: { label: 'Locked', tone: 'warn', icon: 'lock' },
};

/** A coloured status chip. Colour is never the only signal: each status also has an icon and a text label. */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [MatIconModule],
  template: `<span class="badge" [class]="'badge ' + style().tone" data-testid="status-badge">
    <mat-icon aria-hidden="true">{{ style().icon }}</mat-icon
    >{{ style().label }}
  </span>`,
  styles: `
    .badge {
      align-items: center;
      border-radius: 12px;
      display: inline-flex;
      font-size: 12px;
      gap: 4px;
      line-height: 1;
      padding: 3px 10px 3px 6px;
    }
    mat-icon {
      font-size: 14px;
      height: 14px;
      width: 14px;
    }
    .ok {
      background: #e8f5e9;
      color: #1b5e20;
    }
    .neutral {
      background: #eceff1;
      color: #455a64;
    }
    .info {
      background: #e3f2fd;
      color: #0d47a1;
    }
    .warn {
      background: #fff3e0;
      color: #e65100;
    }
    .bad {
      background: #ffebee;
      color: #b71c1c;
    }
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();

  protected readonly style = computed<BadgeStyle>(
    () => STYLES[this.status()] ?? { label: this.status(), tone: 'neutral', icon: 'info' },
  );
}
