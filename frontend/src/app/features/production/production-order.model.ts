/** Mirrors ProductionOrderMaterialDto on the backend. */
export interface ProductionOrderMaterialDto {
  id: number;
  materialId: number;
  materialCode: string | null;
  materialName: string | null;
  plannedQty: number;
  issuedQty: number;
  consumedQty: number;
  returnedQty: number;
  scrapQty: number;
  remainingQty: number;
  uom: string;
  availableQty: number | null;
  shortage: number | null;
}

/** Mirrors ProductionOperationDto on the backend. */
export interface ProductionOperationDto {
  id: number;
  sequenceNo: number;
  operationId: number;
  operationName: string | null;
  machineId: number | null;
  machineName: string | null;
  assignedOperatorId: number | null;
  assignedOperatorName: string | null;
  inspectionPoint: boolean;
  status: string;
  inputQty: number;
  goodQty: number;
  rejectedQty: number;
  scrapQty: number;
  finalOperation: boolean;
  startBlockedReason?: string | null;
}

/** Mirrors ProductionOrderDto on the backend. */
export interface ProductionOrderDto {
  id: number;
  orderNumber: string;
  productId: number;
  productCode: string;
  plannedQty: number;
  goodQty: number;
  rejectedQty: number;
  scrapQty: number;
  status: string;
  priority: number;
  startDate: string;
  expectedCompletionDate: string;
  materials: ProductionOrderMaterialDto[];
  operations: ProductionOperationDto[];
  allowedActions: string[];
}

/** Mirrors CreateIssueRequest on the backend. */
export interface CreateIssueRequest {
  productionOrderId: number;
  warehouseId: number;
  lines: IssueLineRequest[];
  remarks: string | null;
}

export interface IssueLineRequest {
  materialId: number;
  quantity: number;
  explicitBatchId: number | null;
}

/** Mirrors IssueDto on the backend. */
export interface IssueDto {
  id: number;
  issueNumber: string;
  productionOrderId: number;
  lines: IssueLineDto[];
}

export interface IssueLineDto {
  materialId: number;
  materialBatchId: number;
  batchNumber: string;
  fromLocationId: number;
  quantity: number;
  unitCost: number;
}

/** Mirrors ReturnMaterialRequest on the backend. */
export interface ReturnMaterialRequest {
  materialId: number;
  quantity: number;
  remarks: string | null;
}

/** Mirrors CloseOrderRequest on the backend. */
export interface CloseOrderRequest {
  writeOffReason: string | null;
}

/** Mirrors AssignOperatorRequest on the backend; a null operator unassigns. */
export interface AssignOperatorRequest {
  operatorId: number | null;
  reason: string | null;
}

/** Mirrors AssignableOperatorDto on the backend. */
export interface AssignableOperatorDto {
  id: number;
  username: string;
  fullName: string;
}

/** Issued material that is neither consumed, returned nor written off: it has to go back to stock or be written off. */
export function leftoverQty(m: ProductionOrderMaterialDto): number {
  return Math.round((m.issuedQty - m.consumedQty - m.returnedQty - m.scrapQty) * 1000) / 1000;
}

/** Orders in these states can take material and have their operations assigned. */
export function isRunning(order: Pick<ProductionOrderDto, 'status'>): boolean {
  return order.status === 'RELEASED' || order.status === 'IN_PROGRESS';
}
