/** Paged list response of every list endpoint (DESIGN.md section 6.1). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
