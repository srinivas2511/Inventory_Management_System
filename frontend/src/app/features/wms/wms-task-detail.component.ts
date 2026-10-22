import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { BinDto, MOVEMENT_LABELS, MovementType, TransferLineDto, TransferOrderDto, WmsApi } from './wms.api';

const PERMISSION_BY_MOVEMENT: Record<MovementType, string> = {
  PUTAWAY: 'PUTAWAY_MANAGE',
  CROSS_DOCK: 'PUTAWAY_MANAGE',
  PICKING: 'PICKING_MANAGE',
  INTERNAL_TRANSFER: 'TRANSFER_MANAGE',
  CYCLE_COUNT: 'TRANSFER_MANAGE',
};

/**
 * The transfer-order confirmation screen. Each open line takes a quantity and, optionally, the bin and batch codes
 * a scanner read; a mismatching scan is refused by the server before any stock moves.
 */
@Component({
  selector: 'app-wms-task-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    StatusBadgeComponent,
  ],
  template: `
    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      @if (order(); as o) {
        <div class="page-header">
          <h2 class="mat-h2" data-test="title">{{ o.toNumber }} - {{ movementLabel() }}</h2>
          <app-status-badge [status]="o.status" />
        </div>
        <p class="meta">
          @if (o.sourceDocumentType) {
            Source: {{ o.sourceDocumentType }} #{{ o.sourceDocumentId }}.
          }
          @if (o.crossDockTargetType) {
            Cross-dock for {{ o.crossDockTargetType }} #{{ o.crossDockTargetId }}.
          }
          @if (o.waveId) {
            Wave #{{ o.waveId }}.
          }
          Created {{ o.createdAt | istDate: 'dd MMM yyyy HH:mm' }}.
          @if (o.assignedTo) {
            Assigned to user {{ o.assignedTo }}.
          }
          @if (o.notes) {
            {{ o.notes }}
          }
        </p>

        @if (warnings().length > 0) {
          <div class="warning-box" data-test="warnings">
            <mat-icon>warning</mat-icon>
            <div>
              @for (w of warnings(); track w) {
                <div>{{ w }}</div>
              }
            </div>
          </div>
        }

        @if (canExecute() && o.allowedActions.length > 0) {
          <div class="actions">
            @if (o.allowedActions.includes('START')) {
              <button mat-stroked-button (click)="start()" data-test="start">
                <mat-icon>play_arrow</mat-icon> Start
              </button>
            }
            @if (o.allowedActions.includes('ASSIGN')) {
              <mat-form-field appearance="outline" class="narrow">
                <mat-label>Assign to user id</mat-label>
                <input matInput type="number" min="1" [formControl]="assignCtrl" />
              </mat-form-field>
              <button mat-stroked-button (click)="assign()" [disabled]="!assignCtrl.value">Assign</button>
            }
            @if (o.allowedActions.includes('CANCEL')) {
              <mat-form-field appearance="outline">
                <mat-label>Cancel reason</mat-label>
                <input matInput maxlength="300" [formControl]="cancelCtrl" />
              </mat-form-field>
              <button
                mat-stroked-button
                color="warn"
                (click)="cancel()"
                [disabled]="!cancelCtrl.value"
                data-test="cancel"
              >
                <mat-icon>cancel</mat-icon> Cancel order
              </button>
            }
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="confirm()">
          <table class="lines" data-test="lines">
            <thead>
              <tr>
                <th>#</th>
                <th>Item</th>
                <th>Batch</th>
                <th>From</th>
                <th>To</th>
                <th>Planned</th>
                <th>Done</th>
                <th>Status</th>
                @if (canConfirm()) {
                  <th>Confirm</th>
                  <th>Scan bin</th>
                  <th>Scan batch</th>
                }
              </tr>
            </thead>
            <tbody formArrayName="rows">
              @for (l of o.lines; track l.id) {
                <tr>
                  <td>{{ l.lineNo }}</td>
                  <td>{{ l.itemCode }} {{ l.itemName }}</td>
                  <td>{{ l.batchNumber }}</td>
                  <td>{{ l.fromBinCode ?? 'location ' + l.fromLocationId }}</td>
                  <td>
                    @if (isOpen(l) && needsDestination() && canConfirm()) {
                      <mat-select
                        [formControl]="rowControl(l.id, 'toBinId')"
                        placeholder="Choose bin"
                        data-test="to-bin"
                      >
                        @for (b of bins(); track b.id) {
                          <mat-option [value]="b.id">{{ b.binCode }}</mat-option>
                        }
                      </mat-select>
                    } @else {
                      {{ l.toBinCode ?? (needsDestination() ? '-' : 'verify only') }}
                    }
                  </td>
                  <td>{{ l.plannedQty | number: '1.0-3' }} {{ l.uom }}</td>
                  <td>{{ l.confirmedQty | number: '1.0-3' }}</td>
                  <td><app-status-badge [status]="l.status" /></td>
                  @if (canConfirm()) {
                    @if (isOpen(l)) {
                      <td>
                        <input
                          class="qty"
                          type="number"
                          min="0"
                          step="0.001"
                          [formControl]="rowControl(l.id, 'quantity')"
                          data-test="qty"
                        />
                      </td>
                      <td>
                        <input
                          class="scan"
                          [formControl]="rowControl(l.id, 'scannedToBin')"
                          placeholder="bin code"
                          data-test="scan-bin"
                        />
                      </td>
                      <td>
                        <input
                          class="scan"
                          [formControl]="rowControl(l.id, 'scannedBatch')"
                          placeholder="batch"
                          data-test="scan-batch"
                        />
                      </td>
                    } @else {
                      <td colspan="3"></td>
                    }
                  }
                </tr>
                @if (l.warning) {
                  <tr class="line-warning">
                    <td></td>
                    <td [attr.colspan]="canConfirm() ? 10 : 7">{{ l.warning }}</td>
                  </tr>
                }
              }
            </tbody>
          </table>
          @if (canConfirm()) {
            <div class="actions">
              <button mat-flat-button color="primary" type="submit" data-test="confirm">
                <mat-icon>done_all</mat-icon> Confirm quantities
              </button>
            </div>
          }
        </form>
      }
    }
  `,
  styles: `
    .meta {
      color: var(--mat-sys-on-surface-variant, #555);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 12px 0;
    }
    .narrow {
      width: 160px;
    }
    .lines {
      width: 100%;
      border-collapse: collapse;
    }
    .lines th,
    .lines td {
      text-align: left;
      padding: 6px 8px;
      border-bottom: 1px solid rgba(0, 0, 0, 0.12);
    }
    .qty {
      width: 90px;
    }
    .scan {
      width: 120px;
    }
    .line-warning td {
      color: #b26a00;
      font-size: 0.85rem;
    }
    .warning-box {
      display: flex;
      gap: 8px;
      align-items: flex-start;
      padding: 8px 12px;
      margin: 8px 0;
      border-radius: 4px;
      background: rgba(255, 152, 0, 0.12);
      color: #8a5300;
    }
  `,
})
export class WmsTaskDetailComponent implements OnInit {
  private api = inject(WmsApi);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private session = inject(SessionStore);

