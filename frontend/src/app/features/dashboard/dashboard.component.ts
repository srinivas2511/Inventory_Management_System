import { Component, OnInit, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../core/api/api.service';

interface PingResponse {
  status: string;
  application: string;
  version: string;
  time: string;
}

type ApiState = 'checking' | 'up' | 'down';

/**
 * Phase 0 landing page. Shows whether the API is reachable; replaced by role-based dashboards in Phase 7.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  template: `
    <h1>Dashboard</h1>
    <mat-card class="status-card" appearance="outlined">
      <mat-card-header>
        <mat-card-title>System status</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @switch (state()) {
          @case ('checking') {
            <p data-testid="api-status">Checking the server…</p>
          }
          @case ('up') {
            <p data-testid="api-status"><mat-icon class="ok">check_circle</mat-icon> Server is running</p>
            <p class="meta">Version {{ ping()?.version }}</p>
          }
          @case ('down') {
            <p data-testid="api-status"><mat-icon class="bad">error</mat-icon> Server is not reachable</p>
          }
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .status-card {
      max-width: 420px;
    }
    .ok {
      color: #2e7d32;
      vertical-align: middle;
    }
    .bad {
      color: #c62828;
      vertical-align: middle;
    }
    .meta {
      opacity: 0.7;
      margin: 0;
    }
  `,
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly state = signal<ApiState>('checking');
  protected readonly ping = signal<PingResponse | null>(null);

  ngOnInit(): void {
    this.api.get<PingResponse>('/system/ping').subscribe({
      next: (res) => {
        this.ping.set(res);
        this.state.set(res.status === 'UP' ? 'up' : 'down');
      },
      error: () => this.state.set('down'),
    });
  }
}
