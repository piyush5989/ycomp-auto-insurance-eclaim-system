package com.yclaims.workshops.presentation.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class WorkshopResponse {
    private UUID id;
    private String name;
    private String address;
    private String city;
    private String zipCode;
    private String phone;
    private String email;
    private double rating;
    private boolean active;
}
