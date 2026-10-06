import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import {
  DELIVERY_STATUSES,
  DeliveryStatus,
  IntegrationApi,
  WebhookDeliveryDto,
  WebhookDto,
  WebhookSecretDto,
} from './integration.api';

/** Webhook subscriptions, the one-time signing secret, and the delivery history with manual retry (INT-002). */
@Component({
  selector: 'app-webhook-admin',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    MatTabsModule,
    StatusBadgeComponent,
  ],
  template: `
    <h2 class="mat-h2">Webhooks</h2>
    <mat-tab-group (selectedTabChange)="onTab($event.index)">
      <mat-tab label="Subscriptions">
        <div class="pane">
          @if (secret(); as s) {
            <div class="secret" role="alert" data-testid="secret">
              <strong>Signing secret for "{{ s.webhook.name }}"</strong>
              <p>Copy it now. It is shown once and cannot be read back; rotate the secret if it is lost.</p>
              <code data-testid="secret-value">{{ s.secret }}</code>
              <button mat-button type="button" (click)="copySecret(s.secret)">Copy</button>
              <button mat-button type="button" (click)="secret.set(null)" data-testid="secret-dismiss">Done</button>
            </div>
          }
          @if (canManage) {
            <button mat-flat-button color="primary" type="button" (click)="showForm.set(!showForm())" data-testid="new">
              <mat-icon>add</mat-icon> New webhook
            </button>
          }
          @if (showForm()) {
            <form [formGroup]="form" (ngSubmit)="create()" class="edit" data-testid="form">
              <mat-form-field appearance="outline">
                <mat-label>Name</mat-label>
                <input matInput formControlName="name" maxlength="100" data-testid="name" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="wide">
                <mat-label>Target URL (https)</mat-label>
                <input matInput formControlName="targetUrl" maxlength="500" data-testid="url" />
                @if (form.controls.targetUrl.hasError('pattern')) {
                  <mat-error>Must start with https://</mat-error>
                }
              </mat-form-field>
              <div class="events wide" role="group" aria-label="Events">
                @for (e of eventTypes(); track e) {
                  <mat-checkbox [checked]="selected().has(e)" (change)="toggle(e, $event.checked)">{{
                    e
                  }}</mat-checkbox>
                }
              </div>
              <div class="actions">
                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  [disabled]="form.invalid || selected().size === 0"
                  data-testid="create"
                >
                  Create
                </button>
                <button mat-button type="button" (click)="showForm.set(false)">Cancel</button>
              </div>
            </form>
          }
          <table mat-table [dataSource]="webhooks()" class="mat-elevation-z1">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let w">
                {{ w.name }}<br /><small>{{ w.webhookCode }}</small>
              </td>
            </ng-container>
            <ng-container matColumnDef="url">
              <th mat-header-cell *matHeaderCellDef>Target</th>
              <td mat-cell *matCellDef="let w">{{ w.targetUrl }}</td>
            </ng-container>
            <ng-container matColumnDef="events">
              <th mat-header-cell *matHeaderCellDef>Events</th>
              <td mat-cell *matCellDef="let w">{{ w.events.join(', ') }}</td>
            </ng-container>
            <ng-container matColumnDef="hint">
              <th mat-header-cell *matHeaderCellDef>Secret</th>
              <td mat-cell *matCellDef="let w">••••{{ w.secretHint }}</td>
            </ng-container>
            <ng-container matColumnDef="active">
              <th mat-header-cell *matHeaderCellDef>Active</th>
              <td mat-cell *matCellDef="let w"><app-status-badge [status]="w.active ? 'ACTIVE' : 'INACTIVE'" /></td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let w">
                @if (canManage) {
                  <button mat-button type="button" (click)="test(w)" data-testid="test">Send test</button>
                  <button mat-button type="button" (click)="rotate(w)" data-testid="rotate">Rotate secret</button>
                  <button mat-button type="button" (click)="toggleActive(w)" data-testid="toggle">
                    {{ w.active ? 'Deactivate' : 'Activate' }}
                  </button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns" data-testid="webhook-row"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
                No webhooks yet
              </td>
            </tr>
          </table>
        </div>
      </mat-tab>

      <mat-tab label="Deliveries">
        <div class="pane">
          <div class="filter-bar">
            <mat-form-field appearance="outline">
              <mat-label>Status</mat-label>
              <mat-select [formControl]="statusCtrl" (selectionChange)="loadDeliveries()" data-testid="status-filter">
                <mat-option [value]="null">All</mat-option>
                @for (s of statuses; track s) {
                  <mat-option [value]="s">{{ s }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Webhook</mat-label>
              <mat-select [formControl]="subscriptionCtrl" (selectionChange)="loadDeliveries()">
                <mat-option [value]="null">All</mat-option>
                @for (w of webhooks(); track w.id) {
                  <mat-option [value]="w.id">{{ w.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
          <table mat-table [dataSource]="deliveries()" class="mat-elevation-z1">
            <ng-container matColumnDef="created">
              <th mat-header-cell *matHeaderCellDef>Created</th>
              <td mat-cell *matCellDef="let d">{{ d.createdAt | date: 'dd MMM HH:mm:ss' }}</td>
            </ng-container>
            <ng-container matColumnDef="event">
              <th mat-header-cell *matHeaderCellDef>Event</th>
              <td mat-cell *matCellDef="let d">
                {{ d.eventType }}<br /><small>{{ d.eventKey }}</small>
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let d"><app-status-badge [status]="d.status" /></td>
            </ng-container>
            <ng-container matColumnDef="retries">
              <th mat-header-cell *matHeaderCellDef>Retries</th>
              <td mat-cell *matCellDef="let d">{{ d.retryCount }}</td>
            </ng-container>
            <ng-container matColumnDef="next">
              <th mat-header-cell *matHeaderCellDef>Next attempt</th>
              <td mat-cell *matCellDef="let d">{{ d.nextRetryAt ? (d.nextRetryAt | date: 'dd MMM HH:mm') : '' }}</td>
            </ng-container>
            <ng-container matColumnDef="response">
              <th mat-header-cell *matHeaderCellDef>Response</th>
              <td mat-cell *matCellDef="let d">{{ d.responseStatus ?? '' }} {{ d.lastError ?? '' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let d">
                @if (canManage && d.status !== 'DELIVERED') {
                  <button mat-button type="button" (click)="retry(d)" data-testid="retry">Retry now</button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="deliveryColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: deliveryColumns" data-testid="delivery-row"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell" [attr.colspan]="deliveryColumns.length" style="text-align:center;padding:2rem">
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
    .edit {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 8px 16px;
      max-width: 900px;
      padding: 16px 0;
    }
    .wide,
    .actions {
      grid-column: 1 / -1;
    }
    .events {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
    .actions,
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
    .secret {
      border: 1px solid #f57f17;
      background: #fff8e1;
      color: #4e342e;
      padding: 12px 16px;
      margin-bottom: 16px;
      border-radius: 4px;
    }
    .secret code {
      display: block;
      margin: 8px 0;
      word-break: break-all;
    }
  `,
})
export class WebhookAdminComponent implements OnInit {
  private readonly api = inject(IntegrationApi);
  private readonly snack = inject(MatSnackBar);
  private readonly session = inject(SessionStore);

