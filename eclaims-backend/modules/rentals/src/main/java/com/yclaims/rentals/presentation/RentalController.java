package com.yclaims.rentals.presentation;

import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.rentals.presentation.dto.RentalVehicleResponse;
import com.yclaims.rentals.presentation.dto.ReserveVehicleRequest;
import com.yclaims.rentals.presentation.dto.ReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for rental vehicle management.
 * Allows customers to browse and reserve rental vehicles during claim processing.
 */
@RestController
@RequestMapping("/api/v1/rentals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rentals", description = "Rental vehicle browsing and reservation")
public class RentalController {

    @GetMapping("/vehicles")
    @PreAuthorize("hasAnyRole('CUSTOMER','CASE_MANAGER','AUDITOR')")
    @Operation(summary = "List available rental vehicles")
    public ResponseEntity<ApiResponse<List<RentalVehicleResponse>>> listVehicles(
            @RequestParam(defaultValue = "true") boolean availableOnly) {
        String correlationId = correlationId();
        log.info("[{}] Fetching rental vehicles (availableOnly: {})", correlationId, availableOnly);

        // TODO: Implement actual query from rentals.rental_vehicles table
        // For now, return mock data matching database seed
        List<RentalVehicleResponse> vehicles = List.of(
                RentalVehicleResponse.builder()
                        .vehicleId(UUID.fromString("c1c1c1c1-0000-0000-0000-000000000001"))
                        .vehicleType("COMPACT")
                        .make("Toyota")
                        .model("Corolla")
                        .year(2024)
                        .seatingCapacity(5)
                        .transmissionType("AUTOMATIC")
                        .fuelType("GASOLINE")
                        .dailyRate(new BigDecimal("35.00"))
                        .available(true)
                        .providerId(UUID.fromString("d1d1d1d1-0000-0000-0000-000000000001"))
                        .providerName("Enterprise Rent-A-Car")
                        .build(),
                RentalVehicleResponse.builder()
                        .vehicleId(UUID.fromString("c1c1c1c1-0000-0000-0000-000000000002"))
                        .vehicleType("SEDAN")
                        .make("Honda")
                        .model("Accord")
                        .year(2024)
                        .seatingCapacity(5)
                        .transmissionType("AUTOMATIC")
                        .fuelType("GASOLINE")
                        .dailyRate(new BigDecimal("45.00"))
                        .available(true)
                        .providerId(UUID.fromString("d1d1d1d1-0000-0000-0000-000000000001"))
                        .providerName("Enterprise Rent-A-Car")
                        .build(),
                RentalVehicleResponse.builder()
                        .vehicleId(UUID.fromString("c1c1c1c1-0000-0000-0000-000000000003"))
                        .vehicleType("SUV")
                        .make("Ford")
                        .model("Explorer")
                        .year(2024)
                        .seatingCapacity(7)
                        .transmissionType("AUTOMATIC")
                        .fuelType("GASOLINE")
                        .dailyRate(new BigDecimal("65.00"))
                        .available(true)
                        .providerId(UUID.fromString("d1d1d1d1-0000-0000-0000-000000000002"))
                        .providerName("Hertz")
                        .build(),
                RentalVehicleResponse.builder()
                        .vehicleId(UUID.fromString("c1c1c1c1-0000-0000-0000-000000000004"))
                        .vehicleType("LUXURY")
                        .make("BMW")
                        .model("3 Series")
                        .year(2024)
                        .seatingCapacity(5)
                        .transmissionType("AUTOMATIC")
                        .fuelType("GASOLINE")
                        .dailyRate(new BigDecimal("95.00"))
                        .available(true)
                        .providerId(UUID.fromString("d1d1d1d1-0000-0000-0000-000000000002"))
                        .providerName("Hertz")
                        .build()
        );

        log.info("[{}] Returning {} rental vehicles", correlationId, vehicles.size());
        return ResponseEntity.ok(ApiResponse.success(vehicles, correlationId));
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Reserve a rental vehicle for a claim")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserveVehicle(
            @Valid @RequestBody ReserveVehicleRequest request) {
        String correlationId = correlationId();
        log.info("[{}] Reserving rental vehicle {} for claim {} ({} days)",
                correlationId, request.vehicleId(), request.claimId(), request.rentalDays());

        // TODO: Implement actual reservation logic
        // 1. Validate claim exists and belongs to customer
        // 2. Validate vehicle is available
        // 3. Calculate dates and cost
        // 4. Create reservation in rentals.rental_reservations
        // 5. Update vehicle availability status
        // 6. Publish rental.reserved event
        // 7. Send notification to customer

        UUID reservationId = UUID.randomUUID();
        BigDecimal dailyRate = new BigDecimal("45.00"); // Mock - should fetch from vehicle
        BigDecimal totalCost = dailyRate.multiply(new BigDecimal(request.rentalDays()));
        Instant now = Instant.now();

        ReservationResponse response = ReservationResponse.builder()
                .reservationId(reservationId)
                .claimId(request.claimId())
                .vehicleId(request.vehicleId())
                .dailyRate(dailyRate)
                .totalCost(totalCost)
                .rentalDays(request.rentalDays())
                .reservationStart(now)
                .reservationEnd(now.plusSeconds(request.rentalDays() * 86400L))
                .status("RESERVED")
                .build();

        log.info("[{}] 🚙 Rental vehicle reserved | Reservation: {} | Claim: {} | Vehicle: {} | Duration: {} days | Total: ${}",
                correlationId,
                reservationId,
                request.claimId(),
                request.vehicleId(),
                request.rentalDays(),
                totalCost);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId));
    }

    @GetMapping("/reservations/{claimId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get rental reservation for a claim")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(@PathVariable UUID claimId) {
        String correlationId = correlationId();
        log.info("[{}] Fetching rental reservation for claim {}", correlationId, claimId);

        // TODO: Implement actual query
        // For now, return empty (no reservation found)
        return ResponseEntity.ok(ApiResponse.success(null, correlationId));
    }

    private String correlationId() {
        String cid = MDC.get("correlationId");
        return cid != null ? cid : UUID.randomUUID().toString();
    }
}
