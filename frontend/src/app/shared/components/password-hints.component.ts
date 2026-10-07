import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { passwordChecks } from '../../core/auth/password-policy';

/** The password rules as a checklist that ticks off while the user types. */
@Component({
  selector: 'app-password-hints',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <ul class="hints" aria-label="Password requirements">
      @for (check of checks(); track check.label) {
        <li [class.ok]="check.ok" data-testid="password-hint">
          <mat-icon class="hint-icon" aria-hidden="true">{{
            check.ok ? 'check_circle' : 'radio_button_unchecked'
          }}</mat-icon>
          <span>{{ check.label }}</span>
          <span class="cdk-visually-hidden">{{ check.ok ? 'met' : 'not met yet' }}</span>
        </li>
      }
    </ul>
  `,
  styles: [
    `
      .hints {
        list-style: none;
        margin: 0 0 8px;
        padding: 0;
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 2px 12px;
        font-size: 13px;
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      li {
        display: flex;
        align-items: center;
        gap: 6px;
      }
      li.ok {
        color: var(--mat-sys-primary, #1b5e20);
      }
      .hint-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }
    `,
  ],
})
export class PasswordHintsComponent {
  readonly password = input.required<string>();
  readonly username = input<string | null>(null);
  protected readonly checks = computed(() => passwordChecks(this.password(), this.username()));
}
