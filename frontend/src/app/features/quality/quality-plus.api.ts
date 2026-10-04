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

// ---- SPC ------------------------------------------------------------------------------------------------------------

export type SpcChartType = 'XBAR_R' | 'P' | 'C';
export const SPC_CHART_TYPES: SpcChartType[] = ['XBAR_R', 'P', 'C'];

export interface SpcChartSummary {
  id: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  parameterName: string;
  chartType: SpcChartType;
  subgroupSize: number;
  limitsEstablished: boolean;
  active: boolean;
  pointCount: number;
  outOfControlCount: number;
}

export interface SpcPoint {
  id: number;
  inspectionId: number | null;
  subgroupNo: number;
  measurementNo: number;
  measuredValue: number;
  measuredAt: string;
  subgroupMean: number | null;
  subgroupRange: number | null;
  sampleSize: number | null;
  defectCount: number | null;
  pointUcl: number | null;
  pointLcl: number | null;
  outOfControl: boolean;
  violationRule: string | null;
}

export interface SpcChartDetail {
  summary: SpcChartSummary;
  cl: number | null;
  ucl: number | null;
  lcl: number | null;
  clR: number | null;
  uclR: number | null;
  lclR: number | null;
  usl: number | null;
  lsl: number | null;
  limitsEstablishedAt: string | null;
  points: SpcPoint[];
}

export interface SpcCapability {
  chartId: number;
  from: string | null;
  to: string | null;
  cp: number | null;
  cpk: number | null;
  cpu: number | null;
  cpl: number | null;
  mean: number;
  stdDev: number;
  count: number;
}

export interface SaveChartRequest {
  productId: number;
  parameterName: string;
  chartType: SpcChartType;
  subgroupSize: number | null;
  usl: number | null;
  lsl: number | null;
}

export interface AddPointsRequest {
  values?: number[];
  sampleSize?: number | null;
  defectCount?: number | null;
}

// ---- CAPA -----------------------------------------------------------------------------------------------------------

export type CapaStatus = 'OPEN' | 'ROOT_CAUSE_ANALYSIS' | 'ACTION_IN_PROGRESS' | 'VERIFICATION' | 'CLOSED';
export type CapaSourceType = 'CUSTOMER_COMPLAINT' | 'INTERNAL_AUDIT' | 'REJECTION_ANALYSIS' | 'SPC_VIOLATION';
export const CAPA_STATUSES: CapaStatus[] = [
  'OPEN',
  'ROOT_CAUSE_ANALYSIS',
  'ACTION_IN_PROGRESS',
  'VERIFICATION',
  'CLOSED',
];
export const CAPA_SOURCES: CapaSourceType[] = [
  'CUSTOMER_COMPLAINT',
  'INTERNAL_AUDIT',
  'REJECTION_ANALYSIS',
  'SPC_VIOLATION',
];

export interface CapaSummary {
  id: number;
  capaNumber: string;
  title: string;
  sourceType: CapaSourceType;
  sourceId: number | null;
  status: CapaStatus;
  targetCompletionDate: string | null;
  assignedTo: number | null;
  openedAt: string;
  closedAt: string | null;
}

export interface Capa {
  id: number;
  capaNumber: string;
  title: string;
  description: string | null;
  sourceType: CapaSourceType;
  sourceId: number | null;
  sourceLabel: string | null;
  rootCause: string | null;
  correctiveAction: string | null;
  preventiveAction: string | null;
  verificationNotes: string | null;
  targetCompletionDate: string | null;
  status: CapaStatus;
  nextStatuses: CapaStatus[];
  assignedTo: number | null;
  verifiedBy: number | null;
  openedAt: string;
  closedAt: string | null;
}

export interface SaveCapaRequest {
  title: string;
  description: string | null;
  sourceType: CapaSourceType;
  sourceId: number | null;
  rootCause: string | null;
  correctiveAction: string | null;
  preventiveAction: string | null;
  targetCompletionDate: string | null;
  assignedTo: number | null;
}

// ---- complaints -----------------------------------------------------------------------------------------------------

export type ComplaintStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type ComplaintCategory = 'DIMENSIONAL' | 'SURFACE' | 'PERFORMANCE' | 'PACKAGING' | 'DELIVERY' | 'OTHER';
export type ComplaintDisposition = 'REPLACEMENT' | 'CREDIT_NOTE' | 'REWORK' | 'SCRAP';
export const COMPLAINT_STATUSES: ComplaintStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
export const COMPLAINT_CATEGORIES: ComplaintCategory[] = [
  'DIMENSIONAL',
  'SURFACE',
  'PERFORMANCE',
  'PACKAGING',
  'DELIVERY',
  'OTHER',
];
export const COMPLAINT_DISPOSITIONS: ComplaintDisposition[] = ['REPLACEMENT', 'CREDIT_NOTE', 'REWORK', 'SCRAP'];

