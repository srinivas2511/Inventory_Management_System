import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { NotificationChannel, NotificationsApi, PreferenceDto } from './notifications.api';

@Component({
  selector: 'app-notification-preferences',
  standalone: true,
  imports: [RouterLink, MatCheckboxModule, MatIconModule, MatProgressSpinnerModule, MatSnackBarModule, MatTableModule],
  template: `
    <a routerLink="/notifications" class="back"><mat-icon>arrow_back</mat-icon> Notifications</a>
    <h2 class="mat-h2">Notification preferences</h2>
    <p class="hint">
      Choose how you are told about each kind of event. A channel a message is not sent through is shown as a dash.
    </p>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="event">
          <th mat-header-cell *matHeaderCellDef>Event</th>
          <td mat-cell *matCellDef="let r">
            <div>{{ r.description || r.templateCode }}</div>
            <div class="code">{{ r.templateCode }}</div>
          </td>
        </ng-container>
        <ng-container matColumnDef="IN_APP">
          <th mat-header-cell *matHeaderCellDef>In the app</th>
          <td mat-cell *matCellDef="let r">
            @if (has(r, 'IN_APP')) {
              <mat-checkbox
                [checked]="enabled(r, 'IN_APP')"
                (change)="set(r, 'IN_APP', $event.checked)"
                [attr.data-testid]="'pref-' + r.templateCode + '-IN_APP'"
              />
            } @else {
              —
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="EMAIL">
          <th mat-header-cell *matHeaderCellDef>E-mail</th>
          <td mat-cell *matCellDef="let r">
            @if (has(r, 'EMAIL')) {
              <mat-checkbox
                [checked]="enabled(r, 'EMAIL')"
                (change)="set(r, 'EMAIL', $event.checked)"
                [attr.data-testid]="'pref-' + r.templateCode + '-EMAIL'"
              />
            } @else {
              —
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
  styles: `
    .back {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      text-decoration: none;
    }
    .hint,
    .code {
      color: var(--mat-sys-on-surface-variant, #666);
    }
    .code {
      font-size: 0.75rem;
    }
  `,
})
export class NotificationPreferencesComponent implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly snack = inject(MatSnackBar);

  readonly columns = ['event', 'IN_APP', 'EMAIL'];
  readonly rows = signal<PreferenceDto[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.api.preferences().subscribe({
      next: (r) => {
        this.rows.set(r);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Could not load your preferences', 'Close', { duration: 4000 });
      },
    });
  }

  has(row: PreferenceDto, channel: NotificationChannel): boolean {
    return row.channels.some((c) => c.channel === channel);
  }

  enabled(row: PreferenceDto, channel: NotificationChannel): boolean {
    return row.channels.find((c) => c.channel === channel)?.enabled ?? false;
  }

  set(row: PreferenceDto, channel: NotificationChannel, enabled: boolean): void {
    this.api.setPreference(row.templateCode, channel, enabled).subscribe({
      next: (r) => this.rows.set(r),
      error: () => {
        this.snack.open('Could not save the preference', 'Close', { duration: 4000 });
        this.rows.update((list) => [...list]);
      },
    });
  }
}
