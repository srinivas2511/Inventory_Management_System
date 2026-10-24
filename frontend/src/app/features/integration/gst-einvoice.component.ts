import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { EInvoiceDto, IntegrationApi } from './integration.api';

@Component({
  selector: 'app-gst-einvoice',
  standalone: true,
  imports: [
    HasPermissionDirective,
    IstDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
    MatTableModule,
    ReactiveFormsModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">GST e-Invoice</h2>
    </div>

    <section>
      <h3 class="mat-h3">Generate e-Invoice</h3>
      <p class="mat-body-2">
        Generates a GSTN-schema JSON payload for a dispatch. The payload can then be submitted to the IRP (Invoice
        Registration Portal) via your GST Suvidha Provider.
      </p>
      <ng-container *appHasPermission="'GST_EINVOICE_GENERATE'">
        <form class="inline-form" [formGroup]="generateForm" (ngSubmit)="generate()">
          <mat-form-field appearance="outline">
            <mat-label>Dispatch ID</mat-label>
            <input matInput type="number" formControlName="dispatchId" required />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="generateForm.invalid || generating()">
            <mat-icon aria-hidden="true">receipt_long</mat-icon>
            Generate
          </button>
        </form>
      </ng-container>
    </section>

    <section style="margin-top:32px">
      <h3 class="mat-h3">Generated e-Invoices</h3>
      @if (invoices().length === 0) {
        <p class="mat-body-2">No e-invoices generated yet.</p>
      } @else {
        <table mat-table [dataSource]="invoices()" class="mat-elevation-z1">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let e">{{ e.id }}</td>
          </ng-container>
          <ng-container matColumnDef="dispatch">
            <th mat-header-cell *matHeaderCellDef>Dispatch</th>
            <td mat-cell *matCellDef="let e">{{ e.dispatchId }}</td>
          </ng-container>
          <ng-container matColumnDef="irn">
            <th mat-header-cell *matHeaderCellDef>IRN</th>
            <td mat-cell *matCellDef="let e">{{ e.irn ?? '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let e">{{ e.status }}</td>
          </ng-container>
          <ng-container matColumnDef="generatedAt">
            <th mat-header-cell *matHeaderCellDef>Generated</th>
            <td mat-cell *matCellDef="let e">{{ e.generatedAt | istDate: 'dd/MM/yyyy HH:mm' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let e">
              <button mat-icon-button aria-label="Download JSON payload" (click)="download(e)">
                <mat-icon aria-hidden="true">download</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let row; columns: cols"></tr>
        </table>
      }
    </section>
  `,
  styles: [
    `
      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 24px;
      }
    `,
  ],
})
export class GstEInvoiceComponent implements OnInit {
  private api = inject(IntegrationApi);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  cols = ['id', 'dispatch', 'irn', 'status', 'generatedAt', 'actions'];
  invoices = signal<EInvoiceDto[]>([]);
  generating = signal(false);

  generateForm = this.fb.group({ dispatchId: [null as number | null, Validators.required] });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.getEInvoices().subscribe({
      next: (list) => this.invoices.set(list),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load e-invoices'), 'Close', { duration: 4000 }),
    });
  }

  generate(): void {
    if (this.generateForm.invalid) return;
    const dispatchId = this.generateForm.value.dispatchId!;
    this.generating.set(true);
    this.api.generateEInvoice(dispatchId).subscribe({
      next: () => {
        this.generating.set(false);
        this.generateForm.reset();
        this.load();
        this.snackBar.open('e-Invoice generated', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.generating.set(false);
        this.snackBar.open(problemMessage(err, 'Generation failed'), 'Close', { duration: 4000 });
      },
    });
  }

  download(invoice: EInvoiceDto): void {
    this.api.downloadEInvoicePayload(invoice.id).subscribe({
      next: (resp) => {
        const blob = new Blob([resp.payload], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `einvoice-${invoice.id}.json`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.snackBar.open(problemMessage(err, 'Download failed'), 'Close', { duration: 4000 }),
    });
  }
}
