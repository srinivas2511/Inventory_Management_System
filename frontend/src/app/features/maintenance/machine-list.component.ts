import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { LookupApi, LookupOption } from '../../shared/lookup.api';

export interface MachineDto {
  id: number;
  code: string;
  name: string;
  machineType?: string;
  productionLine?: string;
  status: string;
  active: boolean;
  costCentreId?: number;
  description?: string;
  workCentreId?: number;
}

export interface SaveMachineRequest {
  code: string;
  name: string;
  machineType?: string | null;
  productionLine?: string | null;
  status?: string | null;
  costCentreId?: number | null;
  description?: string | null;
  workCentreId?: number | null;
}

@Component({
  selector: 'app-machine-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Machines</h2>
    </div>

    <form *appHasPermission="'MACHINE_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="save()" data-testid="machine-form">
      <h3 class="mat-h3 full">{{ editing() ? 'Edit: ' + editing()!.code : 'New Machine' }}</h3>
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput maxlength="30" formControlName="code" data-testid="code" />
        @if (form.controls.code.hasError('required') && form.controls.code.touched) {
          <mat-error>Required</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Name</mat-label>
        <input matInput maxlength="150" formControlName="name" data-testid="name" />
        @if (form.controls.name.hasError('required') && form.controls.name.touched) {
          <mat-error>Required</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Description</mat-label>
        <input matInput maxlength="300" formControlName="description" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Machine Type</mat-label>
        <input matInput maxlength="60" formControlName="machineType" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Production Line</mat-label>
        <input matInput maxlength="60" formControlName="productionLine" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Work Centre</mat-label>
        <mat-select formControlName="workCentreId" data-testid="work-centre">
          <mat-option [value]="null">— None —</mat-option>
          @for (wc of workCentres(); track wc.id) {
            <mat-option [value]="wc.id">{{ wc.code }} – {{ wc.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <div class="form-actions">
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()" data-testid="save">
          <mat-icon aria-hidden="true">save</mat-icon> Save
        </button>
        @if (editing()) {
          <button mat-button type="button" (click)="cancel()">Cancel</button>
        }
      </div>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">{{ r.code }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.name }}</td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let r">{{ r.description ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.machineType ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="workCentre">
          <th mat-header-cell *matHeaderCellDef>Work Centre</th>
          <td mat-cell *matCellDef="let r">{{ workCentreName(r.workCentreId) }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">{{ r.status }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button aria-label="Edit" (click)="edit(r)" *appHasPermission="'MACHINE_MANAGE'">
              <mat-icon>edit</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
  styles: [`
    .page-header { display: flex; align-items: center; padding: 16px 16px 0; }
    .edit-form { display: flex; flex-wrap: wrap; gap: 12px; padding: 16px; background: var(--mdc-filled-text-field-container-color, #f5f5f5); border-radius: 4px; margin: 16px; }
    .full { width: 100%; }
    .wide { flex: 2; min-width: 200px; }
    .form-actions { display: flex; gap: 8px; align-items: center; }
    .loading-center { display: flex; justify-content: center; padding: 48px; }
    table { width: 100%; }
  `],
})
export class MachineListComponent implements OnInit {
  protected readonly columns = ['code', 'name', 'description', 'type', 'workCentre', 'status', 'actions'];
  protected readonly rows = signal<MachineDto[]>([]);
  protected readonly workCentres = signal<LookupOption[]>([]);
  protected readonly editing = signal<MachineDto | null>(null);
  protected readonly saving = signal(false);
  protected readonly loading = signal(false);

  private readonly http = inject(HttpClient);
  private readonly lookupApi = inject(LookupApi);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(30)]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: [''],
    machineType: [''],
    productionLine: [''],
    workCentreId: [null as number | null],
  });

  ngOnInit(): void {
    this.load();
    this.lookupApi.workCentres().subscribe((wcs) => this.workCentres.set(wcs));
  }

  private load(): void {
    this.loading.set(true);
    this.http.get<MachineDto[]>('/api/machines').subscribe({
      next: (data) => { this.rows.set(data); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected workCentreName(id?: number): string {
    if (!id) return '—';
    const wc = this.workCentres().find((w) => w.id === id);
    return wc ? `${wc.code} – ${wc.name}` : String(id);
  }

  protected edit(m: MachineDto): void {
    this.editing.set(m);
    this.form.patchValue({
      code: m.code, name: m.name, description: m.description ?? '',
      machineType: m.machineType ?? '', productionLine: m.productionLine ?? '',
      workCentreId: m.workCentreId ?? null,
    });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset();
  }

  protected save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    const req: SaveMachineRequest = {
      code: v.code!,
      name: v.name!,
      description: v.description || null,
      machineType: v.machineType || null,
      productionLine: v.productionLine || null,
      workCentreId: v.workCentreId ?? null,
    };
    this.http.post<MachineDto>('/api/machines', req).subscribe({
      next: () => {
        this.snack.open('Saved', 'OK', { duration: 2000 });
        this.cancel();
        this.load();
        this.saving.set(false);
      },
      error: () => this.saving.set(false),
    });
  }
}
