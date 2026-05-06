package com.yclaims.claims.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for looking up a workshop's contact email by ID.
 * Implemented as a cross-schema JDBC read in the infrastructure layer —
 * acceptable in a modular monolith where both schemas share the same DB.
 */
public interface WorkshopEmailPort {
    Optional<String> findEmailByWorkshopId(UUID workshopId);
}
