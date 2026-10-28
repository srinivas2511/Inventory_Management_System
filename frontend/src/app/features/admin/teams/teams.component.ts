import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';

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

  teams = signal<TeamDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  cols = ['code', 'name', 'teamType', 'members'];

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
}