export interface ComplaintSummary {
  id: number;
  complaintNumber: string;
  customerName: string | null;
  productCode: string | null;
  fgBatchNumber: string | null;
  complaintDate: string;
  category: ComplaintCategory;
  status: ComplaintStatus;
  capaNumber: string | null;
}

export interface Complaint {
  id: number;
  complaintNumber: string;
  customerId: number;
  customerName: string | null;
  productId: number | null;
  productCode: string | null;
  productName: string | null;
  fgBatchId: number | null;
  fgBatchNumber: string | null;
  complaintDate: string;
  receivedBy: number;
  category: ComplaintCategory;
  description: string;
  customerRef: string | null;
  quantityComplained: number | null;
  quantityReturned: number | null;
  disposition: ComplaintDisposition | null;
  resolutionNotes: string | null;
  status: ComplaintStatus;
  nextStatuses: ComplaintStatus[];
  capaId: number | null;
  capaNumber: string | null;
  closedAt: string | null;
  createdAt: string;
}

export interface CustomerLookup {
  id: number;
  code: string;
  name: string;
}

export interface SaveComplaintRequest {
  customerId: number;
  productId: number | null;
  fgBatchId: number | null;
  complaintDate: string;
  category: ComplaintCategory;
  description: string;
  customerRef: string | null;
  quantityComplained: number | null;
}

export interface TransitionComplaintRequest {
  target: ComplaintStatus;
  disposition?: ComplaintDisposition | null;
  quantityReturned?: number | null;
  resolutionNotes?: string | null;
}

export interface TraceStep {
  stepType: string;
  reference: string;
  description: string;
  timestamp: string | null;
}

export interface TraceView {
  queryType: string;
  batchId: number;
  batchNumber: string;
  trail: TraceStep[];
}

// ---- first article inspection ---------------------------------------------------------------------------------------

export type FaiStatus = 'REQUIRED' | 'IN_PROGRESS' | 'PASSED' | 'FAILED';
export type FaiTrigger = 'NEW_PRODUCT' | 'ECO_IMPLEMENTED' | 'MANUAL';
export const FAI_STATUSES: FaiStatus[] = ['REQUIRED', 'IN_PROGRESS', 'PASSED', 'FAILED'];

export interface FaiSummary {
  id: number;
  faiNumber: string;
  productCode: string | null;
  productName: string | null;
  triggerType: FaiTrigger;
  triggerReference: string | null;
  status: FaiStatus;
  createdAt: string;
  completedAt: string | null;
}

export interface Fai {
  id: number;
  faiNumber: string;
  productId: number;
  productCode: string | null;
  productName: string | null;
  triggerType: FaiTrigger;
  triggerReference: string | null;
  inspectionPlanId: number | null;
  planCode: string | null;
  productionOrderId: number | null;
  productionOrderNumber: string | null;
  fgBatchId: number | null;
  fgBatchNumber: string | null;
  inspectionId: number | null;
  inspectionNumber: string | null;
  status: FaiStatus;
  nextStatuses: FaiStatus[];
  resultSummary: string | null;
  completedBy: number | null;
  completedAt: string | null;
  createdAt: string;
}

export interface FaiReportItem {
  itemId: number;
  seq: number;
  parameterName: string;
  specification: string | null;
  lowerLimit: number | null;
  upperLimit: number | null;
  unit: string | null;
  actualValue: number | null;
  actualText: string | null;
  result: string;
  mandatory: boolean;
}

export interface FaiReport {
  fai: Fai;
  items: FaiReportItem[];
  generatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class QualityPlusApi {
  private http = inject(HttpClient);

  // SPC
  listCharts(productId: number | null, q: string | null): Observable<SpcChartSummary[]> {
    let params = new HttpParams();
    if (productId) params = params.set('productId', productId);
    if (q) params = params.set('q', q);
    return this.http.get<SpcChartSummary[]>('/api/spc/charts', { params });
  }

  getChart(id: number): Observable<SpcChartDetail> {
    return this.http.get<SpcChartDetail>(`/api/spc/charts/${id}`);
  }

  createChart(req: SaveChartRequest): Observable<SpcChartDetail> {
    return this.http.post<SpcChartDetail>('/api/spc/charts', req);
  }

  updateChart(
    id: number,
    req: { usl: number | null; lsl: number | null; active: boolean | null },
  ): Observable<SpcChartDetail> {
    return this.http.put<SpcChartDetail>(`/api/spc/charts/${id}`, req);
  }

  addPoints(id: number, req: AddPointsRequest): Observable<SpcChartDetail> {
    return this.http.post<SpcChartDetail>(`/api/spc/charts/${id}/points`, req);
  }

  recalculateLimits(id: number): Observable<SpcChartDetail> {
    return this.http.post<SpcChartDetail>(`/api/spc/charts/${id}/recalculate-limits`, {});
  }

  capability(id: number, from: string | null, to: string | null): Observable<SpcCapability> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<SpcCapability>(`/api/spc/charts/${id}/capability`, { params });
  }

