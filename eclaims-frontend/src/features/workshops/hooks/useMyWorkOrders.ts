import { useQuery } from '@tanstack/react-query'
import { workshopsApi } from '../api/workshopsApi'

export const useMyWorkOrders = () =>
  useQuery({
    queryKey: ['workshop', 'my-work-orders'],
    queryFn: () => workshopsApi.getMyWorkOrders(),
    staleTime: 30_000,
    select: (data) => data.data ?? [],
  })
