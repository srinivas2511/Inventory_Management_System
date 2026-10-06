import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { LookupApi, LookupItem } from '../../shared/lookup.api';

/** Mirrors CreateBomRequest and BomItemRequest on the backend. */
export interface CreateBomRequest {
  productId: number;
  baseQuantity: number;
  changeReason: string | null;
  items: {
    lineNo: number;
    itemKind: 'MATERIAL';
    materialId: number;
    operationId: null;
    requiredQty: number;
    uom: string;
    scrapPct: number;
    instruction: string | null;
  }[];
}

@Component({
  selector: 'app-bom-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>New bill of materials</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="bom-form" (ngSubmit)="save()" novalidate>
        <div class="head-grid">
          <mat-form-field appearance="outline">
            <mat-label>Product</mat-label>
            <mat-select formControlName="productId">
              @for (p of products(); track p.id) {
                <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.productId.hasError('required') && form.controls.productId.touched) {
              <mat-error>Choose a product</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Base quantity (pieces)</mat-label>
            <input matInput type="number" step="0.001" formControlName="baseQuantity" />
            @if (form.controls.baseQuantity.invalid && form.controls.baseQuantity.touched) {
              <mat-error>Must be above 0</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-span">
            <mat-label>Reason for this version</mat-label>
            <input matInput formControlName="changeReason" />
          </mat-form-field>
        </div>

        <h3 class="mat-h3">Materials per base quantity</h3>
        <div formArrayName="items">
          @for (line of lines.controls; track $index; let i = $index) {
            <div class="line" [formGroupName]="i">
              <mat-form-field appearance="outline">
                <mat-label>Material</mat-label>
                <mat-select formControlName="materialId" (selectionChange)="materialChosen(i, $event.value)">
                  @for (m of materials(); track m.id) {
                    <mat-option [value]="m.id">{{ m.code }} · {{ m.name }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Quantity</mat-label>
                <input matInput type="number" step="0.0001" formControlName="requiredQty" />
                @if (line.controls['requiredQty'].hasError('min')) {
                  <mat-error>Must be above 0</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Unit</mat-label>
                <input matInput formControlName="uom" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Scrap %</mat-label>
                <input matInput type="number" step="0.01" formControlName="scrapPct" />
                @if (line.controls['scrapPct'].invalid) {
                  <mat-error>0 to 100</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Instruction</mat-label>
                <input matInput formControlName="instruction" />
              </mat-form-field>
              <button
                mat-icon-button
                type="button"
                aria-label="Remove line"
                (click)="removeLine(i)"
                [disabled]="lines.length === 1"
              >
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
        </div>
        <button mat-button type="button" (click)="addLine()"><mat-icon>add</mat-icon> Add material</button>
        @if (errorMessage()) {
          <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="bom-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Create BOM
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .head-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding-top: 8px;
      }
      .full-span {
        grid-column: 1/-1;
      }
      .line {
        display: grid;
        grid-template-columns: 2fr 1fr 0.8fr 0.8fr 1.5fr auto;
        gap: 0 8px;
        align-items: start;
      }
      mat-form-field {
        width: 100%;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class BomFormComponent implements OnInit {
  protected readonly products = signal<LookupItem[]>([]);
  protected readonly materials = signal<LookupItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<BomFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    productId: this.fb.control<number | null>(null, Validators.required),
    baseQuantity: this.fb.control<number | null>(1, [Validators.required, Validators.min(0.001)]),
    changeReason: [''],
    items: this.fb.array([this.newLine()] as FormGroup[]),
  });

  get lines(): FormArray<FormGroup> {
    return this.form.controls.items;
  }

  ngOnInit(): void {
    this.lookups.products().subscribe((p) => this.products.set(p));
    this.lookups.materials().subscribe((m) => this.materials.set(m));
  }

  addLine(): void {
    this.lines.push(this.newLine());
  }

  removeLine(index: number): void {
    if (this.lines.length > 1) {
      this.lines.removeAt(index);
    }
  }

  materialChosen(index: number, materialId: number): void {
    const material = this.materials().find((m) => m.id === materialId);
    if (material) {
      this.lines.at(index).patchValue({ uom: material.uom });
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreateBomRequest = {
      productId: raw.productId as number,
      baseQuantity: Number(raw.baseQuantity),
      changeReason: raw.changeReason || null,
      items: this.lines.controls.map((line, i) => {
        const v = line.value as Record<string, unknown>;
        return {
          lineNo: (i + 1) * 10,
          itemKind: 'MATERIAL' as const,
          materialId: v['materialId'] as number,
          operationId: null,
          requiredQty: Number(v['requiredQty']),
          uom: v['uom'] as string,
          scrapPct: Number(v['scrapPct'] ?? 0),
          instruction: (v['instruction'] as string) || null,
        };
      }),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.http.post('/api/boms', request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The BOM could not be created.'));
      },
    });
  }

  private newLine(): FormGroup {
    return this.fb.group({
      materialId: this.fb.control<number | null>(null, Validators.required),
      requiredQty: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.0001)]),
      uom: ['', Validators.required],
      scrapPct: this.fb.control<number | null>(0, [Validators.min(0), Validators.max(100)]),
      instruction: [''],
    });
  }
}