  // CAPA
  listCapas(status: CapaStatus | null, source: CapaSourceType | null, q: string | null): Observable<Page<CapaSummary>> {
    let params = new HttpParams().set('page', 0).set('size', 50);
    if (status) params = params.set('status', status);
    if (source) params = params.set('source', source);
    if (q) params = params.set('q', q);
    return this.http.get<Page<CapaSummary>>('/api/capa', { params });
  }

  getCapa(id: number): Observable<Capa> {
    return this.http.get<Capa>(`/api/capa/${id}`);
  }

  createCapa(req: SaveCapaRequest): Observable<Capa> {
    return this.http.post<Capa>('/api/capa', req);
  }

  updateCapa(id: number, req: SaveCapaRequest): Observable<Capa> {
    return this.http.put<Capa>(`/api/capa/${id}`, req);
  }

  advanceCapa(id: number, target: CapaStatus, reason: string | null): Observable<Capa> {
    return this.http.post<Capa>(`/api/capa/${id}/advance`, { target, reason });
  }

  closeCapa(id: number, verificationNotes: string): Observable<Capa> {
    return this.http.post<Capa>(`/api/capa/${id}/close`, { verificationNotes });
  }

  capaFromRejection(rejectionId: number): Observable<Capa> {
    return this.http.post<Capa>(`/api/capa/from-rejection/${rejectionId}`, {});
  }

  capaFromSpcPoint(pointId: number): Observable<Capa> {
    return this.http.post<Capa>(`/api/capa/from-spc-point/${pointId}`, {});
  }

  capaFromComplaint(complaintId: number): Observable<Capa> {
    return this.http.post<Capa>(`/api/capa/from-complaint/${complaintId}`, {});
  }

  // complaints
  listComplaints(status: ComplaintStatus | null, q: string | null): Observable<Page<ComplaintSummary>> {
    let params = new HttpParams().set('page', 0).set('size', 50);
    if (status) params = params.set('status', status);
    if (q) params = params.set('q', q);
    return this.http.get<Page<ComplaintSummary>>('/api/complaints', { params });
  }

  customerLookup(q: string | null): Observable<CustomerLookup[]> {
    let params = new HttpParams();
    if (q) params = params.set('q', q);
    return this.http.get<CustomerLookup[]>('/api/complaints/lookups/customers', { params });
  }

  listFgBatches(productId: number): Observable<Page<{ id: number; batchNumber: string; productId: number }>> {
    const params = new HttpParams().set('productId', productId).set('page', 0).set('size', 100);
    return this.http.get<Page<{ id: number; batchNumber: string; productId: number }>>('/api/quality/fg-batches', {
      params,
    });
  }

  getComplaint(id: number): Observable<Complaint> {
    return this.http.get<Complaint>(`/api/complaints/${id}`);
  }

  createComplaint(req: SaveComplaintRequest): Observable<Complaint> {
    return this.http.post<Complaint>('/api/complaints', req);
  }

  updateComplaint(id: number, req: SaveComplaintRequest): Observable<Complaint> {
    return this.http.put<Complaint>(`/api/complaints/${id}`, req);
  }

  transitionComplaint(id: number, req: TransitionComplaintRequest): Observable<Complaint> {
    return this.http.post<Complaint>(`/api/complaints/${id}/transition`, req);
  }

  traceComplaint(id: number): Observable<TraceView> {
    return this.http.get<TraceView>(`/api/complaints/${id}/trace`);
  }

  // first article inspection
  listFais(status: FaiStatus | null, q: string | null): Observable<Page<FaiSummary>> {
    let params = new HttpParams().set('page', 0).set('size', 50);
    if (status) params = params.set('status', status);
    if (q) params = params.set('q', q);
    return this.http.get<Page<FaiSummary>>('/api/fai', { params });
  }

  getFai(id: number): Observable<Fai> {
    return this.http.get<Fai>(`/api/fai/${id}`);
  }

  faiReport(id: number): Observable<FaiReport> {
    return this.http.get<FaiReport>(`/api/fai/${id}/report`);
  }

  createFai(productId: number): Observable<Fai> {
    return this.http.post<Fai>('/api/fai', { productId });
  }

  startFai(id: number, productionOrderId: number): Observable<Fai> {
    return this.http.post<Fai>(`/api/fai/${id}/start`, { productionOrderId });
  }

  submitFaiResults(id: number, values: { itemId: number; actualValue: number }[]): Observable<FaiReport> {
    return this.http.post<FaiReport>(`/api/fai/${id}/results`, { values });
  }

  completeFai(id: number, summary: string | null): Observable<Fai> {
    return this.http.post<Fai>(`/api/fai/${id}/complete`, summary ? { summary } : {});
  }
}
