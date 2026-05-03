import { httpClient } from '@/shared/api/httpClient'

export interface WorkshopProfile {
  id: string
  name: string
  address: string
  city: string
  zipCode: string
  phone: string
  email: string
  rating: number
  active: boolean
  providerType: string
}

export interface WorkOrderSummary {
  workOrderId: string
  claimId: string
  workshopId: string
  workshopName: string
  estimatedCost: number | null
  finalCost: number | null
  repairStatus: string
  estimatedCompletionDate: string | null
  workDescription: string | null
  createdAt: string
  updatedAt: string
}

const getById = (workshopId: string) =>
  httpClient.get<{ data: WorkshopProfile }>(`/workshops/${workshopId}`).then((r) => r.data)

const getMyProfile = () =>
  httpClient.get<{ data: WorkshopProfile }>('/workshops/my-profile').then((r) => r.data)

const getMyWorkOrders = () =>
  httpClient.get<{ data: WorkOrderSummary[] }>('/workshops/my-work-orders').then((r) => r.data)

export const workshopsApi = { getById, getMyProfile, getMyWorkOrders }
