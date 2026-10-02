import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface RoutingStepDto {
  id: number;
  productId: number;
  sequenceNo: number;
  operationId: number;
  machineId: number;
  standardTimeMin: number;
  setupTimeMin: number;
  inspectionPoint: boolean;
  active: boolean;
}

@Component({
  selector: 'app-routing-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    DecimalPipe,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Product Routing</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Product ID</mat-label>
        <input matInput [formControl]="productIdCtrl" type="number" placeholder="Enter product ID" />
      </mat-form-field>
      <button mat-raised-button color="primary" (click)="load()"><mat-icon>search</mat-icon> Load Routing</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else if (rows().length > 0) {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="sequenceNo">
          <th mat-header-cell *matHeaderCellDef>Seq</th>
          <td mat-cell *matCellDef="let r">{{ r.sequenceNo }}</td>
        </ng-container>
        <ng-container matColumnDef="operationId">
          <th mat-header-cell *matHeaderCellDef>Operation ID</th>
          <td mat-cell *matCellDef="let r">{{ r.operationId }}</td>
        </ng-container>
        <ng-container matColumnDef="machineId">
          <th mat-header-cell *matHeaderCellDef>Machine ID</th>
          <td mat-cell *matCellDef="let r">{{ r.machineId ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="standardTimeMin">
          <th mat-header-cell *matHeaderCellDef>Std Time (min)</th>
          <td mat-cell *matCellDef="let r">{{ r.standardTimeMin | number: '1.0-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="setupTimeMin">
          <th mat-header-cell *matHeaderCellDef>Setup (min)</th>
          <td mat-cell *matCellDef="let r">{{ r.setupTimeMin | number: '1.0-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="inspectionPoint">
          <th mat-header-cell *matHeaderCellDef>Inspection</th>
          <td mat-cell *matCellDef="let r">
            <mat-icon [color]="r.inspectionPoint ? 'primary' : ''">
              {{ r.inspectionPoint ? 'check' : 'remove' }}
            </mat-icon>
          </td>
        </ng-container>
        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>Active</th>
          <td mat-cell *matCellDef="let r">
            <mat-icon [color]="r.active ? 'primary' : 'warn'">
              {{ r.active ? 'check_circle' : 'cancel' }}
            </mat-icon>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
    } @else if (searched()) {
      <p class="empty-state">No routing steps found for this product.</p>
    }
  `,
})
export class RoutingEditorComponent {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  columns = ['sequenceNo', 'operationId', 'machineId', 'standardTimeMin', 'setupTimeMin', 'inspectionPoint', 'active'];
  rows = signal<RoutingStepDto[]>([]);
  loading = signal(false);
  searched = signal(false);
  productIdCtrl = new FormControl<number | null>(null);

  load(): void {
    const pid = this.productIdCtrl.value;
    if (!pid) {
      this.snack.open('Enter a product ID', 'OK', { duration: 2000 });
      return;
    }
    this.loading.set(true);
    this.searched.set(false);
    this.http.get<RoutingStepDto[]>(`/api/products/${pid}/routing`).subscribe({
      next: (steps) => {
        this.rows.set(steps ?? []);
        this.loading.set(false);
        this.searched.set(true);
      },
      error: (e) => {
        this.snack.open(e.error?.detail ?? 'Error loading routing', 'OK', { duration: 4000 });
        this.loading.set(false);
        this.searched.set(true);
      },
    });
  }
}
