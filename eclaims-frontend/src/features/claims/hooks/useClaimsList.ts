import { useQuery } from '@tanstack/react-query';
import { claimsApi } from '../api/claimsApi';

export function useClaimsList() {
  return useQuery({
    queryKey: ['claims', 'my-claims'],
    queryFn: () => claimsApi.listMyClaims(),
    staleTime: 60_000,
    select: (data) => data.data ?? [],
  });
}
