import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MaintenanceRequestDto {
  id: number;
  requestNumber: string;
  machineId: number;
  requestType: string;
  priority: number;
  description: string;
  status: string;
  requestedBy: number;
  assignedTo: number | null;
  scheduledDate: string | null;
  completedAt: string | null;
  resolutionNotes: string | null;
  createdAt: string;
  labourHours?: number | null;
  labourCost?: number | null;
  costCentreId?: number | null;
  costPostedAt?: string | null;
}

export interface PmScheduleDto {
  id: number;
  machineId: number;
  taskName: string;
  frequencyDays: number;
  lastPerformedDate: string | null;
  nextDueDate: string | null;
  assignedTo: number | null;
  active: boolean;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface SparePartDto {
  id: number;
  sparePartCode: string;
  name: string;
  description: string | null;
  uom: string;
  materialId: number;
  compatibleMachineTypes: string | null;
  minStock: number;
  reorderLevel: number;
  standardCost: number;
  active: boolean;
  onHandQty: number;
  belowMinStock: boolean;
  version: number;
}

export interface SaveSparePartBody {
  sparePartCode: string;
  name: string;
  description: string | null;
  uom: string;
  compatibleMachineTypes: string | null;
  minStock: number;
  reorderLevel: number;
  standardCost: number;
  version: number | null;
}

export interface SparePartIssueLineDto {
  id: number;
  maintenanceRequestId: number;
  sparePartId: number;
  sparePartCode: string;
  sparePartName: string;
  warehouseId: number;
  issuedQty: number;
  unitCost: number | null;
  totalCost: number;
  issuedBy: number;
  issuedAt: string;
  remarks: string | null;
}

export interface ActivityDto {
  id: number;
  maintenanceRequestId: number;
  description: string;
  performedBy: number;
  performedAt: string;
  durationMinutes: number | null;
  sparePartsUsed: string | null;
}

export interface MaintenanceCostDto {
  requestId: number;
  requestNumber: string;
  machineId: number;
  status: string;
  costCentreId: number | null;
  labourMinutes: number;
  labourHours: number;
  labourRatePerHour: number;
  labourCost: number;
  sparePartsCost: number;
  totalCost: number;
  labourCostFinal: boolean;
  labourJournalHeaderId: number | null;
  costPostedAt: string | null;
}

export interface PredictionDto {
  id: number;
  machineId: number;
  machineCode: string | null;
  machineName: string | null;
  predictedFailureAt: string | null;
  confidencePct: number;
  mtbfHours: number | null;
  failureCount: number;
  lastFailureAt: string | null;
  algorithmVersion: string | null;
  generatedAt: string;
  acknowledged: boolean;
  acknowledgedAt: string | null;
  notes: string | null;
}

export interface MachineRiskRow {
  machineId: number;
  machineCode: string | null;
  machineName: string | null;
  failureCountLast90Days: number;
  mtbfHours: number | null;
  nextPredictedFailure: string | null;
  confidencePct: number | null;
  riskLevel: string;
}

export interface PredictionSummary {
  machinesAnalyzed: number;
  predictionsGenerated: number;
  highRiskCount: number;
  acknowledgedCount: number;
}

export interface RunPredictionsResponse {
  summary: PredictionSummary;
  machines: MachineRiskRow[];
}

export interface SensorReadingDto {
  id: number;
  machineId: number;
  readingType: string;
  readingValue: number;
  unit: string | null;
  readAt: string;
  source: string;
}

@Injectable({ providedIn: 'root' })
export class MaintenanceApi {
  private http = inject(HttpClient);

  getRequest(id: number): Observable<MaintenanceRequestDto> {
    return this.http.get<MaintenanceRequestDto>(`/api/maintenance/requests/${id}`);
  }

  activities(id: number): Observable<ActivityDto[]> {
    return this.http.get<ActivityDto[]>(`/api/maintenance/requests/${id}/activities`);
  }

  addActivity(id: number, body: { description: string; durationMinutes: number }): Observable<ActivityDto> {
    return this.http.post<ActivityDto>(`/api/maintenance/requests/${id}/activities`, body);
  }

  issues(id: number): Observable<SparePartIssueLineDto[]> {
    return this.http.get<SparePartIssueLineDto[]>(`/api/maintenance/requests/${id}/spare-parts`);
  }

