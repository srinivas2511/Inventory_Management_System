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
import { VendorTypeApi, VendorTypeDto } from './vendor-type.api';

@Component({
  selector: 'app-vendor-types',
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
      <h2 class="mat-h2">Vendor Types</h2>
    </div>

    <form *appHasPermission="'VENDOR_TYPE_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="save()" data-testid="vt-form">
      <h3 class="mat-h3 full">{{ editing() ? 'Edit: ' + editing()!.code : 'New Vendor Type' }}</h3>
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput maxlength="20" formControlName="code" data-testid="code" />
        @if (form.controls.code.hasError('required') && form.controls.code.touched) {
          <mat-error>Required</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Description</mat-label>
        <input matInput maxlength="200" formControlName="description" data-testid="description" />
        @if (form.controls.description.hasError('required') && form.controls.description.touched) {
          <mat-error>Required</mat-error>
        }
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
      <ng-container matColumnDef="description">
        <th mat-header-cell *matHeaderCellDef>Description</th>
        <td mat-cell *matCellDef="let r">{{ r.description }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Active</th>
        <td mat-cell *matCellDef="let r">{{ r.active ? 'Yes' : 'No' }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let r">
          <button mat-icon-button aria-label="Edit" (click)="edit(r)" *appHasPermission="'VENDOR_TYPE_MANAGE'">
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
    table { width: calc(100% - 32px); margin: 0 16px; }
  `],
})
export class VendorTypesComponent implements OnInit {
  protected readonly columns = ['code', 'description', 'status', 'actions'];
  protected readonly rows = signal<VendorTypeDto[]>([]);
  protected readonly editing = signal<VendorTypeDto | null>(null);
  protected readonly saving = signal(false);

  private readonly api = inject(VendorTypeApi);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20)]],
    description: ['', [Validators.required, Validators.maxLength(200)]],
    active: [true],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.list().subscribe((data) => this.rows.set(data));
  }

  protected edit(vt: VendorTypeDto): void {
    this.editing.set(vt);
    this.form.patchValue({ code: vt.code, description: vt.description, active: vt.active });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset({ active: true });
  }

  protected save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    const req = { code: v.code!, description: v.description!, active: v.active ?? true, version: this.editing()?.version ?? null };
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
