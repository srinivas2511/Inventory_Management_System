import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface MrpRunDto {
  id: number;
  runNumber: string;
  initiatedAt: string;
  completedAt: string | null;
  planningHorizonDays: number;
  includeSafetyStock: boolean;
  status: 'INITIATED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  initiatedBy: number;
  totalProposals: number | null;
  capacityOverloads: number | null;
  notes: string | null;
  errorMessage: string | null;
}

export interface RunMrpRequest {
  horizonDays: number | null;
  includeSafetyStock: boolean | null;
}

export interface MrpProposalDto {
  id: number;
  mrpRunId: number;
  proposalType: 'PURCHASE' | 'PRODUCTION';
  materialId: number | null;
  materialCode: string | null;
  materialName: string | null;
  productId: number | null;
  productCode: string | null;
  productName: string | null;
  bomId: number | null;
  uom: string | null;
  requiredQty: number;
  proposedQty: number;
  needDate: string;
  proposedOrderDate: string;
  leadTimeDays: number;
  leadTimeAdjusted: boolean;
  sourceDemandType: string | null;
  sourceDemandId: number | null;
  lotSizeRule: string | null;
  machineHoursRequired: number | null;
  capacityOverload: boolean;
  status: 'OPEN' | 'CONVERTED' | 'DISCARDED';
  convertedDocumentType: string | null;
  convertedDocumentId: number | null;
  discardReason: string | null;
}

export interface ProposalFilter {
  runId?: number | null;
  type?: string | null;
  status?: string | null;
  capacityOverload?: boolean | null;
}

export interface ConvertAllResultDto {
  converted: number;
  proposalIds: number[];
}

export interface DemandForecastDto {
  id: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  targetDate: string;
  quantity: number;
  notes: string | null;
}

export interface SaveDemandForecastRequest {
  productId: number;
  targetDate: string;
  quantity: number;
  notes: string | null;
}

export type LotSizeRule = 'EXACT' | 'FIXED' | 'MIN_MAX' | 'EOQ';

export interface PlanningParamDto {
  id: number;
  itemType: 'MATERIAL' | 'PRODUCT';
  materialId: number | null;
  productId: number | null;
  itemCode: string | null;
  itemName: string | null;
  planningStrategy: 'MTS' | 'MTO';
  lotSizeRule: LotSizeRule;
  fixedLotQty: number | null;
  minLotQty: number | null;
  maxLotQty: number | null;
  eoqAnnualDemand: number | null;
  eoqOrderCost: number | null;
  eoqHoldingCost: number | null;
  leadTimeDays: number | null;
}

export interface SavePlanningParamRequest {
  materialId: number | null;
  productId: number | null;
  planningStrategy: 'MTS' | 'MTO';
  lotSizeRule: LotSizeRule;
  fixedLotQty: number | null;
  minLotQty: number | null;
  maxLotQty: number | null;
  eoqAnnualDemand: number | null;
  eoqOrderCost: number | null;
  eoqHoldingCost: number | null;
  leadTimeDays: number | null;
}

@Injectable({ providedIn: 'root' })
export class MrpApi {
  private http = inject(HttpClient);
  private base = '/api/mrp';

  getRuns(page = 0, size = 20): Observable<Page<MrpRunDto>> {
    return this.http.get<Page<MrpRunDto>>(`${this.base}/runs`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  runMrp(req: RunMrpRequest): Observable<MrpRunDto> {
    return this.http.post<MrpRunDto>(`${this.base}/runs`, req);
  }

  getProposals(filter: ProposalFilter, page = 0, size = 50): Observable<Page<MrpProposalDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.runId) params = params.set('runId', filter.runId);
    if (filter.type) params = params.set('type', filter.type);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.capacityOverload != null) params = params.set('capacityOverload', filter.capacityOverload);
    return this.http.get<Page<MrpProposalDto>>(`${this.base}/proposals`, { params });
  }

  convertToRequisition(id: number): Observable<MrpProposalDto> {
    return this.http.post<MrpProposalDto>(`${this.base}/proposals/${id}/convert-to-pr`, {});
  }

  convertToProductionOrder(id: number): Observable<MrpProposalDto> {
    return this.http.post<MrpProposalDto>(`${this.base}/proposals/${id}/convert-to-production-order`, {});
  }

  discardProposal(id: number, reason: string): Observable<MrpProposalDto> {
    return this.http.post<MrpProposalDto>(`${this.base}/proposals/${id}/discard`, { reason });
  }

  convertAll(runId: number, type: 'PURCHASE' | 'PRODUCTION'): Observable<ConvertAllResultDto> {
    return this.http.post<ConvertAllResultDto>(
      `${this.base}/runs/${runId}/convert-all`,
      {},
      {
        params: new HttpParams().set('type', type),
      },
    );
  }

  getForecasts(page = 0, size = 50): Observable<Page<DemandForecastDto>> {
    return this.http.get<Page<DemandForecastDto>>(`${this.base}/forecasts`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  createForecast(req: SaveDemandForecastRequest): Observable<DemandForecastDto> {
    return this.http.post<DemandForecastDto>(`${this.base}/forecasts`, req);
  }

  deleteForecast(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/forecasts/${id}`);
  }

  getPlanningParams(): Observable<PlanningParamDto[]> {
    return this.http.get<PlanningParamDto[]>(`${this.base}/planning-params`);
  }

  savePlanningParam(req: SavePlanningParamRequest): Observable<PlanningParamDto> {
    return this.http.put<PlanningParamDto>(`${this.base}/planning-params`, req);
  }

  deletePlanningParam(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/planning-params/${id}`);
  }
}
