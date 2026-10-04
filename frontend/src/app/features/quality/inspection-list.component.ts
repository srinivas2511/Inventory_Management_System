import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface QualityInspectionDto {
  id: number;
  inspectionNumber: string;
  inspectionType: string;
  status: string;
  materialId: number;
  inspectedQty: number;
  acceptedQty: number;
  rejectedQty: number;
}

@Component({
  selector: 'app-inspection-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    DecimalPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Quality Inspections</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Type</mat-label>
        <mat-select [formControl]="typeCtrl">
          <mat-option [value]="null">All Types</mat-option>
          <mat-option value="INCOMING">Incoming</mat-option>
          <mat-option value="IN_PROCESS">In-Process</mat-option>
          <mat-option value="FINAL">Final</mat-option>
          <mat-option value="FAI">First article</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="PENDING">Pending</mat-option>
          <mat-option value="IN_PROGRESS">In Progress</mat-option>
          <mat-option value="APPROVED">Approved</mat-option>
          <mat-option value="REJECTED">Rejected</mat-option>
          <mat-option value="HOLD">Hold</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="inspectionNumber">
          <th mat-header-cell *matHeaderCellDef>Inspection #</th>
          <td mat-cell *matCellDef="let r">{{ r.inspectionNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="inspectionType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.inspectionType }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="materialId">
          <th mat-header-cell *matHeaderCellDef>Material</th>
          <td mat-cell *matCellDef="let r">{{ r.materialId }}</td>
        </ng-container>
        <ng-container matColumnDef="inspectedQty">
          <th mat-header-cell *matHeaderCellDef>Inspected</th>
          <td mat-cell *matCellDef="let r">{{ r.inspectedQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="acceptedQty">
          <th mat-header-cell *matHeaderCellDef>Accepted</th>
          <td mat-cell *matCellDef="let r">{{ r.acceptedQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="rejectedQty">
          <th mat-header-cell *matHeaderCellDef>Rejected</th>
          <td mat-cell *matCellDef="let r">{{ r.rejectedQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'PENDING' || r.status === 'IN_PROGRESS') {
              <button
                mat-icon-button
                matTooltip="Record Results"
                *appHasPermission="'QUALITY_INSPECT'"
                (click)="submitResult(r.id)"
              >
                <mat-icon>assignment</mat-icon>
              </button>
              <button mat-icon-button matTooltip="Approve" *appHasPermission="'QUALITY_INSPECT'" (click)="accept(r.id)">
                <mat-icon>check_circle</mat-icon>
              </button>
              <button mat-icon-button matTooltip="Reject" *appHasPermission="'QUALITY_INSPECT'" (click)="reject(r.id)">
                <mat-icon>cancel</mat-icon>
              </button>
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
    }
  `,
})
export class InspectionListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  columns = [
    'inspectionNumber',
    'inspectionType',
    'status',
    'materialId',
    'inspectedQty',
    'acceptedQty',
    'rejectedQty',
    'actions',
  ];
  rows = signal<QualityInspectionDto[]>([]);
  loading = signal(false);
  typeCtrl = new FormControl<string | null>(null);
  statusCtrl = new FormControl<string | null>(null);

  ngOnInit(): void {
    this.typeCtrl.valueChanges.subscribe(() => this.load());
    this.statusCtrl.valueChanges.subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const params: Record<string, string> = {};
    const t = this.typeCtrl.value;
    const s = this.statusCtrl.value;
    if (t) params['type'] = t;
    if (s) params['status'] = s;
    this.http.get<{ content: QualityInspectionDto[] }>('/api/quality/inspections', { params }).subscribe({
      next: (page) => {
        this.rows.set(page.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  submitResult(id: number): void {
    // Minimal: submit with zero quantities to open the inspection for recording
    const body = { acceptedQty: 0, rejectedQty: 0, remarks: '', items: [] };
    this.http.post<QualityInspectionDto>(`/api/quality/inspections/${id}/results`, body).subscribe({
      next: () => {
        this.snack.open('Results recorded', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }

  accept(id: number): void {
    this.http.post<QualityInspectionDto>(`/api/quality/inspections/${id}/accept`, {}).subscribe({
      next: () => {
        this.snack.open('Inspection approved', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }

  reject(id: number): void {
    const reason = prompt('Rejection reason:');
    if (!reason) return;
    this.http.post<QualityInspectionDto>(`/api/quality/inspections/${id}/reject`, { reason }).subscribe({
      next: () => {
        this.snack.open('Inspection rejected', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }
}
