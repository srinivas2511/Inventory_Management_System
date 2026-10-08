import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { problemMessage } from '../../../core/models/problem.model';
import { ToastService } from '../../../core/notification/toast.service';
import { PlantApi, PlantDto, PlantSettingDto } from './plant.api';

const SETTING_LABELS: Record<string, string> = {
  default_warehouse_id: 'Default warehouse (id)',
  timezone: 'Time zone',
  production_calendar: 'Production calendar',
  costing_method: 'Costing method',
};

/** Plants (Company → Plant) and the settings a plant may override. Users are assigned to plants from the Users screen. */
@Component({
  selector: 'app-plants',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Plants</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" data-testid="add-plant">
        <mat-icon>add</mat-icon> Add plant
      </button>
    </div>
    <p class="mat-body-2">
      Documents and stock belong to a plant. A user with no plant assigned sees every plant; a user assigned to plants
      sees only those (assign them from Users), unless their role grants all-plant access.
    </p>

    <table mat-table [dataSource]="plants()" class="mat-elevation-z1" data-testid="plant-table">
      <ng-container matColumnDef="plantCode">
        <th mat-header-cell *matHeaderCellDef>Code</th>
        <td mat-cell *matCellDef="let p">{{ p.plantCode }}</td>
      </ng-container>
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let p">{{ p.name }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let p">{{ p.active ? 'Active' : 'Inactive' }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef><span class="cdk-visually-hidden">Actions</span></th>
        <td mat-cell *matCellDef="let p">
          <button
            mat-icon-button
            matTooltip="Edit plant"
            [attr.aria-label]="'Edit plant ' + p.plantCode"
            (click)="startEdit(p)"
            data-testid="edit-plant"
          >
            <mat-icon>edit</mat-icon>
          </button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>

    @if (editing()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="plant-form" data-testid="plant-form" novalidate>
        <h3 class="mat-h3">{{ editing() === 'new' ? 'New plant' : 'Edit plant' }}</h3>
        <mat-form-field appearance="outline">
          <mat-label>Plant code</mat-label>
          <input matInput formControlName="plantCode" maxlength="10" data-testid="plant-code" />
          @if (form.controls.plantCode.hasError('required') && form.controls.plantCode.touched) {
            <mat-error>Enter a code</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="100" data-testid="plant-name" />
          @if (form.controls.name.hasError('required') && form.controls.name.touched) {
            <mat-error>Enter a name</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Address</mat-label>
          <input matInput formControlName="address" />
        </mat-form-field>
        <mat-checkbox formControlName="active">Active</mat-checkbox>
        @if (errorMessage()) {
          <p class="form-error mat-body-2" role="alert" data-testid="plant-error">{{ errorMessage() }}</p>
        }
        <div class="actions">
          <button mat-button type="button" (click)="cancel()">Cancel</button>
          <button mat-flat-button color="primary" type="submit" [disabled]="saving()" data-testid="save-plant">
            Save plant
          </button>
        </div>
      </form>
    }

    <h3 class="mat-h3 settings-title">Settings that a plant can override</h3>
    <mat-form-field appearance="outline">
      <mat-label>Level</mat-label>
      <mat-select [value]="level()" (selectionChange)="chooseLevel($event.value)" data-testid="settings-level">
        <mat-option [value]="0">Company defaults</mat-option>
        @for (p of plants(); track p.id) {
          <mat-option [value]="p.id">{{ p.plantCode }} · {{ p.name }}</mat-option>
        }
      </mat-select>
    </mat-form-field>
    <p class="mat-body-2">
      A plant's value wins over the company default, which wins over the system default. Clearing an override makes the
      next level apply again.
    </p>
    @for (s of settings(); track s.key) {
      <div class="setting" [attr.data-testid]="'setting-' + s.key">
        <span class="setting-label">{{ label(s.key) }}</span>
        @if (s.key === 'costing_method') {
          <mat-form-field appearance="outline">
            <mat-select
              [value]="draft()[s.key] ?? s.value"
              (selectionChange)="setDraft(s.key, $event.value)"
              [attr.aria-label]="label(s.key)"
            >
              <mat-option value="MOVING_AVERAGE">Moving average</mat-option>
              <mat-option value="STANDARD">Standard</mat-option>
            </mat-select>
          </mat-form-field>
        } @else {
          <mat-form-field appearance="outline">
            <input
              matInput
              [value]="draft()[s.key] ?? s.value"
              (input)="setDraft(s.key, $any($event.target).value)"
              [attr.aria-label]="label(s.key)"
            />
          </mat-form-field>
        }
        <span class="source">From: {{ sourceLabel(s.source) }}</span>
        <button mat-button type="button" (click)="saveSetting(s)" [attr.data-testid]="'save-' + s.key">Save</button>
        @if (isOverrideHere(s)) {
          <button mat-button type="button" (click)="clearSetting(s)" [attr.data-testid]="'clear-' + s.key">
            Clear override
          </button>
        }
      </div>
    }
    @if (settingsError()) {
      <p class="form-error mat-body-2" role="alert">{{ settingsError() }}</p>
    }
  `,
  styles: [
    `
      .plant-form {
        display: grid;
        grid-template-columns: 1fr 2fr;
        gap: 0 12px;
        max-width: 720px;
        margin: 16px 0;
      }
      .plant-form .wide,
      .plant-form .actions,
      .plant-form .form-error {
        grid-column: 1/-1;
      }
      .actions {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
      }
      .settings-title {
        margin-top: 24px;
      }
      .setting {
        display: grid;
        grid-template-columns: 220px 280px 160px auto auto;
        gap: 0 12px;
        align-items: center;
      }
      .setting-label {
        font-weight: 500;
      }
      .source {
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class PlantsComponent implements OnInit {
  protected readonly columns = ['plantCode', 'name', 'status', 'actions'];
  protected readonly plants = signal<PlantDto[]>([]);
  protected readonly editing = signal<'new' | number | null>(null);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly level = signal(0);
  protected readonly settings = signal<PlantSettingDto[]>([]);
  protected readonly draft = signal<Record<string, string>>({});
  protected readonly settingsError = signal('');

  private readonly api = inject(PlantApi);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  protected readonly form = this.fb.nonNullable.group({
    plantCode: ['', [Validators.required, Validators.maxLength(10)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    address: [''],
    active: [true],
  });

  ngOnInit(): void {
    this.loadPlants();
    this.loadSettings();
  }

  protected label(key: string): string {
    return SETTING_LABELS[key] ?? key;
  }

  protected sourceLabel(source: string): string {
    return source === 'PLANT' ? 'this plant' : source === 'COMPANY' ? 'company default' : 'system default';
  }

  /** An override can be cleared only at the level it was set on. */
  protected isOverrideHere(s: PlantSettingDto): boolean {
    return this.level() === 0 ? s.source === 'COMPANY' : s.source === 'PLANT';
  }

  protected startCreate(): void {
    this.editing.set('new');
    this.errorMessage.set('');
    this.form.reset({ plantCode: '', name: '', address: '', active: true });
  }

  protected startEdit(p: PlantDto): void {
    this.editing.set(p.id);
    this.errorMessage.set('');
    this.form.reset({ plantCode: p.plantCode, name: p.name, address: p.address ?? '', active: p.active });
  }

  protected cancel(): void {
    this.editing.set(null);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request = {
      plantCode: raw.plantCode.trim(),
      name: raw.name.trim(),
      address: raw.address || null,
      active: raw.active,
    };
    const target = this.editing();
    this.saving.set(true);
    this.errorMessage.set('');
    const call = target === 'new' || target === null ? this.api.create(request) : this.api.update(target, request);
    call.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.editing.set(null);
        this.toast.success(`Plant ${saved.plantCode} saved`);
        this.loadPlants();
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The plant could not be saved.'));
      },
    });
  }

  protected setDraft(key: string, value: string): void {
    this.draft.set({ ...this.draft(), [key]: value });
  }

  protected chooseLevel(level: number): void {
    this.level.set(level);
    this.draft.set({});
    this.loadSettings();
  }

  protected saveSetting(s: PlantSettingDto): void {
    const value = (this.draft()[s.key] ?? s.value).trim();
    this.settingsError.set('');
    this.api.setSetting({ plantId: this.level() === 0 ? null : this.level(), key: s.key, value }).subscribe({
      next: () => {
        this.toast.success(`${this.label(s.key)} saved`);
        this.draft.set({});
        this.loadSettings();
      },
      error: (err) => this.settingsError.set(problemMessage(err, 'The setting could not be saved.')),
    });
  }

  protected clearSetting(s: PlantSettingDto): void {
    this.settingsError.set('');
    this.api.clearSetting(this.level() === 0 ? null : this.level(), s.key).subscribe({
      next: () => {
        this.toast.success(`${this.label(s.key)} override cleared`);
        this.draft.set({});
        this.loadSettings();
      },
      error: (err) => this.settingsError.set(problemMessage(err, 'The override could not be cleared.')),
    });
  }

  private loadPlants(): void {
    this.api.list().subscribe((p) => this.plants.set(p));
  }

  private loadSettings(): void {
    const call = this.level() === 0 ? this.api.companySettings() : this.api.effectiveSettings(this.level());
    call.subscribe({
      next: (s) => this.settings.set(s),
      error: (err) => this.settingsError.set(problemMessage(err, 'The settings could not be loaded.')),
    });
  }
}
