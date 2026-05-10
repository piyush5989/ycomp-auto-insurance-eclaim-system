import { useQuery } from '@tanstack/react-query'
import { workshopsApi } from '../api/workshopsApi'

const CLAIM_ID_UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export const isClaimIdUuid = (claimId: string) => CLAIM_ID_UUID.test(claimId.trim())

/**
 * Load claim status and submit eligibility without calling GET /claims/{id} (workshops lack claim#read).
 */
export const useClaimWorkOrderContext = (claimId: string | undefined) => {
  const trimmed = claimId?.trim() ?? ''
  const enabled = isClaimIdUuid(trimmed)

  return useQuery({
    queryKey: ['work-order-context', trimmed],
    queryFn: () => workshopsApi.getClaimWorkOrderContext(trimmed).then((r) => r.data),
    enabled,
    staleTime: 15_000,
  })
}
