export interface ApiResponse<T> {
  data: T | null;
  error: ApiError | null;
  meta: ApiMeta;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

export interface ApiMeta {
  correlationId: string;
  timestamp: string;
  version: string;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
