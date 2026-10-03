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

export type ZoneType = 'BULK' | 'PICKING' | 'RECEIVING' | 'DISPATCH' | 'QC' | 'STAGING';
export type BinType = 'STANDARD' | 'OVERSIZED' | 'QUARANTINE' | 'QC';
export type PutAwayStrategy = 'FIXED_BIN' | 'NEAREST_EMPTY' | 'BULK_FIRST';
export type MovementType = 'PUTAWAY' | 'PICKING' | 'INTERNAL_TRANSFER' | 'CYCLE_COUNT' | 'CROSS_DOCK';
export type TransferOrderStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type TransferLineStatus = 'OPEN' | 'PARTIAL' | 'CONFIRMED' | 'CANCELLED';
export type WaveStatus = 'CREATED' | 'RELEASED' | 'COMPLETED' | 'CANCELLED';
export type CycleCountType = 'ZONE' | 'ABC' | 'RANDOM';
export type CycleCountStatus = 'CREATED' | 'IN_PROGRESS' | 'REVIEW' | 'POSTED' | 'CANCELLED';

export const ZONE_TYPES: ZoneType[] = ['BULK', 'PICKING', 'RECEIVING', 'DISPATCH', 'QC', 'STAGING'];
export const BIN_TYPES: BinType[] = ['STANDARD', 'OVERSIZED', 'QUARANTINE', 'QC'];
export const STRATEGIES: PutAwayStrategy[] = ['FIXED_BIN', 'NEAREST_EMPTY', 'BULK_FIRST'];
export const MOVEMENT_LABELS: Record<MovementType, string> = {
  PUTAWAY: 'Put-away',
  PICKING: 'Picking',
  INTERNAL_TRANSFER: 'Transfer',
  CYCLE_COUNT: 'Cycle count',
  CROSS_DOCK: 'Cross-dock',
};

export interface ZoneDto {
  id: number;
  warehouseId: number;
  zoneCode: string;
  name: string;
  zoneType: ZoneType;
  active: boolean;
  binCount: number;
  version: number;
}

export interface SaveZoneRequest {
  warehouseId: number;
  zoneCode: string;
  name: string;
  zoneType: ZoneType;
  active: boolean | null;
  version: number | null;
}

export interface BinDto {
  id: number;
  zoneId: number;
  zoneCode: string;
  zoneType: ZoneType;
  warehouseId: number;
  locationId: number;
  binCode: string;
  aisle: string | null;
  rack: string | null;
  level: string | null;
  binPosition: string | null;
  binType: BinType;
  maxWeightKg: number | null;
  maxVolumeM3: number | null;
  currentWeightKg: number;
  allowedMaterialTypes: string | null;
  active: boolean;
  occupied: boolean;
  version: number;
}

export interface SaveBinRequest {
  zoneId: number;
  binCode: string;
  aisle: string | null;
  rack: string | null;
  level: string | null;
  binPosition: string | null;
  binType: BinType | null;
  maxWeightKg: number | null;
  maxVolumeM3: number | null;
  allowedMaterialTypes: string | null;
  locationType: string | null;
  active: boolean | null;
  version: number | null;
}

export interface BinStockLineDto {
  itemType: 'MATERIAL' | 'PRODUCT';
  itemId: number;
  itemCode: string | null;
  itemName: string | null;
  batchId: number | null;
  batchNumber: string | null;
  stockStatus: string;
  qtyOnHand: number;
  qtyReserved: number;
  uom: string | null;
}

export interface BinAssignmentDto {
  id: number;
  binId: number;
  binCode: string;
  materialId: number | null;
  productId: number | null;
  itemCode: string | null;
  itemName: string | null;
  primary: boolean;
}

export interface PutAwayRuleDto {
  id: number;
  warehouseId: number;
  materialType: string | null;
  strategy: PutAwayStrategy;
}

