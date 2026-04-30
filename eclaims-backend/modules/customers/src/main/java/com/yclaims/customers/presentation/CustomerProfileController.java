package com.yclaims.customers.presentation;

import com.yclaims.customers.application.CustomerProfileApplicationService;
import com.yclaims.customers.presentation.dto.CustomerProfileResponse;
import com.yclaims.customers.presentation.dto.UpdateAddressRequest;
import com.yclaims.customers.presentation.dto.UpdateBillingCycleRequest;
import com.yclaims.kernel.security.UserContextHolder;
import com.yclaims.kernel.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Customer self-service profile API.
 * Manages correspondence address and billing cycle preferences.
 * Identity (auth) remains in Keycloak — this module owns mutable domain preferences only.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Profile", description = "Customer self-service profile management")
public class CustomerProfileController {

    private final CustomerProfileApplicationService profileService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get current customer profile (address, billing cycle)")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getMyProfile() {
        String customerId = UserContextHolder.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfile(customerId), correlationId()));
    }

    @PutMapping("/me/address")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update correspondence address")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateAddress(
            @Valid @RequestBody UpdateAddressRequest request) {
        String customerId = UserContextHolder.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(profileService.updateAddress(customerId, request), correlationId()));
    }

    @PutMapping("/me/billing-cycle")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update billing cycle preference — MONTHLY | QUARTERLY | ANNUALLY")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateBillingCycle(
            @Valid @RequestBody UpdateBillingCycleRequest request) {
        String customerId = UserContextHolder.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(profileService.updateBillingCycle(customerId, request), correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
