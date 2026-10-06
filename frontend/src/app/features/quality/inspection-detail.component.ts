import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import {
  InspectionApi,
  InspectionFgBatchDto,
  InspectionItemDto,
  QualityInspectionDto,
  RejectionReasonDto,
} from './inspection.api';

export type ItemOutcome = 'PASS' | 'FAIL' | 'NOT_MEASURED';

/** Mirrors QualityInspectionItem.computeResult so the form can highlight tolerance before the server decides. */
export function previewResult(
  item: Pick<InspectionItemDto, 'lowerLimit' | 'upperLimit'>,
  value: number | null,
  text: string | null,
): ItemOutcome {
  const hasText = text !== null && text.trim() !== '';
  if (value === null && !hasText) {
    return 'NOT_MEASURED';
  }
  if (value !== null && (item.lowerLimit !== null || item.upperLimit !== null)) {
    const pass =
      (item.lowerLimit === null || value >= item.lowerLimit) && (item.upperLimit === null || value <= item.upperLimit);
    return pass ? 'PASS' : 'FAIL';
  }
  return 'PASS';
}

interface ItemRow {
  item: InspectionItemDto;
  value: FormControl<number | null>;
  text: FormControl<string>;
  note: FormControl<string>;
  override: FormControl<string>;
}

interface ReasonLine {
  reason: FormControl<number | null>;
  qty: FormControl<number | null>;
  remarks: FormControl<string>;
}

