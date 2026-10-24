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
  taxSlabCode?: string | null;
  supplyType?: string | null;
  taxLines?: TaxLineDto[];
  currencyCode?: string;
  exchangeRate?: number;
  totalInr?: number;
  priceVarianceInr?: number;
  debitedAmount?: number;
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
  creditedAmount?: number;
  supplyType?: string | null;
  taxLines?: TaxLineDto[];
  currencyCode?: string;
  exchangeRate?: number;
  totalInr?: number;
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
  taxSlabCode?: string;
  currencyCode?: string;
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

/** Mirrors TaxDtos: what a document is taxed, and why. */
export interface TaxLineDto {
  taxCode: string;
  taxType: string;
  ratePct: number;
  taxableAmount: number;
  taxAmount: number;
}

export interface SlabComponentDto {
  supplyType: string;
  taxCode: string;
  taxType: string;
  ratePct: number;
}

export interface SlabDto {
  slabCode: string;
  description: string;
  components: SlabComponentDto[];
}

export interface DeterminationDto {
  slabCode: string;
  supplyType: string;
  companyStateCode: string;
  partnerStateCode: string | null;
  partnerStateAssumed: boolean;
  taxableAmount: number;
  taxTotal: number;
  lines: TaxLineDto[];
}

export interface ProductSlabDto {
  productId: number;
  slabCode: string | null;
}

/** 1 unit of fromCurrency = rate INR for one fiscal period. */
export interface ExchangeRateDto {
  id: number;
  fromCurrency: string;
  toCurrency: string;
  periodId: number;
  periodName: string;
  rate: number;
}

export interface ExchangeRateRequest {
  fromCurrency: string;
  periodId: number;
  rate: number;
}

/** Mirrors ApDebitNoteDtos. */
export interface ApDebitNoteDto {
  id: number;
  debitNoteNumber: string;
  apInvoiceId: number;
  invoiceNumber: string;
  currencyCode: string;
  debitDate: string;
  reason: string;
  subtotal: number;
  taxTotal: number;
  totalAmount: number;
  status: 'DRAFT' | 'POSTED' | 'CANCELLED';
  journalHeaderId: number | null;
  createdBy: number | null;
  approvedBy: number | null;
  approvedAt: string | null;
}

export interface CreateDebitNoteRequest {
  apInvoiceId: number;
  debitDate: string;
  subtotal: number;
  reason: string;
}

/** Mirrors CreditNoteDtos. */
export interface CreditNoteDto {
  id: number;
  creditNoteNumber: string;
  arInvoiceId: number;
  invoiceNumber: string;
  currencyCode: string;
  creditDate: string;
  reason: string;
  subtotal: number;
  taxTotal: number;
  totalAmount: number;
  status: 'DRAFT' | 'POSTED' | 'CANCELLED';
  journalHeaderId: number | null;
  createdBy: number | null;
  approvedBy: number | null;
  approvedAt: string | null;
  returnFgBatchId: number | null;
  returnQuantity: number | null;
  returnTransactionNumber: string | null;
  taxLines: TaxLineDto[];
}

export interface CreateCreditNoteRequest {
  arInvoiceId: number;
  creditDate: string;
  subtotal: number;
  reason: string;
  returnFgBatchId?: number;
  returnQuantity?: number;
  returnWarehouseId?: number;
  returnLocationId?: number;
}

/** One payment (AP) or receipt (AR) with the realised exchange difference booked with it. */
export interface SettlementDto {
  id: number;
  reference: string;
  date: string;
  amount: number;
  paymentMode: string;
  referenceNumber: string | null;
  exchangeRate: number;
  carryingInr: number;
  fxDifference: number;
  journalHeaderId: number | null;
}

export interface BudgetVsActualRow {
  accountId: number;
  accountCode: string;
  accountName: string;
  accountType: string;
  normalBalance: string;
  budgeted: number;
  actual: number;
  variance: number;
  variancePct: number | null;
  favourable: boolean;
}

export interface BudgetVsActualDto {
  fiscalYear: number;
  periodId: number | null;
  periodName: string | null;
  rows: BudgetVsActualRow[];
  totalBudgeted: number;
  totalActual: number;
}

export interface BudgetImportResultDto {
  imported: number;
  created: number;
  updated: number;
}

export interface BudgetRequest {
  glAccountId: number;
  fiscalYear: number;
  periodId: number;
  budgetedAmount: number;
  budgetName: string | null;
}

