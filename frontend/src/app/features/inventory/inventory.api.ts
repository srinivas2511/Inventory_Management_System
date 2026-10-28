import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';
import { Page } from '../master/material/material.api';

export interface InventoryBalanceDto {
  id: number;
  warehouseId: number;
  warehouseName: string;
  locationId: number;
  locationName: string;
  itemType: string;
  materialId?: number;
  productId?: number;
  materialBatchId?: number;
  fgBatchId?: number;
  stockStatus: string;
  qtyOnHand: number;
  qtyReserved: number;
  qtyAvailable: number;
  avgUnitCost: number;
  totalValue: number;
  updatedAt: string;
}

export interface InventoryTransactionDto {
  id: number;
  transactionNumber: string;
  transactionDate: string;
  transactionType: string;
  itemType: string;
  materialId?: number;
  productId?: number;
  materialBatchId?: number;
  warehouseId: number;
  locationId: number;
  stockStatus: string;
  quantity: number;
  uom: string;
  unitCost: number;
  totalCost: number;
  referenceType: string;
  referenceNumber?: string;
  balanceAfter: number;
  remarks?: string;
  reversalOfId?: number;
  createdAt: string;
}

export interface MaterialBatchDto {
  id: number;
  batchNumber: string;
  materialId: number;
  materialCode?: string;
  receivedDate: string;
  expiryDate?: string;
  receivedQty: number;
  remainingQty: number;
  heatNumber?: string;
  certificateNumber?: string;
  unitCost?: number;
  status: string;
}

export interface StockAdjustmentDto {
  id: number;
  adjustmentNumber: string;
  itemType: string;
  materialId?: number;
  productId?: number;
  warehouseId: number;
  locationId: number;
  stockStatus: string;
  systemQty: number;
  countedQty: number;
  varianceQty: number;
  varianceValue?: number;
  reason: string;
  status: string;
  requestedBy: number;
  postedTransactionId?: number;
  createdAt: string;
}

export interface ValuationDto {
  totalValue: number;
  rmValue: number;
  fgValue: number;
}

export interface ValuationLineDto {
  itemType: string;
  itemId: number;
  itemCode: string;
  itemName: string;
  uom: string;
  qtyOnHand: number;
  avgUnitCost: number;
  standardCost: number;
  totalValue: number;
}

export interface ValuationReportDto {
  costMethod: string;
  lines: ValuationLineDto[];
  rmTotal: number;
  fgTotal: number;
  grandTotal: number;
}

export interface TransferRequest {
  itemType: string;
  materialId?: number;
  productId?: number;
  materialBatchId?: number;
  fgBatchId?: number;
  fromWarehouseId: number;
  fromLocationId: number;
  fromStockStatus: string;
  toWarehouseId: number;
  toLocationId: number;
  toStockStatus: string;
  quantity: number;
  uom: string;
  remarks?: string;
}

export interface AdjustmentRequest {
  itemType: string;
  materialId?: number;
  productId?: number;
  batchId?: number;
  warehouseId: number;
  locationId: number;
  stockStatus: string;
  countedQty: number;
  reason: string;
}

/** Mirrors OpeningBalanceLine on the backend. */
export interface OpeningBalanceLine {
  itemType: 'MATERIAL' | 'PRODUCT';
  materialId?: number;
  productId?: number;
  batchId?: number | null;
  warehouseId: number;
  locationId: number;
  stockStatus: string;
  quantity: number;
  uom: string;
  unitCost?: number | null;
  remarks?: string;
}

/** Mirrors PostingResult on the backend. */
export interface PostingResultDto {
  transactionId: number;
  transactionNumber: string;
  balanceAfter: number;
  unitCost: number;
}

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  getBalances(warehouseId?: number, materialId?: number, stockStatus?: string): Observable<InventoryBalanceDto[]> {
    let params = new HttpParams();
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    if (materialId) params = params.set('materialId', materialId);
    if (stockStatus) params = params.set('stockStatus', stockStatus);
    return this.http.get<InventoryBalanceDto[]>(`${this.base}/api/inventory/balances`, { params });
  }

  /** Loads opening stock; each line becomes an OPENING_BALANCE ledger entry (and its journal, Dr stock / Cr 3901). */
  loadOpeningBalances(lines: OpeningBalanceLine[]): Observable<PostingResultDto[]> {
    return this.http.post<PostingResultDto[]>(`${this.base}/api/inventory/opening-balances`, lines);
  }

  getValuation(warehouseId?: number): Observable<ValuationDto> {
    let params = new HttpParams();
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    return this.http.get<ValuationDto>(`${this.base}/api/inventory/balances/valuation`, { params });
  }

  getValuationReport(warehouseId?: number): Observable<ValuationReportDto> {
    let params = new HttpParams();
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    return this.http.get<ValuationReportDto>(`${this.base}/api/inventory/valuation-report`, { params });
  }

  getLedger(
    warehouseId?: number,
    materialId?: number,
    transactionType?: string,
    page = 0,
    size = 50,
  ): Observable<Page<InventoryTransactionDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    if (materialId) params = params.set('materialId', materialId);
    if (transactionType) params = params.set('transactionType', transactionType);
    return this.http.get<Page<InventoryTransactionDto>>(`${this.base}/api/inventory/ledger`, { params });
  }

  getBatches(materialId?: number, status?: string, page = 0, size = 20): Observable<Page<MaterialBatchDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (materialId) params = params.set('materialId', materialId);
    if (status) params = params.set('status', status);
    return this.http.get<Page<MaterialBatchDto>>(`${this.base}/api/inventory/batches`, { params });
  }

  transfer(req: TransferRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/api/inventory/transfers`, req);
  }

  reverse(txId: number, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/api/inventory/ledger/${txId}/reverse`, null, {
      params: new HttpParams().set('reason', reason),
    });
  }

  getAdjustments(status?: string, warehouseId?: number, page = 0, size = 20): Observable<Page<StockAdjustmentDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    return this.http.get<Page<StockAdjustmentDto>>(`${this.base}/api/inventory/adjustments`, { params });
  }

  requestAdjustment(req: AdjustmentRequest): Observable<StockAdjustmentDto> {
    return this.http.post<StockAdjustmentDto>(`${this.base}/api/inventory/adjustments`, req);
  }

  approveAdjustment(approvalId: number, comment: string): Observable<void> {
    return this.http.post<void>(`${this.base}/api/inventory/adjustments/approval-requests/${approvalId}/approve`, {
      comment,
    });
  }

  rejectAdjustment(approvalId: number, comment: string): Observable<void> {
    return this.http.post<void>(`${this.base}/api/inventory/adjustments/approval-requests/${approvalId}/reject`, {
      comment,
    });
  }
}
