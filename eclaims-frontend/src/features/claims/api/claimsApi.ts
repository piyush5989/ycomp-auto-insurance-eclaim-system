import { httpClient } from '@/shared/api/httpClient';
import type { ApiResponse } from '@/shared/types/ApiResponse';
import type { ClaimSubmissionRequest, ClaimResponse, ClaimStatusUpdateRequest } from './claimsApi.types';

/**
 * Claims API service layer.
 * All /api/v1/claims/* calls go through here — never inline in components or hooks.
 */
export const claimsApi = {
  submit: (payload: ClaimSubmissionRequest) =>
    httpClient.post<ApiResponse<ClaimResponse>>('/claims', payload).then((r) => r.data),

  getById: (claimId: string) =>
    httpClient.get<ApiResponse<ClaimResponse>>(`/claims/${claimId}`).then((r) => r.data),

  listMyClaims: () =>
    httpClient.get<ApiResponse<ClaimResponse[]>>('/claims/my-claims').then((r) => r.data),

  updateStatus: (claimId: string, update: ClaimStatusUpdateRequest) =>
    httpClient.patch<ApiResponse<ClaimResponse>>(`/claims/${claimId}/status`, update).then((r) => r.data),

  withdraw: (claimId: string) =>
    httpClient.delete<ApiResponse<ClaimResponse>>(`/claims/${claimId}`).then((r) => r.data),
};
