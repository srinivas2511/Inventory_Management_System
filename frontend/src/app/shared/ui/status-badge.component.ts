import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

type Tone = 'green' | 'amber' | 'orange' | 'red' | 'blue' | 'teal' | 'grey';

interface BadgeStyle {
  label: string;
  tone: Tone;
  icon: string;
}

const TONE_ICONS: Record<Tone, string> = {
  green: 'check_circle',
  amber: 'warning',
  orange: 'hourglass_top',
  red: 'cancel',
  blue: 'play_circle',
  teal: 'circle',
  grey: 'radio_button_unchecked',
};

/** Status to colour tone (DESIGN.md section 8.7). Each tone has its own icon; the label is always shown too. */
const TONES: Record<Tone, string[]> = {
  green: ['AVAILABLE', 'APPROVED', 'ACTIVE', 'PASS', 'RECEIVED', 'DISPATCHED'],
  amber: ['LOW_STOCK', 'HOLD'],
  orange: ['QUARANTINE', 'QUALITY_PENDING'],
  red: ['REJECTED', 'FAIL', 'CANCELLED', 'BREAKDOWN'],
  blue: ['IN_PROGRESS', 'IN_PRODUCTION', 'RELEASED'],
  teal: ['COMPLETED', 'CLOSED'],
  grey: ['PENDING', 'DRAFT', 'SUBMITTED'],
};

/**
 * Statuses the design table does not list: a deactivated record is grey, and states that need attention without being
 * a failure (obsolete, locked) are amber. Anything else, and every PARTIALLY_* state, follows the table's rules.
 */
const EXTRA: Record<string, Tone> = { INACTIVE: 'grey', OBSOLETE: 'amber', LOCKED: 'amber' };

export function statusStyle(status: string): BadgeStyle {
  const key = status.toUpperCase().replace(/\s+/g, '_');
  const tone: Tone =
    EXTRA[key] ??
    (key.startsWith('PARTIALLY_')
      ? 'amber'
      : ((Object.keys(TONES) as Tone[]).find((t) => TONES[t].includes(key)) ?? 'grey'));
  const label = key.charAt(0) + key.slice(1).toLowerCase().replace(/_/g, ' ');
  return { label, tone, icon: TONE_ICONS[tone] };
}

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
    .green {
      background: #e8f5e9;
      color: #1b5e20;
    }
    .amber {
      background: #fff8e1;
      color: #8d6e00;
    }
    .orange {
      background: #fff3e0;
      color: #bf360c;
    }
    .red {
      background: #ffebee;
      color: #b71c1c;
    }
    .blue {
      background: #e3f2fd;
      color: #0d47a1;
    }
    .teal {
      background: #e0f2f1;
      color: #00695c;
    }
    .grey {
      background: #eceff1;
      color: #455a64;
    }
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();

  protected readonly style = computed<BadgeStyle>(() => statusStyle(this.status()));
}
