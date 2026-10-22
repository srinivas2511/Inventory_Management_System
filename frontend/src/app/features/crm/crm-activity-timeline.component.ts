import { Component, Input, OnChanges, inject, signal } from '@angular/core';

import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ACTIVITY_TYPES, ActivityDto, ActivityFilter, ActivityType, CrmApi } from './crm.api';

const TYPE_ICONS: Record<ActivityType, string> = {
  CALL: 'call',
  EMAIL: 'email',
  MEETING: 'groups',
  DEMO: 'slideshow',
  FOLLOW_UP: 'event_repeat',
};

/** Activity log for one customer, lead, opportunity or contact, newest first, with an inline logging form. */
@Component({
  selector: 'app-crm-activity-timeline',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    HasPermissionDirective,
  ],
  template: `
    <form *appHasPermission="'CUSTOMER_ACTIVITY'" class="log-form" [formGroup]="form" (ngSubmit)="log()">
      <mat-form-field appearance="outline">
        <mat-label>Type</mat-label>
        <mat-select formControlName="activityType">
          @for (t of types; track t) {
            <mat-option [value]="t">{{ t }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Subject</mat-label>
        <input matInput maxlength="200" formControlName="subject" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Notes</mat-label>
        <input matInput formControlName="notes" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Outcome</mat-label>
        <input matInput maxlength="200" formControlName="outcome" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="narrow">
        <mat-label>Minutes</mat-label>
        <input matInput type="number" min="0" formControlName="durationMinutes" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> Log activity
      </button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="32" /></div>
    } @else {
      <ol class="timeline">
        @for (a of activities(); track a.id) {
          <li>
            <mat-icon class="type-icon">{{ icon(a.activityType) }}</mat-icon>
            <div class="body">
              <div class="title">
                <strong>{{ a.subject }}</strong>
                <span class="meta">
                  {{ a.activityType }} · {{ a.activityDate | istDate: 'dd MMM yyyy HH:mm' }}
                  @if (a.ownerName) {
                    · {{ a.ownerName }}
                  }
                  @if (a.durationMinutes) {
                    · {{ a.durationMinutes }} min
                  }
                </span>
              </div>
              @if (a.contactName || a.customerName) {
                <div class="meta">{{ a.contactName }} {{ a.customerName ? '(' + a.customerName + ')' : '' }}</div>
              }
              @if (a.notes) {
                <div>{{ a.notes }}</div>
              }
              @if (a.outcome) {
                <div class="outcome">Outcome: {{ a.outcome }}</div>
              }
            </div>
            <button
              *appHasPermission="'CUSTOMER_ACTIVITY'"
              mat-icon-button
              aria-label="Delete activity"
              (click)="remove(a)"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </li>
        } @empty {
          <li class="empty">No activity logged yet</li>
        }
      </ol>
    }
  `,
  styles: `
    .log-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 260px;
    }
    .narrow {
      width: 100px;
    }
    .timeline {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .timeline li {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      padding: 10px 0;
      border-bottom: 1px solid rgba(128, 128, 128, 0.25);
    }
    .type-icon {
      opacity: 0.7;
    }
    .body {
      flex: 1;
    }
    .meta {
      opacity: 0.7;
      font-size: 12px;
      margin-left: 6px;
    }
    .outcome {
      font-style: italic;
    }
    .empty {
      justify-content: center;
      opacity: 0.6;
    }
  `,
})
export class CrmActivityTimelineComponent implements OnChanges {
  private api = inject(CrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  @Input() filter: ActivityFilter = {};

  types = ACTIVITY_TYPES;
  activities = signal<ActivityDto[]>([]);
  loading = signal(false);

  form = this.fb.nonNullable.group({
    activityType: this.fb.nonNullable.control<ActivityType>('CALL'),
    subject: ['', [Validators.required, Validators.maxLength(200)]],
    notes: [''],
    outcome: [''],
    durationMinutes: this.fb.control<number | null>(null),
  });

  ngOnChanges(): void {
    this.load();
  }

  icon(type: ActivityType): string {
    return TYPE_ICONS[type] ?? 'event';
  }

  load(): void {
    const f = this.filter;
    if (!f.customerId && !f.contactId && !f.leadId && !f.opportunityId) {
      this.activities.set([]);
      return;
    }
    this.loading.set(true);
    this.api.getActivities(f).subscribe({
      next: (page) => {
        this.activities.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load activities', 'Dismiss', { duration: 3000 });
      },
    });
  }

  log(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createActivity({
        activityType: v.activityType,
        customerId: this.filter.customerId ?? null,
        contactId: this.filter.contactId ?? null,
        leadId: this.filter.leadId ?? null,
        opportunityId: this.filter.opportunityId ?? null,
        subject: v.subject,
        notes: v.notes || null,
        outcome: v.outcome || null,
        activityDate: null,
        durationMinutes: v.durationMinutes,
        ownerId: null,
        version: null,
      })
      .subscribe({
        next: () => {
          this.form.reset({ activityType: 'CALL', subject: '', notes: '', outcome: '', durationMinutes: null });
          this.load();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not log activity', 'Dismiss', { duration: 5000 }),
      });
  }

  remove(a: ActivityDto): void {
    this.api.deleteActivity(a.id).subscribe({
      next: () => this.load(),
      error: () => this.snack.open('Could not delete activity', 'Dismiss', { duration: 3000 }),
    });
  }
}
