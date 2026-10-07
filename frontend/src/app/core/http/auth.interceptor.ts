import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { TokenStore } from '../auth/token.store';
import { AppConfigService } from '../config/app-config.service';

function addBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

const PUBLIC_AUTH_PATHS = [
  '/api/auth/login',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStore = inject(TokenStore);
  const authService = inject(AuthService);
  const apiBase = inject(AppConfigService).apiBaseUrl;
  const isApiRequest = req.url.startsWith(`${apiBase}/api/`) || req.url.startsWith('/api/');
  const isPublicAuthEndpoint = PUBLIC_AUTH_PATHS.some((p) => req.url.includes(p));

  const token = tokenStore.accessToken();
  const outgoing = token && isApiRequest && !isPublicAuthEndpoint ? addBearer(req, token) : req;

  return next(outgoing).pipe(
    catchError((err) => {
      if (err.status !== 401 || !isApiRequest || isPublicAuthEndpoint) {
        return throwError(() => err);
      }
      // Every 401 that arrives while a refresh is running waits for that one refresh, then retries once. The retry
      // is sent straight down the chain, so it can never trigger another refresh: if the server still refuses a
      // token it has just issued, the session is over.
      return authService.refresh().pipe(
        switchMap((res) =>
          next(addBearer(req, res.accessToken)).pipe(
            catchError((retryErr) => {
              if (retryErr.status === 401) {
                authService.expireSession();
              }
              return throwError(() => retryErr);
            }),
          ),
        ),
      );
    }),
  );
};
