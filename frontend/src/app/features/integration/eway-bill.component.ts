import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { problemMessage } from '../../core/models/problem.model';

interface EwayBillDto {
  id: number;
  invoiceId: number | null;
  dispatchId: number | null;
  ewayBillNumber: string | null;
  status: string;
  transporterId: number | null;
  vehicleNumber: string | null;
  distanceKm: number | null;
  generatedAt: string | null;
  validUntil: string | null;
}

@Component({
  selector: 'app-eway-bill',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
    MatTableModule,
    IstDatePipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-eway-bill">E-Way Bill</h2>
    </div>

    <section>
      <h3 class="mat-h3">Generate E-Way Bill</h3>
      <p class="mat-body-2">
        Build an e-way bill payload for a sales invoice. The payload can then be submitted to the GSP.
      </p>
      <ng-container *appHasPermission="'EWAY_BILL_MANAGE'">
        <form class="form-row" [formGroup]="generateForm" (ngSubmit)="generate()">
          <mat-form-field appearance="outline">
            <mat-label>Invoice ID</mat-label>
            <input matInput type="number" formControlName="invoiceId" required />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Transporter ID</mat-label>
            <input matInput type="number" formControlName="transporterId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Vehicle Number</mat-label>
            <input matInput formControlName="vehicleNumber" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Distance (km)</mat-label>
            <input matInput type="number" formControlName="distanceKm" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="generateForm.invalid || generating()">
            <mat-icon aria-hidden="true">receipt_long</mat-icon>
            Generate
          </button>
        </form>
      </ng-container>
    </section>

    <section style="margin-top:32px">
      <h3 class="mat-h3">E-Way Bills</h3>
      @if (bills().length === 0) {
        <p class="mat-body-2">No e-way bills generated yet.</p>
      } @else {
        <table mat-table [dataSource]="bills()" class="mat-elevation-z1 full-width">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let b">{{ b.id }}</td>
          </ng-container>
          <ng-container matColumnDef="invoiceId">
            <th mat-header-cell *matHeaderCellDef>Invoice</th>
            <td mat-cell *matCellDef="let b">{{ b.invoiceId }}</td>
          </ng-container>
          <ng-container matColumnDef="ewbNo">
            <th mat-header-cell *matHeaderCellDef>EWB Number</th>
            <td mat-cell *matCellDef="let b">{{ b.ewayBillNumber ?? '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="vehicle">
            <th mat-header-cell *matHeaderCellDef>Vehicle</th>
            <td mat-cell *matCellDef="let b">{{ b.vehicleNumber ?? '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let b"><app-status-badge [status]="b.status" /></td>
          </ng-container>
          <ng-container matColumnDef="validUntil">
            <th mat-header-cell *matHeaderCellDef>Valid Until</th>
            <td mat-cell *matCellDef="let b">{{ b.validUntil ? (b.validUntil | istDate: 'dd/MM/yyyy HH:mm') : '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let b">
              @if (b.status === 'DRAFT') {
                <button mat-icon-button aria-label="Submit e-way bill" (click)="submit(b)">
                  <mat-icon aria-hidden="true">send</mat-icon>
                </button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let row; columns: cols"></tr>
        </table>
      }
    </section>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .form-row { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; }
  `],
})
export class EwayBillComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  cols = ['id', 'invoiceId', 'ewbNo', 'vehicle', 'status', 'validUntil', 'actions'];
  bills = signal<EwayBillDto[]>([]);
  generating = signal(false);

  generateForm = this.fb.group({
    invoiceId: [null as number | null, Validators.required],
    transporterId: [null as number | null],
    vehicleNumber: [''],
    distanceKm: [null as number | null],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.http.get<EwayBillDto[]>('/api/integration/eway-bill').subscribe({
      next: (data) => this.bills.set(data),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load e-way bills'), 'Close', { duration: 4000 }),
    });
  }

  generate(): void {
    if (this.generateForm.invalid) return;
    const v = this.generateForm.value;
    this.generating.set(true);
    this.http.post<EwayBillDto>('/api/integration/eway-bill/generate', {
      invoiceId: v.invoiceId,
      transporterId: v.transporterId || null,
      vehicleNumber: v.vehicleNumber || null,
      distanceKm: v.distanceKm || null,
    }).subscribe({
      next: () => {
        this.generating.set(false);
        this.generateForm.reset();
        this.load();
        this.snackBar.open('E-way bill generated', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.generating.set(false);
        this.snackBar.open(problemMessage(err, 'Generation failed'), 'Close', { duration: 4000 });
      },
    });
  }

  submit(bill: EwayBillDto): void {
    this.http.post<EwayBillDto>(`/api/integration/eway-bill/${bill.id}/submit`, {}).subscribe({
      next: () => {
        this.load();
        this.snackBar.open('E-way bill submitted', 'Close', { duration: 3000 });
      },
      error: (err) => this.snackBar.open(problemMessage(err, 'Submission failed'), 'Close', { duration: 4000 }),
    });
  }
}