@Component({
  selector: 'app-inspection-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    @if (insp(); as i) {
      <div class="page-header">
        <div>
          <a routerLink="/quality/inspections" class="back"><mat-icon>arrow_back</mat-icon> Inspections</a>
          <h2 class="mat-h2">{{ i.inspectionNumber }} <app-status-badge [status]="i.status" /></h2>
        </div>
      </div>

      <dl class="facts">
        <dt>Type</dt>
        <dd>{{ i.inspectionType }}</dd>
        @if (i.grNumber) {
          <dt>Goods receipt</dt>
          <dd>{{ i.grNumber }}</dd>
        }
        @if (i.materialCode) {
          <dt>Material</dt>
          <dd>{{ i.materialCode }}</dd>
        }
        @if (i.batchNumber) {
          <dt>Material batch</dt>
          <dd>{{ i.batchNumber }}</dd>
        }
        @if (i.productionOrderId) {
          <dt>Production order</dt>
          <dd>{{ i.productionOrderId }}</dd>
        }
        <dt>Inspected quantity</dt>
        <dd>{{ i.inspectedQty | number: '1.0-3' }}</dd>
        <dt>Sample size</dt>
        <dd>{{ i.sampleSize ?? 'not set' }}</dd>
        @if (i.inspectedAt) {
          <dt>Results recorded</dt>
          <dd>{{ i.inspectedAt | date: 'dd MMM yyyy HH:mm' }}</dd>
        }
        @if (i.decisionAt) {
          <dt>Decision</dt>
          <dd>{{ i.decisionAt | date: 'dd MMM yyyy HH:mm' }}</dd>
        }
      </dl>

      @if (i.inspectionType === 'FAI') {
        <p class="note">First article inspections are handled on the First article screen.</p>
      }

      <h3 class="mat-h3">Parameters</h3>
      @if (rows.length === 0) {
        <p class="note">
          No inspection plan parameters were generated for this inspection, so it can only be decided as a whole.
        </p>
      } @else {
        <table class="params">
          <thead>
            <tr>
              <th>#</th>
              <th>Parameter</th>
              <th>Specification</th>
              <th>Actual value</th>
              <th>Actual text</th>
              <th>Deviation note</th>
              <th>Result</th>
            </tr>
          </thead>
          <tbody>
            @for (r of rows; track r.item.id) {
              <tr [class.fail]="outcome(r) === 'FAIL'" [class.pass]="outcome(r) === 'PASS'">
                <td>{{ r.item.seq }}</td>
                <td>
                  {{ r.item.parameterName }}
                  @if (r.item.mandatory) {
                    <span class="req" title="Mandatory">*</span>
                  }
                </td>
                <td>
                  {{ r.item.specification ?? '' }}
                  @if (r.item.lowerLimit !== null || r.item.upperLimit !== null) {
                    <div class="limits">
                      {{ r.item.lowerLimit ?? '-' }} to {{ r.item.upperLimit ?? '-' }} {{ r.item.unit ?? '' }}
                    </div>
                  }
                </td>
                <td>
                  @if (editable()) {
                    <input class="cell" type="number" step="any" [formControl]="r.value" aria-label="Actual value" />
                  } @else {
                    {{ r.item.actualValue ?? '' }}
                  }
                </td>
                <td>
                  @if (editable()) {
                    <input class="cell" type="text" [formControl]="r.text" aria-label="Actual text" />
                  } @else {
                    {{ r.item.actualText ?? '' }}
                  }
                </td>
                <td>
                  @if (editable()) {
                    <input class="cell" type="text" [formControl]="r.note" aria-label="Deviation note" />
                  } @else {
                    {{ r.item.deviationNote ?? '' }}
                  }
                </td>
                <td class="outcome">{{ editable() ? outcome(r) : r.item.result }}</td>
              </tr>
            }
          </tbody>
        </table>
      }

      @if (editable()) {
        <ng-container *appHasPermission="'QUALITY_INSPECT'">
          @if (isFinal()) {
            <h3 class="mat-h3">Final decision</h3>
            <div class="qty-row">
              <mat-form-field appearance="outline">
                <mat-label>Approved qty</mat-label>
                <input matInput type="number" min="0" [formControl]="approvedCtrl" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Rejected qty</mat-label>
                <input matInput type="number" min="0" [formControl]="finalRejectedCtrl" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Hold qty</mat-label>
                <input matInput type="number" min="0" [formControl]="holdCtrl" />
              </mat-form-field>
            </div>

            @if ((finalRejectedCtrl.value ?? 0) > 0) {
              <h4 class="mat-h4">Rejection reasons (must add up to the rejected quantity)</h4>
              @for (line of reasonLines; track $index) {
                <div class="qty-row">
                  <mat-form-field appearance="outline">
                    <mat-label>Reason</mat-label>
                    <mat-select [formControl]="line.reason">
                      @for (rr of reasons(); track rr.id) {
                        <mat-option [value]="rr.id">{{ rr.reasonCode }} - {{ rr.name }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Quantity</mat-label>
                    <input matInput type="number" min="0" [formControl]="line.qty" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Remarks</mat-label>
                    <input matInput [formControl]="line.remarks" />
                  </mat-form-field>
                  <button mat-icon-button type="button" (click)="removeReason($index)" aria-label="Remove reason">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              }
              <button mat-stroked-button type="button" (click)="addReason()">
                <mat-icon>add</mat-icon> Add reason
              </button>
            }

            <mat-form-field appearance="outline" class="wide">
              <mat-label>Remarks</mat-label>
              <input matInput [formControl]="remarksCtrl" />
            </mat-form-field>
            @if (finalError(); as err) {
              <p class="error">{{ err }}</p>
            }
            <div class="actions">
              @if (rows.length > 0) {
                <button mat-stroked-button (click)="saveMeasurements()" [disabled]="busy()">
                  <mat-icon>save</mat-icon> Save measurements
                </button>
              }
              <button mat-flat-button color="primary" (click)="completeFinal()" [disabled]="busy() || !!finalError()">
                Complete final inspection
              </button>
            </div>
          } @else if (i.inspectionType !== 'FAI') {
            <h3 class="mat-h3">Results and decision</h3>
            <div class="qty-row">
              <mat-form-field appearance="outline">
                <mat-label>Accepted qty</mat-label>
                <input matInput type="number" min="0" [formControl]="acceptedCtrl" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Rejected qty</mat-label>
                <input matInput type="number" min="0" [formControl]="rejectedCtrl" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="wide">
                <mat-label>Remarks</mat-label>
                <input matInput [formControl]="remarksCtrl" />
              </mat-form-field>
            </div>
            @if (qtyError(); as err) {
              <p class="error">{{ err }}</p>
            }
            <div class="actions">
              @if (rows.length > 0) {
                <button mat-stroked-button (click)="saveResults()" [disabled]="busy() || !!qtyError()">
                  <mat-icon>save</mat-icon> Save results
                </button>
              }
              <button mat-flat-button color="primary" (click)="accept()" [disabled]="busy()">
                <mat-icon>check_circle</mat-icon> Accept
              </button>
              <mat-form-field appearance="outline" class="reject-reason">
                <mat-label>Rejection reason</mat-label>
                <input matInput [formControl]="rejectReasonCtrl" />
              </mat-form-field>
              <button
                mat-stroked-button
                color="warn"
                (click)="reject()"
                [disabled]="busy() || rejectReasonCtrl.invalid"
              >
                <mat-icon>cancel</mat-icon> Reject
              </button>
            </div>
          }
        </ng-container>
      }

      @if (needsDeviation().length > 0) {
        <ng-container *appHasPermission="'QUALITY_APPROVE'">
          <h3 class="mat-h3">Deviation approval</h3>
          <p class="note">
            Mandatory parameters that failed need a Quality Manager deviation approval before units can be approved.
          </p>
          @for (r of needsDeviation(); track r.item.id) {
            <div class="qty-row">
              <span class="dev-name">{{ r.item.seq }}. {{ r.item.parameterName }}</span>
              <mat-form-field appearance="outline" class="wide">
                <mat-label>Deviation note</mat-label>
                <input matInput [formControl]="r.override" />
              </mat-form-field>
              <button mat-stroked-button (click)="approveDeviation(r)" [disabled]="busy() || r.override.invalid">
                Approve deviation
              </button>
            </div>
          }
        </ng-container>
      }

      <h3 class="mat-h3">Effect on stock</h3>
      <div class="effect">
        @if (i.inspectionType === 'INCOMING') {
          @switch (i.status) {
            @case ('APPROVED') {
              <p>
                Batch {{ i.batchNumber ?? '' }} of {{ i.materialCode ?? 'the material' }} was released to available
                stock ({{ i.acceptedQty | number: '1.0-3' }} accepted).
              </p>
            }
            @case ('REJECTED') {
              <p>Batch {{ i.batchNumber ?? '' }} was rejected and stays out of available stock.</p>
            }
            @default {
              <p>Batch {{ i.batchNumber ?? '' }} stays in quarantine until this inspection is decided.</p>
            }
          }
        }
        @if (i.inspectionType === 'IN_PROCESS') {
          <p>In-process inspection results feed the control charts; they do not move stock.</p>
        }
        @if (i.inspectionType === 'FINAL') {
          @if (fgBatch(); as b) {
            <p>
              FG batch <strong>{{ b.batchNumber }}</strong> ({{ b.productCode }}): quality status
              <app-status-badge [status]="b.qualityStatus" /> · approved {{ b.approvedQty ?? 0 | number: '1.0-3' }} ·
              rejected {{ b.rejectedQty ?? 0 | number: '1.0-3' }} · remaining
              {{ b.remainingQty ?? 0 | number: '1.0-3' }}
            </p>
            @if (b.qualityStatus === 'HOLD') {
              <p class="note">Held units stay out of available stock until a Quality Manager approves the hold.</p>
            }
          } @else {
            <p>The FG batch stays in QUALITY_PENDING until the final decision.</p>
          }
        }
      </div>
    } @else if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: [
    `
      .facts {
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: 4px 16px;
        margin-bottom: 16px;
      }
      .facts dt {
        color: #666;
      }
      .facts dd {
        margin: 0;
      }
      .params {
        border-collapse: collapse;
        width: 100%;
        margin-bottom: 16px;
      }
      .params th,
      .params td {
        border-bottom: 1px solid #ddd;
        padding: 6px 8px;
        text-align: left;
        vertical-align: top;
      }
      .params tr.fail {
        background: #fdecea;
      }
      .params tr.pass .outcome {
        color: #2e7d32;
      }
      .params tr.fail .outcome {
        color: #b71c1c;
        font-weight: 600;
      }
      .cell {
        width: 120px;
      }
      .limits {
        font-size: 12px;
        color: #666;
      }
      .req {
        color: #b71c1c;
      }
      .qty-row {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: center;
      }
      .wide {
        min-width: 280px;
      }
      .reject-reason {
        min-width: 240px;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: center;
        margin: 8px 0 16px;
      }
      .error {
        color: #b71c1c;
      }
      .note {
        color: #555;
      }
      .dev-name {
        min-width: 220px;
      }
      .back {
        display: inline-flex;
        align-items: center;
        text-decoration: none;
      }
    `,
  ],
})
export class InspectionDetailComponent implements OnInit {
  private api = inject(InspectionApi);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);
  private session = inject(SessionStore);

  insp = signal<QualityInspectionDto | null>(null);
  fgBatch = signal<InspectionFgBatchDto | null>(null);
  reasons = signal<RejectionReasonDto[]>([]);
  loading = signal(false);
  busy = signal(false);

  rows: ItemRow[] = [];
  reasonLines: ReasonLine[] = [];

  acceptedCtrl = new FormControl<number | null>(0, [Validators.min(0)]);
  rejectedCtrl = new FormControl<number | null>(0, [Validators.min(0)]);
  approvedCtrl = new FormControl<number | null>(0, [Validators.min(0)]);
  finalRejectedCtrl = new FormControl<number | null>(0, [Validators.min(0)]);
  holdCtrl = new FormControl<number | null>(0, [Validators.min(0)]);
  remarksCtrl = new FormControl<string>('', { nonNullable: true });
  rejectReasonCtrl = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });

  private id = 0;

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.get(this.id).subscribe({
      next: (i) => {
        this.apply(i);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  isFinal(): boolean {
    return this.insp()?.inspectionType === 'FINAL';
  }

  editable(): boolean {
    const i = this.insp();
    return (
      !!i &&
      i.inspectionType !== 'FAI' &&
      (i.status === 'PENDING' || i.status === 'IN_PROGRESS') &&
      this.session.hasPermission('QUALITY_INSPECT')
    );
  }

  outcome(r: ItemRow): ItemOutcome {
    const text = r.text.value.trim() === '' ? null : r.text.value;
    return previewResult(r.item, r.value.value, text);
  }

  needsDeviation(): ItemRow[] {
    return this.rows.filter((r) => r.item.mandatory && r.item.result === 'FAIL' && r.item.deviationApprovedBy === null);
  }

  qtyError(): string | null {
    const i = this.insp();
    if (!i) return null;
    const total = (this.acceptedCtrl.value ?? 0) + (this.rejectedCtrl.value ?? 0);
    if (this.acceptedCtrl.invalid || this.rejectedCtrl.invalid) return 'Quantities cannot be negative.';
    return total > i.inspectedQty
      ? `Accepted plus rejected (${total}) exceeds the inspected quantity (${i.inspectedQty}).`
      : null;
  }

  finalError(): string | null {
    const i = this.insp();
    if (!i) return null;
    const approved = this.approvedCtrl.value ?? 0;
    const rejected = this.finalRejectedCtrl.value ?? 0;
    const hold = this.holdCtrl.value ?? 0;
    if (approved < 0 || rejected < 0 || hold < 0) return 'Quantities cannot be negative.';
    const total = approved + rejected + hold;
    if (total > i.inspectedQty) {
      return `Approved plus rejected plus hold (${total}) exceeds the inspected quantity (${i.inspectedQty}).`;
    }
    if (rejected > 0) {
      const reasonTotal = this.reasonLines.reduce((s, l) => s + (l.reason.value !== null ? (l.qty.value ?? 0) : 0), 0);
      if (reasonTotal !== rejected) {
        return `Rejection reasons add up to ${reasonTotal}; they must add up to the rejected quantity (${rejected}).`;
      }
    }
    return null;
  }

  addReason(): void {
    this.reasonLines.push({
      reason: new FormControl<number | null>(null),
      qty: new FormControl<number | null>(null, [Validators.min(0)]),
      remarks: new FormControl<string>('', { nonNullable: true }),
    });
  }

  removeReason(index: number): void {
    this.reasonLines.splice(index, 1);
  }

  saveResults(): void {
    const body = {
      acceptedQty: this.acceptedCtrl.value ?? 0,
      rejectedQty: this.rejectedCtrl.value ?? 0,
      remarks: this.remarksCtrl.value,
      items: this.rows.map((r) => ({
        itemId: r.item.id,
        actualValue: r.value.value,
        actualText: r.text.value.trim() === '' ? null : r.text.value,
        deviationNote: r.note.value.trim() === '' ? null : r.note.value,
      })),
    };
    this.run(this.api.submitResult(this.id, body), 'Results saved');
  }

  saveMeasurements(): void {
    // Stored measurements let a failed mandatory parameter be flagged for deviation approval before the decision.
    const body = {
      acceptedQty: this.approvedCtrl.value ?? 0,
      rejectedQty: this.finalRejectedCtrl.value ?? 0,
      remarks: this.remarksCtrl.value,
      items: this.rows.map((r) => ({
        itemId: r.item.id,
        actualValue: r.value.value,
        actualText: r.text.value.trim() === '' ? null : r.text.value,
        deviationNote: r.note.value.trim() === '' ? null : r.note.value,
      })),
    };
    this.run(this.api.submitResult(this.id, body), 'Measurements saved');
  }

  accept(): void {
    this.run(this.api.accept(this.id), 'Inspection accepted');
  }

  reject(): void {
    this.run(this.api.reject(this.id, this.rejectReasonCtrl.value.trim()), 'Inspection rejected');
  }

  completeFinal(): void {
    const body = {
      approvedQty: this.approvedCtrl.value ?? 0,
      rejectedQty: this.finalRejectedCtrl.value ?? 0,
      holdQty: this.holdCtrl.value ?? 0,
      itemResults: this.rows.map((r) => ({
        seq: r.item.seq,
        actualValue: r.value.value,
        actualText: r.text.value.trim() === '' ? null : r.text.value,
        deviationNote: r.note.value.trim() === '' ? null : r.note.value,
      })),
      rejectionReasons: this.reasonLines
        .filter((l) => l.reason.value !== null && (l.qty.value ?? 0) > 0)
        .map((l) => ({
          rejectionReasonId: l.reason.value as number,
          quantity: l.qty.value as number,
          remarks: l.remarks.value.trim() === '' ? null : l.remarks.value,
        })),
      remarks: this.remarksCtrl.value,
    };
    this.run(this.api.completeFinal(this.id, body), 'Final inspection completed');
  }

  approveDeviation(r: ItemRow): void {
    this.run(this.api.recordDeviation(this.id, r.item.seq, r.override.value.trim()), 'Deviation approved');
  }

  private run(call: ReturnType<InspectionApi['accept']>, message: string): void {
    this.busy.set(true);
    call.subscribe({
      next: (i) => {
        this.busy.set(false);
        this.snack.open(message, 'OK', { duration: 2500 });
        this.apply(i);
      },
      error: (e) => {
        this.busy.set(false);
        this.snack.open(e.error?.detail ?? 'The request failed', 'OK', { duration: 5000 });
      },
    });
  }

  private apply(i: QualityInspectionDto): void {
    this.insp.set(i);
    this.rows = (i.items ?? []).map((item) => ({
      item,
      value: new FormControl<number | null>(item.actualValue),
      text: new FormControl<string>(item.actualText ?? '', { nonNullable: true }),
      note: new FormControl<string>(item.deviationNote ?? '', { nonNullable: true }),
      override: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    }));
    this.acceptedCtrl.setValue(i.acceptedQty ?? i.inspectedQty);
    this.rejectedCtrl.setValue(i.rejectedQty ?? 0);
    this.approvedCtrl.setValue(i.acceptedQty ?? i.inspectedQty);
    this.finalRejectedCtrl.setValue(i.rejectedQty ?? 0);
    this.holdCtrl.setValue(i.holdQty ?? 0);
    this.remarksCtrl.setValue(i.remarks ?? '');
    if (i.inspectionType === 'FINAL') {
      if (this.reasons().length === 0) {
        this.api.rejectionReasons().subscribe({
          next: (list) =>
            this.reasons.set(list.filter((r) => r.active && ['FINAL', 'BOTH', 'ALL'].includes(r.appliesTo ?? 'ALL'))),
          error: () => this.reasons.set([]),
        });
      }
      if (this.reasonLines.length === 0) {
        this.addReason();
      }
      if (i.fgBatchId !== null) {
        this.api.getFgBatch(i.fgBatchId).subscribe({
          next: (b) => this.fgBatch.set(b),
          error: () => this.fgBatch.set(null),
        });
      }
    }
  }
}
