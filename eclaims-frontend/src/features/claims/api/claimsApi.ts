import { httpClient } from '@/shared/api/httpClient';
import type { ApiResponse } from '@/shared/types/ApiResponse';
import type {
  ClaimSubmissionRequest, ClaimResponse, ClaimStatusUpdateRequest,
  PotentialDuplicate, UpdateIncidentDetailsRequest, ClaimEndorsement,
  ClaimsPageResponse, CustomerClaimsStats,
} from './claimsApi.types';

/**
 * Claims API service layer.
 * All /api/v1/claims/* calls go through here — never inline in components or hooks.
 */
export const claimsApi = {
  submit: (payload: ClaimSubmissionRequest) =>
    httpClient.post<ApiResponse<ClaimResponse>>('/claims', payload).then((r) => r.data),

  getById: (claimId: string) =>
    httpClient.get<ApiResponse<ClaimResponse>>(`/claims/${claimId}`).then((r) => r.data),

  listMyClaimsPage: (params: {
    page?: number;
    size?: number;
    sortBy?: string;
    sortOrder?: string;
  }) =>
    httpClient
      .get<ApiResponse<ClaimsPageResponse>>('/claims/my-claims', { params })
      .then((r) => r.data),

  getMyClaimsStats: () =>
    httpClient
      .get<ApiResponse<CustomerClaimsStats>>('/claims/my-claims/stats')
      .then((r) => r.data),

  updateStatus: (claimId: string, update: ClaimStatusUpdateRequest) =>
    httpClient.patch<ApiResponse<ClaimResponse>>(`/claims/${claimId}/status`, update).then((r) => r.data),

  withdraw: (claimId: string) =>
    httpClient.delete<ApiResponse<ClaimResponse>>(`/claims/${claimId}`).then((r) => r.data),

  checkDuplicates: (vehicleRegistration: string, incidentDate: string, policyNumber: string) =>
    httpClient
      .post<ApiResponse<PotentialDuplicate[]>>('/claims/check-duplicates', {
        vehicleRegistration,
        incidentDate,
        policyNumber,
      })
      .then((r) => r.data),

  updateIncidentDetails: (claimId: string, payload: UpdateIncidentDetailsRequest) =>
    httpClient
      .patch<ApiResponse<ClaimResponse>>(`/claims/${claimId}/incident-details`, payload)
      .then((r) => r.data),

  getEndorsements: (claimId: string) =>
    httpClient
      .get<ApiResponse<ClaimEndorsement[]>>(`/claims/${claimId}/endorsements`)
      .then((r) => r.data),

  addEndorsement: (claimId: string, note: string) =>
    httpClient
      .post<ApiResponse<ClaimEndorsement>>(`/claims/${claimId}/endorsements`, { note })
      .then((r) => r.data),
};
