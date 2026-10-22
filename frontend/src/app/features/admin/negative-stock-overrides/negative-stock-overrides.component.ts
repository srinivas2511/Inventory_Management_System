import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { AppConfigService } from '../../../core/config/app-config.service';
import { NegativeStockReasonDialogComponent } from './negative-stock-reason-dialog.component';

export interface NegativeStockOverrideDto {
  itemType: 'MATERIAL' | 'PRODUCT';
  id: number;
  code: string;
  name: string;
  allowed: boolean;
}

@Component({
  selector: 'app-negative-stock-overrides',
  standalone: true,
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatTableModule,
    MatTabsModule,
  ],
  template: `
    <div class="page-header">
      <h1 class="mat-headline-5">Negative Stock Overrides</h1>
      <p class="mat-body-2 subtitle">
        Allow specific materials or products to have negative stock. Requires <code>INVENTORY_NEGATIVE_OVERRIDE</code>.
        Every change is audited.
      </p>
    </div>

    @if (loading()) {
      <div class="center"><mat-spinner diameter="40" /></div>
    } @else {
      <mat-tab-group>
        <mat-tab label="Materials">
          <div class="tab-pad">
            <table mat-table [dataSource]="materials()" class="full-table">
              <ng-container matColumnDef="code">
                <th mat-header-cell *matHeaderCellDef>Code</th>
                <td mat-cell *matCellDef="let r">{{ r.code }}</td>
              </ng-container>
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let r">{{ r.name }}</td>
              </ng-container>
              <ng-container matColumnDef="allowed">
                <th mat-header-cell *matHeaderCellDef>Allow negative</th>
                <td mat-cell *matCellDef="let r">
                  <mat-slide-toggle
                    [checked]="r.allowed"
                    aria-label="Toggle negative stock for {{ r.name }}"
                    (change)="onToggle(r, $event.checked)"
                  />
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"></tr>
            </table>
            @if (materials().length === 0) {
              <p class="empty-hint mat-body-2">No materials found.</p>
            }
          </div>
        </mat-tab>

        <mat-tab label="Products">
          <div class="tab-pad">
            <table mat-table [dataSource]="products()" class="full-table">
              <ng-container matColumnDef="code">
                <th mat-header-cell *matHeaderCellDef>Code</th>
                <td mat-cell *matCellDef="let r">{{ r.code }}</td>
              </ng-container>
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Name</th>
                <td mat-cell *matCellDef="let r">{{ r.name }}</td>
              </ng-container>
              <ng-container matColumnDef="allowed">
                <th mat-header-cell *matHeaderCellDef>Allow negative</th>
                <td mat-cell *matCellDef="let r">
                  <mat-slide-toggle
                    [checked]="r.allowed"
                    aria-label="Toggle negative stock for {{ r.name }}"
                    (change)="onToggle(r, $event.checked)"
                  />
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"></tr>
            </table>
            @if (products().length === 0) {
              <p class="empty-hint mat-body-2">No products found.</p>
            }
          </div>
        </mat-tab>
      </mat-tab-group>

      @if (errorMessage()) {
        <p class="form-error mat-body-2">{{ errorMessage() }}</p>
      }
    }
  `,
  styles: [
    `
      .page-header {
        padding: 16px 0 8px;
      }
      .subtitle {
        color: rgba(0, 0, 0, 0.6);
        margin: 4px 0 0;
      }
      .center {
        display: flex;
        justify-content: center;
        padding: 40px;
      }
      .tab-pad {
        padding: 16px 0;
      }
      .full-table {
        width: 100%;
      }
      .empty-hint {
        color: rgba(0, 0, 0, 0.6);
        margin: 8px 0;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
        margin-top: 8px;
      }
    `,
  ],
})
export class NegativeStockOverridesComponent implements OnInit {
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly materials = signal<NegativeStockOverrideDto[]>([]);
  protected readonly products = signal<NegativeStockOverrideDto[]>([]);
  protected readonly columns = ['code', 'name', 'allowed'];

  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.http.get<NegativeStockOverrideDto[]>(`${this.base}/api/inventory/negative-stock-overrides`).subscribe({
      next: (list) => {
        this.materials.set(list.filter((i) => i.itemType === 'MATERIAL'));
        this.products.set(list.filter((i) => i.itemType === 'PRODUCT'));
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Failed to load overrides.');
        this.loading.set(false);
      },
    });
  }

  onToggle(item: NegativeStockOverrideDto, allowed: boolean): void {
    const dialogRef = this.dialog.open(NegativeStockReasonDialogComponent, {
      width: '400px',
      data: { item, allowed },
      disableClose: true,
    });
    dialogRef.afterClosed().subscribe((result: string | undefined) => {
      if (result == null) {
        this.load();
        return;
      }
      const type = item.itemType === 'MATERIAL' ? 'materials' : 'products';
      this.http
        .put(`${this.base}/api/inventory/negative-stock-overrides/${type}/${item.id}`, { allowed, reason: result })
        .subscribe({
          next: () => this.load(),
          error: (err) => {
            this.errorMessage.set(err?.error?.message ?? 'Update failed.');
            this.load();
          },
        });
    });
  }
}
