import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';

interface OnboardingStep {
  id: number;
  stepKey: string;
  stepLabel: string;
  completed: boolean;
  completedAt: string | null;
  sortOrder: number;
}

interface OnboardingStatus {
  steps: OnboardingStep[];
  totalSteps: number;
  completedSteps: number;
  percentComplete: number;
}

@Component({
  selector: 'app-onboarding-wizard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressBarModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page-container">
      <h2 i18n="@@nav.item-onboarding">Setup Wizard</h2>

      @if (status()) {
        <mat-card class="progress-card">
          <mat-card-content>
            <div class="progress-header">
              <span class="progress-text"
                >{{ status()!.completedSteps }} / {{ status()!.totalSteps }} steps completed</span
              >
              <span class="pct">{{ status()!.percentComplete }}%</span>
            </div>
            <mat-progress-bar mode="determinate" [value]="status()!.percentComplete"></mat-progress-bar>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-list>
            @for (step of status()!.steps; track step.stepKey) {
              <mat-list-item [class.completed]="step.completed">
                <mat-icon matListItemIcon [class.done]="step.completed" [class.pending]="!step.completed">
                  {{ step.completed ? 'check_circle' : 'radio_button_unchecked' }}
                </mat-icon>
                <div matListItemTitle>{{ step.stepLabel }}</div>
                @if (step.completed && step.completedAt) {
                  <div matListItemLine class="completed-at">Completed {{ step.completedAt | date: 'mediumDate' }}</div>
                }
                <div matListItemMeta>
                  @if (!step.completed) {
                    <button
                      mat-stroked-button
                      color="primary"
                      (click)="markComplete(step.stepKey)"
                      [disabled]="loading()"
                    >
                      Mark complete
                    </button>
                  } @else {
                    <button
                      mat-icon-button
                      matTooltip="Reset step"
                      (click)="resetStep(step.stepKey)"
                      [disabled]="loading()"
                    >
                      <mat-icon>undo</mat-icon>
                    </button>
                  }
                </div>
              </mat-list-item>
              <mat-divider></mat-divider>
            }
          </mat-list>
        </mat-card>
      }

      @if (loading()) {
        <p>Loading…</p>
      }
    </div>
  `,
  styles: [
    `
      .page-container {
        padding: 24px;
        max-width: 800px;
      }
      .progress-card {
        margin-bottom: 16px;
      }
      .progress-header {
        display: flex;
        justify-content: space-between;
        margin-bottom: 8px;
      }
      .pct {
        font-weight: bold;
      }
      .done {
        color: #4caf50;
      }
      .pending {
        color: #9e9e9e;
      }
      .completed-at {
        font-size: 12px;
        color: #757575;
      }
    `,
  ],
})
export class OnboardingWizardComponent implements OnInit {
  status = signal<OnboardingStatus | null>(null);
  loading = signal(false);

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<OnboardingStatus>('/api/settings/onboarding').subscribe({
      next: (s) => {
        this.status.set(s);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  markComplete(stepKey: string): void {
    this.loading.set(true);
    this.http.post<OnboardingStatus>('/api/settings/onboarding/complete', { stepKey }).subscribe({
      next: (s) => {
        this.status.set(s);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  resetStep(stepKey: string): void {
    this.loading.set(true);
    this.http.delete<OnboardingStatus>(`/api/settings/onboarding/steps/${stepKey}`).subscribe({
      next: (s) => {
        this.status.set(s);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
