import { useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'

export interface WorkshopClaim {
  claim_id: string
  status: string
  vehicle_registration: string
  policy_number: string
  incident_date: string
  incident_location: string | null
  description: string | null
  assessed_amount: number | null
  approved_amount: number | null
  rejection_reason: string | null
  fraud_flag: boolean
  created_at: string
  updated_at: string
}

export const useMyClaims = () =>
  useQuery({
    queryKey: ['workshop', 'my-claims'],
    queryFn: () =>
      httpClient
        .get<{ data: WorkshopClaim[] }>('/workshops/my-claims')
        .then((r) => r.data),
    staleTime: 30_000,
    select: (data) => data.data ?? [],
  })
