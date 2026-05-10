import { useQuery } from '@tanstack/react-query';
import { claimsApi } from '../api/claimsApi';
import type { ClaimsPageResponse, CustomerClaimsStats } from '../api/claimsApi.types';

const MY_CLAIMS_SORT = { sortBy: 'createdAt', sortOrder: 'desc' } as const;

export const useCustomerClaimsPage = (page: number, pageSize: number) => {
  return useQuery({
    queryKey: ['claims', 'my-claims', 'page', page, pageSize],
    queryFn: () =>
      claimsApi.listMyClaimsPage({
        page,
        size: pageSize,
        sortBy: MY_CLAIMS_SORT.sortBy,
        sortOrder: MY_CLAIMS_SORT.sortOrder,
      }),
    staleTime: 60_000,
    select: (envelope): ClaimsPageResponse | null => envelope.data ?? null,
  });
};

export const useCustomerClaimsStats = () => {
  return useQuery({
    queryKey: ['claims', 'my-claims', 'stats'],
    queryFn: () => claimsApi.getMyClaimsStats(),
    staleTime: 60_000,
    select: (envelope): CustomerClaimsStats | null => envelope.data ?? null,
  });
};
