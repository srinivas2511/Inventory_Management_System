import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { WarehouseApi, WarehouseDto } from '../warehouse/warehouse.api';
import { CycleCountDto, CycleCountStatus, CycleCountType, WmsApi, ZoneDto } from './wms.api';

const STATUSES: CycleCountStatus[] = ['CREATED', 'IN_PROGRESS', 'REVIEW', 'POSTED', 'CANCELLED'];
const TYPES: CycleCountType[] = ['ZONE', 'ABC', 'RANDOM'];

@Component({
  selector: 'app-wms-cycle-counts',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
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
      <h2 class="mat-h2">Cycle Counts</h2>
    </div>

    <form *appHasPermission="'CYCLE_COUNT_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="create()">
      <mat-form-field appearance="outline">
        <mat-label>Warehouse</mat-label>
        <mat-select formControlName="warehouseId" (selectionChange)="loadZones()" data-test="count-warehouse">
          @for (w of warehouses(); track w.id) {
            <mat-option [value]="w.id">{{ w.warehouseCode }} - {{ w.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Count by</mat-label>
        <mat-select formControlName="countType" data-test="count-type">
          @for (t of types; track t) {
            <mat-option [value]="t">{{ t }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      @switch (form.controls.countType.value) {
        @case ('ZONE') {
          <mat-form-field appearance="outline">
            <mat-label>Zone</mat-label>
            <mat-select formControlName="zoneId" data-test="count-zone">
              @for (z of zones(); track z.id) {
                <mat-option [value]="z.id">{{ z.zoneCode }} - {{ z.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        @case ('ABC') {
          <mat-form-field appearance="outline">
            <mat-label>ABC class</mat-label>
            <mat-select formControlName="abcClass">
              <mat-option value="A">A (highest value)</mat-option>
              <mat-option value="B">B</mat-option>
              <mat-option value="C">C (lowest value)</mat-option>
            </mat-select>
          </mat-form-field>
        }
        @case ('RANDOM') {
          <mat-form-field appearance="outline">
            <mat-label>Sample size</mat-label>
            <input matInput type="number" min="1" formControlName="sampleSize" />
          </mat-form-field>
        }
      }
      <mat-form-field appearance="outline">
        <mat-label>Notes</mat-label>
        <input matInput maxlength="300" formControlName="notes" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid" data-test="create-count">
        <mat-icon>add</mat-icon> New count
      </button>
    </form>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    <table mat-table [dataSource]="counts()" class="mat-elevation-z1" data-test="counts">
      <ng-container matColumnDef="countNumber">
        <th mat-header-cell *matHeaderCellDef>Count</th>
        <td mat-cell *matCellDef="let c">{{ c.countNumber }}</td>
      </ng-container>
      <ng-container matColumnDef="countType">
        <th mat-header-cell *matHeaderCellDef>Type</th>
        <td mat-cell *matCellDef="let c">{{ c.countType }}{{ c.abcClass ? ' ' + c.abcClass : '' }}</td>
      </ng-container>
      <ng-container matColumnDef="progress">
        <th mat-header-cell *matHeaderCellDef>Counted</th>
        <td mat-cell *matCellDef="let c">{{ c.countedCount }} / {{ c.lineCount }}</td>
      </ng-container>
      <ng-container matColumnDef="variances">
        <th mat-header-cell *matHeaderCellDef>Variances</th>
        <td mat-cell *matCellDef="let c">
          {{ c.status === 'REVIEW' || c.status === 'POSTED' ? c.varianceLineCount : '' }}
        </td>
      </ng-container>
      <ng-container matColumnDef="notes">
        <th mat-header-cell *matHeaderCellDef>Notes</th>
        <td mat-cell *matCellDef="let c">{{ c.notes }}</td>
      </ng-container>
      <ng-container matColumnDef="postedAt">
        <th mat-header-cell *matHeaderCellDef>Posted</th>
        <td mat-cell *matCellDef="let c">{{ c.postedAt | date: 'dd MMM yyyy' }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let c"><app-status-badge [status]="c.status" /></td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">No cycle counts</td>
      </tr>
    </table>
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .clickable {
      cursor: pointer;
    }
  `,
})
export class WmsCycleCountsComponent implements OnInit {
  private api = inject(WmsApi);
  private warehouseApi = inject(WarehouseApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  statuses = STATUSES;
  types = TYPES;
  columns = ['countNumber', 'countType', 'progress', 'variances', 'notes', 'postedAt', 'status'];

  counts = signal<CycleCountDto[]>([]);
  warehouses = signal<WarehouseDto[]>([]);
  zones = signal<ZoneDto[]>([]);

  statusCtrl = new FormControl<CycleCountStatus | null>(null);
  form = this.fb.group({
    warehouseId: new FormControl<number | null>(null, Validators.required),
    countType: ['ZONE' as CycleCountType, Validators.required],
    zoneId: new FormControl<number | null>(null),
    abcClass: ['A'],
    sampleSize: new FormControl<number | null>(10),
    notes: [''],
  });

  ngOnInit(): void {
    this.load();
    this.warehouseApi.list().subscribe((ws) => this.warehouses.set(ws.filter((w) => w.active)));
  }

  load(): void {
    this.api.getCycleCounts(this.statusCtrl.value).subscribe({
      next: (p) => this.counts.set(p.content),
      error: () => this.snack.open('Failed to load cycle counts', 'Dismiss', { duration: 3000 }),
    });
  }

  loadZones(): void {
    const wh = this.form.controls.warehouseId.value;
    if (wh) this.api.getZones(wh).subscribe((z) => this.zones.set(z));
  }

  create(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createCycleCount({
        warehouseId: v.warehouseId as number,
        countType: v.countType as CycleCountType,
        zoneId: v.countType === 'ZONE' ? v.zoneId : null,
        abcClass: v.countType === 'ABC' ? v.abcClass : null,
        sampleSize: v.countType === 'RANDOM' ? v.sampleSize : null,
        notes: v.notes || null,
      })
      .subscribe({
        next: (c) => this.open(c),
        error: (e) => this.snack.open(e?.error?.detail ?? 'Could not create the count', 'Dismiss', { duration: 5000 }),
      });
  }

  open(count: CycleCountDto): void {
    this.router.navigate(['/wms/cycle-counts', count.id]);
  }
}
