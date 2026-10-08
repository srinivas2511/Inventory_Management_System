import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { isProblemDetail } from '../models/problem.model';
import { StepUpPromptService } from '../stepup/step-up-prompt.service';

/**
 * When the server answers 403 STEP_UP_REQUIRED (the trial balance, a supplier payment), ask the user for their
 * password and send the same request once more. If they cancel, the original error goes on unchanged.
 * It sits innermost in the chain so it sees the refusal before the error interceptor does.
 */
export const stepUpInterceptor: HttpInterceptorFn = (req, next) => {
  const prompt = inject(StepUpPromptService);
  return next(req).pipe(
    catchError((error: unknown) => {
      const needsStepUp =
        error instanceof HttpErrorResponse &&
        error.status === 403 &&
        isProblemDetail(error.error) &&
        error.error.code === 'STEP_UP_REQUIRED';
      if (!needsStepUp) {
        return throwError(() => error);
      }
      return prompt.prompt().pipe(switchMap((confirmed) => (confirmed ? next(req) : throwError(() => error))));
    }),
  );
};
