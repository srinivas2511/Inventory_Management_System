import { APP_INITIALIZER, ApplicationConfig, inject, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { AppConfigService } from './core/config/app-config.service';
import { authInterceptor } from './core/http/auth.interceptor';
import { correlationInterceptor } from './core/http/correlation.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { idempotencyInterceptor } from './core/http/idempotency.interceptor';
import { loadingInterceptor } from './core/http/loading.interceptor';
import { stepUpInterceptor } from './core/http/step-up.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'en-IN' },
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideAnimationsAsync(),
    // Order matters: loading wraps everything, errors are mapped last on the way back. Step-up sits innermost so it
    // sees a STEP_UP_REQUIRED refusal first and can ask for the password and retry before anything shows a toast.
    provideHttpClient(
      withInterceptors([
        loadingInterceptor,
        correlationInterceptor,
        authInterceptor,
        idempotencyInterceptor,
        errorInterceptor,
        stepUpInterceptor,
      ]),
    ),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const config = inject(AppConfigService);
        const auth = inject(AuthService);
        // The access token is memory-only, so every full page load starts signed out. After the runtime config is
        // known, restore the session from the HttpOnly refresh cookie before the router (and its guards) run.
        return async () => {
          await config.load();
          await firstValueFrom(auth.restoreSession());
        };
      },
    },
  ],
};
