import { APP_INITIALIZER, ApplicationConfig, inject, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { routes } from './app.routes';
import { AppConfigService } from './core/config/app-config.service';
import { authInterceptor } from './core/http/auth.interceptor';
import { correlationInterceptor } from './core/http/correlation.interceptor';
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
      withInterceptors([loadingInterceptor, correlationInterceptor, authInterceptor, idempotencyInterceptor, errorInterceptor]),
    ),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const config = inject(AppConfigService);
        return () => config.load();
      },
    },
  ],
};
