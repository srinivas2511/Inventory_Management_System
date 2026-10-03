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

export type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'UNQUALIFIED' | 'CONVERTED';
export type OpportunityStage = 'PROSPECT' | 'QUALIFIED' | 'PROPOSAL' | 'NEGOTIATION' | 'CLOSED_WON' | 'CLOSED_LOST';
export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CONVERTED';
export type ActivityType = 'CALL' | 'EMAIL' | 'MEETING' | 'DEMO' | 'FOLLOW_UP';

export const OPPORTUNITY_STAGES: OpportunityStage[] = [
  'PROSPECT',
  'QUALIFIED',
  'PROPOSAL',
  'NEGOTIATION',
  'CLOSED_WON',
  'CLOSED_LOST',
];
export const ACTIVITY_TYPES: ActivityType[] = ['CALL', 'EMAIL', 'MEETING', 'DEMO', 'FOLLOW_UP'];

export interface LeadDto {
  id: number;
  leadNumber: string;
  companyName: string;
  contactId: number | null;
  contactName: string | null;
  source: string | null;
  status: LeadStatus;
  ownerId: number;
  ownerName: string | null;
  estimatedValue: number | null;
  expectedCloseDate: string | null;
  convertedCustomerId: number | null;
  convertedAt: string | null;
  disqualifyReason: string | null;
  notes: string | null;
  version: number;
  allowedActions: string[];
}

export interface SaveLeadRequest {
  companyName: string;
  contactId: number | null;
  source: string | null;
  ownerId: number | null;
  estimatedValue: number | null;
  expectedCloseDate: string | null;
  notes: string | null;
  version: number | null;
}

export interface ConvertLeadResult {
  lead: LeadDto;
  customerId: number;
  customerCode: string;
  opportunity: OpportunityDto;
}

export interface OpportunityDto {
  id: number;
  opportunityNumber: string;
  leadId: number | null;
  customerId: number | null;
  customerName: string | null;
  contactId: number | null;
  contactName: string | null;
  description: string;
  stage: OpportunityStage;
  probabilityPct: number;
  estimatedValue: number | null;
  weightedValue: number | null;
  expectedCloseDate: string | null;
  ownerId: number;
  ownerName: string | null;
  convertedQuotationId: number | null;
  wonAt: string | null;
  lostReason: string | null;
  version: number;
  allowedActions: string[];
}

export interface SaveOpportunityRequest {
  leadId: number | null;
  customerId: number | null;
  contactId: number | null;
  description: string;
  probabilityPct: number | null;
  estimatedValue: number | null;
  expectedCloseDate: string | null;
  ownerId: number | null;
  version: number | null;
}

export interface QuotationItemDto {
  id: number;
  lineNo: number;
  productId: number;
  productCode: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  discountPct: number;
  netUnitPrice: number;
  lineTotal: number;
}

export interface QuotationDto {
  id: number;
  quotationNumber: string;
  opportunityId: number | null;
  customerId: number;
  customerName: string | null;
  validUntil: string;
  status: QuotationStatus;
  totalAmount: number;
  paymentTerms: string | null;
  notes: string | null;
  sentAt: string | null;
  respondedAt: string | null;
  rejectReason: string | null;
  convertedSalesOrderId: number | null;
  convertedSalesOrderNumber: string | null;
  convertedAt: string | null;
  items: QuotationItemDto[];
  version: number;
  allowedActions: string[];
}

export interface SaveQuotationItem {
  productId: number;
  quantity: number;
  unitPrice: number | null;
  discountPct: number | null;
}

export interface SaveQuotationRequest {
  opportunityId: number | null;
  customerId: number | null;
  validUntil: string | null;
  paymentTerms: string | null;
  notes: string | null;
  items: SaveQuotationItem[];
  version: number | null;
}

export interface ActivityDto {
  id: number;
  activityType: ActivityType;
  customerId: number | null;
  customerName: string | null;
  contactId: number | null;
  contactName: string | null;
  leadId: number | null;
  opportunityId: number | null;
  subject: string;
  notes: string | null;
  outcome: string | null;
  activityDate: string;
  durationMinutes: number | null;
  ownerId: number;
  ownerName: string | null;
  version: number;
}

export interface SaveActivityRequest {
  activityType: ActivityType;
  customerId: number | null;
  contactId: number | null;
  leadId: number | null;
  opportunityId: number | null;
  subject: string;
  notes: string | null;
  outcome: string | null;
  activityDate: string | null;
  durationMinutes: number | null;
  ownerId: number | null;
  version: number | null;
}

