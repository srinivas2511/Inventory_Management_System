import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ControllingApi, CostCentreDto, ProfitCentreDto } from './controlling.api';

@Component({
  selector: 'app-cost-centre-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Cost Centres</h2>
      <button *appHasPermission="'CONTROLLING_MANAGE'" mat-flat-button color="primary" (click)="newCostCentre()">
        <mat-icon>add</mat-icon> New cost centre
      </button>
    </div>

    @if (editingCostCentre()) {
      <form class="edit-form" [formGroup]="ccForm" (ngSubmit)="saveCostCentre()">
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input matInput formControlName="costCentreCode" maxlength="20" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="100" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Parent ID</mat-label>
          <input matInput type="number" formControlName="parentId" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Labour rate / hour</mat-label>
          <input matInput type="number" min="0" formControlName="labourRatePerHour" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Machine rate / hour</mat-label>
          <input matInput type="number" min="0" formControlName="machineRatePerHour" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Overhead %</mat-label>
          <input matInput type="number" min="0" formControlName="overheadPct" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="ccForm.invalid">Save</button>
        <button mat-button type="button" (click)="editingCostCentre.set(false)">Cancel</button>
      </form>
    }

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="costCentres()" class="mat-elevation-z1">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">{{ r.costCentreCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.name }}</td>
        </ng-container>
        <ng-container matColumnDef="labour">
          <th mat-header-cell *matHeaderCellDef>Labour / h</th>
          <td mat-cell *matCellDef="let r">{{ r.labourRatePerHour | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="machine">
          <th mat-header-cell *matHeaderCellDef>Machine / h</th>
          <td mat-cell *matCellDef="let r">{{ r.machineRatePerHour | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="overhead">
          <th mat-header-cell *matHeaderCellDef>Overhead %</th>
          <td mat-cell *matCellDef="let r">{{ r.overheadPct | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.active ? 'ACTIVE' : 'INACTIVE'" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <ng-container *appHasPermission="'CONTROLLING_MANAGE'">
              <button mat-stroked-button (click)="editCostCentre(r)">Edit</button>
              <button mat-stroked-button (click)="toggleCostCentre(r)">
                {{ r.active ? 'Deactivate' : 'Activate' }}
              </button>
            </ng-container>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="ccColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: ccColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="ccColumns.length" style="text-align:center;padding:2rem">
            No cost centres found
          </td>
        </tr>
      </table>

      <div class="page-header section-gap">
        <h2 class="mat-h2">Profit Centres</h2>
        <button *appHasPermission="'CONTROLLING_MANAGE'" mat-flat-button color="primary" (click)="newProfitCentre()">
          <mat-icon>add</mat-icon> New profit centre
        </button>
      </div>

      @if (editingProfitCentre()) {
        <form class="edit-form" [formGroup]="pcForm" (ngSubmit)="saveProfitCentre()">
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="profitCentreCode" maxlength="20" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name" maxlength="100" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="pcForm.invalid">Save</button>
          <button mat-button type="button" (click)="editingProfitCentre.set(false)">Cancel</button>
        </form>
      }

      <table mat-table [dataSource]="profitCentres()" class="mat-elevation-z1">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">{{ r.profitCentreCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.name }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.active ? 'ACTIVE' : 'INACTIVE'" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <ng-container *appHasPermission="'CONTROLLING_MANAGE'">
              <button mat-stroked-button (click)="editProfitCentre(r)">Edit</button>
              <button mat-stroked-button (click)="toggleProfitCentre(r)">
                {{ r.active ? 'Deactivate' : 'Activate' }}
              </button>
            </ng-container>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="pcColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: pcColumns"></tr>
      </table>
    }
  `,
  styles: `
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .section-gap {
      margin-top: 32px;
    }
  `,
})
export class CostCentreListComponent implements OnInit {
  private api = inject(ControllingApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  ccColumns = ['code', 'name', 'labour', 'machine', 'overhead', 'status', 'actions'];
  pcColumns = ['code', 'name', 'status', 'actions'];

  costCentres = signal<CostCentreDto[]>([]);
  profitCentres = signal<ProfitCentreDto[]>([]);
  loading = signal(false);
  editingCostCentre = signal(false);
  editingProfitCentre = signal(false);

  private costCentreId: number | null = null;
  private profitCentreId: number | null = null;

  ccForm = this.fb.nonNullable.group({
    costCentreCode: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    parentId: this.fb.control<number | null>(null),
    labourRatePerHour: [0, [Validators.required, Validators.min(0)]],
    machineRatePerHour: [0, [Validators.required, Validators.min(0)]],
    overheadPct: [0, [Validators.required, Validators.min(0)]],
  });

  pcForm = this.fb.nonNullable.group({
    profitCentreCode: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getCostCentres().subscribe({
      next: (list) => {
        this.costCentres.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load cost centres', 'Dismiss', { duration: 3000 });
      },
    });
    this.api.getProfitCentres().subscribe({ next: (list) => this.profitCentres.set(list) });
  }

  newCostCentre(): void {
    this.costCentreId = null;
    this.ccForm.reset({
      costCentreCode: '',
      name: '',
      parentId: null,
      labourRatePerHour: 0,
      machineRatePerHour: 0,
      overheadPct: 0,
    });
    this.editingCostCentre.set(true);
  }

  editCostCentre(cc: CostCentreDto): void {
    this.costCentreId = cc.id;
    this.ccForm.reset({
      costCentreCode: cc.costCentreCode,
      name: cc.name,
      parentId: cc.parentId,
      labourRatePerHour: cc.labourRatePerHour,
      machineRatePerHour: cc.machineRatePerHour,
      overheadPct: cc.overheadPct,
    });
    this.editingCostCentre.set(true);
  }

  saveCostCentre(): void {
    if (this.ccForm.invalid) return;
    const v = this.ccForm.getRawValue();
    const body = { ...v, responsibleUserId: null, plantId: null };
    const call = this.costCentreId
      ? this.api.updateCostCentre(this.costCentreId, body)
      : this.api.createCostCentre(body);
    call.subscribe({
      next: () => {
        this.editingCostCentre.set(false);
        this.load();
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not save cost centre', 'Dismiss', { duration: 4000 }),
    });
  }

  toggleCostCentre(cc: CostCentreDto): void {
    this.api.setCostCentreActive(cc.id, !cc.active).subscribe({
      next: () => this.load(),
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not change status', 'Dismiss', { duration: 4000 }),
    });
  }

  newProfitCentre(): void {
    this.profitCentreId = null;
    this.pcForm.reset({ profitCentreCode: '', name: '' });
    this.editingProfitCentre.set(true);
  }

  editProfitCentre(pc: ProfitCentreDto): void {
    this.profitCentreId = pc.id;
    this.pcForm.reset({ profitCentreCode: pc.profitCentreCode, name: pc.name });
    this.editingProfitCentre.set(true);
  }

  saveProfitCentre(): void {
    if (this.pcForm.invalid) return;
    const body = this.pcForm.getRawValue();
    const call = this.profitCentreId
      ? this.api.updateProfitCentre(this.profitCentreId, body)
      : this.api.createProfitCentre(body);
    call.subscribe({
      next: () => {
        this.editingProfitCentre.set(false);
        this.load();
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not save profit centre', 'Dismiss', { duration: 4000 }),
    });
  }

  toggleProfitCentre(pc: ProfitCentreDto): void {
    this.api.setProfitCentreActive(pc.id, !pc.active).subscribe({
      next: () => this.load(),
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not change status', 'Dismiss', { duration: 4000 }),
    });
  }
}
