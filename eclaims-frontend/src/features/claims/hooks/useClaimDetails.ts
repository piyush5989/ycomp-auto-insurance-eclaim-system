import { useQuery } from '@tanstack/react-query';
import { claimsApi } from '../api/claimsApi';

/**
 * Fetch a single claim by ID.
 * staleTime: 30s — claim status can change, but we don't need sub-second freshness.
 * The claim is never cached in Zustand — React Query owns server state.
 */
export function useClaimDetails(claimId: string | undefined) {
  return useQuery({
    queryKey: ['claim', claimId],
    queryFn: () => claimsApi.getById(claimId!),
    enabled: !!claimId,
    staleTime: 30_000,
    select: (data) => data.data,
  });
}
