import { APP_INITIALIZER, ApplicationConfig, inject, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { routes } from './app.routes';
import { SessionStore } from './core/auth/session.store';
import { AppConfigService } from './core/config/app-config.service';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { idempotencyInterceptor } from './core/http/idempotency.interceptor';
import { loadingInterceptor } from './core/http/loading.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideAnimationsAsync(),
    // Order matters: loading wraps everything, errors are mapped last on the way back.
    provideHttpClient(
      withInterceptors([loadingInterceptor, authInterceptor, idempotencyInterceptor, errorInterceptor]),
    ),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const config = inject(AppConfigService);
        const session = inject(SessionStore);
        // Load the runtime config, then resume a session from the refresh cookie, before any route is evaluated.
        return async () => {
          await config.load();
          await session.restore();
        };
      },
    },
  ],
};