export interface TransferLineDto {
  id: number;
  lineNo: number;
  itemType: 'MATERIAL' | 'PRODUCT';
  itemId: number;
  itemCode: string | null;
  itemName: string | null;
  uom: string | null;
  batchId: number | null;
  batchNumber: string | null;
  stockStatus: string | null;
  fromBinId: number | null;
  fromBinCode: string | null;
  toBinId: number | null;
  toBinCode: string | null;
  fromLocationId: number;
  toLocationId: number | null;
  plannedQty: number;
  confirmedQty: number;
  status: TransferLineStatus;
  warning: string | null;
  confirmedAt: string | null;
}

export interface TransferOrderDto {
  id: number;
  toNumber: string;
  warehouseId: number;
  movementType: MovementType;
  sourceDocumentType: string | null;
  sourceDocumentId: number | null;
  crossDockTargetType: string | null;
  crossDockTargetId: number | null;
  waveId: number | null;
  status: TransferOrderStatus;
  assignedTo: number | null;
  completedAt: string | null;
  notes: string | null;
  createdAt: string;
  version: number;
  lines: TransferLineDto[];
  allowedActions: string[];
}

export interface ConfirmLineRequest {
  lineId: number;
  quantity: number;
  scannedFromBin: string | null;
  scannedToBin: string | null;
  scannedBatch: string | null;
  toBinId: number | null;
}

export interface ConfirmResultDto {
  order: TransferOrderDto;
  warnings: string[];
}

export interface TransferLineRequest {
  itemType: 'MATERIAL' | 'PRODUCT';
  materialId: number | null;
  productId: number | null;
  batchId: number | null;
  fromBinId: number | null;
  fromLocationId: number | null;
  toBinId: number;
  quantity: number;
}

export interface WaveDto {
  id: number;
  waveNumber: string;
  warehouseId: number | null;
  status: WaveStatus;
  releasedAt: string | null;
  notes: string | null;
  salesOrderIds: number[];
  transferOrderIds: number[];
  version: number;
}

export interface PickOrderQty {
  salesOrderId: number;
  orderNumber: string | null;
  transferOrderId: number;
  quantity: number;
}

export interface PickListLineDto {
  zoneId: number | null;
  zoneCode: string | null;
  binId: number | null;
  binCode: string | null;
  locationId: number;
  itemType: string;
  itemId: number;
  itemCode: string | null;
  itemName: string | null;
  batchId: number | null;
  batchNumber: string | null;
  totalQty: number;
  orders: PickOrderQty[];
}

export interface CycleCountLineDto {
  id: number;
  lineNo: number;
  binId: number | null;
  binCode: string | null;
  locationId: number;
  itemType: string;
  itemId: number;
  itemCode: string | null;
  itemName: string | null;
  batchId: number | null;
  batchNumber: string | null;
  stockStatus: string;
  systemQty: number | null;
  countedQty: number | null;
  varianceQty: number | null;
  adjustmentId: number | null;
}

export interface CycleCountDto {
  id: number;
  countNumber: string;
  warehouseId: number;
  countType: CycleCountType;
  zoneId: number | null;
  abcClass: string | null;
  sampleSize: number | null;
  status: CycleCountStatus;
  notes: string | null;
  reviewedAt: string | null;
  postedAt: string | null;
  lineCount: number;
  countedCount: number;
  varianceLineCount: number;
  version: number;
  lines: CycleCountLineDto[];
  allowedActions: string[];
}

export interface CreateCycleCountRequest {
  warehouseId: number;
  countType: CycleCountType;
  zoneId: number | null;
  abcClass: string | null;
  sampleSize: number | null;
  notes: string | null;
}

@Injectable({ providedIn: 'root' })
export class WmsApi {
  private http = inject(HttpClient);
  private base = '/api/wms';

  // zones and bins
  getZones(warehouseId: number): Observable<ZoneDto[]> {
    return this.http.get<ZoneDto[]>(`${this.base}/zones`, { params: new HttpParams().set('warehouseId', warehouseId) });
  }

