package com.yclaims.payments.presentation.dto;

import java.math.BigDecimal;

/** Line items for the customer payment screen, same rules as final bill settlement. */
public record BillPreviewResponse(
        BigDecimal approvedAmount,
        BigDecimal workshopFinalCost,
        BigDecimal processingFee,
        BigDecimal totalDue
) {}
