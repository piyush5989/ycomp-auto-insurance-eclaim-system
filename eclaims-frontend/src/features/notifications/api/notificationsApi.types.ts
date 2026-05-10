export type NotificationType =
  | 'CLAIM_SUBMITTED'
  | 'CLAIM_STATUS_CHANGED'
  | 'REPAIR_STATUS_UPDATED'
  | 'PAYMENT_CONFIRMED'

export interface CustomerNotification {
  id: string
  type: NotificationType
  title: string
  message: string
  claimId?: string
  read: boolean
  createdAt: string
}
