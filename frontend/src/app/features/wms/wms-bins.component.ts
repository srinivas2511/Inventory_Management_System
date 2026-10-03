import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { WarehouseApi, WarehouseDto } from '../warehouse/warehouse.api';
import {
  BIN_TYPES,
  BinDto,
  BinType,
  PutAwayRuleDto,
  PutAwayStrategy,
  STRATEGIES,
  WmsApi,
  ZONE_TYPES,
  ZoneDto,
  ZoneType,
} from './wms.api';

@Component({
  selector: 'app-wms-bins',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Zones, Bins and Put-away Rules</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Warehouse</mat-label>
        <mat-select [formControl]="warehouseCtrl" (selectionChange)="load()" data-test="warehouse">
          @for (w of warehouses(); track w.id) {
            <mat-option [value]="w.id">{{ w.warehouseCode }} - {{ w.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    @if (warehouseCtrl.value) {
      <h3 class="mat-h3">Zones</h3>
      <form *appHasPermission="'BIN_MANAGE'" class="edit-form" [formGroup]="zoneForm" (ngSubmit)="saveZone()">
        <mat-form-field appearance="outline">
          <mat-label>Zone code</mat-label>
          <input matInput maxlength="10" formControlName="zoneCode" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput maxlength="60" formControlName="name" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="zoneType">
            @for (t of zoneTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="zoneForm.invalid">
          <mat-icon>add</mat-icon> Add zone
        </button>
      </form>
      <table mat-table [dataSource]="zones()" class="mat-elevation-z1" data-test="zones">
        <ng-container matColumnDef="zoneCode">
          <th mat-header-cell *matHeaderCellDef>Zone</th>
          <td mat-cell *matCellDef="let z">{{ z.zoneCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let z">{{ z.name }}</td>
        </ng-container>
        <ng-container matColumnDef="zoneType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let z">{{ z.zoneType }}</td>
        </ng-container>
        <ng-container matColumnDef="binCount">
          <th mat-header-cell *matHeaderCellDef>Bins</th>
          <td mat-cell *matCellDef="let z">{{ z.binCount }}</td>
        </ng-container>
        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let z"><app-status-badge [status]="z.active ? 'ACTIVE' : 'INACTIVE'" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="zoneColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: zoneColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="zoneColumns.length" style="text-align:center;padding:1rem">
            No zones yet
          </td>
        </tr>
      </table>

      <h3 class="mat-h3">Bins</h3>
      <form *appHasPermission="'BIN_MANAGE'" class="edit-form" [formGroup]="binForm" (ngSubmit)="saveBin()">
        <mat-form-field appearance="outline">
          <mat-label>Zone</mat-label>
          <mat-select formControlName="zoneId" data-test="bin-zone">
            @for (z of zones(); track z.id) {
              <mat-option [value]="z.id">{{ z.zoneCode }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Bin code</mat-label>
          <input matInput maxlength="20" formControlName="binCode" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="narrow">
          <mat-label>Aisle</mat-label>
          <input matInput maxlength="10" formControlName="aisle" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="narrow">
          <mat-label>Rack</mat-label>
          <input matInput maxlength="10" formControlName="rack" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="narrow">
          <mat-label>Level</mat-label>
          <input matInput maxlength="10" formControlName="level" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Bin type</mat-label>
          <mat-select formControlName="binType">
            @for (t of binTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Max weight (kg)</mat-label>
          <input matInput type="number" min="0" formControlName="maxWeightKg" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Allowed material types</mat-label>
          <input
            matInput
            maxlength="200"
            formControlName="allowedMaterialTypes"
            placeholder="SPRING_STEEL, MUSIC_WIRE"
          />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="binForm.invalid">
          <mat-icon>{{ editingBin() ? 'save' : 'add' }}</mat-icon> {{ editingBin() ? 'Save bin' : 'Add bin' }}
        </button>
        @if (editingBin()) {
          <button mat-stroked-button type="button" (click)="cancelEdit()">Cancel</button>
        }
      </form>
      <table mat-table [dataSource]="bins()" class="mat-elevation-z1" data-test="bins">
        <ng-container matColumnDef="binCode">
          <th mat-header-cell *matHeaderCellDef>Bin</th>
          <td mat-cell *matCellDef="let b">{{ b.binCode }}</td>
        </ng-container>
        <ng-container matColumnDef="zoneCode">
          <th mat-header-cell *matHeaderCellDef>Zone</th>
          <td mat-cell *matCellDef="let b">{{ b.zoneCode }}</td>
        </ng-container>
        <ng-container matColumnDef="position">
          <th mat-header-cell *matHeaderCellDef>Aisle / rack / level</th>
          <td mat-cell *matCellDef="let b">{{ b.aisle || '-' }} / {{ b.rack || '-' }} / {{ b.level || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="binType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let b">{{ b.binType }}</td>
        </ng-container>
        <ng-container matColumnDef="weight">
          <th mat-header-cell *matHeaderCellDef>Weight (kg)</th>
          <td mat-cell *matCellDef="let b">
            {{ b.currentWeightKg | number: '1.0-3' }}
            @if (b.maxWeightKg) {
              / {{ b.maxWeightKg | number: '1.0-3' }}
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="occupied">
          <th mat-header-cell *matHeaderCellDef>Stock</th>
          <td mat-cell *matCellDef="let b">{{ b.occupied ? 'Occupied' : 'Empty' }}</td>
        </ng-container>
        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let b"><app-status-badge [status]="b.active ? 'ACTIVE' : 'INACTIVE'" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let b">
            <button *appHasPermission="'BIN_MANAGE'" mat-button (click)="edit(b)">
              <mat-icon>edit</mat-icon> Edit
            </button>
            <button *appHasPermission="'BIN_MANAGE'" mat-button (click)="toggleActive(b)">
              {{ b.active ? 'Deactivate' : 'Activate' }}
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="binColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: binColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="binColumns.length" style="text-align:center;padding:1rem">
            No bins yet
          </td>
        </tr>
      </table>

      <h3 class="mat-h3">Put-away rules</h3>
      <p class="hint">
        The strategy applies per material type; a rule without a type is the warehouse default. Without any rule the
        system setting wms.putaway.default_strategy applies.
      </p>
      <form *appHasPermission="'BIN_MANAGE'" class="edit-form" [formGroup]="ruleForm" (ngSubmit)="saveRule()">
        <mat-form-field appearance="outline">
          <mat-label>Material type (blank = any)</mat-label>
          <input matInput maxlength="40" formControlName="materialType" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Strategy</mat-label>
          <mat-select formControlName="strategy" data-test="rule-strategy">
            @for (s of strategies; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="ruleForm.invalid">
          <mat-icon>add</mat-icon> Save rule
        </button>
      </form>
      <table mat-table [dataSource]="rules()" class="mat-elevation-z1" data-test="rules">
        <ng-container matColumnDef="materialType">
          <th mat-header-cell *matHeaderCellDef>Material type</th>
          <td mat-cell *matCellDef="let r">{{ r.materialType || 'Any (default)' }}</td>
        </ng-container>
        <ng-container matColumnDef="strategy">
          <th mat-header-cell *matHeaderCellDef>Strategy</th>
          <td mat-cell *matCellDef="let r">{{ r.strategy }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button *appHasPermission="'BIN_MANAGE'" mat-button (click)="deleteRule(r)">
              <mat-icon>delete</mat-icon> Remove
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="ruleColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: ruleColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="ruleColumns.length" style="text-align:center;padding:1rem">
            No rules: the default strategy applies
          </td>
        </tr>
      </table>
    }
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
    .narrow {
      width: 110px;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant, #555);
      margin: 0 0 8px;
    }
  `,
})
export class WmsBinsComponent implements OnInit {
  private api = inject(WmsApi);
  private warehouseApi = inject(WarehouseApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  zoneTypes = ZONE_TYPES;
  binTypes = BIN_TYPES;
  strategies = STRATEGIES;
  zoneColumns = ['zoneCode', 'name', 'zoneType', 'binCount', 'active'];
  binColumns = ['binCode', 'zoneCode', 'position', 'binType', 'weight', 'occupied', 'active', 'actions'];
  ruleColumns = ['materialType', 'strategy', 'actions'];

  warehouses = signal<WarehouseDto[]>([]);
  zones = signal<ZoneDto[]>([]);
  bins = signal<BinDto[]>([]);
  rules = signal<PutAwayRuleDto[]>([]);
  editingBin = signal<BinDto | null>(null);

  warehouseCtrl = new FormControl<number | null>(null);
  zoneForm = this.fb.nonNullable.group({
    zoneCode: ['', [Validators.required, Validators.maxLength(10)]],
    name: ['', [Validators.required, Validators.maxLength(60)]],
    zoneType: ['BULK' as ZoneType, Validators.required],
  });
  binForm = this.fb.group({
    zoneId: new FormControl<number | null>(null, Validators.required),
    binCode: ['', [Validators.required, Validators.maxLength(20)]],
    aisle: [''],
    rack: [''],
    level: [''],
    binType: ['STANDARD' as BinType],
    maxWeightKg: new FormControl<number | null>(null),
    allowedMaterialTypes: [''],
  });
  ruleForm = this.fb.group({
    materialType: [''],
    strategy: ['FIXED_BIN' as PutAwayStrategy, Validators.required],
  });

  ngOnInit(): void {
    this.warehouseApi.list().subscribe({
      next: (ws) => {
        this.warehouses.set(ws.filter((w) => w.active));
        if (this.warehouses().length === 1) {
          this.warehouseCtrl.setValue(this.warehouses()[0].id);
          this.load();
        }
      },
      error: () => this.snack.open('Failed to load warehouses', 'Dismiss', { duration: 3000 }),
    });
  }

  load(): void {
    const wh = this.warehouseCtrl.value;
    if (!wh) return;
    this.api.getZones(wh).subscribe((z) => this.zones.set(z));
    this.api.getBins(wh, null, null).subscribe((p) => this.bins.set(p.content));
    this.api.getRules(wh).subscribe((r) => this.rules.set(r));
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }

  saveZone(): void {
    const wh = this.warehouseCtrl.value;
    if (!wh || this.zoneForm.invalid) return;
    const v = this.zoneForm.getRawValue();
    this.api
      .saveZone(
        { warehouseId: wh, zoneCode: v.zoneCode, name: v.name, zoneType: v.zoneType, active: true, version: null },
        null,
      )
      .subscribe({
        next: () => {
          this.zoneForm.reset({ zoneCode: '', name: '', zoneType: 'BULK' });
          this.load();
        },
        error: (e) => this.fail(e, 'Could not save the zone'),
      });
  }

  saveBin(): void {
    if (this.binForm.invalid) return;
    const v = this.binForm.getRawValue();
    const editing = this.editingBin();
    this.api
      .saveBin(
        {
          zoneId: v.zoneId as number,
          binCode: v.binCode ?? '',
          aisle: v.aisle || null,
          rack: v.rack || null,
          level: v.level || null,
          binPosition: null,
          binType: v.binType,
          maxWeightKg: v.maxWeightKg,
          maxVolumeM3: editing?.maxVolumeM3 ?? null,
          allowedMaterialTypes: v.allowedMaterialTypes || null,
          locationType: null,
          active: editing ? editing.active : true,
          version: editing ? editing.version : null,
        },
        editing ? editing.id : null,
      )
      .subscribe({
        next: () => {
          this.cancelEdit();
          this.load();
        },
        error: (e) => this.fail(e, 'Could not save the bin'),
      });
  }

  edit(bin: BinDto): void {
    this.editingBin.set(bin);
    this.binForm.reset({
      zoneId: bin.zoneId,
      binCode: bin.binCode,
      aisle: bin.aisle ?? '',
      rack: bin.rack ?? '',
      level: bin.level ?? '',
      binType: bin.binType,
      maxWeightKg: bin.maxWeightKg,
      allowedMaterialTypes: bin.allowedMaterialTypes ?? '',
    });
    this.binForm.controls.zoneId.disable();
    this.binForm.controls.binCode.disable();
  }

  cancelEdit(): void {
    this.editingBin.set(null);
    this.binForm.controls.zoneId.enable();
    this.binForm.controls.binCode.enable();
    this.binForm.reset({
      zoneId: null,
      binCode: '',
      aisle: '',
      rack: '',
      level: '',
      binType: 'STANDARD',
      maxWeightKg: null,
      allowedMaterialTypes: '',
    });
  }

  toggleActive(bin: BinDto): void {
    this.api
      .saveBin(
        {
          zoneId: bin.zoneId,
          binCode: bin.binCode,
          aisle: bin.aisle,
          rack: bin.rack,
          level: bin.level,
          binPosition: bin.binPosition,
          binType: bin.binType,
          maxWeightKg: bin.maxWeightKg,
          maxVolumeM3: bin.maxVolumeM3,
          allowedMaterialTypes: bin.allowedMaterialTypes,
          locationType: null,
          active: !bin.active,
          version: bin.version,
        },
        bin.id,
      )
      .subscribe({ next: () => this.load(), error: (e) => this.fail(e, 'Could not change the bin') });
  }

  saveRule(): void {
    const wh = this.warehouseCtrl.value;
    if (!wh || this.ruleForm.invalid) return;
    const v = this.ruleForm.getRawValue();
    this.api.saveRule(wh, v.materialType || null, v.strategy as PutAwayStrategy).subscribe({
      next: () => {
        this.ruleForm.reset({ materialType: '', strategy: 'FIXED_BIN' });
        this.load();
      },
      error: (e) => this.fail(e, 'Could not save the rule'),
    });
  }

  deleteRule(rule: PutAwayRuleDto): void {
    this.api
      .deleteRule(rule.id)
      .subscribe({ next: () => this.load(), error: (e) => this.fail(e, 'Could not remove the rule') });
  }
}
