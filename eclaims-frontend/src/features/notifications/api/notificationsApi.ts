import { httpClient } from '@/shared/api/httpClient'
import type { ApiResponse } from '@/shared/types/ApiResponse'
import type { CustomerNotification } from './notificationsApi.types'

export const notificationsApi = {
  listMine: (unreadOnly = false) =>
    httpClient
      .get<ApiResponse<CustomerNotification[]>>('/notifications/me', {
        params: { unreadOnly },
      })
      .then((r) => r.data),

  markRead: (notificationId: string) =>
    httpClient
      .patch<ApiResponse<null>>(`/notifications/${notificationId}/read`)
      .then((r) => r.data),
}
