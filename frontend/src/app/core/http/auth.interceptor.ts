import { HttpEvent, HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { catchError, filter, switchMap, take, tap } from 'rxjs/operators';
import { AuthService, LoginResponse } from '../auth/auth.service';
import { TokenStore } from '../auth/token.store';
import { AppConfigService } from '../config/app-config.service';

let isRefreshing = false;
const refreshDone$ = new BehaviorSubject<string | null>(null);

function addBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService,
): Observable<HttpEvent<unknown>> {
  if (isRefreshing) {
    return refreshDone$.pipe(
      filter((t): t is string => t !== null),
      take(1),
      switchMap((token) => next(addBearer(req, token))),
    );
  }

  isRefreshing = true;
  refreshDone$.next(null);

  return authService.refresh().pipe(
    tap((res: LoginResponse) => {
      isRefreshing = false;
      refreshDone$.next(res.accessToken);
    }),
    switchMap((res: LoginResponse) => next(addBearer(req, res.accessToken))),
    catchError((err) => {
      isRefreshing = false;
      authService.logout();
      return throwError(() => err);
    }),
  );
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStore = inject(TokenStore);
  const authService = inject(AuthService);
  const apiBase = inject(AppConfigService).apiBaseUrl;

  // Skip auth header for public auth endpoints
  const isAuthEndpoint = req.url.includes('/api/auth/login')
    || req.url.includes('/api/auth/refresh')
    || req.url.includes('/api/auth/forgot-password')
    || req.url.includes('/api/auth/reset-password');

  const token = tokenStore.accessToken();
  const outgoing = token && req.url.startsWith(apiBase) && !isAuthEndpoint
    ? addBearer(req, token)
    : req;

  return next(outgoing).pipe(
    catchError((err) => {
      if (err.status === 401 && req.url.startsWith(apiBase) && !isAuthEndpoint) {
        return handle401(req, next, authService);
      }
      return throwError(() => err);
    }),
  );
};
