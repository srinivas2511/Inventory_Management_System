import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GanttBarDto {
  productionOperationId: number;
  productionOrderId: number;
  orderNumber: string;
  productCode: string;
  priority: number;
  sequenceNo: number;
  operationName: string;
  status: string;
  start: string;
  end: string;
  operatorUserId: number | null;
  operatorName: string | null;
  late: boolean;
  locked: boolean;
}

export interface GanttMaintenanceDto {
  maintenanceRequestId: number;
  requestNumber: string;
  date: string;
  requestType: string;
}

export interface GanttMachineDto {
  machineId: number;
  machineCode: string;
  machineName: string;
  bars: GanttBarDto[];
  maintenance: GanttMaintenanceDto[];
}

export interface GanttDayDto {
  date: string;
  start: string;
  end: string;
  working: boolean;
}

export interface UnscheduledOperationDto {
  productionOperationId: number;
  productionOrderId: number;
  orderNumber: string;
  priority: number;
  sequenceNo: number;
  operationName: string;
  machineId: number | null;
}

export interface GanttDto {
  from: string;
  to: string;
  zone: string;
  days: GanttDayDto[];
  workingDays: string[];
  machines: GanttMachineDto[];
  unscheduled: UnscheduledOperationDto[];
}

export interface PlacementDto {
  productionOperationId: number;
  productionOrderId: number;
  orderNumber: string;
  sequenceNo: number;
  operationName: string;
  machineId: number;
  machineCode: string;
  start: string;
  end: string;
  operatorUserId: number | null;
  warnings: string[];
}

export interface UnplacedDto {
  productionOperationId: number;
  productionOrderId: number;
  orderNumber: string;
  sequenceNo: number;
  operationName: string;
  reason: string;
}

export interface OrderOutcomeDto {
  productionOrderId: number;
  orderNumber: string;
  dueDate: string | null;
  endsAt: string;
  late: boolean;
}

export interface SchedulePlanDto {
  applied: boolean;
  mode: string;
  horizonStart: string;
  horizonEnd: string;
  placements: PlacementDto[];
  unplaced: UnplacedDto[];
  orders: OrderOutcomeDto[];
}

export interface RescheduleResultDto {
  bar: GanttBarDto;
  machineId: number;
  machineCode: string;
  warnings: string[];
}

export const WARNING_TEXT: Record<string, string> = {
  NO_SKILLED_OPERATOR: 'nobody in the skill matrix is authorised for this machine or operation',
  NO_AVAILABLE_OPERATOR: 'skilled operators are absent or already busy then',
  SNAPPED_TO_WORKING_TIME: 'moved to the next working time',
  MACHINE_TYPE_DIFFERS_FROM_OPERATION: 'the machine is not of the type the operation normally uses',
  NEXT_OPERATION_STARTS_BEFORE_THIS_ENDS: 'the next operation now overlaps this one',
  OPERATOR_NOT_SKILLED: 'the assigned operator is not skilled for this machine or operation',
  OPERATOR_UNAVAILABLE: 'the assigned operator is away on one of these days',
  OPERATOR_NOT_AN_ACTIVE_EMPLOYEE: 'the assigned operator is not an active employee',
};

export const UNPLACED_TEXT: Record<string, string> = {
  NO_MACHINE: 'no machine of the right type',
  HORIZON_EXCEEDED: 'does not fit in the planning horizon',
  PREDECESSOR_UNPLACED: 'an earlier operation of the order could not be placed',
};

@Injectable({ providedIn: 'root' })
export class SchedulingApi {
  private http = inject(HttpClient);
  private base = '/api/scheduling';

  gantt(from?: string | null, to?: string | null, machineId?: number | null): Observable<GanttDto> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (machineId) params = params.set('machineId', machineId);
    return this.http.get<GanttDto>(`${this.base}/gantt`, { params });
  }

  run(dryRun: boolean, horizonDays: number | null, mode: string | null): Observable<SchedulePlanDto> {
    return this.http.post<SchedulePlanDto>(
      `${this.base}/run`,
      { horizonDays, productionOrderIds: [], mode },
      { params: new HttpParams().set('dryRun', dryRun) },
    );
  }

  reschedule(operationId: number, machineId: number, startIso: string): Observable<RescheduleResultDto> {
    return this.http.put<RescheduleResultDto>(`${this.base}/operations/${operationId}/schedule`, {
      machineId,
      start: startIso,
    });
  }

  unschedule(operationId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/operations/${operationId}/schedule`);
  }
}
