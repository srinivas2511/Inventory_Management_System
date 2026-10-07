import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface JournalLineDto {
  id: number;
  lineNumber: number;
  glAccountId: number;
  accountCode: string;
  accountName: string;
  debitAmount: number;
  creditAmount: number;
  description?: string;
}

export interface JournalHeaderDto {
  id: number;
  journalNumber: string;
  journalType: string;
  sourceModule?: string;
  postingDate: string;
  periodId: number;
  description: string;
  currencyCode: string;
  status: string;
  preparedBy?: number;
  approvedBy?: number;
  postedBy?: number;
  lines: JournalLineDto[];
}

export interface FiscalPeriodDto {
  id: number;
  periodName: string;
  fiscalYear: number;
  periodNumber: number;
  startDate: string;
  endDate: string;
  status: string;
}

export interface GlAccountDto {
  id: number;
  accountCode: string;
  name: string;
  accountType: string;
  normalBalance: string;
  allowDirectPosting: boolean;
  active: boolean;
}

export interface TrialBalanceRow {
  glAccountId: number;
  accountCode: string;
  accountName: string;
  accountType: string;
  totalDebit: number;
  totalCredit: number;
  balance: number;
}

export interface ApInvoiceDto {
  id: number;
  invoiceNumber: string;
  supplierId: number;
  invoiceDate: string;
  dueDate: string;
  totalAmount: number;
  paidAmount: number;
  balanceAmount: number;
  status: string;
  goodsReceiptId: number | null;
  matchStatus: 'MATCHED' | 'EXCEPTION' | 'OVERRIDDEN' | 'NOT_REQUIRED';
  matchExpectedAmount: number | null;
  matchVarianceAmount: number;
  matchMessage: string | null;
}

export interface ArInvoiceDto {
  id: number;
  invoiceNumber: string;
  customerId: number;
  invoiceDate: string;
  dueDate: string;
  totalAmount: number;
  receivedAmount: number;
  balanceAmount: number;
  status: string;
}

export interface ApAgeingRow {
  supplierId: number;
  supplierName: string;
  current: number;
  overdue30: number;
  overdue60: number;
  overdue90: number;
  overdue90Plus: number;
  total: number;
}

export interface ArAgeingRow {
  customerId: number;
  customerName: string;
  current: number;
  overdue30: number;
  overdue60: number;
  overdue90: number;
  overdue90Plus: number;
  total: number;
}

export interface BankStatementDto {
  id: number;
  statementNumber: string;
  glAccountId: number;
  statementDate: string;
  status: string;
  openingBalance: number;
  closingBalance: number;
  totalLines: number;
  matchedLines: number;
  unmatchedLines: number;
}

export interface FinanceDashboardDto {
  openPeriodName: string;
  totalApOutstanding: number;
  totalArOutstanding: number;
  apOverdue: number;
  arOverdue: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface MissingArInvoiceDto {
  dispatchId: number;
  dispatchNumber: string;
  customerId: number;
  dispatchDate: string;
}

export interface ArReconcileResultDto {
  attempted: number;
  created: number;
  alreadyInvoiced: number;
  failed: number;
  failures: { dispatchId: number; dispatchNumber: string; reason: string }[];
}

export interface FinanceAlertDto {
  id: number;
  alertType: string;
  severity: string;
  entityType: string;
  entityId: number;
  message: string;
  status: string;
  lastSeenAt: string;
  resolvedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class FinanceApi {
  private http = inject(HttpClient);
  private base = '/api';

  getMissingArInvoices(): Observable<MissingArInvoiceDto[]> {
    return this.http.get<MissingArInvoiceDto[]>(`${this.base}/ar/reconciliation/missing-invoices`);
  }

  retryMissingArInvoices(): Observable<ArReconcileResultDto> {
    return this.http.post<ArReconcileResultDto>(`${this.base}/ar/reconciliation/missing-invoices/retry`, {});
  }

  getMissingArInvoiceAlerts(): Observable<Page<FinanceAlertDto>> {
    const params = new HttpParams().set('alertType', 'MISSING_AR_INVOICE').set('size', 200);
    return this.http.get<Page<FinanceAlertDto>>(`${this.base}/alerts`, { params });
  }

  getJournals(page = 0, size = 20): Observable<Page<JournalHeaderDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<JournalHeaderDto>>(`${this.base}/journals`, { params });
  }

