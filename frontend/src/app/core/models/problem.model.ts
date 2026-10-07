import type { FormGroup } from '@angular/forms';

/** RFC 7807 problem body returned by every API error (DESIGN.md section 6.1). */
export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  /** Stable machine-readable code, e.g. INSUFFICIENT_STOCK. */
  code: string;
  fieldErrors?: FieldError[];
  traceId?: string;
  timestamp?: string;
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  return !!value && typeof value === 'object' && 'code' in value && 'status' in value;
}

/** "Validation failed (minStock: must not be null; standardCost: must not be null)". */
export function detailWithFieldErrors(problem: ProblemDetail, fallback: string): string {
  const fields = (problem.fieldErrors ?? []).map((f) => `${f.field}: ${f.message}`);
  const detail = problem.detail ?? fallback;
  return fields.length ? `${detail} (${fields.join('; ')})` : detail;
}

/** One readable message for a failed request: the problem detail, plus any field errors the server listed. */
export function problemMessage(err: unknown, fallback: string): string {
  const body = (err as { error?: unknown } | null)?.error;
  return isProblemDetail(body) ? detailWithFieldErrors(body, fallback) : fallback;
}

/**
 * Applies the server's field errors to the form (DESIGN section 13: server fieldErrors are applied to controls by
 * field path): each control the server named is marked invalid and touched, so it shows in the error colour, and the
 * returned message names every failing field for the banner.
 */
export function applyFieldErrors(form: FormGroup, err: unknown, fallback: string): string {
  const body = (err as { error?: unknown } | null)?.error;
  if (!isProblemDetail(body)) {
    return fallback;
  }
  for (const f of body.fieldErrors ?? []) {
    const control = form.get(f.field.split(/[.[]/)[0]);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: f.message });
      control.markAsTouched();
    }
  }
  return detailWithFieldErrors(body, fallback);
}
