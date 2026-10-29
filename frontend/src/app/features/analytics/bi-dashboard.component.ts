import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe, TitleCasePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';

export interface BiAreaDto {
  area: string;
  totalCount: number;
  totalAmount: number;
  summary: Record<string, unknown>;
}

@Component({
  selector: 'app-bi-dashboard',
  standalone: true,
  imports: [
    DecimalPipe,
    TitleCasePipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTabsModule,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.bi_dashboard">Business Intelligence</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <mat-tab-group>
        @for (area of areas(); track area) {
          <mat-tab [label]="area | titlecase">
            @if (areaData()[area]; as data) {
              <div class="bi-summary">
                <mat-card>
                  <mat-card-header>
                    <mat-card-title>{{ area | titlecase }}</mat-card-title>
                  </mat-card-header>
                  <mat-card-content>
                    <p>
                      Total records: <strong>{{ data.totalCount }}</strong>
                    </p>
                    <p>
                      Total amount: <strong>{{ data.totalAmount | number: '1.2-2' }}</strong>
                    </p>
                  </mat-card-content>
                </mat-card>
              </div>
            } @else {
              <button mat-stroked-button (click)="loadArea(area)">Load {{ area | titlecase }}</button>
            }
          </mat-tab>
        }
      </mat-tab-group>
    }
  `,
})
export class BiDashboardComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  areas = signal<string[]>([]);
  areaData = signal<Record<string, BiAreaDto>>({});
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<string[]>('/api/bi').subscribe({
      next: (data) => {
        this.areas.set(data);
        this.loading.set(false);
        if (data.length > 0) this.loadArea(data[0]);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load BI areas', 'Close', { duration: 3000 });
      },
    });
  }

  loadArea(area: string): void {
    this.http.get<BiAreaDto>(`/api/bi/${area}`).subscribe({
      next: (data) => {
        this.areaData.update((m) => ({ ...m, [area]: data }));
      },
      error: () => {
        this.snackBar.open(`Failed to load BI area: ${area}`, 'Close', { duration: 3000 });
      },
    });
  }
}
