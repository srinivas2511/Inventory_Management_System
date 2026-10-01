import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenStore } from '../auth/token.store';
import { AppConfigService } from '../config/app-config.service';

/** Adds the bearer token to API calls. Refresh-on-401 with request queueing is added in Phase 1. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStore).accessToken();
  const apiBase = inject(AppConfigService).apiBaseUrl;
  if (token && req.url.startsWith(apiBase)) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(req);
};
