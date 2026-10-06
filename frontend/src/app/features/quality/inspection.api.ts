import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from './quality-plus.api';

export interface InspectionItemDto {
  id: number;
  seq: number;
  parameterName: string;
  specification: string | null;
  lowerLimit: number | null;
  upperLimit: number | null;
  actualValue: number | null;
  actualText: string | null;
  unit: string | null;
  result: string;
  deviationNote: string | null;
  mandatory: boolean;
  deviationApprovedBy: number | null;
  deviationApprovedAt: string | null;
}

export interface QualityInspectionDto {
  id: number;
  inspectionNumber: string;
  inspectionType: string;
  status: string;
  goodsReceiptItemId: number | null;
  grNumber: string | null;
  materialBatchId: number | null;
  batchNumber: string | null;
  materialId: number | null;
  materialCode: string | null;
  fgBatchId: number | null;
  productionOrderId: number | null;
  productionOperationId: number | null;
  inspectedQty: number;
  sampleSize: number | null;
  acceptedQty: number | null;
  rejectedQty: number | null;
  holdQty: number | null;
  inspectorId: number | null;
  inspectedAt: string | null;
  decisionAt: string | null;
  remarks: string | null;
  items: InspectionItemDto[];
}

export interface InspectionFgBatchDto {
  id: number;
  batchNumber: string;
  productCode: string;
  productName: string;
  producedQty: number;
  approvedQty: number | null;
  rejectedQty: number | null;
  remainingQty: number | null;
  qualityStatus: string;
}

export interface RejectionReasonDto {
  id: number;
  reasonCode: string;
  name: string;
  appliesTo: string;
  active: boolean;
}

export interface InspectionResultRequest {
  acceptedQty: number;
  rejectedQty: number;
  remarks: string;
  items: { itemId: number; actualValue: number | null; actualText: string | null; deviationNote: string | null }[];
}

export interface FinalInspectionDecisionRequest {
  approvedQty: number;
  rejectedQty: number;
  holdQty: number;
  itemResults: { seq: number; actualValue: number | null; actualText: string | null; deviationNote: string | null }[];
  rejectionReasons: { rejectionReasonId: number; quantity: number; remarks: string | null }[];
  remarks: string;
}

@Injectable({ providedIn: 'root' })
export class InspectionApi {
  private http = inject(HttpClient);
  private base = '/api/quality';

  list(type: string | null, status: string | null, page = 0, size = 50): Observable<Page<QualityInspectionDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (type) params = params.set('type', type);
    if (status) params = params.set('status', status);
    return this.http.get<Page<QualityInspectionDto>>(`${this.base}/inspections`, { params });
  }

  get(id: number): Observable<QualityInspectionDto> {
    return this.http.get<QualityInspectionDto>(`${this.base}/inspections/${id}`);
  }

  submitResult(id: number, body: InspectionResultRequest): Observable<QualityInspectionDto> {
    return this.http.post<QualityInspectionDto>(`${this.base}/inspections/${id}/results`, body);
  }

  accept(id: number, quantities?: { acceptedQty: number; rejectedQty: number }): Observable<QualityInspectionDto> {
    return this.http.post<QualityInspectionDto>(`${this.base}/inspections/${id}/accept`, quantities ?? {});
  }

  reject(id: number, reason: string): Observable<QualityInspectionDto> {
    return this.http.post<QualityInspectionDto>(`${this.base}/inspections/${id}/reject`, { reason });
  }

  createInProcess(productionOperationId: number, inspectedQty: number): Observable<QualityInspectionDto> {
    return this.http.post<QualityInspectionDto>(`${this.base}/inspections/in-process`, {
      productionOperationId,
      inspectedQty,
    });
  }

  createFinal(fgBatchId: number, inspectedQty: number, sampleSize: number | null): Observable<QualityInspectionDto> {
    return this.http.post<QualityInspectionDto>(`${this.base}/inspections/final`, {
      fgBatchId,
      inspectedQty,
      sampleSize,
    });
  }

  completeFinal(id: number, body: FinalInspectionDecisionRequest): Observable<QualityInspectionDto> {
    return this.http.post<QualityInspectionDto>(`${this.base}/inspections/${id}/final-decision`, body);
  }

  recordDeviation(id: number, seq: number, deviationNote: string): Observable<QualityInspectionDto> {
    return this.http.post<QualityInspectionDto>(`${this.base}/inspections/${id}/items/${seq}/deviation`, {
      deviationNote,
    });
  }

  getFgBatch(id: number): Observable<InspectionFgBatchDto> {
    return this.http.get<InspectionFgBatchDto>(`${this.base}/fg-batches/${id}`);
  }

  rejectionReasons(): Observable<RejectionReasonDto[]> {
    return this.http.get<RejectionReasonDto[]>('/api/rejection-reasons');
  }
}
