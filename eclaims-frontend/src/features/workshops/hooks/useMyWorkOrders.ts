import { useQuery } from '@tanstack/react-query'
import { workshopsApi } from '../api/workshopsApi'

export const useMyWorkOrders = () =>
  useQuery({
    queryKey: ['workshop', 'my-work-orders'],
    queryFn: () => workshopsApi.getMyWorkOrders(),
    staleTime: 0,
    refetchOnMount: 'always',
    select: (data) => data.data ?? [],
  })
