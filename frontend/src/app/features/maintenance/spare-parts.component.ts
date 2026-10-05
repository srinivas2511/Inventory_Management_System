import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { MaintenanceApi, SaveSparePartBody, SparePartDto } from './maintenance.api';

@Component({
  selector: 'app-spare-parts',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
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
      <h2 class="mat-h2">Spare Parts</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Search code or name</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" data-test="search" />
      </mat-form-field>
      <mat-slide-toggle [formControl]="activeOnly" (change)="load()" data-test="active-only">
        Active only
      </mat-slide-toggle>
      <button mat-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    <form *appHasPermission="'SPARE_PART_MANAGE'" class="edit-form" (ngSubmit)="save()" data-test="part-form">
      <h3 class="mat-h3 full">{{ editing() ? 'Change ' + editing()!.sparePartCode : 'New spare part' }}</h3>
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput maxlength="20" [formControl]="code" data-test="code" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Name</mat-label>
        <input matInput maxlength="150" [formControl]="name" data-test="name" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="narrow">
        <mat-label>Unit</mat-label>
        <input matInput maxlength="10" [formControl]="uom" data-test="uom" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="narrow">
        <mat-label>Minimum stock</mat-label>
        <input matInput type="number" min="0" [formControl]="minStock" data-test="min" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="narrow">
        <mat-label>Reorder level</mat-label>
        <input matInput type="number" min="0" [formControl]="reorderLevel" data-test="reorder" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="narrow">
        <mat-label>Standard cost</mat-label>
        <input matInput type="number" min="0" [formControl]="standardCost" data-test="cost" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Compatible machine types</mat-label>
        <input matInput maxlength="200" [formControl]="machineTypes" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="!valid()" data-test="save">
        <mat-icon>save</mat-icon> Save
      </button>
      @if (editing()) {
        <button mat-button type="button" (click)="cancel()">Cancel</button>
      }
      @if (levelsError()) {
        <span class="warn" data-test="levels-error">Minimum stock must not exceed the reorder level.</span>
      }
    </form>

    <table mat-table [dataSource]="parts()" class="mat-elevation-z1" data-test="parts">
      <ng-container matColumnDef="code">
        <th mat-header-cell *matHeaderCellDef>Code</th>
        <td mat-cell *matCellDef="let p">{{ p.sparePartCode }}</td>
      </ng-container>
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let p">{{ p.name }}</td>
      </ng-container>
      <ng-container matColumnDef="onHand">
        <th mat-header-cell *matHeaderCellDef>On hand</th>
        <td mat-cell *matCellDef="let p">
          {{ p.onHandQty | number: '1.0-3' }} {{ p.uom }}
          @if (p.belowMinStock) {
            <span class="low" data-test="low-stock">below minimum</span>
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="levels">
        <th mat-header-cell *matHeaderCellDef>Min / reorder</th>
        <td mat-cell *matCellDef="let p">
          {{ p.minStock | number: '1.0-3' }} / {{ p.reorderLevel | number: '1.0-3' }}
        </td>
      </ng-container>
      <ng-container matColumnDef="cost">
        <th mat-header-cell *matHeaderCellDef>Standard cost</th>
        <td mat-cell *matCellDef="let p">{{ p.standardCost | number: '1.2-4' }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let p">{{ p.active ? 'Active' : 'Inactive' }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let p">
          <button *appHasPermission="'SPARE_PART_MANAGE'" mat-button (click)="edit(p)" data-test="edit">
            <mat-icon>edit</mat-icon> Edit
          </button>
          <button
            *appHasPermission="'SPARE_PART_MANAGE'"
            mat-button
            [disabled]="!p.active"
            (click)="deactivate(p)"
            data-test="deactivate"
          >
            <mat-icon>block</mat-icon> Deactivate
          </button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">No spare parts</td>
      </tr>
    </table>
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .full {
      flex-basis: 100%;
      margin: 8px 0 0;
    }
    .wide {
      min-width: 240px;
    }
    .narrow {
      width: 140px;
    }
    .low {
      margin-left: 6px;
      padding: 1px 8px;
      border-radius: 10px;
      background: rgba(244, 67, 54, 0.15);
      color: #c62828;
      font-size: 0.75rem;
    }
    .warn {
      color: #c62828;
    }
  `,
})
export class SparePartsComponent implements OnInit {
  private api = inject(MaintenanceApi);
  private snack = inject(MatSnackBar);

  columns = ['code', 'name', 'onHand', 'levels', 'cost', 'status', 'actions'];
  parts = signal<SparePartDto[]>([]);
  editing = signal<SparePartDto | null>(null);

  searchCtrl = new FormControl('', { nonNullable: true });
  activeOnly = new FormControl(true, { nonNullable: true });

  code = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(20)] });
  name = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(150)] });
  uom = new FormControl('PCS', { nonNullable: true, validators: [Validators.required] });
  minStock = new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] });
  reorderLevel = new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] });
  standardCost = new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] });
  machineTypes = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.spareParts(this.searchCtrl.value, this.activeOnly.value ? true : null).subscribe({
      next: (p) => this.parts.set(p.content),
      error: () => this.snack.open('Failed to load spare parts', 'Dismiss', { duration: 3000 }),
    });
  }

  levelsError(): boolean {
    return this.minStock.value > this.reorderLevel.value;
  }

  /** A disabled control (the code while editing) is never "valid", so it is only checked when creating. */
  valid(): boolean {
    const codeOk = this.editing() !== null || (this.code.valid && this.code.value.trim().length > 0);
    return (
      codeOk &&
      this.name.valid &&
      this.uom.valid &&
      this.minStock.valid &&
      this.reorderLevel.valid &&
      this.standardCost.valid &&
      !this.levelsError()
    );
  }

  edit(part: SparePartDto): void {
    this.editing.set(part);
    this.code.setValue(part.sparePartCode);
    this.code.disable();
    this.name.setValue(part.name);
    this.uom.setValue(part.uom);
    this.minStock.setValue(part.minStock);
    this.reorderLevel.setValue(part.reorderLevel);
    this.standardCost.setValue(part.standardCost);
    this.machineTypes.setValue(part.compatibleMachineTypes ?? '');
  }

  cancel(): void {
    this.editing.set(null);
    this.code.enable();
    this.code.reset('');
    this.name.reset('');
    this.uom.reset('PCS');
    this.minStock.reset(0);
    this.reorderLevel.reset(0);
    this.standardCost.reset(0);
    this.machineTypes.reset('');
  }

  save(): void {
    if (!this.valid()) return;
    const editing = this.editing();
    const body: SaveSparePartBody = {
      sparePartCode: editing ? editing.sparePartCode : this.code.value.trim(),
      name: this.name.value.trim(),
      description: null,
      uom: this.uom.value.trim(),
      compatibleMachineTypes: this.machineTypes.value.trim() || null,
      minStock: this.minStock.value,
      reorderLevel: this.reorderLevel.value,
      standardCost: this.standardCost.value,
      version: editing ? editing.version : null,
    };
    const call = editing ? this.api.updateSparePart(editing.id, body) : this.api.createSparePart(body);
    call.subscribe({
      next: () => {
        this.snack.open(editing ? 'Spare part updated' : 'Spare part created', 'Dismiss', { duration: 3000 });
        this.cancel();
        this.load();
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not save the spare part', 'Dismiss', { duration: 5000 }),
    });
  }

  deactivate(part: SparePartDto): void {
    this.api.deactivateSparePart(part.id).subscribe({
      next: () => this.load(),
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not deactivate', 'Dismiss', { duration: 5000 }),
    });
  }
}
