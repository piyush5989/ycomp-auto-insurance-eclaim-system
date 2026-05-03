import { useQuery } from '@tanstack/react-query'
import { workshopsApi } from '../api/workshopsApi'

export const useMyWorkshop = () =>
  useQuery({
    queryKey: ['workshop', 'my-profile'],
    queryFn: () => workshopsApi.getMyProfile(),
    staleTime: 5 * 60_000,
    select: (data) => data.data,
  })
