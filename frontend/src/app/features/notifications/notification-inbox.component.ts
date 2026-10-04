import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { NotificationDto, NotificationsApi, notificationLink } from './notifications.api';

@Component({
  selector: 'app-notification-inbox',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Notifications</h2>
      <div class="tools">
        <mat-slide-toggle [checked]="unreadOnly()" (change)="setUnreadOnly($event.checked)" data-testid="unread-only">
          Unread only
        </mat-slide-toggle>
        <button mat-stroked-button type="button" (click)="markAll()" data-testid="mark-all">
          <mat-icon>done_all</mat-icon> Mark all read
        </button>
        <a mat-stroked-button routerLink="/notifications/preferences"><mat-icon>tune</mat-icon> Preferences</a>
      </div>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else if (items().length === 0) {
      <p class="empty" data-testid="empty">No notifications.</p>
    } @else {
      <ul class="inbox">
        @for (n of items(); track n.id) {
          <li [class.unread]="!n.read" data-testid="row">
            <mat-icon class="dot">{{ n.read ? 'drafts' : 'mark_email_unread' }}</mat-icon>
            <div class="text">
              <div class="subject">{{ n.subject || n.templateCode }}</div>
              <div class="body">{{ n.body }}</div>
              <div class="when">{{ n.createdAt | date: 'dd MMM yyyy HH:mm' }}</div>
            </div>
            @if (hasLink(n)) {
              <button mat-button type="button" (click)="open(n)">Open</button>
            }
            @if (!n.read) {
              <button mat-button type="button" (click)="read(n)" data-testid="read">Mark read</button>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .tools {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
    }
    .inbox {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .inbox li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 4px;
      border-bottom: 1px solid var(--mat-sys-outline-variant, #ddd);
    }
    .text {
      flex: 1 1 auto;
      min-width: 0;
    }
    .subject {
      font-weight: 400;
    }
    .unread .subject {
      font-weight: 700;
    }
    .body,
    .when,
    .empty {
      color: var(--mat-sys-on-surface-variant, #666);
    }
    .when {
      font-size: 0.8rem;
    }
  `,
})
export class NotificationInboxComponent implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  readonly items = signal<NotificationDto[]>([]);
  readonly unreadOnly = signal(false);
  readonly loading = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.inbox(this.unreadOnly(), 0, 50).subscribe({
      next: (p) => {
        this.items.set(p.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Could not load notifications', 'Close', { duration: 4000 });
      },
    });
  }

  setUnreadOnly(value: boolean): void {
    this.unreadOnly.set(value);
    this.load();
  }

  hasLink(n: NotificationDto): boolean {
    return notificationLink(n) !== null;
  }

  read(n: NotificationDto): void {
    this.api.markRead(n.id).subscribe({
      next: (updated) =>
        this.items.update((list) =>
          this.unreadOnly() ? list.filter((x) => x.id !== n.id) : list.map((x) => (x.id === n.id ? updated : x)),
        ),
      error: () => this.snack.open('Could not mark it as read', 'Close', { duration: 4000 }),
    });
  }

  open(n: NotificationDto): void {
    const link = notificationLink(n);
    if (!n.read) this.read(n);
    if (link) void this.router.navigate(link);
  }

  markAll(): void {
    this.api.markAllRead().subscribe({
      next: () => this.load(),
      error: () => this.snack.open('Could not mark them as read', 'Close', { duration: 4000 }),
    });
  }
}
