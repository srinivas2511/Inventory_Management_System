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

/** One readable message for a failed request: the problem detail, plus any field errors the server listed. */
export function problemMessage(err: unknown, fallback: string): string {
  const body = (err as { error?: unknown } | null)?.error;
  if (!isProblemDetail(body)) {
    return fallback;
  }
  const fields = (body.fieldErrors ?? []).map((f) => `${f.field}: ${f.message}`);
  const detail = body.detail ?? fallback;
  return fields.length ? `${detail} (${fields.join('; ')})` : detail;
}
