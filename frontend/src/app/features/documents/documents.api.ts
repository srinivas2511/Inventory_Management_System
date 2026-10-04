import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type DocumentStatus = 'DRAFT' | 'REVIEW' | 'APPROVED' | 'OBSOLETE';
export const DOCUMENT_STATUSES: DocumentStatus[] = ['DRAFT', 'REVIEW', 'APPROVED', 'OBSOLETE'];

/** Record types a document can be linked to; mirrors the backend and the database constraint. */
export const LINKABLE_TYPES = ['PRODUCT', 'BOM', 'ECO', 'SUPPLIER', 'MACHINE', 'CAPA', 'COMPLAINT'] as const;
export type LinkableType = (typeof LINKABLE_TYPES)[number];

export interface DocumentCategoryDto {
  id: number;
  categoryCode: string;
  name: string;
  retentionYears: number | null;
  requiredAuthority: string | null;
  active: boolean;
}

export interface DocumentLinkDto {
  id: number;
  entityType: string;
  entityId: number;
}

export interface DocumentVersionDto {
  id: number;
  versionNo: number;
  revisionLabel: string | null;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  changeNote: string | null;
  uploadedBy: number;
  uploadedAt: string;
}

export interface DocumentSummaryDto {
  id: number;
  documentNumber: string;
  title: string;
  categoryCode: string;
  categoryName: string;
  status: DocumentStatus;
  currentVersionNo: number;
  updatedAt: string;
  links: DocumentLinkDto[];
}

export interface DocumentDto {
  id: number;
  documentNumber: string;
  title: string;
  description: string | null;
  categoryCode: string;
  categoryName: string;
  status: DocumentStatus;
  statusReason: string | null;
  currentVersionNo: number;
  approvedBy: number | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
  versions: DocumentVersionDto[];
  links: DocumentLinkDto[];
}

export interface CreateDocumentRequest {
  categoryCode: string;
  title: string;
  description?: string | null;
  revisionLabel?: string | null;
  changeNote?: string | null;
  links?: { entityType: string; entityId: number }[];
}

export interface AddVersionRequest {
  revisionLabel?: string | null;
  changeNote?: string | null;
}

export interface DocumentFilter {
  q?: string | null;
  category?: string | null;
  status?: DocumentStatus | null;
  entityType?: string | null;
  entityId?: number | null;
  page?: number;
  size?: number;
}

/** A multipart body: the JSON description as a "meta" part and the content as a "file" part. */
export function multipartBody(meta: object, file: File): FormData {
  const form = new FormData();
  form.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }));
  form.append('file', file, file.name);
  return form;
}

export interface DownloadedFile {
  blob: Blob;
  filename: string | null;
}

/** The file name from a Content-Disposition header, preferring the RFC 5987 encoded form. */
export function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim());
    } catch {
      // fall through to the plain form
    }
  }
  const quoted = /filename\s*=\s*"([^"]+)"/i.exec(header);
  if (quoted) return quoted[1];
  const plain = /filename\s*=\s*([^;]+)/i.exec(header);
  return plain ? plain[1].trim() : null;
}

/** Hands a downloaded blob to the browser as a file. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/documents';

  list(f: DocumentFilter = {}): Observable<Page<DocumentSummaryDto>> {
    let params = new HttpParams().set('page', f.page ?? 0).set('size', f.size ?? 50);
    if (f.q) params = params.set('q', f.q);
    if (f.category) params = params.set('category', f.category);
    if (f.status) params = params.set('status', f.status);
    if (f.entityType) params = params.set('entityType', f.entityType);
    if (f.entityId != null) params = params.set('entityId', f.entityId);
    return this.http.get<Page<DocumentSummaryDto>>(this.base, { params });
  }

  get(id: number): Observable<DocumentDto> {
    return this.http.get<DocumentDto>(`${this.base}/${id}`);
  }

  categories(): Observable<DocumentCategoryDto[]> {
    return this.http.get<DocumentCategoryDto[]>('/api/document-categories');
  }

  create(meta: CreateDocumentRequest, file: File): Observable<DocumentDto> {
    return this.http.post<DocumentDto>(this.base, multipartBody(meta, file));
  }

  addVersion(id: number, meta: AddVersionRequest, file: File): Observable<DocumentDto> {
    return this.http.post<DocumentDto>(`${this.base}/${id}/versions`, multipartBody(meta, file));
  }

  download(id: number, version?: number): Observable<DownloadedFile> {
    let params = new HttpParams();
    if (version != null) params = params.set('version', version);
    return this.http.get(`${this.base}/${id}/download`, { params, responseType: 'blob', observe: 'response' }).pipe(
      map((r) => ({
        blob: r.body as Blob,
        filename: filenameFromDisposition(r.headers.get('Content-Disposition')),
      })),
    );
  }

  addLink(id: number, entityType: string, entityId: number): Observable<DocumentDto> {
    return this.http.post<DocumentDto>(`${this.base}/${id}/links`, { entityType, entityId });
  }

  removeLink(id: number, linkId: number): Observable<DocumentDto> {
    return this.http.delete<DocumentDto>(`${this.base}/${id}/links/${linkId}`);
  }

  submit(id: number): Observable<DocumentDto> {
    return this.http.post<DocumentDto>(`${this.base}/${id}/submit`, {});
  }

  approve(id: number): Observable<DocumentDto> {
    return this.http.post<DocumentDto>(`${this.base}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<DocumentDto> {
    return this.http.post<DocumentDto>(`${this.base}/${id}/reject`, { reason });
  }

  obsolete(id: number, reason: string): Observable<DocumentDto> {
    return this.http.post<DocumentDto>(`${this.base}/${id}/obsolete`, { reason });
  }
}
