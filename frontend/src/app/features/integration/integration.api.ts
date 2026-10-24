import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type DeliveryStatus = 'PENDING' | 'RETRY' | 'DELIVERED' | 'FAILED';
export const DELIVERY_STATUSES: DeliveryStatus[] = ['PENDING', 'RETRY', 'DELIVERED', 'FAILED'];

export interface WebhookDto {
  id: number;
  webhookCode: string;
  name: string;
  targetUrl: string;
  secretHint: string;
  events: string[];
  active: boolean;
  createdAt: string;
  version: number;
}

/** The only response that carries a signing secret; it is shown once. */
export interface WebhookSecretDto {
  webhook: WebhookDto;
  secret: string;
}

export interface SaveWebhookRequest {
  name: string;
  targetUrl: string;
  events: string[];
  active: boolean | null;
  version: number | null;
}

export interface WebhookDeliveryDto {
  id: number;
  subscriptionId: number;
  eventType: string;
  eventKey: string;
  status: DeliveryStatus;
  retryCount: number;
  nextRetryAt: string | null;
  attemptedAt: string | null;
  deliveredAt: string | null;
  responseStatus: number | null;
  responseBody: string | null;
  lastError: string | null;
  createdAt: string;
}

export interface EInvoiceDto {
  id: number;
  dispatchId: number;
  irn: string | null;
  ackNo: string | null;
  ackDate: string | null;
  status: string;
  generatedAt: string;
}

export interface IoTEvent {
  machineId: number;
  eventType: string;
  readingType: string | null;
  value: number | null;
  unit: string | null;
  timestamp: string | null;
}

export interface ImportRowResult {
  rowNumber: number;
  key: string;
  status: 'OK' | 'CREATED' | 'ERROR';
  message: string | null;
}

export interface ImportResult {
  entity: string;
  dryRun: boolean;
  totalRows: number;
  valid: number;
  created: number;
  failed: number;
  rows: ImportRowResult[];
  rowsTruncated: boolean;
}

@Injectable({ providedIn: 'root' })
export class IntegrationApi {
  private readonly http = inject(HttpClient);

  webhooks(): Observable<WebhookDto[]> {
    return this.http.get<WebhookDto[]>('/api/integration/webhooks');
  }

  eventTypes(): Observable<string[]> {
    return this.http.get<string[]>('/api/integration/webhooks/event-types');
  }

  createWebhook(req: SaveWebhookRequest): Observable<WebhookSecretDto> {
    return this.http.post<WebhookSecretDto>('/api/integration/webhooks', req);
  }

  updateWebhook(id: number, req: SaveWebhookRequest): Observable<WebhookDto> {
    return this.http.put<WebhookDto>(`/api/integration/webhooks/${id}`, req);
  }

  rotateSecret(id: number): Observable<WebhookSecretDto> {
    return this.http.post<WebhookSecretDto>(`/api/integration/webhooks/${id}/rotate-secret`, {});
  }

  testWebhook(id: number): Observable<WebhookDeliveryDto> {
    return this.http.post<WebhookDeliveryDto>(`/api/integration/webhooks/${id}/test`, {});
  }

  deliveries(
    subscriptionId: number | null,
    status: DeliveryStatus | null,
    page = 0,
    size = 20,
  ): Observable<Page<WebhookDeliveryDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (subscriptionId != null) params = params.set('subscriptionId', subscriptionId);
    if (status) params = params.set('status', status);
    return this.http.get<Page<WebhookDeliveryDto>>('/api/integration/webhook-deliveries', { params });
  }

  retryDelivery(id: number): Observable<WebhookDeliveryDto> {
    return this.http.post<WebhookDeliveryDto>(`/api/integration/webhook-deliveries/${id}/retry`, {});
  }

  importEntities(): Observable<string[]> {
    return this.http.get<string[]>('/api/integration/import');
  }

  importFile(entity: string, file: File, dryRun: boolean): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    const params = new HttpParams().set('dryRun', dryRun);
    return this.http.post<ImportResult>(`/api/integration/import/${entity}`, form, { params });
  }

  importTemplate(entity: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`/api/integration/import/${entity}/template`, { observe: 'response', responseType: 'blob' });
  }

  exportEntities(): Observable<string[]> {
    return this.http.get<string[]>('/api/integration/export');
  }

  exportEntity(entity: string, format: 'csv' | 'json', from: string | null, to: string | null) {
    let params = new HttpParams().set('format', format);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get(`/api/integration/export/${entity}`, { params, observe: 'response', responseType: 'blob' });
  }

  tallyVouchers(from: string | null, to: string | null) {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get('/api/integration/tally/vouchers', { params, observe: 'response', responseType: 'blob' });
  }

  tallyLedgers() {
    return this.http.get('/api/integration/tally/ledgers', { observe: 'response', responseType: 'blob' });
  }

  // GST e-invoice (R19-6.5)
  getEInvoices(): Observable<EInvoiceDto[]> {
    return this.http.get<EInvoiceDto[]>('/api/integration/gst-einvoice');
  }

  generateEInvoice(dispatchId: number): Observable<EInvoiceDto> {
    return this.http.post<EInvoiceDto>('/api/integration/gst-einvoice/generate', { dispatchId });
  }

  downloadEInvoicePayload(id: number): Observable<{ payload: string }> {
    return this.http.get<{ payload: string }>(`/api/integration/gst-einvoice/${id}/payload`);
  }

  // IoT ingestion (R19-6.3)
  ingestIoTEvents(events: IoTEvent[]): Observable<{ accepted: number; rejected: number; errors: string[] }> {
    return this.http.post<{ accepted: number; rejected: number; errors: string[] }>('/api/integration/iot/events', {
      events,
    });
  }
}