  readonly statuses = DELIVERY_STATUSES;
  readonly columns = ['name', 'url', 'events', 'hint', 'active', 'actions'];
  readonly deliveryColumns = ['created', 'event', 'status', 'retries', 'next', 'response', 'actions'];
  readonly canManage = this.session.hasPermission('WEBHOOK_MANAGE');

  readonly webhooks = signal<WebhookDto[]>([]);
  readonly deliveries = signal<WebhookDeliveryDto[]>([]);
  readonly eventTypes = signal<string[]>([]);
  readonly selected = signal<Set<string>>(new Set());
  readonly showForm = signal(false);
  readonly secret = signal<WebhookSecretDto | null>(null);

  readonly statusCtrl = new FormControl<DeliveryStatus | null>(null);
  readonly subscriptionCtrl = new FormControl<number | null>(null);
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    targetUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^https:\/\/\S+$/i)],
    }),
  });

  ngOnInit(): void {
    this.loadWebhooks();
    this.api.eventTypes().subscribe({ next: (e) => this.eventTypes.set(e), error: () => this.eventTypes.set([]) });
  }

  onTab(index: number): void {
    if (index === 1) this.loadDeliveries();
  }

  loadWebhooks(): void {
    this.api.webhooks().subscribe({
      next: (w) => this.webhooks.set(w),
      error: () => this.snack.open('Could not load webhooks', 'Close', { duration: 4000 }),
    });
  }

  loadDeliveries(): void {
    this.api.deliveries(this.subscriptionCtrl.value, this.statusCtrl.value).subscribe({
      next: (p) => this.deliveries.set(p.content),
      error: () => this.snack.open('Could not load deliveries', 'Close', { duration: 4000 }),
    });
  }

  toggle(event: string, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) next.add(event);
    else next.delete(event);
    this.selected.set(next);
  }

  create(): void {
    if (this.form.invalid || this.selected().size === 0) return;
    this.api
      .createWebhook({
        name: this.form.controls.name.value,
        targetUrl: this.form.controls.targetUrl.value,
        events: [...this.selected()],
        active: true,
        version: null,
      })
      .subscribe({
        next: (created) => {
          this.secret.set(created);
          this.showForm.set(false);
          this.form.reset({ name: '', targetUrl: '' });
          this.selected.set(new Set());
          this.loadWebhooks();
        },
        error: (e) => this.snack.open(e?.error?.detail ?? 'Could not create the webhook', 'Close', { duration: 5000 }),
      });
  }

  rotate(w: WebhookDto): void {
    this.api.rotateSecret(w.id).subscribe({
      next: (rotated) => {
        this.secret.set(rotated);
        this.loadWebhooks();
      },
      error: () => this.snack.open('Could not rotate the secret', 'Close', { duration: 4000 }),
    });
  }

  test(w: WebhookDto): void {
    this.api.testWebhook(w.id).subscribe({
      next: (d) =>
        this.snack.open(
          d.status === 'DELIVERED' ? 'Test delivered' : `Test not delivered: ${d.lastError ?? d.status}`,
          'Close',
          { duration: 6000 },
        ),
      error: () => this.snack.open('Could not send the test', 'Close', { duration: 4000 }),
    });
  }

  toggleActive(w: WebhookDto): void {
    this.api
      .updateWebhook(w.id, {
        name: w.name,
        targetUrl: w.targetUrl,
        events: w.events,
        active: !w.active,
        version: w.version,
      })
      .subscribe({
        next: () => this.loadWebhooks(),
        error: (e) => this.snack.open(e?.error?.detail ?? 'Could not change the webhook', 'Close', { duration: 5000 }),
      });
  }

  retry(d: WebhookDeliveryDto): void {
    this.api.retryDelivery(d.id).subscribe({
      next: (r) => {
        this.snack.open(r.status === 'DELIVERED' ? 'Delivered' : `Still failing: ${r.lastError ?? r.status}`, 'Close', {
          duration: 5000,
        });
        this.loadDeliveries();
      },
      error: () => this.snack.open('Could not retry the delivery', 'Close', { duration: 4000 }),
    });
  }

  copySecret(value: string): void {
    void navigator.clipboard?.writeText(value);
    this.snack.open('Copied', 'Close', { duration: 1500 });
  }
}
