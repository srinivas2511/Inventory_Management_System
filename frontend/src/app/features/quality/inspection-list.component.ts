import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
import { InspectionApi, QualityInspectionDto } from './inspection.api';

@Component({
  selector: 'app-inspection-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
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

    <div class="filter-bar" *appHasPermission="'QUALITY_INSPECT'">
      <mat-form-field appearance="outline">
        <mat-label>Production operation ID</mat-label>
        <input matInput type="number" min="1" [formControl]="operationCtrl" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Inspected qty</mat-label>
        <input matInput type="number" min="0" step="any" [formControl]="inProcessQtyCtrl" />
      </mat-form-field>
      <button
        mat-stroked-button
        (click)="startInProcess()"
        [disabled]="operationCtrl.invalid || inProcessQtyCtrl.invalid || creating()"
      >
        <mat-icon>add</mat-icon> New in-process inspection
      </button>
      <mat-form-field appearance="outline">
        <mat-label>FG batch ID</mat-label>
        <input matInput type="number" min="1" [formControl]="fgBatchCtrl" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Inspected qty</mat-label>
        <input matInput type="number" min="0" step="any" [formControl]="finalQtyCtrl" />
      </mat-form-field>
      <button
        mat-stroked-button
        (click)="startFinal()"
        [disabled]="fgBatchCtrl.invalid || finalQtyCtrl.invalid || creating()"
      >
        <mat-icon>add</mat-icon> New final inspection
      </button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="inspectionNumber">
          <th mat-header-cell *matHeaderCellDef>Inspection #</th>
          <td mat-cell *matCellDef="let r">
            <a [routerLink]="['/quality/inspections', r.id]">{{ r.inspectionNumber }}</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="inspectionType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.inspectionType }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="reference">
          <th mat-header-cell *matHeaderCellDef>Reference</th>
          <td mat-cell *matCellDef="let r">{{ reference(r) }}</td>
        </ng-container>
        <ng-container matColumnDef="inspectedQty">
          <th mat-header-cell *matHeaderCellDef>Inspected</th>
          <td mat-cell *matCellDef="let r">{{ r.inspectedQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="acceptedQty">
          <th mat-header-cell *matHeaderCellDef>Accepted</th>
          <td mat-cell *matCellDef="let r">{{ r.acceptedQty ?? 0 | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="rejectedQty">
          <th mat-header-cell *matHeaderCellDef>Rejected</th>
          <td mat-cell *matCellDef="let r">{{ r.rejectedQty ?? 0 | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <a mat-icon-button [routerLink]="['/quality/inspections', r.id]" matTooltip="Open">
              <mat-icon>open_in_new</mat-icon>
            </a>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
    }
  `,
})
export class InspectionListComponent implements OnInit {
  private api = inject(InspectionApi);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  columns = [
    'inspectionNumber',
    'inspectionType',
    'status',
    'reference',
    'inspectedQty',
    'acceptedQty',
    'rejectedQty',
    'actions',
  ];
  rows = signal<QualityInspectionDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  typeCtrl = new FormControl<string | null>(null);
  statusCtrl = new FormControl<string | null>(null);
  operationCtrl = new FormControl<number | null>(null, [Validators.required, Validators.min(1)]);
  inProcessQtyCtrl = new FormControl<number | null>(null, [Validators.required, Validators.min(0.001)]);
  fgBatchCtrl = new FormControl<number | null>(null, [Validators.required, Validators.min(1)]);
  finalQtyCtrl = new FormControl<number | null>(null, [Validators.required, Validators.min(0.001)]);

  ngOnInit(): void {
    this.typeCtrl.valueChanges.subscribe(() => this.load());
    this.statusCtrl.valueChanges.subscribe(() => this.load());
    this.load();
  }

  reference(r: QualityInspectionDto): string {
    if (r.grNumber) return `${r.grNumber} · ${r.materialCode ?? ''} ${r.batchNumber ?? ''}`.trim();
    if (r.productionOperationId) return `Operation ${r.productionOperationId}`;
    if (r.fgBatchId) return `FG batch ${r.fgBatchId}`;
    return '';
  }

  load(): void {
    this.loading.set(true);
    this.api.list(this.typeCtrl.value, this.statusCtrl.value).subscribe({
      next: (page) => {
        this.rows.set(page.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  startInProcess(): void {
    this.creating.set(true);
    this.api.createInProcess(this.operationCtrl.value as number, this.inProcessQtyCtrl.value as number).subscribe({
      next: (i) => this.opened(i),
      error: (e) => this.failed(e),
    });
  }

  startFinal(): void {
    this.creating.set(true);
    this.api.createFinal(this.fgBatchCtrl.value as number, this.finalQtyCtrl.value as number, null).subscribe({
      next: (i) => this.opened(i),
      error: (e) => this.failed(e),
    });
  }

  private opened(i: QualityInspectionDto): void {
    this.creating.set(false);
    this.snack.open(`Inspection ${i.inspectionNumber} created`, 'OK', { duration: 2500 });
    this.router.navigate(['/quality/inspections', i.id]);
  }

  private failed(e: { error?: { detail?: string } }): void {
    this.creating.set(false);
    this.snack.open(e.error?.detail ?? 'The inspection could not be created', 'OK', { duration: 5000 });
  }
}
