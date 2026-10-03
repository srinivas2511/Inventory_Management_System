import { Component, Input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';

export type StatusColor = 'green' | 'amber' | 'orange' | 'red' | 'blue' | 'teal' | 'grey';

const STATUS_MAP: Record<string, { color: StatusColor; icon: string; label?: string }> = {
  AVAILABLE: { color: 'green', icon: 'check_circle' },
  APPROVED: { color: 'green', icon: 'check_circle' },
  ACTIVE: { color: 'green', icon: 'check_circle' },
  PASS: { color: 'green', icon: 'check_circle' },
  RECEIVED: { color: 'green', icon: 'check_circle' },
  DISPATCHED: { color: 'green', icon: 'check_circle' },
  LOW_STOCK: { color: 'amber', icon: 'warning', label: 'Low Stock' },
  HOLD: { color: 'amber', icon: 'pause_circle' },
  QUARANTINE: { color: 'orange', icon: 'radio_button_half', label: 'Quarantine' },
  QUALITY_PENDING: { color: 'orange', icon: 'radio_button_half', label: 'Quality Pending' },
  REJECTED: { color: 'red', icon: 'cancel' },
  FAIL: { color: 'red', icon: 'cancel' },
  CANCELLED: { color: 'red', icon: 'cancel' },
  BREAKDOWN: { color: 'red', icon: 'cancel' },
  IN_PROGRESS: { color: 'blue', icon: 'play_circle', label: 'In Progress' },
  RELEASED: { color: 'blue', icon: 'play_circle' },
  COMPLETED: { color: 'teal', icon: 'task_alt' },
  CLOSED: { color: 'teal', icon: 'task_alt' },
  PENDING: { color: 'grey', icon: 'radio_button_unchecked' },
  DRAFT: { color: 'grey', icon: 'radio_button_unchecked' },
  SUBMITTED: { color: 'grey', icon: 'radio_button_unchecked' },
  OBSOLETE: { color: 'grey', icon: 'block' },
  INACTIVE: { color: 'grey', icon: 'block' },
  NEW: { color: 'blue', icon: 'fiber_new' },
  CONTACTED: { color: 'amber', icon: 'call' },
  QUALIFIED: { color: 'teal', icon: 'verified' },
  UNQUALIFIED: { color: 'red', icon: 'cancel' },
  CONVERTED: { color: 'green', icon: 'check_circle' },
  PROSPECT: { color: 'grey', icon: 'radio_button_unchecked' },
  PROPOSAL: { color: 'blue', icon: 'description' },
  NEGOTIATION: { color: 'amber', icon: 'handshake' },
  CLOSED_WON: { color: 'green', icon: 'emoji_events', label: 'Won' },
  CLOSED_LOST: { color: 'red', icon: 'cancel', label: 'Lost' },
  SENT: { color: 'blue', icon: 'send' },
  ACCEPTED: { color: 'green', icon: 'thumb_up' },
  EXPIRED: { color: 'orange', icon: 'schedule' },
  PRESENT: { color: 'green', icon: 'check_circle' },
  ABSENT: { color: 'red', icon: 'cancel' },
  HALF_DAY: { color: 'amber', icon: 'timelapse', label: 'Half day' },
  HOLIDAY: { color: 'blue', icon: 'beach_access' },
  LEAVE: { color: 'teal', icon: 'event_busy' },
  NOT_RECORDED: { color: 'grey', icon: 'radio_button_unchecked', label: 'Not recorded' },
  PAID: { color: 'green', icon: 'payments' },
  PLANNED: { color: 'grey', icon: 'event' },
  NO_SHOW: { color: 'red', icon: 'person_off', label: 'No show' },
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [MatChipsModule, MatIconModule],
  template: `
    <span class="status-badge status-{{ color }}">
      <mat-icon class="status-icon">{{ icon }}</mat-icon>
      {{ displayLabel }}
    </span>
  `,
  styles: [
    `
      .status-badge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 0.75rem;
        font-weight: 500;
        white-space: nowrap;
      }
      .status-icon {
        font-size: 14px;
        width: 14px;
        height: 14px;
      }
      .status-green {
        background: #e8f5e9;
        color: #2e7d32;
      }
      .status-amber {
        background: #fff8e1;
        color: #f57f17;
      }
      .status-orange {
        background: #fff3e0;
        color: #e65100;
      }
      .status-red {
        background: #ffebee;
        color: #c62828;
      }
      .status-blue {
        background: #e3f2fd;
        color: #1565c0;
      }
      .status-teal {
        background: #e0f2f1;
        color: #00695c;
      }
      .status-grey {
        background: #f5f5f5;
        color: #616161;
      }
    `,
  ],
})
export class StatusBadgeComponent {
  @Input() status = '';
  @Input() label?: string;

  get color(): StatusColor {
    return STATUS_MAP[this.status]?.color ?? 'grey';
  }
  get icon(): string {
    return STATUS_MAP[this.status]?.icon ?? 'radio_button_unchecked';
  }
  get displayLabel(): string {
    return this.label ?? STATUS_MAP[this.status]?.label ?? this.status.replace(/_/g, ' ');
  }
}