  saveZone(req: SaveZoneRequest, id: number | null): Observable<ZoneDto> {
    return id
      ? this.http.put<ZoneDto>(`${this.base}/zones/${id}`, req)
      : this.http.post<ZoneDto>(`${this.base}/zones`, req);
  }

  getBins(
    warehouseId: number,
    zoneId: number | null,
    q: string | null,
    page = 0,
    size = 200,
  ): Observable<Page<BinDto>> {
    let params = new HttpParams().set('warehouseId', warehouseId).set('page', page).set('size', size);
    if (zoneId) params = params.set('zoneId', zoneId);
    if (q) params = params.set('q', q);
    return this.http.get<Page<BinDto>>(`${this.base}/bins`, { params });
  }

  saveBin(req: SaveBinRequest, id: number | null): Observable<BinDto> {
    return id
      ? this.http.put<BinDto>(`${this.base}/bins/${id}`, req)
      : this.http.post<BinDto>(`${this.base}/bins`, req);
  }

  getBinStock(id: number): Observable<BinStockLineDto[]> {
    return this.http.get<BinStockLineDto[]>(`${this.base}/bins/${id}/stock`);
  }

  getAssignments(binId: number): Observable<BinAssignmentDto[]> {
    return this.http.get<BinAssignmentDto[]>(`${this.base}/bins/${binId}/assignments`);
  }

  addAssignment(binId: number, materialId: number | null, productId: number | null): Observable<BinAssignmentDto> {
    return this.http.post<BinAssignmentDto>(`${this.base}/bins/${binId}/assignments`, {
      materialId,
      productId,
      primary: true,
    });
  }

