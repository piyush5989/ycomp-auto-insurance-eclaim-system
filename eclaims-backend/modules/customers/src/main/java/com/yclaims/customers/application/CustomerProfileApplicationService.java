package com.yclaims.customers.application;

import com.yclaims.customers.infrastructure.persistence.CustomerProfileEntity;
import com.yclaims.customers.infrastructure.persistence.CustomerProfileJpaRepository;
import com.yclaims.customers.presentation.dto.CustomerProfileResponse;
import com.yclaims.customers.presentation.dto.UpdateAddressRequest;
import com.yclaims.customers.presentation.dto.UpdateBillingCycleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerProfileApplicationService {

    private final CustomerProfileJpaRepository profileRepository;

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(String customerId) {
        CustomerProfileEntity entity = getOrCreate(customerId);
        return toResponse(entity);
    }

    @Transactional
    public CustomerProfileResponse updateAddress(String customerId, UpdateAddressRequest request) {
        CustomerProfileEntity entity = getOrCreate(customerId);
        entity.setAddressLine1(request.addressLine1());
        entity.setAddressLine2(request.addressLine2());
        entity.setCity(request.city());
        entity.setState(request.state());
        entity.setZipCode(request.zipCode());
        entity.setCountry(request.country() != null ? request.country() : "US");
        profileRepository.save(entity);
        log.info("Updated correspondence address for customer {}", customerId);
        return toResponse(entity);
    }

    @Transactional
    public CustomerProfileResponse updateBillingCycle(String customerId, UpdateBillingCycleRequest request) {
        CustomerProfileEntity entity = getOrCreate(customerId);
        entity.setBillingCycle(request.billingCycle());
        profileRepository.save(entity);
        log.info("Updated billing cycle to {} for customer {}", request.billingCycle(), customerId);
        return toResponse(entity);
    }

    private CustomerProfileEntity getOrCreate(String customerId) {
        return profileRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    CustomerProfileEntity fresh = new CustomerProfileEntity();
                    fresh.setId(UUID.randomUUID());
                    fresh.setCustomerId(customerId);
                    fresh.setBillingCycle("MONTHLY");
                    fresh.setCountry("US");
                    return profileRepository.save(fresh);
                });
    }

    private CustomerProfileResponse toResponse(CustomerProfileEntity e) {
        return CustomerProfileResponse.builder()
                .customerId(e.getCustomerId())
                .addressLine1(e.getAddressLine1())
                .addressLine2(e.getAddressLine2())
                .city(e.getCity())
                .state(e.getState())
                .zipCode(e.getZipCode())
                .country(e.getCountry())
                .billingCycle(e.getBillingCycle())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
