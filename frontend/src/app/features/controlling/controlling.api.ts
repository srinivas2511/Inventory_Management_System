import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface CostCentreDto {
  id: number;
  costCentreCode: string;
  name: string;
  parentId: number | null;
  responsibleUserId: number | null;
  plantId: number | null;
  labourRatePerHour: number;
  machineRatePerHour: number;
  overheadPct: number;
  active: boolean;
}

export interface SaveCostCentreRequest {
  costCentreCode: string;
  name: string;
  parentId: number | null;
  responsibleUserId: number | null;
  plantId: number | null;
  labourRatePerHour: number;
  machineRatePerHour: number;
  overheadPct: number;
}

export interface ProfitCentreDto {
  id: number;
  profitCentreCode: string;
  name: string;
  active: boolean;
}

export interface SaveProfitCentreRequest {
  profitCentreCode: string;
  name: string;
}

export interface CostEstimateLineDto {
  id: number;
  costElement: 'MATERIAL' | 'LABOUR' | 'MACHINE' | 'OVERHEAD';
  itemDescription: string | null;
  materialId: number | null;
  operationId: number | null;
  quantity: number | null;
  uom: string | null;
  unitRate: number | null;
  amount: number;
}

export interface CostEstimateDto {
  id: number;
  estimateNumber: string;
  productId: number;
  productCode: string | null;
  productName: string | null;
  bomId: number;
  bomNumber: string | null;
  costCentreId: number | null;
  status: 'DRAFT' | 'RELEASED' | 'HISTORICAL';
  estimatedAt: string;
  validFrom: string | null;
  validTo: string | null;
  materialCost: number;
  labourCost: number;
  machineCost: number;
  overheadCost: number;
  totalStandardCost: number;
  costLotSize: number;
  unitStandardCost: number;
  releasedAt: string | null;
  lines: CostEstimateLineDto[];
  allowedActions: string[];
}

export interface CreateCostEstimateRequest {
  productId: number;
  bomId: number;
  costCentreId: number | null;
}

export interface VarianceDto {
  id: number;
  productionOrderId: number;
  orderNumber: string | null;
  productId: number;
  productCode: string | null;
  productName: string | null;
  estimateId: number | null;
  goodQty: number;
  standardCost: number;
  actualMaterialCost: number;
  actualLabourCost: number;
  actualMachineCost: number;
  actualOverheadCost: number;
  actualCost: number;
  priceVariance: number;
  quantityVariance: number;
  efficiencyVariance: number;
  otherVariance: number;
  totalVariance: number;
  variancePct: number | null;
  status: 'POSTED' | 'NO_VARIANCE' | 'NO_ESTIMATE';
  journalHeaderId: number | null;
  calculatedAt: string;
}

export interface ReportMeta {
  code: string;
  name: string;
}

export interface ReportResult {
  reportCode: string;
  displayName: string;
  headers: string[];
  rows: Record<string, unknown>[];
  totalRows: number;
}

// R10-6.1 Internal Orders

export interface InternalOrderDto {
  id: number;
  orderNumber: string;
  description: string;
  orderType: string;
  responsibleCostCentreId: number | null;
  budgetAmount: number;
  actualAmount: number;
  status: string;
  settlementCostCentreId: number | null;
  settledAmount: number | null;
  settledAt: string | null;
  settledBy: number | null;
  settlementJournalId: number | null;
  startDate: string | null;
  endDate: string | null;
}

export interface CreateInternalOrderRequest {
  orderNumber: string;
  description: string;
  orderType: string;
  responsibleCostCentreId: number | null;
  budgetAmount: number;
  startDate: string | null;
  endDate: string | null;
}

export interface SettleInternalOrderRequest {
  settlementCostCentreId: number;
  reason: string;
}

export interface InternalOrderCostRow {
  glAccountId: number;
  accountCode: string;
  accountName: string;
  totalDebit: number;
  totalCredit: number;
  netAmount: number;
}

@Injectable({ providedIn: 'root' })
export class ControllingApi {
  private http = inject(HttpClient);
  private base = '/api/controlling';

  getCostCentres(): Observable<CostCentreDto[]> {
    return this.http.get<CostCentreDto[]>(`${this.base}/cost-centres`);
  }

