import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';

/**
 * Mark posting requests (issue, output, receipt, dispatch...) with {@code context: new HttpContext().set(IDEMPOTENT, true)}
 * so a double tap or network retry cannot post stock twice (DESIGN.md section 5.8 / INV-08).
 */
export const IDEMPOTENT = new HttpContextToken<boolean>(() => false);

export const idempotencyInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method === 'POST' && req.context.get(IDEMPOTENT) && !req.headers.has('Idempotency-Key')) {
    return next(req.clone({ setHeaders: { 'Idempotency-Key': crypto.randomUUID() } }));
  }
  return next(req);
};
