import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTabsModule } from '@angular/material/tabs';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { MaintenanceApi, MaintenanceRequestDto, PmScheduleDto } from './maintenance.api';

@Component({
  selector: 'app-maintenance-list',
  standalone: true,
  providers: [MaintenanceApi],
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatTabsModule,
    DatePipe,
    RouterLink,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Maintenance</h2>
    </div>

    <mat-tab-group>
      <mat-tab label="Work Requests">
        <div class="filter-bar">
          <mat-form-field appearance="outline">
            <mat-label>Status</mat-label>
            <mat-select [formControl]="statusCtrl">
              <mat-option [value]="null">All</mat-option>
              <mat-option value="OPEN">Open</mat-option>
              <mat-option value="IN_PROGRESS">In Progress</mat-option>
              <mat-option value="COMPLETED">Completed</mat-option>
              <mat-option value="CANCELLED">Cancelled</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Type</mat-label>
            <mat-select [formControl]="typeCtrl">
              <mat-option [value]="null">All</mat-option>
              <mat-option value="CORRECTIVE">Corrective</mat-option>
              <mat-option value="PREVENTIVE">Preventive</mat-option>
              <mat-option value="PREDICTIVE">Predictive</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        @if (loadingRequests()) {
          <div class="spinner-center"><mat-spinner diameter="40" /></div>
        } @else {
          <table mat-table [dataSource]="requests()" class="mat-elevation-z1">
            <ng-container matColumnDef="requestNumber">
              <th mat-header-cell *matHeaderCellDef>Request #</th>
              <td mat-cell *matCellDef="let r">{{ r.requestNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="requestType">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.requestType" /></td>
            </ng-container>
            <ng-container matColumnDef="priority">
              <th mat-header-cell *matHeaderCellDef>Priority</th>
              <td mat-cell *matCellDef="let r">{{ r.priority }}</td>
            </ng-container>
            <ng-container matColumnDef="description">
              <th mat-header-cell *matHeaderCellDef>Description</th>
              <td mat-cell *matCellDef="let r">{{ r.description }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
            </ng-container>
            <ng-container matColumnDef="scheduledDate">
              <th mat-header-cell *matHeaderCellDef>Scheduled</th>
              <td mat-cell *matCellDef="let r">{{ r.scheduledDate | date: 'mediumDate' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let r">
                <a mat-icon-button matTooltip="Open" [routerLink]="['/maintenance/requests', r.id]" data-test="open">
                  <mat-icon>open_in_new</mat-icon>
                </a>
                @if (r.status === 'OPEN') {
                  <button
                    mat-icon-button
                    matTooltip="Start"
                    *appHasPermission="'MAINTENANCE_MANAGE'"
                    (click)="updateStatus(r, 'IN_PROGRESS')"
                  >
                    <mat-icon>play_arrow</mat-icon>
                  </button>
                }
                @if (r.status === 'IN_PROGRESS') {
                  <button
                    mat-icon-button
                    matTooltip="Complete"
                    *appHasPermission="'MAINTENANCE_MANAGE'"
                    (click)="updateStatus(r, 'COMPLETED')"
                  >
                    <mat-icon>done</mat-icon>
                  </button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="requestCols"></tr>
            <tr mat-row *matRowDef="let row; columns: requestCols"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell no-data" [attr.colspan]="requestCols.length">No maintenance requests</td>
            </tr>
          </table>
        }
      </mat-tab>

      <mat-tab label="PM Schedules">
        @if (loadingPm()) {
          <div class="spinner-center"><mat-spinner diameter="40" /></div>
        } @else {
          <table mat-table [dataSource]="pmSchedules()" class="mat-elevation-z1" style="margin-top:16px">
            <ng-container matColumnDef="taskName">
              <th mat-header-cell *matHeaderCellDef>Task</th>
              <td mat-cell *matCellDef="let s">{{ s.taskName }}</td>
            </ng-container>
            <ng-container matColumnDef="machineId">
              <th mat-header-cell *matHeaderCellDef>Machine ID</th>
              <td mat-cell *matCellDef="let s">{{ s.machineId }}</td>
            </ng-container>
            <ng-container matColumnDef="frequencyDays">
              <th mat-header-cell *matHeaderCellDef>Every (days)</th>
              <td mat-cell *matCellDef="let s">{{ s.frequencyDays }}</td>
            </ng-container>
            <ng-container matColumnDef="nextDueDate">
              <th mat-header-cell *matHeaderCellDef>Next Due</th>
              <td mat-cell *matCellDef="let s">{{ s.nextDueDate | date: 'mediumDate' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let s">
                <button
                  mat-icon-button
                  matTooltip="Generate work request"
                  *appHasPermission="'MAINTENANCE_MANAGE'"
                  (click)="generatePmRequest(s)"
                >
                  <mat-icon>add_task</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="pmCols"></tr>
            <tr mat-row *matRowDef="let row; columns: pmCols"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell no-data" [attr.colspan]="pmCols.length">No PM schedules</td>
            </tr>
          </table>
        }
      </mat-tab>
    </mat-tab-group>
  `,
  styles: `
    .filter-bar {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      padding: 8px 0;
    }
    .spinner-center {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
    .no-data {
      text-align: center;
      padding: 24px;
      color: rgba(0, 0, 0, 0.54);
    }
  `,
})
export class MaintenanceListComponent implements OnInit {
  private readonly api = inject(MaintenanceApi);
  private readonly snack = inject(MatSnackBar);

  protected readonly requestCols = [
    'requestNumber',
    'requestType',
    'priority',
    'description',
    'status',
    'scheduledDate',
    'actions',
  ];
  protected readonly pmCols = ['taskName', 'machineId', 'frequencyDays', 'nextDueDate', 'actions'];

  protected readonly loadingRequests = signal(false);
  protected readonly loadingPm = signal(false);
  protected readonly requests = signal<MaintenanceRequestDto[]>([]);
  protected readonly pmSchedules = signal<PmScheduleDto[]>([]);

  protected readonly statusCtrl = new FormControl<string | null>(null);
  protected readonly typeCtrl = new FormControl<string | null>(null);

  ngOnInit(): void {
    this.loadRequests();
    this.loadPm();
    this.statusCtrl.valueChanges.subscribe(() => this.loadRequests());
    this.typeCtrl.valueChanges.subscribe(() => this.loadRequests());
  }

  private loadRequests(): void {
    this.loadingRequests.set(true);
    this.api.getRequests(this.statusCtrl.value ?? undefined, this.typeCtrl.value ?? undefined).subscribe({
      next: (p) => {
        this.requests.set(p.content);
        this.loadingRequests.set(false);
      },
      error: () => this.loadingRequests.set(false),
    });
  }

  private loadPm(): void {
    this.loadingPm.set(true);
    this.api.getPmSchedules().subscribe({
      next: (s) => {
        this.pmSchedules.set(s);
        this.loadingPm.set(false);
      },
      error: () => this.loadingPm.set(false),
    });
  }

  protected updateStatus(r: MaintenanceRequestDto, newStatus: string): void {
    this.api.updateStatus(r.id, newStatus).subscribe({
      next: () => {
        this.snack.open('Updated', 'Dismiss', { duration: 3000 });
        this.loadRequests();
      },
      error: (e) => this.snack.open(e.error?.message ?? 'Error', 'Dismiss', { duration: 4000 }),
    });
  }

  protected generatePmRequest(s: PmScheduleDto): void {
    this.api.generatePmRequest(s.id).subscribe({
      next: () => {
        this.snack.open('PM request created', 'Dismiss', { duration: 3000 });
        this.loadRequests();
      },
      error: (e) => this.snack.open(e.error?.message ?? 'Error', 'Dismiss', { duration: 4000 }),
    });
  }
}
