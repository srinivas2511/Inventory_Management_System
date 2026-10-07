import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

export type AttributeDataType = 'NUMBER' | 'TEXT' | 'ENUM' | 'BOOLEAN';

export interface AttributeDefinitionAdminDto {
  id: number;
  productType: string;
  attributeCode: string;
  label: string;
  dataType: AttributeDataType;
  unit: string | null;
  required: boolean;
  minValue: number | null;
  maxValue: number | null;
  enumValues: string[];
  displayOrder: number;
  active: boolean;
}

export interface ProductTypeOption {
  code: string;
  name: string;
}

@Component({
  selector: 'app-attribute-definitions',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Attribute Definitions</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" data-testid="add-attribute">
        <mat-icon>add</mat-icon> Add attribute
      </button>
    </div>
    <p class="mat-body-2">
      The attributes a product type carries on its product form. Changes apply immediately and are audited; no migration
      is needed. An attribute's product type and code never change after it is created.
    </p>

    <mat-form-field appearance="outline">
      <mat-label>Product type</mat-label>
      <mat-select [value]="filterType()" (selectionChange)="setFilter($event.value)" data-testid="type-filter">
        <mat-option [value]="''">All types</mat-option>
        @for (t of productTypes(); track t.code) {
          <mat-option [value]="t.code">{{ t.name }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    @if (formOpen()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="attr-form mat-elevation-z1" data-testid="attribute-form">
        <h3 class="mat-h3">{{ editingId() === null ? 'New attribute' : 'Edit attribute' }}</h3>
        <mat-form-field appearance="outline">
          <mat-label>Product type</mat-label>
          <mat-select formControlName="productType" data-testid="form-type">
            @for (t of productTypes(); track t.code) {
              <mat-option [value]="t.code">{{ t.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Code (lower snake case)</mat-label>
          <input matInput formControlName="attributeCode" data-testid="form-code" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Label</mat-label>
          <input matInput formControlName="label" data-testid="form-label" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Data type</mat-label>
          <mat-select formControlName="dataType" data-testid="form-datatype">
            @for (d of dataTypes; track d) {
              <mat-option [value]="d">{{ d }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Unit</mat-label>
          <input matInput formControlName="unit" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Display order</mat-label>
          <input matInput type="number" formControlName="displayOrder" />
        </mat-form-field>
        @if (form.controls.dataType.value === 'NUMBER') {
          <mat-form-field appearance="outline">
            <mat-label>Minimum</mat-label>
            <input matInput type="number" formControlName="minValue" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Maximum</mat-label>
            <input matInput type="number" formControlName="maxValue" />
          </mat-form-field>
        }
        @if (form.controls.dataType.value === 'ENUM') {
          <mat-form-field appearance="outline">
            <mat-label>Allowed values (comma separated)</mat-label>
            <input matInput formControlName="enumValues" data-testid="form-enum" />
          </mat-form-field>
        }
        <mat-checkbox formControlName="required">Required</mat-checkbox>
        <div class="actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">Save</button>
          <button mat-button type="button" (click)="cancel()">Cancel</button>
        </div>
      </form>
    }

    @if (loading()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="productType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.productType }}</td>
        </ng-container>
        <ng-container matColumnDef="attributeCode">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">
            <code>{{ r.attributeCode }}</code>
          </td>
        </ng-container>
        <ng-container matColumnDef="label">
          <th mat-header-cell *matHeaderCellDef>Label</th>
          <td mat-cell *matCellDef="let r">{{ r.label }}{{ r.unit ? ' (' + r.unit + ')' : '' }}</td>
        </ng-container>
        <ng-container matColumnDef="dataType">
          <th mat-header-cell *matHeaderCellDef>Data type</th>
          <td mat-cell *matCellDef="let r">
            {{ r.dataType }}{{ r.enumValues.length ? ': ' + r.enumValues.join(', ') : '' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="required">
          <th mat-header-cell *matHeaderCellDef>Required</th>
          <td mat-cell *matCellDef="let r">{{ r.required ? 'Yes' : 'No' }}</td>
        </ng-container>
        <ng-container matColumnDef="displayOrder">
          <th mat-header-cell *matHeaderCellDef>Order</th>
          <td mat-cell *matCellDef="let r">{{ r.displayOrder }}</td>
        </ng-container>
        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">{{ r.active ? 'Active' : 'Inactive' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button
              mat-icon-button
              matTooltip="Edit"
              aria-label="Edit"
              (click)="startEdit(r)"
              data-testid="edit-attribute"
            >
              <mat-icon>edit</mat-icon>
            </button>
            @if (r.active) {
              <button
                mat-icon-button
                matTooltip="Deactivate"
                aria-label="Deactivate"
                (click)="toggle(r)"
                data-testid="deactivate-attribute"
              >
                <mat-icon>visibility_off</mat-icon>
              </button>
            } @else {
              <button
                mat-icon-button
                matTooltip="Activate"
                aria-label="Activate"
                (click)="toggle(r)"
                data-testid="activate-attribute"
              >
                <mat-icon>visibility</mat-icon>
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" [class.inactive-row]="!row.active"></tr>
      </table>
      @if (rows().length === 0) {
        <p class="mat-body-2">No attribute definitions for this selection.</p>
      }
    }
  `,
  styles: [
    `
      .attr-form {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: 8px 16px;
        padding: 16px;
        margin-bottom: 16px;
        align-items: center;
      }
      .attr-form h3,
      .attr-form .actions {
        grid-column: 1 / -1;
      }
      .inactive-row {
        opacity: 0.55;
      }
    `,
  ],
})
export class AttributeDefinitionsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  protected readonly dataTypes: AttributeDataType[] = ['NUMBER', 'TEXT', 'ENUM', 'BOOLEAN'];
  protected readonly columns = [
    'productType',
    'attributeCode',
    'label',
    'dataType',
    'required',
    'displayOrder',
    'active',
    'actions',
  ];

  protected readonly rows = signal<AttributeDefinitionAdminDto[]>([]);
  protected readonly productTypes = signal<ProductTypeOption[]>([]);
  protected readonly filterType = signal('');
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    productType: ['', Validators.required],
    attributeCode: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9_]{1,39}$/)]],
    label: ['', [Validators.required, Validators.maxLength(80)]],
    dataType: ['TEXT' as AttributeDataType, Validators.required],
    unit: [''],
    required: [false],
    minValue: [null as number | null],
    maxValue: [null as number | null],
    enumValues: [''],
    displayOrder: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.http.get<ProductTypeOption[]>('/api/product-types').subscribe((types) => this.productTypes.set(types));
    this.load();
  }

  protected setFilter(type: string): void {
    this.filterType.set(type);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    let params = new HttpParams();
    if (this.filterType()) params = params.set('productType', this.filterType());
    this.http.get<AttributeDefinitionAdminDto[]>('/api/admin/attribute-definitions', { params }).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load attribute definitions', 'Close', { duration: 4000 });
      },
    });
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.form.reset({
      productType: this.filterType(),
      attributeCode: '',
      label: '',
      dataType: 'TEXT',
      unit: '',
      required: false,
      minValue: null,
      maxValue: null,
      enumValues: '',
      displayOrder: 0,
    });
    this.form.controls.productType.enable();
    this.form.controls.attributeCode.enable();
    this.formOpen.set(true);
  }

  protected startEdit(d: AttributeDefinitionAdminDto): void {
    this.editingId.set(d.id);
    this.form.reset({
      productType: d.productType,
      attributeCode: d.attributeCode,
      label: d.label,
      dataType: d.dataType,
      unit: d.unit ?? '',
      required: d.required,
      minValue: d.minValue,
      maxValue: d.maxValue,
      enumValues: d.enumValues.join(', '),
      displayOrder: d.displayOrder,
    });
    this.form.controls.productType.disable();
    this.form.controls.attributeCode.disable();
    this.formOpen.set(true);
  }

  protected cancel(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  protected save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const enumValues =
      v.dataType === 'ENUM'
        ? v.enumValues
            .split(',')
            .map((s) => s.trim())
            .filter((s) => s.length > 0)
        : [];
    const body = {
      productType: v.productType,
      attributeCode: v.attributeCode,
      label: v.label.trim(),
      dataType: v.dataType,
      unit: v.unit.trim() || null,
      required: v.required,
      minValue: v.dataType === 'NUMBER' ? v.minValue : null,
      maxValue: v.dataType === 'NUMBER' ? v.maxValue : null,
      enumValues,
      displayOrder: v.displayOrder,
    };
    const id = this.editingId();
    this.saving.set(true);
    const request =
      id === null
        ? this.http.post<AttributeDefinitionAdminDto>('/api/admin/attribute-definitions', body)
        : this.http.put<AttributeDefinitionAdminDto>(`/api/admin/attribute-definitions/${id}`, body);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.editingId.set(null);
        this.snack.open('Attribute definition saved', 'Close', { duration: 3000 });
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.snack.open(e.error?.detail ?? 'Could not save the attribute definition', 'Close', { duration: 5000 });
      },
    });
  }

  protected toggle(d: AttributeDefinitionAdminDto): void {
    const action = d.active ? 'deactivate' : 'activate';
    this.http.post<AttributeDefinitionAdminDto>(`/api/admin/attribute-definitions/${d.id}/${action}`, {}).subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) =>
        this.snack.open(e.error?.detail ?? `Could not ${action} the attribute`, 'Close', { duration: 5000 }),
    });
  }
}