  removeAssignment(binId: number, assignmentId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/bins/${binId}/assignments/${assignmentId}`);
  }

  getRules(warehouseId: number): Observable<PutAwayRuleDto[]> {
    return this.http.get<PutAwayRuleDto[]>(`${this.base}/putaway-rules`, {
      params: new HttpParams().set('warehouseId', warehouseId),
    });
  }

  saveRule(warehouseId: number, materialType: string | null, strategy: PutAwayStrategy): Observable<PutAwayRuleDto> {
    return this.http.post<PutAwayRuleDto>(`${this.base}/putaway-rules`, { warehouseId, materialType, strategy });
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/putaway-rules/${id}`);
  }

  // transfer orders
  getTransferOrders(
    movementType: MovementType | null,
    status: TransferOrderStatus | null,
    warehouseId: number | null,
    q: string | null,
    page = 0,
    size = 50,
  ): Observable<Page<TransferOrderDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (movementType) params = params.set('movementType', movementType);
    if (status) params = params.set('status', status);
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    if (q) params = params.set('q', q);
    return this.http.get<Page<TransferOrderDto>>(`${this.base}/transfer-orders`, { params });
  }

  getTransferOrder(id: number): Observable<TransferOrderDto> {
    return this.http.get<TransferOrderDto>(`${this.base}/transfer-orders/${id}`);
  }

  createTransfer(
    warehouseId: number,
    notes: string | null,
    lines: TransferLineRequest[],
  ): Observable<TransferOrderDto> {
    return this.http.post<TransferOrderDto>(`${this.base}/transfer-orders`, { warehouseId, notes, lines });
  }

  putAwayFromReceipt(goodsReceiptId: number): Observable<TransferOrderDto> {
    return this.http.post<TransferOrderDto>(`${this.base}/transfer-orders/putaway/from-receipt/${goodsReceiptId}`, {});
  }

  crossDock(goodsReceiptId: number, targetType: string, targetId: number): Observable<TransferOrderDto> {
    return this.http.post<TransferOrderDto>(`${this.base}/transfer-orders/cross-dock`, {
      goodsReceiptId,
      targetType,
      targetId,
      notes: null,
    });
  }

  assignOrder(id: number, userId: number): Observable<TransferOrderDto> {
    return this.http.post<TransferOrderDto>(`${this.base}/transfer-orders/${id}/assign`, { userId });
  }

  startOrder(id: number): Observable<TransferOrderDto> {
    return this.http.post<TransferOrderDto>(`${this.base}/transfer-orders/${id}/start`, {});
  }

  setTarget(id: number, lineId: number, toBinId: number): Observable<TransferOrderDto> {
    return this.http.post<TransferOrderDto>(`${this.base}/transfer-orders/${id}/set-target`, { lineId, toBinId });
  }

  confirmOrder(id: number, lines: ConfirmLineRequest[]): Observable<ConfirmResultDto> {
    return this.http.post<ConfirmResultDto>(`${this.base}/transfer-orders/${id}/confirm`, { lines });
  }

  cancelOrder(id: number, reason: string): Observable<TransferOrderDto> {
    return this.http.post<TransferOrderDto>(`${this.base}/transfer-orders/${id}/cancel`, { reason });
  }

  // picking and waves
  pickSalesOrder(salesOrderId: number): Observable<TransferOrderDto[]> {
    return this.http.post<TransferOrderDto[]>(`${this.base}/picking/sales-orders/${salesOrderId}`, {});
  }

  getWaves(status: WaveStatus | null, page = 0, size = 50): Observable<Page<WaveDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<Page<WaveDto>>(`${this.base}/waves`, { params });
  }

  createWave(salesOrderIds: number[], warehouseId: number | null, notes: string | null): Observable<WaveDto> {
    return this.http.post<WaveDto>(`${this.base}/waves`, { salesOrderIds, warehouseId, notes });
  }

  releaseWave(id: number): Observable<WaveDto> {
    return this.http.post<WaveDto>(`${this.base}/waves/${id}/release`, {});
  }

  cancelWave(id: number, reason: string): Observable<WaveDto> {
    return this.http.post<WaveDto>(`${this.base}/waves/${id}/cancel`, { reason });
  }

  getPickList(waveId: number, zoneId: number | null): Observable<PickListLineDto[]> {
    let params = new HttpParams();
    if (zoneId) params = params.set('zoneId', zoneId);
    return this.http.get<PickListLineDto[]>(`${this.base}/waves/${waveId}/pick-list`, { params });
  }

  // cycle counts
  getCycleCounts(status: CycleCountStatus | null, page = 0, size = 50): Observable<Page<CycleCountDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<Page<CycleCountDto>>(`${this.base}/cycle-counts`, { params });
  }

  getCycleCount(id: number): Observable<CycleCountDto> {
    return this.http.get<CycleCountDto>(`${this.base}/cycle-counts/${id}`);
  }

  createCycleCount(req: CreateCycleCountRequest): Observable<CycleCountDto> {
    return this.http.post<CycleCountDto>(`${this.base}/cycle-counts`, req);
  }

  recordCounts(id: number, lines: { lineId: number; countedQty: number }[]): Observable<CycleCountDto> {
    return this.http.put<CycleCountDto>(`${this.base}/cycle-counts/${id}/lines`, { lines });
  }

  reviewCycleCount(id: number): Observable<CycleCountDto> {
    return this.http.post<CycleCountDto>(`${this.base}/cycle-counts/${id}/review`, {});
  }

  reopenCycleCount(id: number): Observable<CycleCountDto> {
    return this.http.post<CycleCountDto>(`${this.base}/cycle-counts/${id}/reopen`, {});
  }

  postCycleCount(id: number): Observable<CycleCountDto> {
    return this.http.post<CycleCountDto>(`${this.base}/cycle-counts/${id}/post`, {});
  }

  cancelCycleCount(id: number, reason: string): Observable<CycleCountDto> {
    return this.http.post<CycleCountDto>(`${this.base}/cycle-counts/${id}/cancel`, { reason });
  }
}
