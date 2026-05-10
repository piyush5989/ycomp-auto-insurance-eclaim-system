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
    WORKSHOP_PROGRESS_PHOTO,  // Workshop: repair progress photos
    WORKSHOP_PROGRESS_VIDEO,  // Workshop: repair progress videos  
    WORKSHOP_BEFORE_PHOTO,    // Workshop: vehicle condition on arrival
    WORKSHOP_AFTER_PHOTO,     // Workshop: completed repair photos
    OTHER
}
