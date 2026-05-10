import axios, { AxiosError, AxiosHeaders } from 'axios';
import { getToken } from '@/shared/auth/keycloakInstance';

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

httpClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (!config.headers['X-Correlation-ID']) {
    config.headers['X-Correlation-ID'] = crypto.randomUUID();
  }
  if (config.data instanceof FormData) {
    const headers = config.headers
    if (headers instanceof AxiosHeaders) {
      headers.delete('Content-Type')
    } else if (headers && typeof headers === 'object') {
      delete (headers as Record<string, unknown>)['Content-Type']
      delete (headers as Record<string, unknown>)['content-type']
    }
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const correlationId = error.response?.headers?.['x-correlation-id'];
    const apiError = (error.response?.data as any)?.error;

    const enrichedError = new Error(
      apiError?.message || error.message || 'An unexpected error occurred'
    ) as Error & { code?: string; correlationId?: string; statusCode?: number };

    enrichedError.code = apiError?.code;
    enrichedError.correlationId = correlationId;
    enrichedError.statusCode = error.response?.status;

    return Promise.reject(enrichedError);
  }
);
