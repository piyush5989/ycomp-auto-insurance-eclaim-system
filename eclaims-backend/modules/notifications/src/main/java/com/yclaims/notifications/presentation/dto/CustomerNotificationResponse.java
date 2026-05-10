package com.yclaims.notifications.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CustomerNotificationResponse {
    private UUID id;
    private String type;
    private String title;
    private String message;
    private UUID claimId;
    private boolean read;
    private Instant createdAt;
}
