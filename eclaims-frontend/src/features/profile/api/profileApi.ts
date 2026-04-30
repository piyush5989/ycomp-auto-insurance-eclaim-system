import { httpClient } from '@/shared/api/httpClient'
import type { ApiResponse } from '@/shared/types/ApiResponse'
import type { CustomerProfile, UpdateAddressRequest, UpdateBillingCycleRequest } from './profileApi.types'

export const profileApi = {
  getProfile: () =>
    httpClient.get<ApiResponse<CustomerProfile>>('/customers/me').then((r) => r.data),

  updateAddress: (payload: UpdateAddressRequest) =>
    httpClient.put<ApiResponse<CustomerProfile>>('/customers/me/address', payload).then((r) => r.data),

  updateBillingCycle: (payload: UpdateBillingCycleRequest) =>
    httpClient.put<ApiResponse<CustomerProfile>>('/customers/me/billing-cycle', payload).then((r) => r.data),
}
