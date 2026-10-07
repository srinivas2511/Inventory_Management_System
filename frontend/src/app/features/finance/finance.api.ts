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
  costCentreId?: number | null;
  profitCentreId?: number | null;
}

export interface JournalHeaderDto {
  id: number;
  journalNumber: string;
  journalType: string;
  sourceModule?: string;
  sourceDocumentType?: string | null;
  sourceDocumentId?: number | null;
  postingDate: string;
  periodId: number;
  description: string;
  currencyCode: string;
  exchangeRate?: number;
  status: string;
  reversalOfId?: number | null;
  preparedBy?: number;
  approvedBy?: number;
  approvedAt?: string | null;
  postedBy?: number;
  postedAt?: string | null;
  lines: JournalLineDto[];
}

/** Mirrors JournalLineRequest: a line carries a debit or a credit, never both. */
export interface JournalLineRequest {
  glAccountId: number;
  debitAmount: number;
  creditAmount: number;
  description: string | null;
}

/** Mirrors JournalCreateRequest. */
export interface JournalCreateRequest {
  journalType: string;
  postingDate: string;
  description: string;
  currencyCode: string;
  lines: JournalLineRequest[];
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
  supplierInvoiceNo?: string | null;
  supplierId: number;
  invoiceDate: string;
  dueDate: string;
  subtotal?: number;
  taxTotal?: number;
  totalAmount: number;
  paidAmount: number;
  balanceAmount: number;
  status: string;
  periodId?: number;
  journalHeaderId?: number | null;
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
  dispatchId?: number | null;
  invoiceDate: string;
  dueDate: string;
  subtotal?: number;
  taxTotal?: number;
  totalAmount: number;
  receivedAmount: number;
  balanceAmount: number;
  status: string;
  periodId?: number;
  journalHeaderId?: number | null;
}

/** Mirrors ApInvoiceCreateRequest. The goods receipt is mandatory: the invoice is matched against it. */
export interface ApInvoiceCreateRequest {
  supplierId: number;
  supplierInvoiceNo: string | null;
  invoiceDate: string;
  dueDate: string;
  goodsReceiptId: number;
  subtotal: number;
  taxTotal: number | null;
}

/** Mirrors ApPaymentRequest (paying a supplier) and ArReceiptRequest (a customer paying) - the payload is the same. */
export interface ApPaymentRequest {
  paymentDate: string;
  amount: number;
  paymentMode: string;
  referenceNumber: string | null;
}

export interface ArReceiptRequest {
  receiptDate: string;
  amount: number;
  paymentMode: string;
  referenceNumber: string | null;
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

  createJournal(request: JournalCreateRequest): Observable<JournalHeaderDto> {
    return this.http.post<JournalHeaderDto>(`${this.base}/journals`, request);
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

  createApInvoice(request: ApInvoiceCreateRequest): Observable<ApInvoiceDto> {
    return this.http.post<ApInvoiceDto>(`${this.base}/ap/invoices`, request);
  }

  openApInvoice(id: number): Observable<ApInvoiceDto> {
    return this.http.put<ApInvoiceDto>(`${this.base}/ap/invoices/${id}/open`, {});
  }

  cancelApInvoice(id: number): Observable<ApInvoiceDto> {
    return this.http.put<ApInvoiceDto>(`${this.base}/ap/invoices/${id}/cancel`, {});
  }

  recordApPayment(id: number, request: ApPaymentRequest): Observable<ApInvoiceDto> {
    return this.http.post<ApInvoiceDto>(`${this.base}/ap/invoices/${id}/payments`, request);
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

  recordArReceipt(id: number, request: ArReceiptRequest): Observable<ArInvoiceDto> {
    return this.http.post<ArInvoiceDto>(`${this.base}/ar/invoices/${id}/receipts`, request);
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
