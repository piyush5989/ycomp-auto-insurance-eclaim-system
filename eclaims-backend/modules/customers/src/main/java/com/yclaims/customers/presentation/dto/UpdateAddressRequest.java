package com.yclaims.customers.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200)           String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100)           String state,
        @NotBlank @Size(max = 20)  String zipCode,
        @Size(max = 100)           String country
) {}
