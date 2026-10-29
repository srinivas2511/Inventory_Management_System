import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

export interface RegisterDto {
  code: string;
  name: string;
  rows: Record<string, unknown>[];
}

@Component({
  selector: 'app-registers',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.registers">Registers</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <div class="register-layout">
        <mat-nav-list>
          @for (code of codes(); track code) {
            <mat-list-item (click)="loadRegister(code)" [class.selected]="selected() === code">
              {{ code }}
            </mat-list-item>
          }
        </mat-nav-list>
        @if (register()) {
          <div class="register-data">
            <h3>{{ register()!.name }}</h3>
            <p>{{ register()!.rows.length }} row(s)</p>
          </div>
        }
      </div>
    }
  `,
})
export class RegistersComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  codes = signal<string[]>([]);
  selected = signal<string | null>(null);
  register = signal<RegisterDto | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<string[]>('/api/registers').subscribe({
      next: (data) => {
        this.codes.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load registers', 'Close', { duration: 3000 });
      },
    });
  }

  loadRegister(code: string): void {
    this.selected.set(code);
    this.http.get<RegisterDto>(`/api/registers/${code}`).subscribe({
      next: (data) => this.register.set(data),
      error: () => this.snackBar.open(`Failed to load register ${code}`, 'Close', { duration: 3000 }),
    });
  }
}