export interface ActivityFilter {
  customerId?: number | null;
  contactId?: number | null;
  leadId?: number | null;
  opportunityId?: number | null;
  type?: string | null;
}

export interface ContactDto {
  id: number;
  customerId: number | null;
  customerName: string | null;
  firstName: string;
  lastName: string | null;
  fullName: string;
  email: string | null;
  phone: string | null;
  designation: string | null;
  department: string | null;
  active: boolean;
  version: number;
}

export interface SaveContactRequest {
  customerId: number | null;
  firstName: string;
  lastName: string | null;
  email: string | null;
  phone: string | null;
  designation: string | null;
  department: string | null;
  version: number | null;
}

export interface PriceDto {
  id: number;
  customerId: number | null;
  customerName: string | null;
  productId: number;
  productCode: string;
  productName: string;
  unitPrice: number;
  minQty: number;
  validFrom: string;
  validTo: string | null;
  active: boolean;
  version: number;
}

export interface SavePriceRequest {
  customerId: number | null;
  productId: number;
  unitPrice: number;
  minQty: number | null;
  validFrom: string;
  validTo: string | null;
  active: boolean | null;
}

export interface ResolvedPrice {
  unitPrice: number | null;
  source: 'CUSTOMER' | 'STANDARD' | 'NONE';
  priceId: number | null;
}

export interface TagDto {
  id: number;
  tagGroup: 'INDUSTRY' | 'REGION' | 'TIER' | 'STRATEGIC' | 'OTHER';
  name: string;
  active: boolean;
}

export interface CrmCustomerDto {
  id: number;
  customerCode: string;
  name: string;
  status: string;
  tags: TagDto[];
}

export interface ReportResult {
  reportCode: string;
  displayName: string;
  headers: string[];
  rows: Record<string, unknown>[];
  totalRows: number;
}

@Injectable({ providedIn: 'root' })
export class CrmApi {
  private http = inject(HttpClient);
  private base = '/api/crm';

