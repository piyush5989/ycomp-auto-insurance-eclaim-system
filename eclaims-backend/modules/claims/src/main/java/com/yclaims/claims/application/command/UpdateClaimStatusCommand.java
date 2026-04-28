package com.yclaims.claims.application.command;

import com.yclaims.claims.domain.model.ClaimStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateClaimStatusCommand(
        UUID claimId,
        ClaimStatus targetStatus,
        String performedByUserId,
        BigDecimal amount,
        String reason,
        String workshopId,
        String correlationId
) {}
