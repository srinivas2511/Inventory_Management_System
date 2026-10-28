import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface ApprovalRuleStepDto {
  stepNo: number;
  stepGroup: number;
  approverType: 'ROLE' | 'USER' | 'TEAM';
  approverRef: string;
  requiredPermission: string | null;
}

export interface ApprovalRuleDto {
  id: number;
  documentType: string;
  ruleName: string;
  conditionExpr: string | null;
  active: boolean;
  priority: number;
  steps: ApprovalRuleStepDto[];
}

@Component({
  selector: 'app-approval-rules',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@approval-rules.title">Approval Rules</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" [disabled]="creating()">
        <mat-icon aria-hidden="true">add</mat-icon> Add Rule
      </button>
    </div>

    <mat-form-field class="filter-field">
      <mat-label>Filter by Document Type</mat-label>
      <mat-select [value]="docTypeFilter()" (selectionChange)="docTypeFilter.set($event.value); load()">
        <mat-option value="">All</mat-option>
        <mat-option value="PURCHASE_ORDER">Purchase Order</mat-option>
        <mat-option value="SALES_ORDER">Sales Order</mat-option>
        <mat-option value="JOURNAL">Journal</mat-option>
        <mat-option value="GOODS_RECEIPT">Goods Receipt</mat-option>
      </mat-select>
    </mat-form-field>

    @if (creating()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="inline-form mat-elevation-z2">
        <mat-form-field>
          <mat-label>Document Type</mat-label>
          <input matInput formControlName="documentType" placeholder="PURCHASE_ORDER" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Rule Name</mat-label>
          <input matInput formControlName="ruleName" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Priority</mat-label>
          <input matInput type="number" formControlName="priority" />
        </mat-form-field>
        <div class="form-actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Save (No Steps)</button>
          <button mat-button type="button" (click)="creating.set(false)">Cancel</button>
        </div>
      </form>
    }

    @if (loading()) {
      <mat-spinner diameter="32"></mat-spinner>
    } @else {
      <mat-accordion>
        @for (rule of rules(); track rule.id) {
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title>{{ rule.documentType }} — {{ rule.ruleName }}</mat-panel-title>
              <mat-panel-description>
                Priority {{ rule.priority }} · {{ rule.steps.length }} step(s)
                @if (!rule.active) {
                  <mat-chip color="warn">Inactive</mat-chip>
                }
              </mat-panel-description>
            </mat-expansion-panel-header>
            <table mat-table [dataSource]="rule.steps" class="steps-table">
              <ng-container matColumnDef="stepNo">
                <th mat-header-cell *matHeaderCellDef>#</th>
                <td mat-cell *matCellDef="let s">{{ s.stepNo }}</td>
              </ng-container>
              <ng-container matColumnDef="approverType">
                <th mat-header-cell *matHeaderCellDef>Approver Type</th>
                <td mat-cell *matCellDef="let s">{{ s.approverType }}</td>
              </ng-container>
              <ng-container matColumnDef="approverRef">
                <th mat-header-cell *matHeaderCellDef>Approver</th>
                <td mat-cell *matCellDef="let s">{{ s.approverRef }}</td>
              </ng-container>
              <ng-container matColumnDef="requiredPermission">
                <th mat-header-cell *matHeaderCellDef>Permission</th>
                <td mat-cell *matCellDef="let s">{{ s.requiredPermission ?? '—' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="stepCols"></tr>
              <tr mat-row *matRowDef="let s; columns: stepCols"></tr>
            </table>
            <mat-action-row>
              <button
                mat-button
                color="warn"
                (click)="deactivate(rule)"
                aria-label="Deactivate rule"
                [disabled]="!rule.active"
              >
                Deactivate
              </button>
            </mat-action-row>
          </mat-expansion-panel>
        }
      </mat-accordion>
    }
  `,
  styles: [
    `
      .page-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;
      }
      .filter-field {
        margin-bottom: 16px;
        min-width: 220px;
      }
      .inline-form {
        padding: 16px;
        margin-bottom: 16px;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
      }
      .form-actions {
        display: flex;
        gap: 8px;
        align-items: center;
        padding-top: 8px;
      }
      .steps-table {
        width: 100%;
      }
    `,
  ],
})
export class ApprovalRulesComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  rules = signal<ApprovalRuleDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  docTypeFilter = signal('');
  stepCols = ['stepNo', 'approverType', 'approverRef', 'requiredPermission'];

  form = this.fb.group({
    documentType: ['', [Validators.required, Validators.maxLength(40)]],
    ruleName: ['', [Validators.required, Validators.maxLength(100)]],
    conditionExpr: [''],
    priority: [0],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    let params = new HttpParams();
    if (this.docTypeFilter()) params = params.set('documentType', this.docTypeFilter());
    this.http.get<ApprovalRuleDto[]>('/api/admin/approval-rules', { params }).subscribe({
      next: (data) => {
        this.rules.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load rules', 'Close', { duration: 3000 });
      },
    });
  }

  startCreate() {
    this.form.reset({ priority: 0 });
    this.creating.set(true);
  }

  save() {
    if (this.form.invalid) return;
    const body = { ...this.form.value, steps: [] };
    this.http.post<ApprovalRuleDto>('/api/admin/approval-rules', body).subscribe({
      next: (created) => {
        this.rules.update((arr) => [...arr, created]);
        this.creating.set(false);
        this.snack.open('Rule created', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to create rule', 'Close', { duration: 3000 }),
    });
  }

  deactivate(rule: ApprovalRuleDto) {
    this.http.delete(`/api/admin/approval-rules/${rule.id}`).subscribe({
      next: () => {
        this.rules.update((arr) => arr.map((r) => (r.id === rule.id ? { ...r, active: false } : r)));
        this.snack.open('Rule deactivated', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to deactivate', 'Close', { duration: 3000 }),
    });
  }
}