export interface FxRevaluationRunResult {
  apCount: number;
  arCount: number;
  apGainInr: number;
  apLossInr: number;
  arGainInr: number;
  arLossInr: number;
}

export interface FxRevaluationDto {
  id: number;
  periodId: number;
  periodName: string;
  documentType: string;
  documentId: number;
  documentNumber: string;
  currencyCode: string;
  remainingForeign: number;
  bookingRate: number;
  closingRate: number;
  differenceInr: number;
  journalHeaderId: number | null;
}

// R9-5.1 Financial Statements DTOs

export interface FinancialStatementRow {
  glAccountId: number;
  accountCode: string;
  accountName: string;
  accountType: string;
  balance: number;
}

export interface ProfitLossStatement {
  periodId: number;
  periodName: string;
  fiscalYear: number;
  revenue: FinancialStatementRow[];
  cogs: FinancialStatementRow[];
  expenses: FinancialStatementRow[];
  totalRevenue: number;
  totalCogs: number;
  grossProfit: number;
  totalExpenses: number;
  netIncome: number;
}

export interface BalanceSheetStatement {
  periodId: number;
  periodName: string;
  assets: FinancialStatementRow[];
  liabilities: FinancialStatementRow[];
  equity: FinancialStatementRow[];
  totalAssets: number;
  totalLiabilities: number;
  retainedEarnings: number;
  totalEquity: number;
  totalLiabilitiesAndEquity: number;
}

export interface CashFlowLine {
  activity: string;
  journalType: string;
  sourceModule: string;
  netCash: number;
}

export interface CashFlowStatement {
  periodId: number;
  periodName: string;
  lines: CashFlowLine[];
  netOperating: number;
  netInvesting: number;
  netFinancing: number;
  netCashChange: number;
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

  getJournals(page = 0, size = 20, sort?: string): Observable<Page<JournalHeaderDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (sort) params = params.set('sort', sort);
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

  finalizePeriodClose(id: number, reason: string): Observable<FiscalPeriodDto> {
    return this.http.post<FiscalPeriodDto>(`${this.base}/periods/${id}/finalize-close`, { reason });
  }

  reopenPeriod(id: number, reason: string): Observable<FiscalPeriodDto> {
    return this.http.post<FiscalPeriodDto>(`${this.base}/periods/${id}/reopen`, { reason });
  }

  getGlAccounts(): Observable<GlAccountDto[]> {
    return this.http.get<GlAccountDto[]>(`${this.base}/gl/accounts`);
  }

  getTrialBalance(periodId: number): Observable<TrialBalanceRow[]> {
    const params = new HttpParams().set('periodId', periodId);
    return this.http.get<TrialBalanceRow[]>(`${this.base}/gl/trial-balance`, { params });
  }

  getApInvoices(page = 0, size = 20, sort?: string): Observable<Page<ApInvoiceDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (sort) params = params.set('sort', sort);
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

  getArInvoices(page = 0, size = 20, sort?: string): Observable<Page<ArInvoiceDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (sort) params = params.set('sort', sort);
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

  getTaxSlabs(): Observable<SlabDto[]> {
    return this.http.get<SlabDto[]>(`${this.base}/finance/tax/slabs`);
  }

  determineTax(direction: 'PURCHASE' | 'SALES', slab: string, partnerId: number | null, amount: number) {
    let params = new HttpParams().set('direction', direction).set('slab', slab).set('amount', amount);
    if (partnerId) params = params.set('partnerId', partnerId);
    return this.http.get<DeterminationDto>(`${this.base}/finance/tax/determine`, { params });
  }

  getProductSlab(productId: number): Observable<ProductSlabDto> {
    return this.http.get<ProductSlabDto>(`${this.base}/finance/tax/product-slabs/${productId}`);
  }

  assignProductSlab(productId: number, slabCode: string | null): Observable<ProductSlabDto> {
    return this.http.put<ProductSlabDto>(`${this.base}/finance/tax/product-slabs/${productId}`, { slabCode });
  }

  getExchangeRates(periodId?: number): Observable<ExchangeRateDto[]> {
    let params = new HttpParams();
    if (periodId) params = params.set('periodId', periodId);
    return this.http.get<ExchangeRateDto[]>(`${this.base}/finance/exchange-rates`, { params });
  }

