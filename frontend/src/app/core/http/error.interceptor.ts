import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { isProblemDetail } from '../models/problem.model';
import { ToastService } from '../notification/toast.service';

/**
 * Maps API problems to user-facing toasts. Validation errors (400) are left to forms to show next to the
 * fields; 401 handling (redirect to login) arrives with authentication in Phase 1.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const problem = isProblemDetail(error.error) ? error.error : null;
        if (error.status === 0) {
          toast.error('Cannot reach the server. Check your connection and try again.');
        } else if (error.status === 403) {
          toast.error('You do not have permission to perform this action.');
        } else if (error.status >= 500) {
          toast.error(`Something went wrong on the server.${problem?.traceId ? ` Reference: ${problem.traceId}` : ''}`);
        } else if (error.status !== 400 && error.status !== 401) {
          toast.error(problem?.detail ?? problem?.title ?? 'The request could not be completed.');
        }
      }
      return throwError(() => error);
    }),
  );
};
