import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SalesOrderItemDto {
  id: number;
  lineNo: number;
  productId: number;
  productCode: string;
  productName: string;
  orderedQty: number;
  reservedQty: number;
  packedQty: number;
  dispatchedQty: number;
  pendingQty: number;
  unitPrice: number;
  customerSpecRef: string | null;
  notes: string | null;
  fulfillmentStatus: string;
}

export interface SalesOrderDto {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  orderDate: string;
  requiredDate: string | null;
  deliveryAddress: string | null;
  paymentTerms: string | null;
  notes: string | null;
  status: string;
  totalValue: number;
  items: SalesOrderItemDto[];
  allowedActions: string[];
}

export interface DispatchItemDto {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  fgBatchId: number;
  batchNumber: string;
  quantity: number;
  unitPrice: number | null;
  customerSpecRef: string | null;
}

export interface DispatchDto {
  id: number;
  dispatchNumber: string;
  salesOrderId: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  dispatchDate: string;
  transporter: string | null;
  vehicleNo: string | null;
  lrNumber: string | null;
  deliveryAddress: string;
  invoiceReference: string | null;
  status: string;
  notes: string | null;
  items: DispatchItemDto[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** Mirrors CreateSalesOrderRequest and AddSalesOrderItemRequest on the backend. */
export interface CreateSalesOrderRequest {
  customerId: number;
  orderDate: string;
  requiredDate: string | null;
  deliveryAddress: string | null;
  paymentTerms: string | null;
  notes: string | null;
  items: {
    productId: number;
    orderedQty: number;
    unitPrice: number | null;
    customerSpecRef: string | null;
    notes: string | null;
  }[];
}

/** Mirrors ReserveRequest on the backend. */
export interface ReserveRequest {
  salesOrderItemId: number;
  fgBatchId: number;
  warehouseId: number;
  locationId: number;
  qty: number;
}

/** Mirrors CreateDispatchRequest and DispatchLineRequest on the backend. */
export interface CreateDispatchRequest {
  salesOrderId: number;
  dispatchDate: string;
  transporter: string | null;
  vehicleNo: string | null;
  lrNumber: string | null;
  deliveryAddress: string;
  invoiceReference: string | null;
  notes: string | null;
  lines: {
    salesOrderItemId: number;
    fgBatchId: number;
    quantity: number;
    warehouseId: number;
    locationId: number;
    unitPrice: number | null;
  }[];
}

@Injectable({ providedIn: 'root' })
export class SalesApi {
  private http = inject(HttpClient);

  getSalesOrders(status?: string, customerId?: number, page = 0, size = 20): Observable<PageResponse<SalesOrderDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (customerId) params = params.set('customerId', customerId);
    return this.http.get<PageResponse<SalesOrderDto>>('/api/sales/orders', { params });
  }

  getSalesOrder(id: number): Observable<SalesOrderDto> {
    return this.http.get<SalesOrderDto>(`/api/sales/orders/${id}`);
  }

  createOrder(request: CreateSalesOrderRequest): Observable<SalesOrderDto> {
    return this.http.post<SalesOrderDto>('/api/sales/orders', request);
  }

  reserve(request: ReserveRequest): Observable<unknown> {
    return this.http.post('/api/sales/reservations', request);
  }

  createDispatch(request: CreateDispatchRequest): Observable<DispatchDto> {
    return this.http.post<DispatchDto>('/api/sales/dispatches', request);
  }

  confirmOrder(id: number): Observable<SalesOrderDto> {
    return this.http.post<SalesOrderDto>(`/api/sales/orders/${id}/confirm`, {});
  }

  approveOrder(id: number): Observable<SalesOrderDto> {
    return this.http.post<SalesOrderDto>(`/api/sales/orders/${id}/approve`, {});
  }

  cancelOrder(id: number, reason: string): Observable<SalesOrderDto> {
    return this.http.post<SalesOrderDto>(`/api/sales/orders/${id}/cancel`, { reason });
  }

  getDispatches(customerId?: number, page = 0, size = 20): Observable<PageResponse<DispatchDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (customerId) params = params.set('customerId', customerId);
    return this.http.get<PageResponse<DispatchDto>>('/api/sales/dispatches', { params });
  }

  getDispatch(id: number): Observable<DispatchDto> {
    return this.http.get<DispatchDto>(`/api/sales/dispatches/${id}`);
  }
}
