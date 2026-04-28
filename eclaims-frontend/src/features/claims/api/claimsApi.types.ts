export type ClaimStatus =
  | 'DRAFT' | 'SUBMITTED' | 'ASSIGNED' | 'UNDER_SURVEY' | 'SURVEYED'
  | 'UNDER_ADJUDICATION' | 'APPROVED' | 'REJECTED' | 'PAYMENT_INITIATED'
  | 'SETTLED' | 'WITHDRAWN' | 'ARCHIVED';

export type ClaimType =
  | 'COLLISION' | 'COMPREHENSIVE' | 'THEFT' | 'FIRE'
  | 'FLOOD' | 'VANDALISM' | 'GLASS_DAMAGE' | 'ROADSIDE_ASSISTANCE';

export interface ClaimSubmissionRequest {
  policyNumber: string;
  vehicleRegistration: string;
  incidentDate: string;      // ISO date: "2026-04-28"
  incidentLocation?: string;
  description?: string;
  claimType: ClaimType;
  policeReportFiled: boolean;
  policeReportNumber?: string;
}

export interface ClaimResponse {
  claimId: string;
  policyNumber: string;
  customerId: string;
  vehicleRegistration: string;
  claimType: ClaimType;
  status: ClaimStatus;
  incidentDate: string;
  incidentLocation?: string;
  description?: string;
  policeReportFiled: boolean;
  assignedSurveyorId?: string;
  assignedAdjustorId?: string;
  assessedAmount?: number;
  approvedAmount?: number;
  workshopId?: string;
  rejectionReason?: string;
  fraudFlag: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ClaimStatusUpdateRequest {
  targetStatus: ClaimStatus;
  amount?: number;
  reason?: string;
  workshopId?: string;
}
