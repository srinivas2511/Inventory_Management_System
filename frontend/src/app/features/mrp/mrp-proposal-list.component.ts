import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { MrpApi, MrpProposalDto } from './mrp.api';

@Component({
  selector: 'app-mrp-proposal-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">MRP Proposals</h2>
      @if (filters.value.runId) {
        <span>
          <button *appHasPermission="'PURCHASE_PROPOSAL'" mat-stroked-button (click)="convertAll('PURCHASE')">
            Convert all purchase proposals
          </button>
          <button *appHasPermission="'PRODUCTION_PROPOSAL'" mat-stroked-button (click)="convertAll('PRODUCTION')">
            Convert all production proposals
          </button>
        </span>
      }
    </div>

    <form class="filters" [formGroup]="filters" (ngSubmit)="reload()">
      <mat-form-field appearance="outline">
        <mat-label>Run ID</mat-label>
        <input matInput type="number" formControlName="runId" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Type</mat-label>
        <mat-select formControlName="type">
          <mat-option [value]="''">All</mat-option>
          <mat-option value="PURCHASE">Purchase</mat-option>
          <mat-option value="PRODUCTION">Production</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select formControlName="status">
          <mat-option [value]="''">All</mat-option>
          <mat-option value="OPEN">Open</mat-option>
          <mat-option value="CONVERTED">Converted</mat-option>
          <mat-option value="DISCARDED">Discarded</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Capacity</mat-label>
        <mat-select formControlName="overload">
          <mat-option [value]="''">All</mat-option>
          <mat-option value="true">Overloaded only</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit">Apply</button>
    </form>

    @if (discardingId() !== null) {
      <form class="filters" [formGroup]="discardForm" (ngSubmit)="discard()">
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Reason for discarding proposal {{ discardingId() }}</mat-label>
          <input matInput formControlName="reason" maxlength="300" />
        </mat-form-field>
        <button mat-flat-button color="warn" type="submit" [disabled]="discardForm.invalid">Discard</button>
        <button mat-button type="button" (click)="discardingId.set(null)">Cancel</button>
      </form>
    }

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="proposals()" class="mat-elevation-z1">
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.proposalType === 'PURCHASE' ? 'Purchase' : 'Production' }}</td>
        </ng-container>
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let r">
            {{ r.proposalType === 'PURCHASE' ? r.materialCode : r.productCode }}
            {{ r.proposalType === 'PURCHASE' ? r.materialName : r.productName }}
          </td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef>Proposed qty</th>
          <td mat-cell *matCellDef="let r">
            {{ r.proposedQty | number: '1.0-3' }} {{ r.uom }}
            @if (r.proposedQty !== r.requiredQty) {
              <small>(need {{ r.requiredQty | number: '1.0-3' }})</small>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="orderDate">
          <th mat-header-cell *matHeaderCellDef>Order by</th>
          <td mat-cell *matCellDef="let r">{{ r.proposedOrderDate | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="needDate">
          <th mat-header-cell *matHeaderCellDef>Needed</th>
          <td mat-cell *matCellDef="let r">
            {{ r.needDate | date: 'dd MMM yyyy' }}
            @if (r.leadTimeAdjusted) {
              <mat-icon class="warn" title="Earliest possible date after lead time; demand is due sooner">
                schedule
              </mat-icon>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="source">
          <th mat-header-cell *matHeaderCellDef>Source</th>
          <td mat-cell *matCellDef="let r">{{ r.sourceDemandType }}</td>
        </ng-container>
        <ng-container matColumnDef="capacity">
          <th mat-header-cell *matHeaderCellDef>Machine hours</th>
          <td mat-cell *matCellDef="let r">
            @if (r.machineHoursRequired !== null) {
              {{ r.machineHoursRequired | number: '1.0-1' }} h
            }
            @if (r.capacityOverload) {
              <mat-icon class="warn" title="Overloads a machine in this week">warning</mat-icon>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.status" />
            @if (r.convertedDocumentType) {
              <small>{{ r.convertedDocumentType }} #{{ r.convertedDocumentId }}</small>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'OPEN') {
              @if (r.proposalType === 'PURCHASE') {
                <button *appHasPermission="'PURCHASE_PROPOSAL'" mat-stroked-button (click)="toRequisition(r)">
                  Create PR
                </button>
              } @else {
                <button *appHasPermission="'PRODUCTION_PROPOSAL'" mat-stroked-button (click)="toOrder(r)">
                  Create order
                </button>
              }
              <button *appHasPermission="'MRP_RUN'" mat-button (click)="startDiscard(r)">Discard</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No proposals match the filters
          </td>
        </tr>
      </table>
      <mat-paginator
        [length]="total()"
        [pageSize]="pageSize"
        [pageIndex]="pageIndex()"
        [pageSizeOptions]="[25, 50, 100]"
        (page)="onPage($event)"
      />
    }
  `,
  styles: `
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 360px;
    }
    .warn {
      color: #e65100;
      vertical-align: middle;
    }
  `,
})
export class MrpProposalListComponent implements OnInit {
  private api = inject(MrpApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private route = inject(ActivatedRoute);

  columns = ['type', 'item', 'qty', 'orderDate', 'needDate', 'source', 'capacity', 'status', 'actions'];
  proposals = signal<MrpProposalDto[]>([]);
  loading = signal(false);
  total = signal(0);
  pageIndex = signal(0);
  pageSize = 50;
  discardingId = signal<number | null>(null);

  filters = this.fb.nonNullable.group({
    runId: this.fb.control<number | null>(null),
    type: [''],
    status: [''],
    overload: [''],
  });
  discardForm = this.fb.nonNullable.group({ reason: ['', [Validators.required, Validators.maxLength(300)]] });

  ngOnInit(): void {
    const runId = this.route.snapshot.queryParamMap.get('runId');
    if (runId) this.filters.patchValue({ runId: Number(runId) });
    this.reload();
  }

  reload(): void {
    this.pageIndex.set(0);
    this.load();
  }

  onPage(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.pageSize = e.pageSize;
    this.load();
  }

  load(): void {
    const f = this.filters.getRawValue();
    this.loading.set(true);
    this.api
      .getProposals(
        {
          runId: f.runId,
          type: f.type || null,
          status: f.status || null,
          capacityOverload: f.overload === 'true' ? true : null,
        },
        this.pageIndex(),
        this.pageSize,
      )
      .subscribe({
        next: (page) => {
          this.proposals.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.snack.open('Failed to load proposals', 'Dismiss', { duration: 3000 });
        },
      });
  }

  toRequisition(p: MrpProposalDto): void {
    this.api.convertToRequisition(p.id).subscribe({
      next: () => this.converted('Purchase requisition created'),
      error: (err) => this.failed(err),
    });
  }

  toOrder(p: MrpProposalDto): void {
    this.api.convertToProductionOrder(p.id).subscribe({
      next: () => this.converted('Draft production order created'),
      error: (err) => this.failed(err),
    });
  }

  convertAll(type: 'PURCHASE' | 'PRODUCTION'): void {
    const runId = this.filters.getRawValue().runId;
    if (!runId) return;
    this.api.convertAll(runId, type).subscribe({
      next: (result) => this.converted(`${result.converted} proposals converted`),
      error: (err) => this.failed(err),
    });
  }

  startDiscard(p: MrpProposalDto): void {
    this.discardForm.reset({ reason: '' });
    this.discardingId.set(p.id);
  }

  discard(): void {
    const id = this.discardingId();
    if (id === null || this.discardForm.invalid) return;
    this.api.discardProposal(id, this.discardForm.getRawValue().reason).subscribe({
      next: () => {
        this.discardingId.set(null);
        this.converted('Proposal discarded');
      },
      error: (err) => this.failed(err),
    });
  }

  private converted(message: string): void {
    this.snack.open(message, 'OK', { duration: 3000 });
    this.load();
  }

  private failed(err: { error?: { detail?: string } }): void {
    this.snack.open(err?.error?.detail ?? 'Action failed', 'Dismiss', { duration: 5000 });
  }
}