  createCostCentre(req: SaveCostCentreRequest): Observable<CostCentreDto> {
    return this.http.post<CostCentreDto>(`${this.base}/cost-centres`, req);
  }

  updateCostCentre(id: number, req: SaveCostCentreRequest): Observable<CostCentreDto> {
    return this.http.put<CostCentreDto>(`${this.base}/cost-centres/${id}`, req);
  }

  setCostCentreActive(id: number, active: boolean): Observable<CostCentreDto> {
    return this.http.post<CostCentreDto>(`${this.base}/cost-centres/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  getProfitCentres(): Observable<ProfitCentreDto[]> {
    return this.http.get<ProfitCentreDto[]>(`${this.base}/profit-centres`);
  }

  createProfitCentre(req: SaveProfitCentreRequest): Observable<ProfitCentreDto> {
    return this.http.post<ProfitCentreDto>(`${this.base}/profit-centres`, req);
  }

  updateProfitCentre(id: number, req: SaveProfitCentreRequest): Observable<ProfitCentreDto> {
    return this.http.put<ProfitCentreDto>(`${this.base}/profit-centres/${id}`, req);
  }

  setProfitCentreActive(id: number, active: boolean): Observable<ProfitCentreDto> {
    return this.http.post<ProfitCentreDto>(
      `${this.base}/profit-centres/${id}/${active ? 'activate' : 'deactivate'}`,
      {},
    );
  }

  getEstimates(page = 0, size = 20, status?: string): Observable<Page<CostEstimateDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<Page<CostEstimateDto>>(`${this.base}/cost-estimates`, { params });
  }

  getEstimate(id: number): Observable<CostEstimateDto> {
    return this.http.get<CostEstimateDto>(`${this.base}/cost-estimates/${id}`);
  }

  createEstimate(req: CreateCostEstimateRequest): Observable<CostEstimateDto> {
    return this.http.post<CostEstimateDto>(`${this.base}/cost-estimates`, req);
  }

  releaseEstimate(id: number): Observable<CostEstimateDto> {
    return this.http.post<CostEstimateDto>(`${this.base}/cost-estimates/${id}/release`, {});
  }

  getVariances(page = 0, size = 20): Observable<Page<VarianceDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<VarianceDto>>(`${this.base}/variances`, { params });
  }

  calculateVariance(productionOrderId: number): Observable<VarianceDto> {
    return this.http.post<VarianceDto>(`${this.base}/variances/${productionOrderId}/calculate`, {});
  }

  listControllingReports(): Observable<ReportMeta[]> {
    return this.http.get<ReportMeta[]>('/api/reports').pipe(map((all) => all.filter((r) => r.code.startsWith('CTR_'))));
  }

  runReport(code: string, from?: string, to?: string, entityId?: number): Observable<ReportResult> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (entityId) params = params.set('entityId', entityId);
    return this.http.get<ReportResult>(`/api/reports/${code}`, { params });
  }

  // R10-6.1 Internal Orders

  getInternalOrders(): Observable<InternalOrderDto[]> {
    return this.http.get<InternalOrderDto[]>(`${this.base}/internal-orders`);
  }

  getInternalOrder(id: number): Observable<InternalOrderDto> {
    return this.http.get<InternalOrderDto>(`${this.base}/internal-orders/${id}`);
  }

  createInternalOrder(req: CreateInternalOrderRequest): Observable<InternalOrderDto> {
    return this.http.post<InternalOrderDto>(`${this.base}/internal-orders`, req);
  }

  updateInternalOrder(id: number, req: CreateInternalOrderRequest): Observable<InternalOrderDto> {
    return this.http.put<InternalOrderDto>(`${this.base}/internal-orders/${id}`, req);
  }

  getInternalOrderCosts(id: number): Observable<InternalOrderCostRow[]> {
    return this.http.get<InternalOrderCostRow[]>(`${this.base}/internal-orders/${id}/costs`);
  }

  settleInternalOrder(id: number, req: SettleInternalOrderRequest): Observable<InternalOrderDto> {
    return this.http.post<InternalOrderDto>(`${this.base}/internal-orders/${id}/settle`, req);
  }
}
