package com.yclaims.workshops.presentation.dto;

import java.util.UUID;

/**
 * Minimal claim facts for the partner workshop work-order UI (no {@code claim#read} required).
 */
public record ClaimWorkOrderContextResponse(
        UUID claimId,
        String status,
        boolean workshopMatches,
        boolean canSubmitWorkOrder
) {}
