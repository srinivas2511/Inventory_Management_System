import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HrmApi, MONTH_NAMES, PayrollSlipDto } from './hrm.api';

/**
 * Print-friendly pay slip. No PDF library is a dependency of the project, so "Download" uses the browser's
 * print dialog (Save as PDF).
 */
@Component({
  selector: 'app-payslip',
  standalone: true,
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    @if (slip(); as s) {
      <div class="toolbar no-print">
        <a mat-button [routerLink]="['/hrm/payroll', s.payrollRunId]"><mat-icon>arrow_back</mat-icon> Back to run</a>
        <button mat-flat-button color="primary" (click)="print()">
          <mat-icon>print</mat-icon> Print / save as PDF
        </button>
      </div>

      <section class="slip">
        <h2>Pay slip - {{ monthNames[s.periodMonth - 1] }} {{ s.periodYear }}</h2>
        <div class="who">
          <div>
            <strong>{{ s.employeeName }}</strong> ({{ s.employeeCode }})
          </div>
          <div>{{ s.designation }} {{ s.department ? '- ' + s.department : '' }}</div>
          <div>Run {{ s.runNumber }}</div>
          <div>
            Paid days {{ s.paidDays | number: '1.0-1' }} of {{ s.workingDays | number: '1.0-1' }} (loss of pay
            {{ s.lopDays | number: '1.0-1' }})
          </div>
        </div>

        <div class="columns">
          <table>
            <caption>
              Earnings
            </caption>
            <tr>
              <td>Basic pay</td>
              <td class="n">{{ s.basicPay | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>HRA</td>
              <td class="n">{{ s.hra | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>DA</td>
              <td class="n">{{ s.da | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>Travel allowance</td>
              <td class="n">{{ s.travelAllowance | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>Other allowances</td>
              <td class="n">{{ s.otherAllowances | number: '1.2-2' }}</td>
            </tr>
            <tr class="total">
              <td>Gross pay</td>
              <td class="n">{{ s.grossPay | number: '1.2-2' }}</td>
            </tr>
          </table>
          <table>
            <caption>
              Deductions
            </caption>
            <tr>
              <td>Provident fund</td>
              <td class="n">{{ s.pfEmployee | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>ESI</td>
              <td class="n">{{ s.esiEmployee | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>Professional tax</td>
              <td class="n">{{ s.professionalTax | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>TDS</td>
              <td class="n">{{ s.tds | number: '1.2-2' }}</td>
            </tr>
            <tr class="total">
              <td>Total deductions</td>
              <td class="n">{{ s.totalDeductions | number: '1.2-2' }}</td>
            </tr>
          </table>
        </div>

        <div class="net">Net pay: {{ s.netPay | number: '1.2-2' }}</div>
        <p class="note">
          Employer contributions (not deducted from pay): PF {{ s.pfEmployer | number: '1.2-2' }}, ESI
          {{ s.esiEmployer | number: '1.2-2' }}.
        </p>
      </section>
    } @else {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .toolbar {
      display: flex;
      justify-content: space-between;
      margin-bottom: 16px;
    }
    .slip {
      max-width: 800px;
      margin: 0 auto;
      padding: 24px;
      border: 1px solid rgba(128, 128, 128, 0.4);
      border-radius: 8px;
    }
    .who {
      margin-bottom: 16px;
    }
    .columns {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    caption {
      text-align: left;
      font-weight: 600;
      padding-bottom: 4px;
    }
    td {
      padding: 4px 0;
    }
    .n {
      text-align: right;
    }
    .total td {
      border-top: 1px solid rgba(128, 128, 128, 0.6);
      font-weight: 600;
    }
    .net {
      margin-top: 16px;
      font-size: 1.4rem;
      font-weight: 600;
      text-align: right;
    }
    .note {
      opacity: 0.7;
      font-size: 0.85rem;
    }
    @media print {
      .no-print {
        display: none;
      }
      .slip {
        border: none;
      }
    }
  `,
})
export class PayslipComponent implements OnInit {
  private api = inject(HrmApi);
  private route = inject(ActivatedRoute);

  monthNames = MONTH_NAMES;
  slip = signal<PayrollSlipDto | null>(null);

  ngOnInit(): void {
    this.api.getPayrollSlip(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (s) => this.slip.set(s),
    });
  }

  print(): void {
    window.print();
  }
}
