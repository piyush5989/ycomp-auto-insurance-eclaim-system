package com.yclaims.customers.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CustomerProfileResponse {
    private String customerId;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String billingCycle;
    private Instant updatedAt;
}
