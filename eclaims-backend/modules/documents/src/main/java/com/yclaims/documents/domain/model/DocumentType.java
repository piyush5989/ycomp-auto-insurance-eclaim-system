package com.yclaims.documents.domain.model;

public enum DocumentType {
    POLICE_REPORT,
    PHOTO_DAMAGE,           // Customer-facing: damage photos
    ACCIDENT_PHOTO,         // Legacy/internal
    VEHICLE_REGISTRATION,
    DRIVING_LICENSE,
    INSURANCE_CERTIFICATE,
    REPAIR_ESTIMATE,
    MEDICAL_REPORT,
    INVOICE,                // Customer-facing: payment invoices
    WITNESS_STATEMENT,
    OTHER
}
