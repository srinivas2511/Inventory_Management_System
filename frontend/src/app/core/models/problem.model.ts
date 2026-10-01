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
