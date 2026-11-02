import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

interface OperationDto {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  defaultMachineType?: string | null;
  standardTimeMin: number;
  setupTimeMin: number;
  isInspection: boolean;
  active: boolean;
}

interface SaveOperationRequest {
  code: string;
  name: string;
  description?: string | null;
  defaultMachineType?: string | null;
  standardTimeMin: number;
  setupTimeMin: number;
  isInspection: boolean;
}

class OperationApi {
  constructor(
    private readonly http: HttpClient,
    private readonly base: string,
  ) {}

  list(): Observable<OperationDto[]> {
    return this.http.get<OperationDto[]>(`${this.base}/api/operations`);
  }

  create(req: SaveOperationRequest): Observable<OperationDto> {
    return this.http.post<OperationDto>(`${this.base}/api/operations`, req);
  }

  update(id: number, req: SaveOperationRequest): Observable<OperationDto> {
    return this.http.put<OperationDto>(`${this.base}/api/operations/${id}`, req);
  }

  deactivate(id: number): Observable<OperationDto> {
    return this.http.post<OperationDto>(`${this.base}/api/operations/${id}/deactivate`, {});
  }
}

@Component({
  selector: 'app-operations',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Operations</h2>
    </div>

    <form *appHasPermission="'MASTERDATA_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="save()" data-testid="op-form">
      <h3 class="mat-h3 full">{{ editing() ? 'Edit: ' + editing()!.code : 'New Operation' }}</h3>

      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput maxlength="20" formControlName="code" [readonly]="!!editing()" data-testid="code" />
        @if (form.controls.code.hasError('required') && form.controls.code.touched) {
          <mat-error>Required</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline" class="wide">
        <mat-label>Name</mat-label>
        <input matInput maxlength="100" formControlName="name" data-testid="name" />
        @if (form.controls.name.hasError('required') && form.controls.name.touched) {
          <mat-error>Required</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline" class="full">
        <mat-label>Description</mat-label>
        <input matInput maxlength="300" formControlName="description" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Default machine type</mat-label>
        <input matInput maxlength="60" formControlName="defaultMachineType" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Standard time (min)</mat-label>
        <input matInput type="number" min="0" formControlName="standardTimeMin" data-testid="stdTime" />
        @if (form.controls.standardTimeMin.invalid && form.controls.standardTimeMin.touched) {
          <mat-error>Required, ≥ 0</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Setup time (min)</mat-label>
        <input matInput type="number" min="0" formControlName="setupTimeMin" />
        @if (form.controls.setupTimeMin.invalid && form.controls.setupTimeMin.touched) {
          <mat-error>Required, ≥ 0</mat-error>
        }
      </mat-form-field>

      <mat-slide-toggle formControlName="isInspection">Inspection step</mat-slide-toggle>

      <div class="form-actions full">
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()" data-testid="save">
          <mat-icon aria-hidden="true">save</mat-icon> Save
        </button>
        @if (editing()) {
          <button mat-button type="button" (click)="cancel()">Cancel</button>
        }
      </div>
    </form>

    <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
      <ng-container matColumnDef="code">
        <th mat-header-cell *matHeaderCellDef>Code</th>
        <td mat-cell *matCellDef="let r">{{ r.code }}</td>
      </ng-container>
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let r">{{ r.name }}</td>
      </ng-container>
      <ng-container matColumnDef="stdTime">
        <th mat-header-cell *matHeaderCellDef>Std Time (min)</th>
        <td mat-cell *matCellDef="let r">{{ r.standardTimeMin }}</td>
      </ng-container>
      <ng-container matColumnDef="setupTime">
        <th mat-header-cell *matHeaderCellDef>Setup Time (min)</th>
        <td mat-cell *matCellDef="let r">{{ r.setupTimeMin }}</td>
      </ng-container>
      <ng-container matColumnDef="inspection">
        <th mat-header-cell *matHeaderCellDef>Inspection Step</th>
        <td mat-cell *matCellDef="let r">{{ r.isInspection ? 'Yes' : 'No' }}</td>
      </ng-container>
      <ng-container matColumnDef="active">
        <th mat-header-cell *matHeaderCellDef>Active</th>
        <td mat-cell *matCellDef="let r">{{ r.active ? 'Yes' : 'No' }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let r">
          <button mat-icon-button aria-label="Edit" (click)="edit(r)" *appHasPermission="'MASTERDATA_MANAGE'">
            <mat-icon>edit</mat-icon>
          </button>
          @if (r.active) {
            <button mat-icon-button aria-label="Deactivate" (click)="deactivate(r)" *appHasPermission="'MASTERDATA_MANAGE'">
              <mat-icon>block</mat-icon>
            </button>
          }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 16px 0; }
    .edit-form { display: flex; flex-wrap: wrap; gap: 12px; padding: 16px; background: var(--mdc-filled-text-field-container-color, #f5f5f5); border-radius: 4px; margin: 16px; }
    .full { width: 100%; }
    .wide { flex: 2; min-width: 200px; }
    .form-actions { display: flex; gap: 8px; align-items: center; }
    table { width: calc(100% - 32px); margin: 0 16px; }
  `],
})
export class OperationsComponent implements OnInit {
  protected readonly columns = ['code', 'name', 'stdTime', 'setupTime', 'inspection', 'active', 'actions'];
  protected readonly rows = signal<OperationDto[]>([]);
  protected readonly editing = signal<OperationDto | null>(null);
  protected readonly saving = signal(false);

  private readonly api = new OperationApi(inject(HttpClient), inject(AppConfigService).apiBaseUrl);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: [''],
    defaultMachineType: [''],
    standardTimeMin: [0, [Validators.required, Validators.min(0)]],
    setupTimeMin: [0, [Validators.required, Validators.min(0)]],
    isInspection: [false],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.list().subscribe((data) => this.rows.set(data));
  }

  protected edit(op: OperationDto): void {
    this.editing.set(op);
    this.form.patchValue({
      code: op.code,
      name: op.name,
      description: op.description ?? '',
      defaultMachineType: op.defaultMachineType ?? '',
      standardTimeMin: op.standardTimeMin,
      setupTimeMin: op.setupTimeMin,
      isInspection: op.isInspection,
    });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset({ standardTimeMin: 0, setupTimeMin: 0, isInspection: false });
  }

  protected save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    const req: SaveOperationRequest = {
      code: v.code!,
      name: v.name!,
      description: v.description || null,
      defaultMachineType: v.defaultMachineType || null,
      standardTimeMin: v.standardTimeMin ?? 0,
      setupTimeMin: v.setupTimeMin ?? 0,
      isInspection: v.isInspection ?? false,
    };
    const call = this.editing()
      ? this.api.update(this.editing()!.id, req)
      : this.api.create(req);
    call.subscribe({
      next: () => {
        this.snack.open('Saved', 'OK', { duration: 2000 });
        this.cancel();
        this.load();
        this.saving.set(false);
      },
      error: () => this.saving.set(false),
    });
  }

  protected deactivate(op: OperationDto): void {
    this.api.deactivate(op.id).subscribe(() => {
      this.snack.open('Deactivated', 'OK', { duration: 2000 });
      this.load();
    });
  }
}
