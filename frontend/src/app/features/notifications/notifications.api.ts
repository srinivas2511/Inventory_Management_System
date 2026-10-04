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

export type NotificationChannel = 'EMAIL' | 'IN_APP';
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'READ';
export const NOTIFICATION_STATUSES: NotificationStatus[] = ['PENDING', 'SENT', 'FAILED', 'READ'];

export interface NotificationDto {
  id: number;
  templateCode: string;
  subject: string | null;
  body: string;
  entityType: string | null;
  entityId: number | null;
  createdAt: string;
  readAt: string | null;
  read: boolean;
}

export interface ChannelPreferenceDto {
  channel: NotificationChannel;
  enabled: boolean;
}

export interface PreferenceDto {
  templateCode: string;
  description: string | null;
  channels: ChannelPreferenceDto[];
}

export interface TemplateDto {
  id: number;
  templateCode: string;
  channel: 'EMAIL' | 'IN_APP' | 'BOTH';
  description: string | null;
  subjectTemplate: string | null;
  bodyTemplate: string;
  active: boolean;
}

export interface SaveTemplateRequest {
  channel: 'EMAIL' | 'IN_APP' | 'BOTH';
  description: string | null;
  subjectTemplate: string | null;
  bodyTemplate: string;
  active: boolean;
}

export interface DeliveryLogDto {
  id: number;
  userId: number | null;
  recipientEmail: string | null;
  templateCode: string;
  channel: NotificationChannel;
  subject: string | null;
  status: NotificationStatus;
  attempts: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

/** Where a notification should take the user, or null when it has no screen of its own. */
export function notificationLink(n: Pick<NotificationDto, 'entityType' | 'entityId'>): string[] | null {
  switch (n.entityType) {
    case 'CAPA':
      return n.entityId == null ? null : ['/quality/capa', String(n.entityId)];
    case 'ALERT':
      return ['/insight/alerts'];
    case 'PURCHASE_ORDER':
      return ['/procurement/purchase-orders'];
    default:
      return null;
  }
}

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  inbox(unreadOnly: boolean, page = 0, size = 20): Observable<Page<NotificationDto>> {
    const params = new HttpParams().set('unreadOnly', unreadOnly).set('page', page).set('size', size);
    return this.http.get<Page<NotificationDto>>('/api/notifications', { params });
  }

  unreadCount(): Observable<{ unread: number }> {
    return this.http.get<{ unread: number }>('/api/notifications/unread-count');
  }

  markRead(id: number): Observable<NotificationDto> {
    return this.http.post<NotificationDto>(`/api/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<{ unread: number }> {
    return this.http.post<{ unread: number }>('/api/notifications/read-all', {});
  }

  preferences(): Observable<PreferenceDto[]> {
    return this.http.get<PreferenceDto[]>('/api/notification-preferences');
  }

  setPreference(templateCode: string, channel: NotificationChannel, enabled: boolean): Observable<PreferenceDto[]> {
    return this.http.put<PreferenceDto[]>('/api/notification-preferences', { templateCode, channel, enabled });
  }

  templates(): Observable<TemplateDto[]> {
    return this.http.get<TemplateDto[]>('/api/notification-templates');
  }

  updateTemplate(id: number, req: SaveTemplateRequest): Observable<TemplateDto> {
    return this.http.put<TemplateDto>(`/api/notification-templates/${id}`, req);
  }

  log(
    status: NotificationStatus | null,
    channel: NotificationChannel | null,
    page = 0,
    size = 50,
  ): Observable<Page<DeliveryLogDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (channel) params = params.set('channel', channel);
    return this.http.get<Page<DeliveryLogDto>>('/api/notification-log', { params });
  }
}
