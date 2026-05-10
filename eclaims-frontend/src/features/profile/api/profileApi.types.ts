export interface CustomerProfile {
  customerId: string
  addressLine1?: string
  addressLine2?: string
  city?: string
  state?: string
  zipCode?: string
  country?: string
  billingCycle: 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY'
  updatedAt: string
}

export interface UpdateAddressRequest {
  addressLine1: string
  addressLine2?: string
  city: string
  state?: string
  zipCode: string
  country?: string
}

export interface UpdateBillingCycleRequest {
  billingCycle: 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY'
}
