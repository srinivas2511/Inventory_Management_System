import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin } from 'rxjs';
import { problemMessage } from '../../core/models/problem.model';
import { LookupApi, LookupOption, MaterialStock } from '../../shared/lookup.api';
import { ProductionExecutionApi } from './production-execution.api';
import { CreateIssueRequest, IssueDto, ProductionOrderMaterialDto } from './production-order.model';

export interface IssueDialogData {
  orderId: number;
  orderNumber: string;
  materials: ProductionOrderMaterialDto[];
}

interface BatchOption {
  batchId: number;
  label: string;
}

/** Issues raw material from a warehouse to the order's WIP. Leave the batch on automatic to use earliest expiry first. */
@Component({
  selector: 'app-issue-material-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    DecimalPipe,
  ],
  template: `
    <h2 mat-dialog-title>Issue material to {{ data.orderNumber }}</h2>
    <mat-dialog-content>
      @if (result(); as issued) {
        <p class="mat-body-1">Issue {{ issued.issueNumber }} posted. Stock taken from:</p>
        <ul class="issued-lines">
          @for (l of issued.lines; track $index) {
            <li>
              {{ materialLabel(l.materialId) }}: {{ l.quantity | number: '1.0-3' }} from batch {{ l.batchNumber }}
            </li>
          }
        </ul>
      } @else {
        <form [formGroup]="form" id="issue-form" (ngSubmit)="save()" novalidate>
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Issue from warehouse</mat-label>
            <mat-select formControlName="warehouseId" (selectionChange)="warehouseChosen($event.value)">
              @for (w of warehouses(); track w.id) {
                <mat-option [value]="w.id">{{ w.code }} · {{ w.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.warehouseId.hasError('required') && form.controls.warehouseId.touched) {
              <mat-error>Choose the warehouse the material comes from</mat-error>
            }
          </mat-form-field>

          @if (loadingStock()) {
            <mat-spinner diameter="20" />
          }

          <div formArrayName="lines">
            @for (line of lines.controls; track $index; let i = $index) {
              <div class="line" [formGroupName]="i">
                <div class="what">
                  <strong>{{ materialLabel(data.materials[i].materialId) }}</strong>
                  <span class="mat-caption">
                    Planned {{ data.materials[i].plannedQty | number: '1.0-3' }} · issued
                    {{ data.materials[i].issuedQty | number: '1.0-3' }} · still to issue
                    {{ toIssue(i) | number: '1.0-3' }} {{ data.materials[i].uom }}
                  </span>
                  @if (warehouseId()) {
                    <span class="mat-caption" [class.short]="availableFor(i) < toIssue(i)">
                      Available here: {{ availableFor(i) | number: '1.0-3' }} {{ data.materials[i].uom }}
                    </span>
                  }
                </div>
                <mat-form-field appearance="outline">
                  <mat-label>Quantity</mat-label>
                  <input matInput type="number" step="0.001" formControlName="quantity" />
                  @if (line.controls.quantity.invalid) {
                    <mat-error>Enter 0 to skip this material, or at least 0.001</mat-error>
                  }
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Batch</mat-label>
                  <mat-select formControlName="batchId">
                    <mat-option [value]="null">Automatic (earliest expiry first)</mat-option>
                    @for (b of batchOptions(i); track b.batchId) {
                      <mat-option [value]="b.batchId">{{ b.label }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                @if (overIssue(i)) {
                  <p class="over mat-caption">
                    More than is still to be issued. The server accepts this only within its over-issue tolerance.
                  </p>
                }
              </div>
            }
          </div>

          <mat-form-field appearance="outline" class="wide">
            <mat-label>Remarks</mat-label>
            <input matInput formControlName="remarks" />
          </mat-form-field>
          @if (errorMessage()) {
            <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
          }
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (result()) {
        <button mat-flat-button color="primary" (click)="done()">Done</button>
      } @else {
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" form="issue-form" type="submit" [disabled]="saving() || !canSubmit()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          } @else {
            Issue material
          }
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [
    `
      .wide {
        width: 100%;
      }
      .line {
        display: grid;
        grid-template-columns: 1fr 140px 1fr;
        gap: 0 12px;
        align-items: start;
        padding: 4px 0;
      }
      .what {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding-top: 6px;
      }
      .line .over {
        grid-column: 1/-1;
        margin: 0 0 8px;
      }
      .short,
      .over,
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
      @media (max-width: 640px) {
        .line {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class IssueMaterialDialogComponent implements OnInit {
  protected readonly data = inject<IssueDialogData>(MAT_DIALOG_DATA);
  protected readonly warehouses = signal<LookupOption[]>([]);
  protected readonly warehouseId = signal<number | null>(null);
  protected readonly stock = signal<Record<number, MaterialStock[]>>({});
  protected readonly loadingStock = signal(false);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly result = signal<IssueDto | null>(null);

  private readonly fb = inject(FormBuilder);
  private readonly lookups = inject(LookupApi);
  private readonly api = inject(ProductionExecutionApi);
  private readonly dialogRef = inject(MatDialogRef<IssueMaterialDialogComponent>);

  protected readonly form = this.fb.nonNullable.group({
    warehouseId: this.fb.control<number | null>(null, Validators.required),
    remarks: [''],
    lines: this.fb.array(
      this.data.materials.map((m) =>
        this.fb.nonNullable.group({
          materialId: [m.materialId],
          quantity: this.fb.control<number | null>(Math.max(m.remainingQty, 0), [Validators.min(0)]),
          batchId: this.fb.control<number | null>(null),
        }),
      ),
    ),
  });

  protected get lines() {
    return this.form.controls.lines;
  }

  ngOnInit(): void {
    this.lookups.warehouses().subscribe((w) => this.warehouses.set(w));
  }

  protected warehouseChosen(warehouseId: number): void {
    this.warehouseId.set(warehouseId);
    this.stock.set({});
    this.loadingStock.set(true);
    this.errorMessage.set('');
    for (const line of this.lines.controls) {
      line.controls.batchId.setValue(null);
    }
    forkJoin(this.data.materials.map((m) => this.lookups.materialStock(m.materialId, warehouseId))).subscribe({
      next: (all) => {
        const byMaterial: Record<number, MaterialStock[]> = {};
        this.data.materials.forEach((m, i) => (byMaterial[m.materialId] = all[i]));
        this.stock.set(byMaterial);
        this.loadingStock.set(false);
      },
      error: (err) => {
        this.loadingStock.set(false);
        this.errorMessage.set(problemMessage(err, 'The stock in that warehouse could not be loaded.'));
      },
    });
  }

  protected materialLabel(materialId: number): string {
    const m = this.data.materials.find((x) => x.materialId === materialId);
    return m ? [m.materialCode, m.materialName].filter(Boolean).join(' · ') : `Material ${materialId}`;
  }

  protected toIssue(i: number): number {
    return Math.max(this.data.materials[i].remainingQty, 0);
  }

  protected availableFor(i: number): number {
    const rows = this.stock()[this.data.materials[i].materialId] ?? [];
    return rows.reduce((sum, r) => sum + r.available, 0);
  }

  protected batchOptions(i: number): BatchOption[] {
    const m = this.data.materials[i];
    const rows = this.stock()[m.materialId] ?? [];
    const byBatch = new Map<number, { row: MaterialStock; available: number }>();
    for (const r of rows) {
      const existing = byBatch.get(r.batchId);
      if (existing) existing.available += r.available;
      else byBatch.set(r.batchId, { row: r, available: r.available });
    }
    return [...byBatch.values()].map(({ row, available }) => ({
      batchId: row.batchId,
      label:
        `${row.batchNumber} · ${available} ${m.uom} available` + (row.expiryDate ? ` · expires ${row.expiryDate}` : ''),
    }));
  }

  protected overIssue(i: number): boolean {
    const qty = Number(this.lines.at(i).controls.quantity.value ?? 0);
    return qty > this.toIssue(i);
  }

  protected canSubmit(): boolean {
    return (
      this.form.valid &&
      this.lines.controls.some((l) => Number(l.controls.quantity.value ?? 0) > 0) &&
      !this.loadingStock()
    );
  }

  protected save(): void {
    if (!this.canSubmit()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreateIssueRequest = {
      productionOrderId: this.data.orderId,
      warehouseId: raw.warehouseId as number,
      lines: raw.lines
        .filter((l) => Number(l.quantity ?? 0) > 0)
        .map((l) => ({
          materialId: l.materialId,
          quantity: Number(l.quantity),
          explicitBatchId: l.batchId,
        })),
      remarks: raw.remarks || null,
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.issue(request).subscribe({
      next: (issued) => {
        this.saving.set(false);
        this.result.set(issued);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The material could not be issued.'));
      },
    });
  }

  protected done(): void {
    this.dialogRef.close(true);
  }
}
