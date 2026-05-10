package com.yclaims.claims.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ClaimEndorsementResponse {
    private UUID endorsementId;
    private UUID claimId;
    private String note;
    private String addedBy;
    private String endorsementType;
    private Instant createdAt;
}
