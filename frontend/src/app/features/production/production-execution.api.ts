import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AssignOperatorRequest,
  AssignableOperatorDto,
  CloseOrderRequest,
  CreateIssueRequest,
  IssueDto,
  ProductionOperationDto,
  ProductionOrderDto,
  ReturnMaterialRequest,
} from './production-order.model';

/** Material issue, return, close and operator assignment on a production order. */
@Injectable({ providedIn: 'root' })
export class ProductionExecutionApi {
  private readonly http = inject(HttpClient);

  issue(request: CreateIssueRequest): Observable<IssueDto> {
    return this.http.post<IssueDto>('/api/production/issues', request);
  }

  returnMaterial(orderId: number, request: ReturnMaterialRequest): Observable<void> {
    return this.http.post<void>(`/api/production-orders/${orderId}/material-returns`, request);
  }

  close(orderId: number, request: CloseOrderRequest): Observable<ProductionOrderDto> {
    return this.http.post<ProductionOrderDto>(`/api/production-orders/${orderId}/close`, request);
  }

  assignOperation(operationId: number, request: AssignOperatorRequest): Observable<ProductionOperationDto> {
    return this.http.put<ProductionOperationDto>(`/api/production/operations/${operationId}/assignment`, request);
  }

  assignOrder(orderId: number, request: AssignOperatorRequest): Observable<ProductionOperationDto[]> {
    return this.http.put<ProductionOperationDto[]>(`/api/production-orders/${orderId}/operator-assignment`, request);
  }

  assignableOperators(q?: string): Observable<AssignableOperatorDto[]> {
    let params = new HttpParams();
    if (q) params = params.set('q', q);
    return this.http.get<AssignableOperatorDto[]>('/api/production/assignable-operators', { params });
  }
}
