import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { WorkCentreApi, WorkCentreDto } from './work-centre.api';

@Component({
  selector: 'app-work-centres',
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
      <h2 class="mat-h2">Work Centres</h2>
    </div>

    <form *appHasPermission="'WORK_CENTRE_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="save()" data-testid="wc-form">
      <h3 class="mat-h3 full">{{ editing() ? 'Edit: ' + editing()!.code : 'New Work Centre' }}</h3>
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput maxlength="20" formControlName="code" data-testid="code" />
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
      @if (editing()) {
        <mat-slide-toggle formControlName="active" data-testid="active">Active</mat-slide-toggle>
      }
      <div class="form-actions">
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
      <ng-container matColumnDef="description">
        <th mat-header-cell *matHeaderCellDef>Description</th>
        <td mat-cell *matCellDef="let r">{{ r.description ?? '—' }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Active</th>
        <td mat-cell *matCellDef="let r">{{ r.active ? 'Yes' : 'No' }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let r">
          <button mat-icon-button aria-label="Edit" (click)="edit(r)" *appHasPermission="'WORK_CENTRE_MANAGE'">
            <mat-icon>edit</mat-icon>
          </button>
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
    table { width: 100%; margin: 0 16px; width: calc(100% - 32px); }
  `],
})
export class WorkCentresComponent implements OnInit {
  protected readonly columns = ['code', 'name', 'description', 'status', 'actions'];
  protected readonly rows = signal<WorkCentreDto[]>([]);
  protected readonly editing = signal<WorkCentreDto | null>(null);
  protected readonly saving = signal(false);

  private readonly api = inject(WorkCentreApi);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: [''],
    active: [true],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.list().subscribe((data) => this.rows.set(data));
  }

  protected edit(wc: WorkCentreDto): void {
    this.editing.set(wc);
    this.form.patchValue({ code: wc.code, name: wc.name, description: wc.description ?? '', active: wc.active });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset({ active: true });
  }

  protected save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    const req = { code: v.code!, name: v.name!, description: v.description || null, active: v.active ?? true, version: this.editing()?.version ?? null };
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
}
