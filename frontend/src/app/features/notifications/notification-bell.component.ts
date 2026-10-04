import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { interval } from 'rxjs';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { NotificationDto, NotificationsApi, notificationLink } from './notifications.api';

const POLL_MS = 60_000;

/** The toolbar bell: unread count (refreshed every minute) and the latest notifications in a menu. */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [DatePipe, RouterLink, MatBadgeModule, MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button
      mat-icon-button
      type="button"
      [matMenuTriggerFor]="menu"
      (menuOpened)="loadRecent()"
      aria-label="Notifications"
      data-testid="bell"
    >
      <mat-icon [matBadge]="unread()" [matBadgeHidden]="unread() === 0" matBadgeColor="warn" matBadgeSize="small">
        notifications
      </mat-icon>
    </button>
    <mat-menu #menu="matMenu" class="notification-menu">
      @if (recent().length === 0) {
        <div class="empty" data-testid="bell-empty">You have no notifications</div>
      }
      @for (n of recent(); track n.id) {
        <button
          mat-menu-item
          type="button"
          class="item"
          [class.unread]="!n.read"
          (click)="open(n)"
          data-testid="bell-item"
        >
          <span class="subject">{{ n.subject || n.templateCode }}</span>
          <span class="body">{{ n.body }}</span>
          <span class="when">{{ n.createdAt | date: 'dd MMM HH:mm' }}</span>
        </button>
      }
      <div class="footer">
        <a mat-button routerLink="/notifications">View all</a>
        <button
          mat-button
          type="button"
          [disabled]="unread() === 0"
          (click)="markAll(); $event.stopPropagation()"
          data-testid="mark-all"
        >
          Mark all read
        </button>
      </div>
    </mat-menu>
  `,
  styles: `
    .empty {
      padding: 16px;
      min-width: 260px;
    }
    .item {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      height: auto;
      min-width: 300px;
      max-width: 380px;
      padding-block: 8px;
      line-height: 1.3;
      white-space: normal;
    }
    .subject {
      font-weight: 400;
    }
    .unread .subject {
      font-weight: 700;
    }
    .body,
    .when {
      font-size: 0.8rem;
      opacity: 0.8;
    }
    .footer {
      display: flex;
      justify-content: space-between;
      padding: 4px 8px 0;
    }
  `,
})
export class NotificationBellComponent implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly unread = signal(0);
  readonly recent = signal<NotificationDto[]>([]);

  ngOnInit(): void {
    this.refreshCount();
    interval(POLL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshCount());
  }

  refreshCount(): void {
    this.api.unreadCount().subscribe({ next: (c) => this.unread.set(c.unread), error: () => undefined });
  }

  loadRecent(): void {
    this.api.inbox(false, 0, 8).subscribe({ next: (p) => this.recent.set(p.content), error: () => undefined });
  }

  open(n: NotificationDto): void {
    const go = (): void => {
      const link = notificationLink(n);
      if (link) void this.router.navigate(link);
    };
    if (n.read) {
      go();
      return;
    }
    this.api.markRead(n.id).subscribe({
      next: () => {
        this.refreshCount();
        go();
      },
      error: go,
    });
  }

  markAll(): void {
    this.api.markAllRead().subscribe({
      next: () => {
        this.unread.set(0);
        this.recent.update((list) => list.map((n) => ({ ...n, read: true })));
      },
      error: () => undefined,
    });
  }
}
