import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface SystemSettingDto {
  key: string;
  value: string;
  valueType: string;
  description: string;
  updatedAt: string | null;
}

@Component({
  selector: 'app-system-settings',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">System Settings</h2>
    </div>

    @if (loading()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="settings()" class="mat-elevation-z1">
        <ng-container matColumnDef="key">
          <th mat-header-cell *matHeaderCellDef>Setting Key</th>
          <td mat-cell *matCellDef="let s">
            <code>{{ s.key }}</code>
          </td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let s">{{ s.description }}</td>
        </ng-container>
        <ng-container matColumnDef="valueType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let s">{{ s.valueType }}</td>
        </ng-container>
        <ng-container matColumnDef="value">
          <th mat-header-cell *matHeaderCellDef>Value</th>
          <td mat-cell *matCellDef="let s">
            @if (editing() === s.key) {
              <mat-form-field appearance="outline" style="width:200px">
                <input matInput [formControl]="editCtrl" />
              </mat-form-field>
            } @else {
              <strong>{{ s.value }}</strong>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let s">
            @if (editing() === s.key) {
              <button mat-icon-button matTooltip="Save" color="primary" (click)="save(s)">
                <mat-icon>save</mat-icon>
              </button>
              <button mat-icon-button matTooltip="Cancel" (click)="cancel()"><mat-icon>close</mat-icon></button>
            } @else {
              <button mat-icon-button matTooltip="Edit" (click)="startEdit(s)"><mat-icon>edit</mat-icon></button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
    }
  `,
  styles: `
    .spinner-center {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
    code {
      font-family: monospace;
      background: rgba(0, 0, 0, 0.05);
      padding: 2px 4px;
      border-radius: 3px;
    }
  `,
})
export class SystemSettingsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);

  protected readonly cols = ['key', 'description', 'valueType', 'value', 'actions'];
  protected readonly loading = signal(false);
  protected readonly settings = signal<SystemSettingDto[]>([]);
  protected readonly editing = signal<string | null>(null);
  protected readonly editCtrl = new FormControl('');

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.http.get<SystemSettingDto[]>('/api/settings').subscribe({
      next: (s) => {
        this.settings.set(s);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected startEdit(s: SystemSettingDto): void {
    this.editing.set(s.key);
    this.editCtrl.setValue(s.value);
  }

  protected cancel(): void {
    this.editing.set(null);
  }

  protected save(s: SystemSettingDto): void {
    this.http
      .put<SystemSettingDto>(`/api/settings/${encodeURIComponent(s.key)}`, { value: this.editCtrl.value })
      .subscribe({
        next: (updated) => {
          this.settings.update((list) => list.map((x) => (x.key === s.key ? updated : x)));
          this.editing.set(null);
          this.snack.open('Setting updated', 'Dismiss', { duration: 3000 });
        },
        error: (e) => this.snack.open(e.error?.message ?? 'Error', 'Dismiss', { duration: 4000 }),
      });
  }
}
