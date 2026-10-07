import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import {
  WarehouseApi,
  WarehouseDto,
  WarehouseLocationDto,
  SaveWarehouseRequest,
  SaveLocationRequest,
} from './warehouse.api';

@Component({
  selector: 'app-warehouse-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatExpansionModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDialogModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Warehouses</h2>
      <button
        mat-flat-button
        color="primary"
        *appHasPermission="'WAREHOUSE_MANAGE'"
        (click)="openCreateDialog()"
        data-testid="add-warehouse"
      >
        <mat-icon>add</mat-icon> New Warehouse
      </button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <mat-accordion>
        @for (wh of warehouses(); track wh.id) {
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title
                ><strong>{{ wh.warehouseCode }}</strong> — {{ wh.name }}</mat-panel-title
              >
              <mat-panel-description>
                <app-status-badge [status]="wh.active ? 'ACTIVE' : 'INACTIVE'" />
              </mat-panel-description>
            </mat-expansion-panel-header>

            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
              <span class="mat-body-2">Locations</span>
              <button mat-stroked-button *appHasPermission="'LOCATION_MANAGE'" (click)="openAddLocation(wh)">
                <mat-icon>add</mat-icon> Add Location
              </button>
            </div>

            <table mat-table [dataSource]="locationMap()[wh.id] ?? []" class="compact-table">
              <ng-container matColumnDef="code">
                <th mat-header-cell *matHeaderCellDef>Code</th>
                <td mat-cell *matCellDef="let l">{{ l.locationCode }}</td>
              </ng-container>
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let l">{{ l.name }}</td>
              </ng-container>
              <ng-container matColumnDef="type">
                <th mat-header-cell *matHeaderCellDef>Type</th>
                <td mat-cell *matCellDef="let l"><app-status-badge [status]="l.locationType" /></td>
              </ng-container>
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let l">
                  <app-status-badge [status]="l.active ? 'ACTIVE' : 'INACTIVE'" />
                </td>
              </ng-container>
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let l">
                  <button
                    mat-icon-button
                    color="warn"
                    matTooltip="Delete"
                    aria-label="Delete"
                    *appHasPermission="'LOCATION_MANAGE'"
                    (click)="deleteLocation(wh, l)"
                  >
                    <mat-icon>delete</mat-icon>
                  </button>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="locationCols"></tr>
              <tr mat-row *matRowDef="let row; columns: locationCols"></tr>
            </table>

            <mat-action-row>
              <button mat-button color="warn" *appHasPermission="'WAREHOUSE_MANAGE'" (click)="deactivate(wh)">
                Deactivate
              </button>
            </mat-action-row>
          </mat-expansion-panel>
        }
      </mat-accordion>
    }
  `,
})
export class WarehouseListComponent implements OnInit {
  private readonly api = inject(WarehouseApi);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(false);
  readonly warehouses = signal<WarehouseDto[]>([]);
  readonly locationMap = signal<Record<number, WarehouseLocationDto[]>>({});
  readonly locationCols = ['code', 'name', 'type', 'status', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (data) => {
        this.warehouses.set(data);
        this.locationMap.set({});
        data.forEach((wh) => {
          this.api.getLocations(wh.id).subscribe((locs) => this.locationMap.update((m) => ({ ...m, [wh.id]: locs })));
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openCreateDialog(): void {
    this.dialog
      .open(WarehouseFormDialogComponent, { data: null, width: '420px' })
      .afterClosed()
      .subscribe((result: SaveWarehouseRequest | undefined) => {
        if (result) this.api.create(result).subscribe(() => this.load());
      });
  }

  openAddLocation(wh: WarehouseDto): void {
    this.dialog
      .open(LocationFormDialogComponent, { data: { warehouseName: wh.name }, width: '420px' })
      .afterClosed()
      .subscribe((result: SaveLocationRequest | undefined) => {
        if (result) this.api.createLocation(wh.id, result).subscribe(() => this.load());
      });
  }

  deactivate(wh: WarehouseDto): void {
    this.api.deactivate(wh.id).subscribe(() => this.load());
  }

  deleteLocation(wh: WarehouseDto, loc: WarehouseLocationDto): void {
    this.api.deleteLocation(wh.id, loc.id).subscribe(() => this.load());
  }
}

@Component({
  selector: 'app-warehouse-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>New Warehouse</h2>
    <mat-dialog-content [formGroup]="form" style="display:flex;flex-direction:column;padding-top:8px">
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput formControlName="warehouseCode" data-testid="wh-code" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Name</mat-label>
        <input matInput formControlName="name" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Address</mat-label>
        <textarea matInput formControlName="address" rows="2"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="ref.close(form.value)">Save</button>
    </mat-dialog-actions>
  `,
})
export class WarehouseFormDialogComponent {
  readonly ref = inject(MatDialogRef<WarehouseFormDialogComponent>);
  private readonly fb = inject(FormBuilder);
  readonly form = this.fb.group({
    warehouseCode: ['', Validators.required],
    name: ['', Validators.required],
    address: [''],
  });
}

@Component({
  selector: 'app-location-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>Add Location — {{ data.warehouseName }}</h2>
    <mat-dialog-content [formGroup]="form" style="display:flex;flex-direction:column;padding-top:8px">
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput formControlName="locationCode" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Name</mat-label>
        <input matInput formControlName="name" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Type</mat-label>
        <mat-select formControlName="locationType">
          <mat-option value="RAW_MATERIAL">Raw Material</mat-option>
          <mat-option value="WIP">WIP</mat-option>
          <mat-option value="FINISHED">Finished Goods</mat-option>
          <mat-option value="QUARANTINE">Quarantine</mat-option>
          <mat-option value="QUALITY_PENDING">Quality Pending</mat-option>
          <mat-option value="SCRAP">Scrap</mat-option>
          <mat-option value="DISPATCH">Dispatch</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="ref.close(form.value)">Save</button>
    </mat-dialog-actions>
  `,
})
export class LocationFormDialogComponent {
  readonly data = inject(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<LocationFormDialogComponent>);
  private readonly fb = inject(FormBuilder);
  readonly form = this.fb.group({
    locationCode: ['', Validators.required],
    name: ['', Validators.required],
    locationType: ['RAW_MATERIAL', Validators.required],
  });
}
