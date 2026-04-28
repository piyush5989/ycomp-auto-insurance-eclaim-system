package com.yclaims.claims.domain.model;

import com.yclaims.kernel.domain.ValueObject;

import java.time.LocalDate;

/**
 * Value object capturing accident-specific information.
 * Immutable — all fields set at construction; no setters.
 */
public record AccidentDetails(
        LocalDate incidentDate,
        String incidentLocation,
        String description,
        boolean policeReportFiled,
        String policeReportNumber
) implements ValueObject {

    public AccidentDetails {
        if (incidentDate == null) throw new IllegalArgumentException("Incident date is required");
        if (incidentDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Incident date cannot be in the future");
        }
    }
}
