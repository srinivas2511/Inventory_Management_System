import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

/** Thin wrapper over HttpClient that prefixes the configured API base URL. Feature `*Api` services build on it. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  get<T>(path: string, params?: HttpParams): Observable<T> {
    return this.http.get<T>(this.url(path), { params });
  }

  post<T>(path: string, body: unknown, context?: HttpContext): Observable<T> {
    return this.http.post<T>(this.url(path), body, { context });
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(this.url(path), body);
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(this.url(path));
  }

  private url(path: string): string {
    return `${this.config.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`;
  }
}
