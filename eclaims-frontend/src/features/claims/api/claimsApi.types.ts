export type ClaimStatus =
  | 'DRAFT' | 'SUBMITTED' | 'WORKSHOP_SELECTED' | 'VEHICLE_AT_WORKSHOP'
  | 'ASSIGNED' | 'UNDER_SURVEY' | 'SURVEYED'
  | 'UNDER_ADJUDICATION' | 'APPROVED' | 'REJECTED' | 'PAYMENT_INITIATED'
  | 'PAYMENT_PROCESSED' | 'SETTLED' | 'WITHDRAWN' | 'ARCHIVED';

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
  estimatedAmount?: number;
  assessedAmount?: number;
  approvedAmount?: number;
  workshopId?: string;
  rejectionReason?: string;
  fraudFlag: boolean;
  rentalReservationId?: string;
  rentalStatus?: 'NOT_SELECTED' | 'RESERVED' | 'SKIPPED';
  surveyCompletedAt?: string;
  adjudicatedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ClaimStatusUpdateRequest {
  targetStatus: ClaimStatus;
  amount?: number;
  reason?: string;
  workshopId?: string;
}

export interface PotentialDuplicate {
  claimId: string;
  policyNumber: string;
  vehicleRegistration: string;
  claimType: ClaimType;
  status: ClaimStatus;
  incidentDate: string;
  incidentLocation?: string;
  createdAt: string;
}

export interface UpdateIncidentDetailsRequest {
  incidentLocation?: string;
  description?: string;
}

export interface ClaimEndorsement {
  endorsementId: string;
  claimId: string;
  note: string;
  addedBy: string;
  endorsementType: string;
  createdAt: string;
}