  issueSparePart(
    id: number,
    body: { sparePartId: number; warehouseId: number; quantity: number; remarks?: string | null },
  ): Observable<SparePartIssueLineDto> {
    return this.http.post<SparePartIssueLineDto>(`/api/maintenance/requests/${id}/spare-parts`, body);
  }

  cost(id: number): Observable<MaintenanceCostDto> {
    return this.http.get<MaintenanceCostDto>(`/api/maintenance/requests/${id}/cost`);
  }

  postCost(id: number): Observable<MaintenanceCostDto> {
    return this.http.post<MaintenanceCostDto>(`/api/maintenance/requests/${id}/post-cost`, {});
  }

  spareParts(q?: string | null, active?: boolean | null, page = 0, size = 100): Observable<PageResponse<SparePartDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== null && active !== undefined) params = params.set('active', active);
    return this.http.get<PageResponse<SparePartDto>>('/api/spare-parts', { params });
  }

  createSparePart(body: SaveSparePartBody): Observable<SparePartDto> {
    return this.http.post<SparePartDto>('/api/spare-parts', body);
  }

  updateSparePart(id: number, body: SaveSparePartBody): Observable<SparePartDto> {
    return this.http.put<SparePartDto>(`/api/spare-parts/${id}`, body);
  }

  deactivateSparePart(id: number): Observable<SparePartDto> {
    return this.http.delete<SparePartDto>(`/api/spare-parts/${id}`);
  }

  getRequests(
    status?: string,
    requestType?: string,
    page = 0,
    size = 50,
  ): Observable<PageResponse<MaintenanceRequestDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (requestType) params = params.set('requestType', requestType);
    return this.http.get<PageResponse<MaintenanceRequestDto>>('/api/maintenance/requests', { params });
  }

  createRequest(body: {
    machineId: number;
    requestType: string;
    priority: number;
    description: string;
    assignedTo?: number;
    scheduledDate?: string;
  }): Observable<MaintenanceRequestDto> {
    return this.http.post<MaintenanceRequestDto>('/api/maintenance/requests', body);
  }

  updateStatus(id: number, newStatus: string, resolutionNotes?: string): Observable<MaintenanceRequestDto> {
    let params = new HttpParams().set('newStatus', newStatus);
    if (resolutionNotes) params = params.set('resolutionNotes', resolutionNotes);
    return this.http.put<MaintenanceRequestDto>(`/api/maintenance/requests/${id}/status`, null, { params });
  }

  getPmSchedules(): Observable<PmScheduleDto[]> {
    return this.http.get<PmScheduleDto[]>('/api/maintenance/pm-schedules');
  }

  createPmSchedule(body: {
    machineId: number;
    taskName: string;
    frequencyDays: number;
    assignedTo?: number;
  }): Observable<PmScheduleDto> {
    return this.http.post<PmScheduleDto>('/api/maintenance/pm-schedules', body);
  }

  generatePmRequest(scheduleId: number): Observable<MaintenanceRequestDto> {
    return this.http.post<MaintenanceRequestDto>(`/api/maintenance/pm-schedules/${scheduleId}/generate-request`, {});
  }

  // Predictive maintenance (R19-5.2)
  getPredictions(all = false): Observable<PredictionDto[]> {
    const params = new HttpParams().set('all', all);
    return this.http.get<PredictionDto[]>('/api/maintenance/predictive/predictions', { params });
  }

  runPredictions(): Observable<RunPredictionsResponse> {
    return this.http.post<RunPredictionsResponse>('/api/maintenance/predictive/run', {});
  }

  acknowledgePrediction(id: number): Observable<PredictionDto> {
    return this.http.post<PredictionDto>(`/api/maintenance/predictive/predictions/${id}/acknowledge`, {});
  }

  getMachineRisk(): Observable<MachineRiskRow[]> {
    return this.http.get<MachineRiskRow[]>('/api/maintenance/predictive/machine-risk');
  }

  getSensorReadings(machineId: number): Observable<SensorReadingDto[]> {
    const params = new HttpParams().set('machineId', machineId);
    return this.http.get<SensorReadingDto[]>('/api/maintenance/predictive/sensor-readings', { params });
  }

  recordSensorReading(body: {
    machineId: number;
    readingType: string;
    readingValue: number;
    unit?: string;
  }): Observable<SensorReadingDto> {
    return this.http.post<SensorReadingDto>('/api/maintenance/predictive/sensor-readings', body);
  }
}
