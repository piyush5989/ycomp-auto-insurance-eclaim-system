package com.yclaims.reporting.presentation.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class FraudAgeingResponse {
    private UUID claimId;
    private String policyNumber;
    private String fraudReason;
    private long ageInHours;
    private String ageingBucket; // "< 1hr", "1-24hrs", "1-7days", "> 7days"
    private BigDecimal assessedAmount;
    private String currentStatus;
}
