import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import {
  DeliveryLogDto,
  NOTIFICATION_STATUSES,
  NotificationChannel,
  NotificationStatus,
  NotificationsApi,
  TemplateDto,
} from './notifications.api';

/** Administrators: edit message templates and watch the e-mail outbox (retries and failures). */
@Component({
  selector: 'app-notification-admin',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatTableModule,
    MatTabsModule,
    StatusBadgeComponent,
  ],
  template: `
    <h2 class="mat-h2">Notification administration</h2>
    <mat-tab-group (selectedTabChange)="onTab($event.index)">
      <mat-tab label="Templates">
        <div class="pane">
          <table mat-table [dataSource]="templates()" class="mat-elevation-z1">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Template</th>
              <td mat-cell *matCellDef="let t">{{ t.templateCode }}</td>
            </ng-container>
            <ng-container matColumnDef="channel">
              <th mat-header-cell *matHeaderCellDef>Channel</th>
              <td mat-cell *matCellDef="let t">{{ t.channel }}</td>
            </ng-container>
            <ng-container matColumnDef="description">
              <th mat-header-cell *matHeaderCellDef>Description</th>
              <td mat-cell *matCellDef="let t">{{ t.description }}</td>
            </ng-container>
            <ng-container matColumnDef="active">
              <th mat-header-cell *matHeaderCellDef>Active</th>
              <td mat-cell *matCellDef="let t">
                <app-status-badge [status]="t.active ? 'ACTIVE' : 'INACTIVE'" />
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="templateColumns"></tr>
            <tr
              mat-row
              *matRowDef="let row; columns: templateColumns"
              class="clickable"
              (click)="edit(row)"
              data-testid="template-row"
            ></tr>
          </table>

          @if (editing(); as t) {
            <form [formGroup]="form" (ngSubmit)="save()" class="edit" data-testid="edit-form">
              <h3 class="mat-h3">{{ t.templateCode }}</h3>
              <p class="hint">
                Use <code ngNonBindable>{{ name }}</code> placeholders; only the values the event supplies are
                available.
              </p>
              <mat-form-field appearance="outline">
                <mat-label>Channel</mat-label>
                <mat-select formControlName="channel">
                  <mat-option value="IN_APP">In the app</mat-option>
                  <mat-option value="EMAIL">E-mail</mat-option>
                  <mat-option value="BOTH">Both</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Description</mat-label>
                <input matInput formControlName="description" maxlength="200" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="wide">
                <mat-label>Subject</mat-label>
                <input matInput formControlName="subjectTemplate" maxlength="200" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="wide">
                <mat-label>Body</mat-label>
                <textarea matInput formControlName="bodyTemplate" rows="6" data-testid="body"></textarea>
              </mat-form-field>
              <mat-slide-toggle formControlName="active">Active</mat-slide-toggle>
              <div class="actions">
                <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid" data-testid="save">
                  Save
                </button>
                <button mat-button type="button" (click)="editing.set(null)">Cancel</button>
              </div>
            </form>
          }
        </div>
      </mat-tab>

      <mat-tab label="Delivery log">
        <div class="pane">
          <div class="filter-bar">
            <mat-form-field appearance="outline">
              <mat-label>Status</mat-label>
              <mat-select [formControl]="statusCtrl" (selectionChange)="loadLog()">
                <mat-option [value]="null">All</mat-option>
                @for (s of statuses; track s) {
                  <mat-option [value]="s">{{ s }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Channel</mat-label>
              <mat-select [formControl]="channelCtrl" (selectionChange)="loadLog()">
                <mat-option [value]="null">All</mat-option>
                <mat-option value="EMAIL">E-mail</mat-option>
                <mat-option value="IN_APP">In the app</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          <table mat-table [dataSource]="log()" class="mat-elevation-z1">
            <ng-container matColumnDef="created">
              <th mat-header-cell *matHeaderCellDef>Created</th>
              <td mat-cell *matCellDef="let r">{{ r.createdAt | istDate: 'dd MMM HH:mm' }}</td>
            </ng-container>
            <ng-container matColumnDef="template">
              <th mat-header-cell *matHeaderCellDef>Template</th>
              <td mat-cell *matCellDef="let r">{{ r.templateCode }}</td>
            </ng-container>
            <ng-container matColumnDef="recipient">
              <th mat-header-cell *matHeaderCellDef>Recipient</th>
              <td mat-cell *matCellDef="let r">{{ r.recipientEmail || 'user #' + r.userId }}</td>
            </ng-container>
            <ng-container matColumnDef="channel">
              <th mat-header-cell *matHeaderCellDef>Channel</th>
              <td mat-cell *matCellDef="let r">{{ r.channel }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
            </ng-container>
            <ng-container matColumnDef="attempts">
              <th mat-header-cell *matHeaderCellDef>Attempts</th>
              <td mat-cell *matCellDef="let r">{{ r.attempts }}</td>
            </ng-container>
            <ng-container matColumnDef="error">
              <th mat-header-cell *matHeaderCellDef>Last error</th>
              <td mat-cell *matCellDef="let r">{{ r.lastError }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="logColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: logColumns"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell" [attr.colspan]="logColumns.length" style="text-align:center;padding:2rem">
                Nothing to show
              </td>
            </tr>
          </table>
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: `
    .pane {
      padding-top: 16px;
    }
    .clickable {
      cursor: pointer;
    }
    .edit {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 8px 16px;
      max-width: 900px;
      padding-top: 16px;
    }
    .wide,
    .hint,
    .actions,
    h3 {
      grid-column: 1 / -1;
    }
    .actions {
      display: flex;
      gap: 12px;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant, #666);
    }
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
  `,
})
export class NotificationAdminComponent implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly snack = inject(MatSnackBar);

  readonly statuses = NOTIFICATION_STATUSES;
  readonly templateColumns = ['code', 'channel', 'description', 'active'];
  readonly logColumns = ['created', 'template', 'recipient', 'channel', 'status', 'attempts', 'error'];

  readonly templates = signal<TemplateDto[]>([]);
  readonly editing = signal<TemplateDto | null>(null);
  readonly log = signal<DeliveryLogDto[]>([]);
  readonly statusCtrl = new FormControl<NotificationStatus | null>(null);
  readonly channelCtrl = new FormControl<NotificationChannel | null>(null);

  readonly form = new FormGroup({
    channel: new FormControl<'EMAIL' | 'IN_APP' | 'BOTH'>('BOTH', { nonNullable: true }),
    description: new FormControl<string>('', { nonNullable: true }),
    subjectTemplate: new FormControl<string>('', { nonNullable: true }),
    bodyTemplate: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    active: new FormControl<boolean>(true, { nonNullable: true }),
  });

  ngOnInit(): void {
    this.loadTemplates();
  }

  onTab(index: number): void {
    if (index === 1) this.loadLog();
  }

  loadTemplates(): void {
    this.api.templates().subscribe({
      next: (t) => this.templates.set(t),
      error: () => this.snack.open('Could not load templates', 'Close', { duration: 4000 }),
    });
  }

  loadLog(): void {
    this.api.log(this.statusCtrl.value, this.channelCtrl.value).subscribe({
      next: (p) => this.log.set(p.content),
      error: () => this.snack.open('Could not load the delivery log', 'Close', { duration: 4000 }),
    });
  }

  edit(t: TemplateDto): void {
    this.editing.set(t);
    this.form.reset({
      channel: t.channel,
      description: t.description ?? '',
      subjectTemplate: t.subjectTemplate ?? '',
      bodyTemplate: t.bodyTemplate,
      active: t.active,
    });
  }

  save(): void {
    const t = this.editing();
    if (!t || this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .updateTemplate(t.id, {
        channel: v.channel,
        description: v.description.trim() || null,
        subjectTemplate: v.subjectTemplate.trim() || null,
        bodyTemplate: v.bodyTemplate,
        active: v.active,
      })
      .subscribe({
        next: (updated) => {
          this.templates.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
          this.editing.set(null);
          this.snack.open('Template saved', 'Close', { duration: 3000 });
        },
        error: (e) => this.snack.open(e?.error?.detail ?? 'Could not save the template', 'Close', { duration: 6000 }),
      });
  }
}
