import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface PaymentReminderDto {
  id?: number;
  openItemId?: number;
  partyId?: number;
  level?: number;
  mode: string;
  sentAt?: string;
  channel?: string;
  sentBy?: number;
  createdAt?: string;
}

@Component({
  selector: 'app-payment-reminders',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-payment-reminders">Payment Reminders</h2>
      <button mat-fab extended (click)="openScheduleDialog()" aria-label="Schedule payment reminder">
        <mat-icon aria-hidden="true">add</mat-icon>
        Schedule
      </button>
    </div>

    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="reminders()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="partyId">
          <th mat-header-cell *matHeaderCellDef>Party ID</th>
          <td mat-cell *matCellDef="let row">{{ row.partyId ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="openItemId">
          <th mat-header-cell *matHeaderCellDef>Open Item</th>
          <td mat-cell *matCellDef="let row">{{ row.openItemId ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="level">
          <th mat-header-cell *matHeaderCellDef>Level</th>
          <td mat-cell *matCellDef="let row">
            @if (row.level === 1) {
              1st
            } @else if (row.level === 2) {
              2nd
            } @else if (row.level === 3) {
              Final
            } @else {
              —
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="mode">
          <th mat-header-cell *matHeaderCellDef>Mode</th>
          <td mat-cell *matCellDef="let row">
            <app-status-badge [status]="row.mode" />
          </td>
        </ng-container>
        <ng-container matColumnDef="channel">
          <th mat-header-cell *matHeaderCellDef>Channel</th>
          <td mat-cell *matCellDef="let row">{{ row.channel ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="sentAt">
          <th mat-header-cell *matHeaderCellDef>Sent At</th>
          <td mat-cell *matCellDef="let row">{{ row.sentAt | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="createdAt">
          <th mat-header-cell *matHeaderCellDef>Created</th>
          <td mat-cell *matCellDef="let row">{{ row.createdAt | istDate }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (reminders().length === 0) {
        <p class="empty-state">No payment reminders scheduled.</p>
      }
    }

    <!-- Schedule dialog form (rendered inline for simplicity) -->
    @if (showForm()) {
      <div class="inline-form mat-elevation-z2">
        <h3>Schedule Payment Reminder</h3>
        <form [formGroup]="form" class="dialog-form">
          <mat-form-field>
            <mat-label>Open Item ID</mat-label>
            <input matInput type="number" formControlName="openItemId" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Party ID</mat-label>
            <input matInput type="number" formControlName="partyId" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Level</mat-label>
            <mat-select formControlName="level">
              <mat-option [value]="1">1st Reminder</mat-option>
              <mat-option [value]="2">2nd Reminder</mat-option>
              <mat-option [value]="3">Final Notice</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Channel</mat-label>
            <mat-select formControlName="channel">
              <mat-option value="EMAIL">Email</mat-option>
              <mat-option value="SMS">SMS</mat-option>
            </mat-select>
          </mat-form-field>
          <div class="form-actions">
            <button mat-button type="button" (click)="showForm.set(false)">Cancel</button>
            <button mat-flat-button color="primary" type="button" (click)="saveReminder()" [disabled]="saving()">
              Save
            </button>
          </div>
        </form>
      </div>
    }
  `,
})
export class PaymentRemindersComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  columns = ['partyId', 'openItemId', 'level', 'mode', 'channel', 'sentAt', 'createdAt'];
  reminders = signal<PaymentReminderDto[]>([]);
  loading = signal(true);
  saving = signal(false);
  showForm = signal(false);
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      openItemId: [null],
      partyId: [null],
      level: [1],
      channel: ['EMAIL'],
    });
    this.loadReminders();
  }

  private loadReminders(): void {
    this.http.get<PaymentReminderDto[]>('/api/finance/payment-reminders').subscribe({
      next: (data) => {
        this.reminders.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load payment reminders', 'Close', { duration: 3000 });
      },
    });
  }

  openScheduleDialog(): void {
    this.form.reset({ level: 1, channel: 'EMAIL' });
    this.showForm.set(true);
  }

  saveReminder(): void {
    this.saving.set(true);
    const val = this.form.value;
    const payload: PaymentReminderDto = {
      openItemId: val.openItemId,
      partyId: val.partyId,
      level: val.level,
      mode: 'MANUAL',
      channel: val.channel,
    };
    this.http.post<PaymentReminderDto>('/api/finance/payment-reminders', payload).subscribe({
      next: (created) => {
        this.reminders.update((list) => [created, ...list]);
        this.saving.set(false);
        this.showForm.set(false);
        this.snackBar.open('Reminder scheduled', 'OK', { duration: 2500 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Failed to schedule reminder', 'Close', { duration: 3000 });
      },
    });
  }
}
