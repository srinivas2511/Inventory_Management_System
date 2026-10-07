import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, CurrencyPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DispatchFormComponent } from './dispatch-form.component';
import { SalesApi, DispatchDto } from './sales.api';
import { PagedList } from '../../shared/paged-list';
import { ListPaginatorComponent } from '../../shared/components/list-paginator.component';

@Component({
  selector: 'app-dispatch-list',
  standalone: true,
  providers: [SalesApi],
  imports: [
    ListPaginatorComponent,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    DatePipe,
    DecimalPipe,
    CurrencyPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Dispatches</h2>
      <button mat-raised-button color="primary" *appHasPermission="'DISPATCH_CREATE'" (click)="createDispatch()">
        <mat-icon>add</mat-icon> New dispatch
      </button>
    </div>

    @if (loading()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="dispatches()" class="mat-elevation-z2 w-full">
        <ng-container matColumnDef="dispatchNumber">
          <th mat-header-cell *matHeaderCellDef>Dispatch #</th>
          <td mat-cell *matCellDef="let d">{{ d.dispatchNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="orderNumber">
          <th mat-header-cell *matHeaderCellDef>Order #</th>
          <td mat-cell *matCellDef="let d">{{ d.orderNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="customer">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let d">{{ d.customerName }}</td>
        </ng-container>
        <ng-container matColumnDef="dispatchDate">
          <th mat-header-cell *matHeaderCellDef>Dispatch Date</th>
          <td mat-cell *matCellDef="let d">{{ d.dispatchDate | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="transporter">
          <th mat-header-cell *matHeaderCellDef>Transporter</th>
          <td mat-cell *matCellDef="let d">{{ d.transporter || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="lrNumber">
          <th mat-header-cell *matHeaderCellDef>LR #</th>
          <td mat-cell *matCellDef="let d">{{ d.lrNumber || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let d">
            <app-status-badge [status]="d.status" />
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let d">
            <button
              mat-icon-button
              matTooltip="Dispatch Note"
              *appHasPermission="'DISPATCH_VIEW'"
              (click)="viewNote(d)"
            >
              <mat-icon>description</mat-icon>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No dispatches found
          </td>
        </tr>
      </table>
      <app-list-paginator [paged]="paged" (changed)="load()" />
    }
  `,
})
export class DispatchListComponent implements OnInit {
  private api = inject(SalesApi);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  dispatches = signal<DispatchDto[]>([]);
  loading = signal(false);
  readonly paged = new PagedList();
  columns = [
    'dispatchNumber',
    'orderNumber',
    'customer',
    'dispatchDate',
    'transporter',
    'lrNumber',
    'status',
    'actions',
  ];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getDispatches(undefined, this.paged.pageIndex(), this.paged.pageSize(), 'id,desc').subscribe({
      next: (page) => {
        this.dispatches.set(this.paged.apply(page));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load dispatches', 'Dismiss', { duration: 3000 });
      },
    });
  }

  createDispatch(): void {
    this.dialog
      .open(DispatchFormComponent, { width: '960px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snack.open('Dispatch created; the AR invoice follows automatically', '', { duration: 4000 });
          this.load();
        }
      });
  }

  viewNote(dispatch: DispatchDto): void {
    this.snack.open(`Dispatch note for ${dispatch.dispatchNumber} — print/export coming soon`, '', { duration: 3000 });
  }
}
