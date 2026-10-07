import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface LookupOption {
  id: number;
  code: string;
  name: string;
}

export interface LookupItem extends LookupOption {
  uom: string;
}

export interface LookupLocation {
  id: number;
  warehouseId: number;
  code: string;
  name: string;
  locationType: string;
}

export interface ReceivablePoItem {
  poItemId: number;
  lineNo: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  uom: string;
  orderedQty: number;
  receivedQty: number;
  remainingQty: number;
}

export interface ReceivablePo {
  id: number;
  poNumber: string;
  supplierId: number;
  supplierName: string;
  status: string;
  items: ReceivablePoItem[];
}

export interface ActiveBom {
  id: number;
  bomNumber: string;
  revision: string;
  baseQuantity: number;
}

export interface FgStock {
  fgBatchId: number;
  batchNumber: string;
  productId: number;
  warehouseId: number;
  locationId: number;
  locationCode: string;
  onHand: number;
  reserved: number;
  available: number;
}

export interface DispatchableItem {
  salesOrderItemId: number;
  lineNo: number;
  productId: number;
  productCode: string;
  productName: string;
  orderedQty: number;
  dispatchedQty: number;
  remainingQty: number;
  unitPrice: number;
}

export interface PickableOrder {
  id: number;
  orderNumber: string;
  customerName: string;
}

export interface DispatchableOrder {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  deliveryAddress: string;
  status: string;
  items: DispatchableItem[];
}

/** A posted goods receipt with value still open to invoice (mirrors LookupDtos.InvoiceableReceipt). */
export interface InvoiceableReceipt {
  id: number;
  grNumber: string;
  receiptDate: string;
  poNumber: string;
  supplierId: number;
  receivedValue: number;
  receivedTax: number;
  invoicedValue: number;
  openValue: number;
}

/** Raw material in one batch at one location, available to issue (mirrors LookupDtos.MaterialStock). */
export interface MaterialStock {
  batchId: number;
  batchNumber: string;
  expiryDate: string | null;
  receivedDate: string;
  locationId: number;
  locationCode: string;
  onHand: number;
  reserved: number;
  available: number;
}

/** Dropdown data for the create forms; readable with the transactional permission, not the master-data ones. */
@Injectable({ providedIn: 'root' })
export class LookupApi {
  private readonly http = inject(HttpClient);

  suppliers(): Observable<LookupOption[]> {
    return this.http.get<LookupOption[]>('/api/lookups/suppliers');
  }

  customers(): Observable<LookupOption[]> {
    return this.http.get<LookupOption[]>('/api/lookups/customers');
  }

  invoiceableReceipts(supplierId?: number): Observable<InvoiceableReceipt[]> {
    let params = new HttpParams();
    if (supplierId) params = params.set('supplierId', supplierId);
    return this.http.get<InvoiceableReceipt[]>('/api/lookups/goods-receipts/invoiceable', { params });
  }

  warehouses(): Observable<LookupOption[]> {
    return this.http.get<LookupOption[]>('/api/lookups/warehouses');
  }

  locations(warehouseId?: number): Observable<LookupLocation[]> {
    let params = new HttpParams();
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    return this.http.get<LookupLocation[]>('/api/lookups/locations', { params });
  }

  materials(): Observable<LookupItem[]> {
    return this.http.get<LookupItem[]>('/api/lookups/materials');
  }

  products(): Observable<LookupItem[]> {
    return this.http.get<LookupItem[]>('/api/lookups/products');
  }

  activeBom(productId: number): Observable<ActiveBom | null> {
    return this.http.get<ActiveBom | null>(`/api/lookups/products/${productId}/active-bom`);
  }

  receivablePurchaseOrders(): Observable<ReceivablePo[]> {
    return this.http.get<ReceivablePo[]>('/api/lookups/purchase-orders/receivable');
  }

  finishedGoodsStock(productId?: number): Observable<FgStock[]> {
    let params = new HttpParams();
    if (productId) params = params.set('productId', productId);
    return this.http.get<FgStock[]>('/api/lookups/fg-stock', { params });
  }

  materialStock(materialId: number, warehouseId: number): Observable<MaterialStock[]> {
    const params = new HttpParams().set('materialId', materialId).set('warehouseId', warehouseId);
    return this.http.get<MaterialStock[]>('/api/lookups/material-stock', { params });
  }

  dispatchableOrders(): Observable<DispatchableOrder[]> {
    return this.http.get<DispatchableOrder[]>('/api/lookups/sales-orders/dispatchable');
  }

  /** Approved sales orders a pick wave can take in; needs only the wave permission, not sales-read rights. */
  pickableOrders(): Observable<PickableOrder[]> {
    return this.http.get<PickableOrder[]>('/api/lookups/sales-orders/approved');
  }
}
