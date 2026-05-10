import type { ClaimStatus } from '@/features/claims/api/claimsApi.types'

export type { ClaimStatus }

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  DRAFT:               'Draft',
  SUBMITTED:           'Submitted',
  WORKSHOP_SELECTED:   'Workshop Selected',
  VEHICLE_AT_WORKSHOP: 'Vehicle at Workshop',
  ASSIGNED:            'Surveyor Assigned',
  UNDER_SURVEY:        'Under Survey',
  SURVEYED:            'Surveyed',
  UNDER_ADJUDICATION:  'Under Adjudication',
  APPROVED:            'Approved',
  REJECTED:            'Rejected',
  PAYMENT_INITIATED:   'Payment Initiated',
  PAYMENT_PROCESSED:   'Payment Processed',
  SETTLED:             'Settled',
  WITHDRAWN:           'Withdrawn',
  ARCHIVED:            'Archived',
};

export const CLAIM_STATUS_COLORS: Record<ClaimStatus, string> = {
  DRAFT:               'bg-gray-100 text-gray-700',
  SUBMITTED:           'bg-blue-100 text-blue-700',
  WORKSHOP_SELECTED:   'bg-indigo-100 text-indigo-700',
  VEHICLE_AT_WORKSHOP: 'bg-purple-100 text-purple-700',
  ASSIGNED:            'bg-violet-100 text-violet-700',
  UNDER_SURVEY:        'bg-amber-100 text-amber-700',
  SURVEYED:            'bg-orange-100 text-orange-700',
  UNDER_ADJUDICATION:  'bg-red-100 text-red-700',
  APPROVED:            'bg-green-100 text-green-700',
  REJECTED:            'bg-red-200 text-red-800',
  PAYMENT_INITIATED:   'bg-sky-100 text-sky-700',
  PAYMENT_PROCESSED:   'bg-teal-100 text-teal-800',
  SETTLED:             'bg-emerald-100 text-emerald-700',
  WITHDRAWN:           'bg-gray-200 text-gray-600',
  ARCHIVED:            'bg-gray-100 text-gray-500',
};

export function getStatusLabel(status: ClaimStatus): string {
  return CLAIM_STATUS_LABELS[status] ?? status;
}

export function getStatusColor(status: ClaimStatus): string {
  return CLAIM_STATUS_COLORS[status] ?? 'bg-gray-100 text-gray-700';
}