  saveExchangeRate(request: ExchangeRateRequest): Observable<ExchangeRateDto> {
    return this.http.put<ExchangeRateDto>(`${this.base}/finance/exchange-rates`, request);
  }

  getCreditNotes(page = 0, size = 20): Observable<Page<CreditNoteDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<CreditNoteDto>>(`${this.base}/ar/credit-notes`, { params });
  }

  createCreditNote(request: CreateCreditNoteRequest): Observable<CreditNoteDto> {
    return this.http.post<CreditNoteDto>(`${this.base}/ar/credit-notes`, request);
  }

  approveCreditNote(id: number): Observable<CreditNoteDto> {
    return this.http.post<CreditNoteDto>(`${this.base}/ar/credit-notes/${id}/approve`, {});
  }

  cancelCreditNote(id: number): Observable<CreditNoteDto> {
    return this.http.post<CreditNoteDto>(`${this.base}/ar/credit-notes/${id}/cancel`, {});
  }

  getDebitNotes(page = 0, size = 20): Observable<Page<ApDebitNoteDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ApDebitNoteDto>>(`${this.base}/ap/debit-notes`, { params });
  }

  createDebitNote(request: CreateDebitNoteRequest): Observable<ApDebitNoteDto> {
    return this.http.post<ApDebitNoteDto>(`${this.base}/ap/debit-notes`, request);
  }

  approveDebitNote(id: number): Observable<ApDebitNoteDto> {
    return this.http.post<ApDebitNoteDto>(`${this.base}/ap/debit-notes/${id}/approve`, {});
  }

  cancelDebitNote(id: number): Observable<ApDebitNoteDto> {
    return this.http.post<ApDebitNoteDto>(`${this.base}/ap/debit-notes/${id}/cancel`, {});
  }

  getApPayments(invoiceId: number): Observable<SettlementDto[]> {
    return this.http.get<SettlementDto[]>(`${this.base}/ap/invoices/${invoiceId}/payments`);
  }

  getArReceipts(invoiceId: number): Observable<SettlementDto[]> {
    return this.http.get<SettlementDto[]>(`${this.base}/ar/invoices/${invoiceId}/receipts`);
  }

  getBudgetVsActual(fiscalYear: number, periodId?: number): Observable<BudgetVsActualDto> {
    let params = new HttpParams().set('fiscalYear', fiscalYear);
    if (periodId) params = params.set('periodId', periodId);
    return this.http.get<BudgetVsActualDto>(`${this.base}/config/budgets/vs-actual`, { params });
  }

  saveBudget(request: BudgetRequest): Observable<unknown> {
    return this.http.post<unknown>(`${this.base}/config/budgets`, request);
  }

  importBudgetCsv(fiscalYear: number, file: File): Observable<BudgetImportResultDto> {
    const body = new FormData();
    body.append('file', file);
    const params = new HttpParams().set('fiscalYear', fiscalYear);
    return this.http.post<BudgetImportResultDto>(`${this.base}/config/budgets/import`, body, { params });
  }

  getDashboard(): Observable<FinanceDashboardDto> {
    return this.http.get<FinanceDashboardDto>(`${this.base}/finance/dashboard`);
  }

  runFxRevaluation(periodId: number): Observable<FxRevaluationRunResult> {
    const params = new HttpParams().set('periodId', periodId);
    return this.http.post<FxRevaluationRunResult>(`${this.base}/finance/fx-revaluation/run`, null, { params });
  }

  listFxRevaluations(periodId: number): Observable<FxRevaluationDto[]> {
    const params = new HttpParams().set('periodId', periodId);
    return this.http.get<FxRevaluationDto[]>(`${this.base}/finance/fx-revaluation`, { params });
  }

  getProfitLoss(periodId: number): Observable<ProfitLossStatement> {
    const params = new HttpParams().set('periodId', periodId);
    return this.http.get<ProfitLossStatement>(`${this.base}/gl/profit-loss`, { params });
  }

  getBalanceSheet(periodId: number): Observable<BalanceSheetStatement> {
    const params = new HttpParams().set('periodId', periodId);
    return this.http.get<BalanceSheetStatement>(`${this.base}/gl/balance-sheet`, { params });
  }

  getCashFlow(periodId: number): Observable<CashFlowStatement> {
    const params = new HttpParams().set('periodId', periodId);
    return this.http.get<CashFlowStatement>(`${this.base}/gl/cash-flow`, { params });
  }
}