  order = signal<TransferOrderDto | null>(null);
  bins = signal<BinDto[]>([]);
  warnings = signal<string[]>([]);
  loading = signal(true);

  form = this.fb.group({ rows: this.fb.array<FormGroup>([]) });
  private rowIndex = new Map<number, FormGroup>();
  assignCtrl = new FormControl<number | null>(null);
  cancelCtrl = new FormControl('');

  movementLabel = computed(() => {
    const o = this.order();
    return o ? (MOVEMENT_LABELS[o.movementType] ?? o.movementType) : '';
  });

  canExecute = computed(() => {
    const o = this.order();
    return !!o && this.session.hasPermission(PERMISSION_BY_MOVEMENT[o.movementType]);
  });

  canConfirm = computed(() => this.canExecute() && !!this.order()?.allowedActions.includes('CONFIRM'));

  needsDestination = computed(() => this.order()?.movementType !== 'PICKING');

  ngOnInit(): void {
    this.reload(Number(this.route.snapshot.paramMap.get('id')));
  }

  isOpen(line: TransferLineDto): boolean {
    return line.status === 'OPEN' || line.status === 'PARTIAL';
  }

  rowControl(lineId: number, name: string): FormControl {
    return this.rowIndex.get(lineId)!.get(name) as FormControl;
  }

  private reload(id: number): void {
    this.api.getTransferOrder(id).subscribe({
      next: (o) => {
        this.apply(o);
        this.loading.set(false);
        this.api.getBins(o.warehouseId, null, null).subscribe((p) => this.bins.set(p.content.filter((b) => b.active)));
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Could not load the order', 'Dismiss', { duration: 4000 });
      },
    });
  }

  private apply(o: TransferOrderDto): void {
    this.order.set(o);
    const rows = this.form.controls.rows as FormArray<FormGroup>;
    rows.clear();
    this.rowIndex.clear();
    for (const l of o.lines) {
      const group = this.fb.group({
        lineId: [l.id],
        quantity: [Math.max(0, l.plannedQty - l.confirmedQty)],
        scannedToBin: [''],
        scannedBatch: [''],
        toBinId: [l.toBinId],
      });
      rows.push(group);
      this.rowIndex.set(l.id, group);
    }
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 6000 });
  }

  confirm(): void {
    const o = this.order();
    if (!o) return;
    const lines = o.lines
      .filter((l) => this.isOpen(l))
      .map((l) => {
        const v = this.rowIndex.get(l.id)!.getRawValue();
        return {
          lineId: l.id,
          quantity: Number(v.quantity),
          scannedFromBin: null,
          scannedToBin: v.scannedToBin ? String(v.scannedToBin).trim() : null,
          scannedBatch: v.scannedBatch ? String(v.scannedBatch).trim() : null,
          toBinId: v.toBinId ?? null,
        };
      })
      .filter((c) => c.quantity > 0);
    if (lines.length === 0) {
      this.snack.open('Enter a quantity to confirm', 'Dismiss', { duration: 3000 });
      return;
    }
    this.api.confirmOrder(o.id, lines).subscribe({
      next: (r) => {
        this.warnings.set(r.warnings);
        this.apply(r.order);
        this.snack.open('Confirmed', 'Dismiss', { duration: 2500 });
      },
      error: (e) => this.fail(e, 'Could not confirm'),
    });
  }

  start(): void {
    const o = this.order();
    if (o)
      this.api
        .startOrder(o.id)
        .subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, 'Could not start') });
  }

  assign(): void {
    const o = this.order();
    const user = this.assignCtrl.value;
    if (o && user)
      this.api
        .assignOrder(o.id, user)
        .subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, 'Could not assign') });
  }

  cancel(): void {
    const o = this.order();
    const reason = this.cancelCtrl.value;
    if (o && reason)
      this.api
        .cancelOrder(o.id, reason)
        .subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, 'Could not cancel') });
  }
}
