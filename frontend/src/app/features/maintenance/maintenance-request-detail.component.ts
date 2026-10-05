import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { WarehouseApi, WarehouseDto } from '../warehouse/warehouse.api';
import {
  ActivityDto,
  MaintenanceApi,
  MaintenanceCostDto,
  MaintenanceRequestDto,
  SparePartDto,
  SparePartIssueLineDto,
} from './maintenance.api';

/** The state machine the server enforces: which statuses a request can move to from each. */
export const NEXT_STATUSES: Record<string, { status: string; label: string; icon: string }[]> = {
  OPEN: [
    { status: 'IN_PROGRESS', label: 'Start', icon: 'play_arrow' },
    { status: 'CANCELLED', label: 'Cancel', icon: 'cancel' },
  ],
  IN_PROGRESS: [
    { status: 'COMPLETED', label: 'Complete', icon: 'done' },
    { status: 'CANCELLED', label: 'Cancel', icon: 'cancel' },
  ],
  COMPLETED: [],
  CANCELLED: [],
};

@Component({
  selector: 'app-maintenance-request-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <a mat-button routerLink="/maintenance"><mat-icon>arrow_back</mat-icon> Maintenance</a>
    </div>

    @if (request(); as r) {
      <h2 class="mat-h2" data-test="title">{{ r.requestNumber }} <app-status-badge [status]="r.status" /></h2>
      <p>
        {{ r.requestType }}, priority {{ r.priority }}, machine {{ r.machineId }}<br />
        {{ r.description }}
        @if (r.resolutionNotes) {
          <br /><em>{{ r.resolutionNotes }}</em>
        }
      </p>
      <div class="actions" *appHasPermission="'MAINTENANCE_MANAGE'">
        @for (n of nextStatuses(); track n.status) {
          <button mat-stroked-button (click)="move(n.status)" [attr.data-test]="'move-' + n.status">
            <mat-icon>{{ n.icon }}</mat-icon> {{ n.label }}
          </button>
        }
      </div>

      <h3 class="mat-h3">Work done</h3>
      <table mat-table [dataSource]="activities()" class="mat-elevation-z1" data-test="activities">
        <ng-container matColumnDef="when">
          <th mat-header-cell *matHeaderCellDef>When</th>
          <td mat-cell *matCellDef="let a">{{ a.performedAt | date: 'medium' }}</td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Work</th>
          <td mat-cell *matCellDef="let a">{{ a.description }}</td>
        </ng-container>
        <ng-container matColumnDef="minutes">
          <th mat-header-cell *matHeaderCellDef>Minutes</th>
          <td mat-cell *matCellDef="let a">{{ a.durationMinutes }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="activityColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: activityColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="activityColumns.length">No work booked yet</td>
        </tr>
      </table>
      @if (acceptsWork()) {
        <form
          *appHasPermission="'MAINTENANCE_MANAGE'"
          class="edit-form"
          (ngSubmit)="addActivity()"
          data-test="activity-form"
        >
          <mat-form-field appearance="outline" class="wide">
            <mat-label>What was done</mat-label>
            <input matInput maxlength="500" [formControl]="activityText" data-test="activity-text" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="narrow">
            <mat-label>Minutes</mat-label>
            <input matInput type="number" min="1" [formControl]="activityMinutes" data-test="activity-minutes" />
          </mat-form-field>
          <button
            mat-flat-button
            color="primary"
            type="submit"
            [disabled]="activityText.invalid || activityMinutes.invalid"
          >
            <mat-icon>add</mat-icon> Book work
          </button>
        </form>
      }

      <h3 class="mat-h3">Spare parts issued</h3>
      <table mat-table [dataSource]="issues()" class="mat-elevation-z1" data-test="issues">
        <ng-container matColumnDef="part">
          <th mat-header-cell *matHeaderCellDef>Part</th>
          <td mat-cell *matCellDef="let l">{{ l.sparePartCode }} {{ l.sparePartName }}</td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef>Quantity</th>
          <td mat-cell *matCellDef="let l">{{ l.issuedQty | number: '1.0-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="unitCost">
          <th mat-header-cell *matHeaderCellDef>Unit cost</th>
          <td mat-cell *matCellDef="let l">{{ l.unitCost | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>Cost</th>
          <td mat-cell *matCellDef="let l">{{ l.totalCost | number: '1.2-2' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="issueColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: issueColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="issueColumns.length">No spare parts issued</td>
        </tr>
      </table>
      @if (acceptsWork()) {
        <form *appHasPermission="'SPARE_PART_ISSUE'" class="edit-form" (ngSubmit)="issue()" data-test="issue-form">
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Spare part</mat-label>
            <mat-select [formControl]="partCtrl" data-test="issue-part">
              @for (p of parts(); track p.id) {
                <mat-option [value]="p.id"
                  >{{ p.sparePartCode }} {{ p.name }} ({{ p.onHandQty | number: '1.0-3' }} on hand)</mat-option
                >
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>From warehouse</mat-label>
            <mat-select [formControl]="warehouseCtrl" data-test="issue-warehouse">
              @for (w of warehouses(); track w.id) {
                <mat-option [value]="w.id">{{ w.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="narrow">
            <mat-label>Quantity</mat-label>
            <input matInput type="number" min="0.001" step="any" [formControl]="qtyCtrl" data-test="issue-qty" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="!canIssue()" data-test="issue-save">
            <mat-icon>outbox</mat-icon> Issue
          </button>
        </form>
      }

      @if (cost(); as c) {
        <h3 class="mat-h3">Cost</h3>
        <div class="cost" data-test="cost">
          <div>
            Labour: {{ c.labourHours | number: '1.2-2' }} h at {{ c.labourRatePerHour | number: '1.2-2' }} per hour =
            <strong>{{ c.labourCost | number: '1.2-2' }}</strong>
            <em>{{ c.labourCostFinal ? '' : ' (estimate until the request is completed)' }}</em>
          </div>
          <div>
            Spare parts: <strong>{{ c.sparePartsCost | number: '1.2-2' }}</strong>
          </div>
          <div>
            Total: <strong data-test="cost-total">{{ c.totalCost | number: '1.2-2' }}</strong>
          </div>
          @if (c.labourCostFinal) {
            @if (c.costPostedAt) {
              <div data-test="posted">Labour posted to the GL on {{ c.costPostedAt | date: 'medium' }}.</div>
            } @else {
              <div class="warn" data-test="not-posted">Labour is not in the GL yet.</div>
              <button
                *appHasPermission="'MAINTENANCE_COST_POST'"
                mat-flat-button
                color="primary"
                (click)="postCost()"
                data-test="post-cost"
              >
                <mat-icon>account_balance</mat-icon> Post to GL
              </button>
            }
          }
        </div>
      }
    }
  `,
  styles: `
    .actions,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 280px;
    }
    .narrow {
      width: 120px;
    }
    .cost {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 8px 16px;
      border-radius: 6px;
      background: rgba(63, 81, 181, 0.07);
      max-width: 560px;
    }
    .warn {
      color: #c62828;
    }
    .no-data {
      text-align: center;
      padding: 16px;
      color: rgba(0, 0, 0, 0.54);
    }
  `,
})
export class MaintenanceRequestDetailComponent implements OnInit {
  private api = inject(MaintenanceApi);
  private warehouseApi = inject(WarehouseApi);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);
  private session = inject(SessionStore);

  activityColumns = ['when', 'description', 'minutes'];
  issueColumns = ['part', 'qty', 'unitCost', 'total'];

  id = 0;
  request = signal<MaintenanceRequestDto | null>(null);
  activities = signal<ActivityDto[]>([]);
  issues = signal<SparePartIssueLineDto[]>([]);
  cost = signal<MaintenanceCostDto | null>(null);
  parts = signal<SparePartDto[]>([]);
  warehouses = signal<WarehouseDto[]>([]);

  activityText = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(500)],
  });
  activityMinutes = new FormControl(30, { nonNullable: true, validators: [Validators.required, Validators.min(1)] });
  partCtrl = new FormControl<number | null>(null);
  warehouseCtrl = new FormControl<number | null>(null);
  qtyCtrl = new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(0.001)] });

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.reload();
    if (this.session.hasPermission('SPARE_PART_ISSUE')) {
      this.api.spareParts(null, true).subscribe((p) => this.parts.set(p.content));
      this.warehouseApi.list().subscribe((w) => this.warehouses.set(w));
    }
  }

  reload(): void {
    this.api.getRequest(this.id).subscribe({
      next: (r) => this.request.set(r),
      error: () => this.snack.open('Could not load the request', 'Dismiss', { duration: 4000 }),
    });
    this.api.activities(this.id).subscribe((a) => this.activities.set(a));
    this.api.issues(this.id).subscribe((i) => this.issues.set(i));
    if (this.session.hasPermission('MAINTENANCE_COST_VIEW')) {
      this.api.cost(this.id).subscribe((c) => this.cost.set(c));
    }
  }

  nextStatuses(): { status: string; label: string; icon: string }[] {
    return NEXT_STATUSES[this.request()?.status ?? ''] ?? [];
  }

  acceptsWork(): boolean {
    const s = this.request()?.status;
    return s === 'OPEN' || s === 'IN_PROGRESS';
  }

  move(status: string): void {
    this.api.updateStatus(this.id, status).subscribe({
      next: () => this.reload(),
      error: (e) => this.fail(e, 'Could not change the status'),
    });
  }

  addActivity(): void {
    if (this.activityText.invalid || this.activityMinutes.invalid) return;
    this.api
      .addActivity(this.id, {
        description: this.activityText.value.trim(),
        durationMinutes: this.activityMinutes.value,
      })
      .subscribe({
        next: () => {
          this.activityText.reset('');
          this.reload();
        },
        error: (e) => this.fail(e, 'Could not book the work'),
      });
  }

  canIssue(): boolean {
    return !!this.partCtrl.value && !!this.warehouseCtrl.value && this.qtyCtrl.valid;
  }

  issue(): void {
    if (!this.canIssue()) return;
    this.api
      .issueSparePart(this.id, {
        sparePartId: this.partCtrl.value as number,
        warehouseId: this.warehouseCtrl.value as number,
        quantity: this.qtyCtrl.value,
      })
      .subscribe({
        next: () => {
          this.partCtrl.reset(null);
          this.qtyCtrl.reset(1);
          this.reload();
          this.api.spareParts(null, true).subscribe((p) => this.parts.set(p.content));
        },
        error: (e) => this.fail(e, 'Could not issue the spare part'),
      });
  }

  postCost(): void {
    this.api.postCost(this.id).subscribe({
      next: (c) => {
        this.cost.set(c);
        this.snack.open('Labour cost posted', 'Dismiss', { duration: 3000 });
      },
      error: (e) => this.fail(e, 'Could not post the cost'),
    });
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }
}
