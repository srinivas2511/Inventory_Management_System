import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { AllocationRunDto, ControllingApi, CostAllocationRuleDto } from './controlling.api';
import { FinanceApi } from '../finance/finance.api';

@Component({
  selector: 'app-overhead-allocation',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    IstDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatTableModule,
    ReactiveFormsModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Overhead Allocation</h2>
    </div>

    <!-- Allocation Rules -->
    <section>
      <h3 class="mat-h3">Allocation Rules</h3>

      <ng-container *appHasPermission="'OVERHEAD_ALLOC_RUN'">
        <form class="inline-form" [formGroup]="ruleForm" (ngSubmit)="addRule()">
          <mat-form-field appearance="outline">
            <mat-label>Cost Centre</mat-label>
            <mat-select formControlName="costCentreId" required>
              @for (cc of costCentres(); track cc.id) {
                <mat-option [value]="cc.id">{{ cc.costCentreCode }} — {{ cc.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Allocation Base</mat-label>
            <mat-select formControlName="allocationBase" required>
              <mat-option value="LABOUR_HOURS">Labour Hours</mat-option>
              <mat-option value="MACHINE_HOURS">Machine Hours</mat-option>
              <mat-option value="PRODUCTION_QTY">Production Quantity</mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="ruleForm.invalid">Add Rule</button>
        </form>
      </ng-container>

      <table mat-table [dataSource]="rules()" class="mat-elevation-z1">
        <ng-container matColumnDef="costCentre">
          <th mat-header-cell *matHeaderCellDef>Cost Centre</th>
          <td mat-cell *matCellDef="let r">{{ r.costCentreCode }} — {{ r.costCentreName }}</td>
        </ng-container>
        <ng-container matColumnDef="base">
          <th mat-header-cell *matHeaderCellDef>Allocation Base</th>
          <td mat-cell *matCellDef="let r">{{ r.allocationBase }}</td>
        </ng-container>
        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>Active</th>
          <td mat-cell *matCellDef="let r">
            <mat-slide-toggle
              *appHasPermission="'OVERHEAD_ALLOC_RUN'"
              [checked]="r.active"
              aria-label="Toggle rule active"
              (change)="toggleRule(r)"
            />
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="ruleCols"></tr>
        <tr mat-row *matRowDef="let row; columns: ruleCols"></tr>
      </table>
    </section>

    <!-- Run Allocation -->
    <section style="margin-top:32px">
      <h3 class="mat-h3">Run Allocation</h3>

      <ng-container *appHasPermission="'OVERHEAD_ALLOC_RUN'">
        <form class="inline-form" [formGroup]="runForm" (ngSubmit)="runAllocation()">
          <mat-form-field appearance="outline">
            <mat-label>Period</mat-label>
            <mat-select formControlName="periodId" required>
              @for (p of periods(); track p.id) {
                <mat-option [value]="p.id">{{ p.fiscalYear }}/{{ p.periodNumber }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <button mat-flat-button color="accent" type="submit" [disabled]="runForm.invalid || running()">
            <mat-icon aria-hidden="true">play_arrow</mat-icon>
            Run Allocation
          </button>
        </form>
      </ng-container>
    </section>

    <!-- Run History -->
    <section style="margin-top:32px">
      <h3 class="mat-h3">Run History</h3>
      <table mat-table [dataSource]="runs()" class="mat-elevation-z1">
        <ng-container matColumnDef="runAt">
          <th mat-header-cell *matHeaderCellDef>Run At</th>
          <td mat-cell *matCellDef="let r">{{ r.runAt | istDate: 'dd/MM/yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="period">
          <th mat-header-cell *matHeaderCellDef>Period</th>
          <td mat-cell *matCellDef="let r">{{ r.periodName }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef style="text-align:right">Total Allocated (₹)</th>
          <td mat-cell *matCellDef="let r" style="text-align:right">
            {{ r.totalAllocated | number: '1.2-2' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">{{ r.status }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button aria-label="View lines" (click)="viewLines(r)">
              <mat-icon aria-hidden="true">expand_more</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="runCols"></tr>
        <tr mat-row *matRowDef="let row; columns: runCols"></tr>
      </table>
    </section>

    <!-- Lines detail -->
    @if (selectedRun(); as run) {
      <section style="margin-top:24px">
        <h4 class="mat-h4">Lines — {{ run.periodName }}</h4>
        @if (run.lines.length === 0) {
          <p class="mat-body-2">No allocation lines — no overhead or no production activity.</p>
        } @else {
          <table mat-table [dataSource]="run.lines" class="mat-elevation-z1">
            <ng-container matColumnDef="cc">
              <th mat-header-cell *matHeaderCellDef>Cost Centre</th>
              <td mat-cell *matCellDef="let l">{{ l.costCentreCode }}</td>
            </ng-container>
            <ng-container matColumnDef="product">
              <th mat-header-cell *matHeaderCellDef>Product</th>
              <td mat-cell *matCellDef="let l">{{ l.productCode }} {{ l.productName }}</td>
            </ng-container>
            <ng-container matColumnDef="base">
              <th mat-header-cell *matHeaderCellDef>Base</th>
              <td mat-cell *matCellDef="let l">{{ l.allocationBase }}</td>
            </ng-container>
            <ng-container matColumnDef="baseQty">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Base Qty</th>
              <td mat-cell *matCellDef="let l" style="text-align:right">
                {{ l.baseQty | number: '1.3-3' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="rate">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Rate (₹)</th>
              <td mat-cell *matCellDef="let l" style="text-align:right">
                {{ l.ratePerUnit | number: '1.4-4' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Allocated (₹)</th>
              <td mat-cell *matCellDef="let l" style="text-align:right">
                {{ l.allocatedAmount | number: '1.2-2' }}
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="lineCols"></tr>
            <tr mat-row *matRowDef="let row; columns: lineCols"></tr>
          </table>
        }
      </section>
    }
  `,
})
export class OverheadAllocationComponent implements OnInit {
  private api = inject(ControllingApi);
  private financeApi = inject(FinanceApi);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  ruleCols = ['costCentre', 'base', 'active'];
  runCols = ['runAt', 'period', 'total', 'status', 'actions'];
  lineCols = ['cc', 'product', 'base', 'baseQty', 'rate', 'amount'];

  rules = signal<CostAllocationRuleDto[]>([]);
  runs = signal<AllocationRunDto[]>([]);
  selectedRun = signal<AllocationRunDto | null>(null);
  costCentres = signal<{ id: number; costCentreCode: string; name: string }[]>([]);
  periods = signal<{ id: number; fiscalYear: number; periodNumber: number }[]>([]);
  running = signal(false);

  ruleForm = this.fb.group({
    costCentreId: [null as number | null, Validators.required],
    allocationBase: ['LABOUR_HOURS', Validators.required],
  });

  runForm = this.fb.group({
    periodId: [null as number | null, Validators.required],
  });

  ngOnInit(): void {
    this.loadRules();
    this.loadRuns();
    this.api.getCostCentres().subscribe({ next: (cc) => this.costCentres.set(cc) });
    this.financeApi.getPeriods().subscribe({
      next: (ps) =>
        this.periods.set(
          ps
            .filter((p) => p.status === 'OPEN')
            .map((p) => ({ id: p.id, fiscalYear: p.fiscalYear, periodNumber: p.periodNumber })),
        ),
    });
  }

  private loadRules(): void {
    this.api.getAllocationRules().subscribe({
      next: (r) => this.rules.set(r),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load rules'), 'Close', { duration: 4000 }),
    });
  }

  private loadRuns(): void {
    this.api.getAllocationRuns().subscribe({
      next: (r) => this.runs.set(r),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load runs'), 'Close', { duration: 4000 }),
    });
  }

  addRule(): void {
    if (this.ruleForm.invalid) return;
    const { costCentreId, allocationBase } = this.ruleForm.value;
    this.api.createAllocationRule({ costCentreId: costCentreId!, allocationBase: allocationBase! }).subscribe({
      next: () => {
        this.ruleForm.reset({ allocationBase: 'LABOUR_HOURS' });
        this.loadRules();
      },
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to add rule'), 'Close', { duration: 4000 }),
    });
  }

  toggleRule(rule: CostAllocationRuleDto): void {
    this.api.setRuleActive(rule.id, !rule.active).subscribe({
      next: () => this.loadRules(),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to update rule'), 'Close', { duration: 4000 }),
    });
  }

  runAllocation(): void {
    if (this.runForm.invalid) return;
    const periodId = this.runForm.value.periodId!;
    this.running.set(true);
    this.api.runAllocation(periodId).subscribe({
      next: (run) => {
        this.running.set(false);
        this.snackBar.open(`Allocation complete — ₹${run.totalAllocated.toFixed(2)} allocated`, 'Close', {
          duration: 4000,
        });
        this.loadRuns();
      },
      error: (err) => {
        this.running.set(false);
        this.snackBar.open(problemMessage(err, 'Allocation failed'), 'Close', { duration: 4000 });
      },
    });
  }

  viewLines(run: AllocationRunDto): void {
    if (this.selectedRun()?.id === run.id) {
      this.selectedRun.set(null);
      return;
    }
    this.api.getAllocationRun(run.id).subscribe({
      next: (r) => this.selectedRun.set(r),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load lines'), 'Close', { duration: 4000 }),
    });
  }
}
