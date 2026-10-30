import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { CommonModule } from '@angular/common';

export interface TeamMemberDto {
  userId: number;
  userName: string;
  role: string;
}

export interface TeamDto {
  id: number;
  code: string;
  name: string;
  description: string | null;
  teamType: string;
  active: boolean;
  members: TeamMemberDto[];
  notificationSettings: string | null;
  moduleAccess: string[] | null;
}

const ALL_MODULES = [
  'INVENTORY', 'PROCUREMENT', 'MANUFACTURING', 'SALES',
  'FINANCE', 'QUALITY', 'HR', 'CRM', 'WMS', 'MAINTENANCE',
];

@Component({
  selector: 'app-team-settings-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    CommonModule,
  ],
  template: `
    <h2 mat-dialog-title>Team Settings — {{ team.name }}</h2>
    <mat-dialog-content>
      <div class="section-label">Module Access</div>
      <div class="module-grid">
        @for (mod of allModules; track mod) {
          <mat-checkbox [checked]="isEnabled(mod)" (change)="toggleModule(mod, $event.checked)">
            {{ mod }}
          </mat-checkbox>
        }
      </div>
      <div class="section-label mt-16">Notification Settings (JSON)</div>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Notification Settings JSON</mat-label>
        <textarea matInput [(ngModel)]="notificationSettingsJson" rows="4"
                  [ngModelOptions]="{standalone: true}"></textarea>
      </mat-form-field>
      @if (jsonError()) {
        <p class="form-error">{{ jsonError() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) { <mat-spinner diameter="18" /> } @else { Save }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .section-label { font-weight: 500; margin-bottom: 8px; }
    .mt-16 { margin-top: 16px; }
    .module-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 4px; }
    .full-width { width: 100%; margin-top: 8px; }
    .form-error { color: #f44336; font-size: 0.75rem; }
  `],
})
export class TeamSettingsDialogComponent {
  readonly team = inject<TeamDto>(MAT_DIALOG_DATA);
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<TeamSettingsDialogComponent>);

  readonly allModules = ALL_MODULES;
  readonly saving = signal(false);
  readonly jsonError = signal('');

  notificationSettingsJson = this.team.notificationSettings ?? '{}';
  selectedModules = signal<string[]>([...(this.team.moduleAccess ?? [])]);

  isEnabled(mod: string): boolean {
    return this.selectedModules().includes(mod);
  }

  toggleModule(mod: string, checked: boolean) {
    if (checked) {
      this.selectedModules.update((arr) => [...arr, mod]);
    } else {
      this.selectedModules.update((arr) => arr.filter((m) => m !== mod));
    }
  }

  save() {
    this.jsonError.set('');
    try {
      JSON.parse(this.notificationSettingsJson);
    } catch {
      this.jsonError.set('Must be valid JSON');
      return;
    }
    this.saving.set(true);
    this.http.put<TeamDto>(`/api/admin/teams/${this.team.id}/settings`, {
      notificationSettings: this.notificationSettingsJson,
      moduleAccess: this.selectedModules(),
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.snack.open('Settings saved', 'Close', { duration: 2000 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.saving.set(false);
        this.snack.open('Failed to save settings', 'Close', { duration: 3000 });
      },
    });
  }
}

@Component({
  selector: 'app-teams',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    CommonModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@teams.title">Teams</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" [disabled]="creating()">
        <mat-icon aria-hidden="true">add</mat-icon> Add Team
      </button>
    </div>

    @if (creating()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="inline-form mat-elevation-z2">
        <mat-form-field>
          <mat-label>Code</mat-label>
          <input matInput formControlName="code" placeholder="TEAM-001" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Team Type</mat-label>
          <mat-select formControlName="teamType">
            <mat-option value="APPROVAL">Approval</mat-option>
            <mat-option value="FUNCTIONAL">Functional</mat-option>
            <mat-option value="REPORTING">Reporting</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field class="full-width">
          <mat-label>Description</mat-label>
          <input matInput formControlName="description" />
        </mat-form-field>
        <div class="form-actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Save</button>
          <button mat-button type="button" (click)="creating.set(false)">Cancel</button>
        </div>
      </form>
    }

    @if (loading()) {
      <mat-spinner diameter="32"></mat-spinner>
    } @else {
      <table mat-table [dataSource]="teams()" class="full-width">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let row">{{ row.code }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let row">{{ row.name }}</td>
        </ng-container>
        <ng-container matColumnDef="teamType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let row">{{ row.teamType }}</td>
        </ng-container>
        <ng-container matColumnDef="members">
          <th mat-header-cell *matHeaderCellDef>Members</th>
          <td mat-cell *matCellDef="let row">{{ row.members?.length ?? 0 }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <button mat-icon-button aria-label="Team settings" (click)="openSettings(row)">
              <mat-icon>settings</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
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
      .inline-form {
        padding: 16px;
        margin-bottom: 16px;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
      }
      .full-width {
        flex: 1 1 100%;
      }
      .form-actions {
        display: flex;
        gap: 8px;
        align-items: center;
        padding-top: 8px;
      }
      table {
        width: 100%;
      }
    `,
  ],
})
export class TeamsComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  teams = signal<TeamDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  cols = ['code', 'name', 'teamType', 'members', 'actions'];

  form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    teamType: ['FUNCTIONAL', Validators.required],
    description: [''],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.http.get<TeamDto[]>('/api/admin/teams').subscribe({
      next: (data) => {
        this.teams.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load teams', 'Close', { duration: 3000 });
      },
    });
  }

  startCreate() {
    this.form.reset({ teamType: 'FUNCTIONAL' });
    this.creating.set(true);
  }

  save() {
    if (this.form.invalid) return;
    this.http.post<TeamDto>('/api/admin/teams', { ...this.form.value, members: [] }).subscribe({
      next: (created) => {
        this.teams.update((arr) => [...arr, created]);
        this.creating.set(false);
        this.snack.open('Team created', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to create team', 'Close', { duration: 3000 }),
    });
  }

  openSettings(team: TeamDto) {
    this.dialog.open(TeamSettingsDialogComponent, {
      width: '480px',
      data: team,
    });
  }
}
