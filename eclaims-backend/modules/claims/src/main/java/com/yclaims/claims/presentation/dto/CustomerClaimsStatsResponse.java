package com.yclaims.claims.presentation.dto;

import lombok.Builder;

@Builder
public record CustomerClaimsStatsResponse(
        long total,
        long active,
        long settled
) {}
