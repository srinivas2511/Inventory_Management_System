import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpClient } from '@angular/common/http';
import { ProductionOrderTemplateDto, SaveTemplateRequest } from './production-order.model';

@Component({
  selector: 'app-production-order-templates',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title i18n="@@prodTemplates.title">Production Order Templates</mat-card-title>
        <mat-card-subtitle i18n="@@prodTemplates.subtitle">Reusable order blueprints</mat-card-subtitle>
        <span class="spacer"></span>
        <button
          mat-raised-button
          color="primary"
          (click)="openForm()"
          aria-label="Create template"
          i18n="@@prodTemplates.create"
        >
          <mat-icon aria-hidden="true">add</mat-icon> New Template
        </button>
      </mat-card-header>
      <mat-card-content>
        @if (loading) {
          <div class="center"><mat-spinner diameter="40"></mat-spinner></div>
        } @else if (templates.length === 0) {
          <p class="empty" i18n="@@prodTemplates.empty">No templates defined yet.</p>
        } @else {
          <table mat-table [dataSource]="templates" class="full-width">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef i18n="@@prodTemplates.col.code">Code</th>
              <td mat-cell *matCellDef="let t">{{ t.code }}</td>
            </ng-container>
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef i18n="@@prodTemplates.col.name">Name</th>
              <td mat-cell *matCellDef="let t">{{ t.name }}</td>
            </ng-container>
            <ng-container matColumnDef="plannedQty">
              <th mat-header-cell *matHeaderCellDef i18n="@@prodTemplates.col.plannedQty">Planned Qty</th>
              <td mat-cell *matCellDef="let t">{{ t.plannedQty | number: '1.0-3' }}</td>
            </ng-container>
            <ng-container matColumnDef="leadTime">
              <th mat-header-cell *matHeaderCellDef i18n="@@prodTemplates.col.leadTime">Lead Time (days)</th>
              <td mat-cell *matCellDef="let t">{{ t.leadTimeDays }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let t">
                <button mat-icon-button (click)="openForm(t)" aria-label="Edit template" matTooltip="Edit">
                  <mat-icon aria-hidden="true">edit</mat-icon>
                </button>
                <button
                  mat-icon-button
                  color="warn"
                  (click)="deactivate(t)"
                  aria-label="Deactivate template"
                  matTooltip="Deactivate"
                >
                  <mat-icon aria-hidden="true">delete</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        }
      </mat-card-content>
    </mat-card>

    @if (formOpen) {
      <mat-card class="form-card">
        <mat-card-header>
          <mat-card-title>{{ editing ? 'Edit Template' : 'New Template' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()">
            <mat-form-field appearance="outline" class="field">
              <mat-label i18n="@@prodTemplates.field.code">Code</mat-label>
              <input matInput formControlName="code" maxlength="40" />
              @if (form.get('code')?.hasError('required') && form.get('code')?.touched) {
                <mat-error i18n="@@prodTemplates.err.code">Code is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="field">
              <mat-label i18n="@@prodTemplates.field.name">Name</mat-label>
              <input matInput formControlName="name" maxlength="120" />
              @if (form.get('name')?.hasError('required') && form.get('name')?.touched) {
                <mat-error i18n="@@prodTemplates.err.name">Name is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="field">
              <mat-label i18n="@@prodTemplates.field.productId">Product ID</mat-label>
              <input matInput type="number" formControlName="productId" />
              @if (form.get('productId')?.hasError('required') && form.get('productId')?.touched) {
                <mat-error i18n="@@prodTemplates.err.productId">Product ID is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="field">
              <mat-label i18n="@@prodTemplates.field.plannedQty">Planned Qty</mat-label>
              <input matInput type="number" formControlName="plannedQty" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="field">
              <mat-label i18n="@@prodTemplates.field.leadTimeDays">Lead Time (days)</mat-label>
              <input matInput type="number" formControlName="leadTimeDays" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="field full">
              <mat-label i18n="@@prodTemplates.field.notes">Notes</mat-label>
              <textarea matInput formControlName="notes" rows="3" maxlength="500"></textarea>
            </mat-form-field>
            <div class="actions">
              <button mat-button type="button" (click)="closeForm()" i18n="@@common.cancel">Cancel</button>
              <button
                mat-raised-button
                color="primary"
                type="submit"
                [disabled]="form.invalid || saving"
                i18n="@@common.save"
              >
                Save
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: [
    `
      .center {
        display: flex;
        justify-content: center;
        padding: 2rem;
      }
      .empty {
        color: var(--mat-sys-on-surface-variant);
        padding: 1rem 0;
      }
      .full-width {
        width: 100%;
      }
      mat-card-header {
        display: flex;
        align-items: center;
      }
      .spacer {
        flex: 1;
      }
      .form-card {
        margin-top: 1rem;
      }
      .field {
        width: 260px;
        margin-right: 1rem;
        margin-bottom: 0.5rem;
      }
      .field.full {
        width: 100%;
        margin-right: 0;
      }
      .actions {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
        margin-top: 0.5rem;
      }
    `,
  ],
})
export class ProductionOrderTemplatesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  templates: ProductionOrderTemplateDto[] = [];
  loading = true;
  formOpen = false;
  editing: ProductionOrderTemplateDto | null = null;
  saving = false;
  readonly columns = ['code', 'name', 'plannedQty', 'leadTime', 'actions'];

  form = this.fb.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    productId: [null as number | null, Validators.required],
    bomId: [null as number | null],
    plannedQty: [null as number | null],
    leadTimeDays: [null as number | null],
    notes: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.http.get<ProductionOrderTemplateDto[]>('/api/production/templates').subscribe({
      next: (ts) => {
        this.templates = ts;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  openForm(t?: ProductionOrderTemplateDto): void {
    this.editing = t ?? null;
    this.form.reset({
      code: t?.code ?? '',
      name: t?.name ?? '',
      productId: t?.productId ?? null,
      bomId: t?.bomId ?? null,
      plannedQty: t?.plannedQty ?? null,
      leadTimeDays: t?.leadTimeDays ?? null,
      notes: t?.notes ?? '',
    });
    this.formOpen = true;
  }

  closeForm(): void {
    this.formOpen = false;
    this.editing = null;
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    const v = this.form.getRawValue();
    const req: SaveTemplateRequest = {
      code: v.code!,
      name: v.name!,
      productId: v.productId!,
      bomId: v.bomId ?? null,
      plannedQty: v.plannedQty ?? null,
      leadTimeDays: v.leadTimeDays ?? null,
      notes: v.notes ?? null,
    };
    const call = this.editing
      ? this.http.put<ProductionOrderTemplateDto>(`/api/production/templates/${this.editing.id}`, req)
      : this.http.post<ProductionOrderTemplateDto>('/api/production/templates', req);
    call.subscribe({
      next: () => {
        this.saving = false;
        this.closeForm();
        this.load();
      },
      error: () => {
        this.saving = false;
      },
    });
  }

  deactivate(t: ProductionOrderTemplateDto): void {
    this.http.delete(`/api/production/templates/${t.id}`).subscribe({
      next: () => {
        this.load();
        this.snack.open('Template deactivated', 'OK', { duration: 3000 });
      },
    });
  }
}