  // leads
  getLeads(status: string | null, q: string | null, page = 0, size = 50): Observable<Page<LeadDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (q) params = params.set('q', q);
    return this.http.get<Page<LeadDto>>(`${this.base}/leads`, { params });
  }

  getLead(id: number): Observable<LeadDto> {
    return this.http.get<LeadDto>(`${this.base}/leads/${id}`);
  }

  createLead(req: SaveLeadRequest): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.base}/leads`, req);
  }

  updateLead(id: number, req: SaveLeadRequest): Observable<LeadDto> {
    return this.http.put<LeadDto>(`${this.base}/leads/${id}`, req);
  }

  contactLead(id: number): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.base}/leads/${id}/contact`, {});
  }

  qualifyLead(id: number): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.base}/leads/${id}/qualify`, {});
  }

  disqualifyLead(id: number, reason: string): Observable<LeadDto> {
    return this.http.post<LeadDto>(`${this.base}/leads/${id}/disqualify`, { reason });
  }

  convertLead(
    id: number,
    existingCustomerId: number | null,
    opportunityDescription: string | null,
  ): Observable<ConvertLeadResult> {
    return this.http.post<ConvertLeadResult>(`${this.base}/leads/${id}/convert-to-opportunity`, {
      existingCustomerId,
      opportunityDescription,
    });
  }

  // opportunities
  getOpportunities(stage: string | null, q: string | null, page = 0, size = 200): Observable<Page<OpportunityDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (stage) params = params.set('stage', stage);
    if (q) params = params.set('q', q);
    return this.http.get<Page<OpportunityDto>>(`${this.base}/opportunities`, { params });
  }

  createOpportunity(req: SaveOpportunityRequest): Observable<OpportunityDto> {
    return this.http.post<OpportunityDto>(`${this.base}/opportunities`, req);
  }

  advanceOpportunity(id: number, stage: OpportunityStage, lostReason: string | null): Observable<OpportunityDto> {
    return this.http.post<OpportunityDto>(`${this.base}/opportunities/${id}/advance`, { stage, lostReason });
  }

  // quotations
  getQuotations(status: string | null, page = 0, size = 50): Observable<Page<QuotationDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<Page<QuotationDto>>(`${this.base}/quotations`, { params });
  }

  getQuotation(id: number): Observable<QuotationDto> {
    return this.http.get<QuotationDto>(`${this.base}/quotations/${id}`);
  }

  createQuotation(req: SaveQuotationRequest): Observable<QuotationDto> {
    return this.http.post<QuotationDto>(`${this.base}/quotations`, req);
  }

  updateQuotation(id: number, req: SaveQuotationRequest): Observable<QuotationDto> {
    return this.http.put<QuotationDto>(`${this.base}/quotations/${id}`, req);
  }

  sendQuotation(id: number): Observable<QuotationDto> {
    return this.http.post<QuotationDto>(`${this.base}/quotations/${id}/send`, {});
  }

  acceptQuotation(id: number): Observable<QuotationDto> {
    return this.http.post<QuotationDto>(`${this.base}/quotations/${id}/accept`, {});
  }

  rejectQuotation(id: number, reason: string): Observable<QuotationDto> {
    return this.http.post<QuotationDto>(`${this.base}/quotations/${id}/reject`, { reason });
  }

  convertQuotation(id: number): Observable<QuotationDto> {
    return this.http.post<QuotationDto>(`${this.base}/quotations/${id}/convert-to-sales-order`, {});
  }

  // activities
  getActivities(filter: ActivityFilter, page = 0, size = 50): Observable<Page<ActivityDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.customerId) params = params.set('customerId', filter.customerId);
    if (filter.contactId) params = params.set('contactId', filter.contactId);
    if (filter.leadId) params = params.set('leadId', filter.leadId);
    if (filter.opportunityId) params = params.set('opportunityId', filter.opportunityId);
    if (filter.type) params = params.set('type', filter.type);
    return this.http.get<Page<ActivityDto>>(`${this.base}/activities`, { params });
  }

  createActivity(req: SaveActivityRequest): Observable<ActivityDto> {
    return this.http.post<ActivityDto>(`${this.base}/activities`, req);
  }

  deleteActivity(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/activities/${id}`);
  }

  // contacts
  getContacts(customerId: number | null, q: string | null, page = 0, size = 50): Observable<Page<ContactDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (customerId) params = params.set('customerId', customerId);
    if (q) params = params.set('q', q);
    return this.http.get<Page<ContactDto>>(`${this.base}/contacts`, { params });
  }

  createContact(req: SaveContactRequest): Observable<ContactDto> {
    return this.http.post<ContactDto>(`${this.base}/contacts`, req);
  }

  updateContact(id: number, req: SaveContactRequest): Observable<ContactDto> {
    return this.http.put<ContactDto>(`${this.base}/contacts/${id}`, req);
  }

  deactivateContact(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/contacts/${id}`);
  }

  // prices
  getPrices(customerId: number | null, productId: number | null, page = 0, size = 50): Observable<Page<PriceDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (customerId) params = params.set('customerId', customerId);
    if (productId) params = params.set('productId', productId);
    return this.http.get<Page<PriceDto>>(`${this.base}/prices`, { params });
  }

  resolvePrice(customerId: number, productId: number, quantity: number): Observable<ResolvedPrice> {
    const params = new HttpParams().set('customerId', customerId).set('productId', productId).set('quantity', quantity);
    return this.http.get<ResolvedPrice>(`${this.base}/prices/resolve`, { params });
  }

  createPrice(req: SavePriceRequest): Observable<PriceDto> {
    return this.http.post<PriceDto>(`${this.base}/prices`, req);
  }

  deletePrice(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/prices/${id}`);
  }

  // segmentation
  getTags(): Observable<TagDto[]> {
    return this.http.get<TagDto[]>(`${this.base}/tags`);
  }

  createTag(tagGroup: string, name: string): Observable<TagDto> {
    return this.http.post<TagDto>(`${this.base}/tags`, { tagGroup, name });
  }

  deleteTag(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/tags/${id}`);
  }

  getCustomers(q: string | null, tagId: number | null, page = 0, size = 100): Observable<Page<CrmCustomerDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (tagId) params = params.set('tagId', tagId);
    return this.http.get<Page<CrmCustomerDto>>(`${this.base}/customers`, { params });
  }

  setCustomerTags(customerId: number, tagIds: number[]): Observable<CrmCustomerDto> {
    return this.http.put<CrmCustomerDto>(`${this.base}/customers/${customerId}/tags`, { tagIds });
  }

  // reporting
  runPipelineReport(from?: string, to?: string): Observable<ReportResult> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<ReportResult>('/api/reports/CRM_PIPELINE', { params });
  }
}
