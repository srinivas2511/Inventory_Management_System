import { Component, inject, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { AppConfigService } from '../../core/config/app-config.service';

/** Centred card used by the pages shown before sign-in (no shell, no menu). */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <main class="wrap">
      <mat-card appearance="outlined" class="card">
        <mat-card-header>
          <mat-card-title>{{ appName }}</mat-card-title>
          <mat-card-subtitle>{{ heading() }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <ng-content />
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: `
    .wrap {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
      box-sizing: border-box;
    }
    .card {
      width: 100%;
      max-width: 420px;
    }
    mat-card-content {
      padding-top: 16px;
    }
  `,
})
export class AuthLayoutComponent {
  readonly heading = input.required<string>();
  protected readonly appName = inject(AppConfigService).appName;
}
