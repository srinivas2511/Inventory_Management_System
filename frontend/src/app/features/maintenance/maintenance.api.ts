import { inject } from '@angular/core';
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

export class MaintenanceApi {
  private http = inject(HttpClient);

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
}
