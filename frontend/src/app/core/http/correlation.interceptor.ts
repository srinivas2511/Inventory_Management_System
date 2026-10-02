import { HttpInterceptorFn } from '@angular/common/http';

function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export const correlationInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.headers.has('X-Correlation-Id')) {
    return next(req.clone({ setHeaders: { 'X-Correlation-Id': uuid() } }));
  }
  return next(req);
};