  getJournal(id: number): Observable<JournalHeaderDto> {
    return this.http.get<JournalHeaderDto>(`${this.base}/journals/${id}`);
  }

  submitJournal(id: number): Observable<JournalHeaderDto> {
    return this.http.post<JournalHeaderDto>(`${this.base}/journals/${id}/submit`, {});
  }

  approveJournal(id: number): Observable<JournalHeaderDto> {
    return this.http.post<JournalHeaderDto>(`${this.base}/journals/${id}/approve`, {});
  }

  postJournal(id: number): Observable<JournalHeaderDto> {
    return this.http.post<JournalHeaderDto>(`${this.base}/journals/${id}/post`, {});
  }

  reverseJournal(id: number, reason: string): Observable<JournalHeaderDto> {
    return this.http.post<JournalHeaderDto>(
      `${this.base}/journals/${id}/reverse?reason=${encodeURIComponent(reason)}`,
      {},
    );
  }

  getPeriods(): Observable<FiscalPeriodDto[]> {
    return this.http.get<FiscalPeriodDto[]>(`${this.base}/periods`);
  }

  initiatePeriodClose(id: number): Observable<FiscalPeriodDto> {
    return this.http.post<FiscalPeriodDto>(`${this.base}/periods/${id}/initiate-close`, {});
  }

  finalizePeriodClose(id: number): Observable<FiscalPeriodDto> {
    return this.http.post<FiscalPeriodDto>(`${this.base}/periods/${id}/finalize-close`, {});
  }

  reopenPeriod(id: number): Observable<FiscalPeriodDto> {
    return this.http.post<FiscalPeriodDto>(`${this.base}/periods/${id}/reopen`, {});
  }

  getGlAccounts(): Observable<GlAccountDto[]> {
    return this.http.get<GlAccountDto[]>(`${this.base}/gl/accounts`);
  }

  getTrialBalance(periodId: number): Observable<TrialBalanceRow[]> {
    const params = new HttpParams().set('periodId', periodId);
    return this.http.get<TrialBalanceRow[]>(`${this.base}/gl/trial-balance`, { params });
  }

  getApInvoices(page = 0, size = 20): Observable<Page<ApInvoiceDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ApInvoiceDto>>(`${this.base}/ap/invoices`, { params });
  }

  overrideApMatch(id: number, reason: string): Observable<ApInvoiceDto> {
    return this.http.post<ApInvoiceDto>(`${this.base}/ap/invoices/${id}/match-override`, { reason });
  }

  getApAgeing(): Observable<ApAgeingRow[]> {
    return this.http.get<ApAgeingRow[]>(`${this.base}/ap/ageing`);
  }

  getArInvoices(page = 0, size = 20): Observable<Page<ArInvoiceDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ArInvoiceDto>>(`${this.base}/ar/invoices`, { params });
  }

  getArAgeing(): Observable<ArAgeingRow[]> {
    return this.http.get<ArAgeingRow[]>(`${this.base}/ar/ageing`);
  }

  getBankStatements(page = 0, size = 20): Observable<Page<BankStatementDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<BankStatementDto>>(`${this.base}/bank/statements`, { params });
  }

  autoMatchStatement(id: number): Observable<BankStatementDto> {
    return this.http.post<BankStatementDto>(`${this.base}/bank/statements/${id}/auto-match`, {});
  }

  reconcileStatement(id: number): Observable<BankStatementDto> {
    return this.http.post<BankStatementDto>(`${this.base}/bank/statements/${id}/reconcile`, {});
  }

  getDashboard(): Observable<FinanceDashboardDto> {
    return this.http.get<FinanceDashboardDto>(`${this.base}/finance/dashboard`);
  }
}
