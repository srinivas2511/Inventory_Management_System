import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { BomFormComponent } from './bom-form.component';
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
import { openDocumentsDialog } from '../documents/documents-dialog.component';

export interface BomDto {
  id: number;
  bomNumber: string;
  revision: string;
  productId: number;
  status: string;
  effectiveFrom: string;
  effectiveTo: string;
  baseQuantity: number;
  allowedActions: string[];
}

@Component({
  selector: 'app-bom-list',
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
    DatePipe,
    DecimalPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Bill of Materials</h2>
      <button mat-raised-button color="primary" *appHasPermission="'BOM_CREATE'" (click)="createBom()">
        <mat-icon>add</mat-icon> New BOM
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="DRAFT">Draft</mat-option>
          <mat-option value="ENGINEERING_REVIEW">Engineering Review</mat-option>
          <mat-option value="APPROVED">Approved</mat-option>
          <mat-option value="ACTIVE">Active</mat-option>
          <mat-option value="SUPERSEDED">Superseded</mat-option>
          <mat-option value="OBSOLETE">Obsolete</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="bomNumber">
          <th mat-header-cell *matHeaderCellDef>BOM Number</th>
          <td mat-cell *matCellDef="let r">{{ r.bomNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="revision">
          <th mat-header-cell *matHeaderCellDef>Rev</th>
          <td mat-cell *matCellDef="let r">{{ r.revision }}</td>
        </ng-container>
        <ng-container matColumnDef="productId">
          <th mat-header-cell *matHeaderCellDef>Product ID</th>
          <td mat-cell *matCellDef="let r">{{ r.productId }}</td>
        </ng-container>
        <ng-container matColumnDef="baseQuantity">
          <th mat-header-cell *matHeaderCellDef>Base Qty</th>
          <td mat-cell *matCellDef="let r">{{ r.baseQuantity | number: '1.0-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="effectiveFrom">
          <th mat-header-cell *matHeaderCellDef>Effective From</th>
          <td mat-cell *matCellDef="let r">{{ r.effectiveFrom | date: 'dd/MM/yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button
              *appHasPermission="'DOCUMENT_VIEW'"
              mat-icon-button
              matTooltip="Documents"
              aria-label="Documents"
              (click)="openDocuments(r)"
            >
              <mat-icon>attach_file</mat-icon>
            </button>
            @if (r.allowedActions?.includes('submit')) {
              <button
                mat-icon-button
                matTooltip="Submit for Review"
                *appHasPermission="'BOM_CREATE'"
                (click)="doAction(r.id, 'submit')"
              >
                <mat-icon>send</mat-icon>
              </button>
            }
            @if (r.allowedActions?.includes('approve')) {
              <button
                mat-icon-button
                matTooltip="Approve"
                *appHasPermission="'BOM_APPROVE'"
                (click)="doAction(r.id, 'approve')"
              >
                <mat-icon>check_circle</mat-icon>
              </button>
            }
            @if (r.allowedActions?.includes('activate')) {
              <button
                mat-icon-button
                matTooltip="Activate"
                *appHasPermission="'BOM_APPROVE'"
                (click)="doAction(r.id, 'activate')"
              >
                <mat-icon>toggle_on</mat-icon>
              </button>
            }
            @if (r.allowedActions?.includes('copy_revision')) {
              <button
                mat-icon-button
                matTooltip="Copy Revision"
                *appHasPermission="'BOM_UPDATE'"
                (click)="doAction(r.id, 'copy-revision')"
              >
                <mat-icon>copy_all</mat-icon>
              </button>
            }
            @if (r.allowedActions?.includes('recall')) {
              <button
                mat-icon-button
                matTooltip="Recall"
                *appHasPermission="'BOM_CREATE'"
                (click)="doAction(r.id, 'recall')"
              >
                <mat-icon>undo</mat-icon>
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
export class BomListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  columns = ['bomNumber', 'revision', 'productId', 'baseQuantity', 'effectiveFrom', 'status', 'actions'];
  rows = signal<BomDto[]>([]);
  loading = signal(false);
  statusCtrl = new FormControl<string | null>(null);

  openDocuments(bom: BomDto): void {
    openDocumentsDialog(this.dialog, { entityType: 'BOM', entityId: bom.id, title: bom.bomNumber });
  }

  ngOnInit(): void {
    this.statusCtrl.valueChanges.subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const params: Record<string, string> = {};
    const s = this.statusCtrl.value;
    if (s) params['status'] = s;
    this.http.get<{ content: BomDto[] }>('/api/boms', { params }).subscribe({
      next: (page) => {
        this.rows.set(page.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  createBom(): void {
    this.dialog
      .open(BomFormComponent, { width: '960px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snack.open('BOM created as a draft', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  doAction(id: number, action: string): void {
    this.http.post<BomDto>(`/api/boms/${id}/${action}`, {}).subscribe({
      next: () => {
        this.snack.open('Done', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }
}
