import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { SessionStore } from '../../core/auth/session.store';
import { LookupApi, PickableOrder } from '../../shared/lookup.api';
import { PickListLineDto, WaveDto, WaveStatus, WmsApi } from './wms.api';

const STATUSES: WaveStatus[] = ['CREATED', 'RELEASED', 'COMPLETED', 'CANCELLED'];

@Component({
  selector: 'app-wms-waves',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
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
      <h2 class="mat-h2">Pick Waves</h2>
    </div>

    <form *appHasPermission="'WAVE_MANAGE'" class="edit-form" (ngSubmit)="create()">
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Approved sales orders</mat-label>
        <mat-select [formControl]="ordersCtrl" multiple data-test="wave-orders">
          @for (o of salesOrders(); track o.id) {
            <mat-option [value]="o.id">{{ o.orderNumber }} · {{ o.customerName }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Notes</mat-label>
        <input matInput maxlength="300" [formControl]="notesCtrl" />
      </mat-form-field>
      <button
        mat-flat-button
        color="primary"
        type="submit"
        [disabled]="(ordersCtrl.value?.length ?? 0) === 0"
        data-test="create-wave"
      >
        <mat-icon>waves</mat-icon> Create wave
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

    <table mat-table [dataSource]="waves()" class="mat-elevation-z1" data-test="waves">
      <ng-container matColumnDef="waveNumber">
        <th mat-header-cell *matHeaderCellDef>Wave</th>
        <td mat-cell *matCellDef="let w">{{ w.waveNumber }}</td>
      </ng-container>
      <ng-container matColumnDef="orders">
        <th mat-header-cell *matHeaderCellDef>Sales orders</th>
        <td mat-cell *matCellDef="let w">{{ w.salesOrderIds.length }}</td>
      </ng-container>
      <ng-container matColumnDef="pickOrders">
        <th mat-header-cell *matHeaderCellDef>Picking orders</th>
        <td mat-cell *matCellDef="let w">{{ w.transferOrderIds.length }}</td>
      </ng-container>
      <ng-container matColumnDef="notes">
        <th mat-header-cell *matHeaderCellDef>Notes</th>
        <td mat-cell *matCellDef="let w">{{ w.notes }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let w"><app-status-badge [status]="w.status" /></td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let w">
          <button mat-button (click)="showPickList(w); $event.stopPropagation()">
            <mat-icon>list_alt</mat-icon> Pick list
          </button>
          <button
            *appHasPermission="'WAVE_MANAGE'"
            mat-button
            [disabled]="w.status !== 'CREATED'"
            (click)="release(w); $event.stopPropagation()"
            data-test="release"
          >
            <mat-icon>play_arrow</mat-icon> Release
          </button>
          <button
            *appHasPermission="'WAVE_MANAGE'"
            mat-button
            [disabled]="w.status === 'COMPLETED' || w.status === 'CANCELLED'"
            (click)="cancel(w); $event.stopPropagation()"
          >
            <mat-icon>cancel</mat-icon> Cancel
          </button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">No waves</td>
      </tr>
    </table>

    @if (selected(); as wave) {
      <h3 class="mat-h3">Pick list for {{ wave.waveNumber }}</h3>
      <div class="filter-bar">
        <mat-form-field appearance="outline">
          <mat-label>Zone</mat-label>
          <mat-select [formControl]="zoneCtrl" (selectionChange)="showPickList(wave)" data-test="zone-filter">
            <mat-option [value]="null">All zones</mat-option>
            @for (z of zoneOptions(); track z.id) {
              <mat-option [value]="z.id">{{ z.code }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>
      <table mat-table [dataSource]="pickList()" class="mat-elevation-z1" data-test="pick-list">
        <ng-container matColumnDef="zone">
          <th mat-header-cell *matHeaderCellDef>Zone</th>
          <td mat-cell *matCellDef="let l">{{ l.zoneCode ?? '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="bin">
          <th mat-header-cell *matHeaderCellDef>Bin</th>
          <td mat-cell *matCellDef="let l">{{ l.binCode ?? 'location ' + l.locationId }}</td>
        </ng-container>
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let l">{{ l.itemCode }} {{ l.itemName }}</td>
        </ng-container>
        <ng-container matColumnDef="batch">
          <th mat-header-cell *matHeaderCellDef>Batch</th>
          <td mat-cell *matCellDef="let l">{{ l.batchNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef>Total to pick</th>
          <td mat-cell *matCellDef="let l">{{ l.totalQty | number: '1.0-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="orders">
          <th mat-header-cell *matHeaderCellDef>Per order</th>
          <td mat-cell *matCellDef="let l">
            @for (o of l.orders; track o.transferOrderId) {
              <span class="chip">{{ o.orderNumber ?? '#' + o.salesOrderId }}: {{ o.quantity | number: '1.0-3' }}</span>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="pickColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: pickColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="pickColumns.length" style="text-align:center;padding:1rem">
            Nothing left to pick
          </td>
        </tr>
      </table>
    }
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
    .wide {
      min-width: 280px;
    }
    .chip {
      display: inline-block;
      margin-right: 8px;
      padding: 1px 8px;
      border-radius: 10px;
      background: rgba(63, 81, 181, 0.1);
      font-size: 0.8rem;
    }
  `,
})
export class WmsWavesComponent implements OnInit {
  private api = inject(WmsApi);
  private lookups = inject(LookupApi);
  private session = inject(SessionStore);
  private snack = inject(MatSnackBar);

  statuses = STATUSES;
  columns = ['waveNumber', 'orders', 'pickOrders', 'notes', 'status', 'actions'];
  pickColumns = ['zone', 'bin', 'item', 'batch', 'qty', 'orders'];

  waves = signal<WaveDto[]>([]);
  salesOrders = signal<PickableOrder[]>([]);
  selected = signal<WaveDto | null>(null);
  pickList = signal<PickListLineDto[]>([]);
  zoneOptions = signal<{ id: number; code: string }[]>([]);

  statusCtrl = new FormControl<WaveStatus | null>(null);
  ordersCtrl = new FormControl<number[]>([]);
  notesCtrl = new FormControl('');
  zoneCtrl = new FormControl<number | null>(null);

  ngOnInit(): void {
    this.load();
    // Only planners can create a wave (the picker is hidden for others), so only they may read the orders to pick.
    if (this.session.hasAnyPermission(['WAVE_MANAGE'])) {
      this.lookups.pickableOrders().subscribe((orders) => this.salesOrders.set(orders));
    }
  }

  load(): void {
    this.api.getWaves(this.statusCtrl.value).subscribe({
      next: (p) => this.waves.set(p.content),
      error: () => this.snack.open('Failed to load waves', 'Dismiss', { duration: 3000 }),
    });
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }

  create(): void {
    const ids = this.ordersCtrl.value ?? [];
    if (ids.length === 0) return;
    this.api.createWave(ids, null, this.notesCtrl.value || null).subscribe({
      next: (w) => {
        this.ordersCtrl.reset([]);
        this.notesCtrl.reset('');
        this.load();
        this.showPickList(w);
      },
      error: (e) => this.fail(e, 'Could not create the wave'),
    });
  }

  release(wave: WaveDto): void {
    this.api
      .releaseWave(wave.id)
      .subscribe({ next: () => this.load(), error: (e) => this.fail(e, 'Could not release the wave') });
  }

  cancel(wave: WaveDto): void {
    this.api.cancelWave(wave.id, 'Cancelled from the wave list').subscribe({
      next: () => this.load(),
      error: (e) => this.fail(e, 'Could not cancel the wave'),
    });
  }

  showPickList(wave: WaveDto): void {
    if (this.selected()?.id !== wave.id) {
      this.zoneCtrl.reset(null);
    }
    this.selected.set(wave);
    this.api.getPickList(wave.id, null).subscribe((all) => {
      const zones = new Map<number, string>();
      for (const l of all) {
        if (l.zoneId && l.zoneCode) zones.set(l.zoneId, l.zoneCode);
      }
      this.zoneOptions.set([...zones].map(([id, code]) => ({ id, code })));
      const zone = this.zoneCtrl.value;
      this.pickList.set(zone ? all.filter((l) => l.zoneId === zone) : all);
    });
  }
}
