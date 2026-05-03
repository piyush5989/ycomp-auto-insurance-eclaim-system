package com.yclaims.workshops.presentation;

import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.workshops.application.WorkshopApplicationService;
import com.yclaims.workshops.presentation.dto.*;
import com.yclaims.kernel.security.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Workshops", description = "Partner service provider search and work order management")
public class WorkshopController {

    private final WorkshopApplicationService workshopService;

    @GetMapping("/workshops/{workshopId}")
    @PreAuthorize("@authz.isAllowed('workshop', 'search')")
    @Operation(summary = "Get a single workshop by ID")
    public ResponseEntity<ApiResponse<WorkshopResponse>> getWorkshopById(
            @PathVariable UUID workshopId) {
        WorkshopResponse workshop = workshopService.getWorkshopById(workshopId);
        return ResponseEntity.ok(ApiResponse.success(workshop, correlationId()));
    }

    @GetMapping("/workshops/my-claims")
    @PreAuthorize("@authz.isAllowed('workshop', 'work-order-read')")
    @Operation(summary = "Get all claims assigned to the currently logged-in workshop",
               description = "Returns claims where this workshop has been selected and vehicle dropped off, including accident details for workshop context.")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getMyClaims() {
        java.util.List<java.util.Map<String, Object>> claims =
                workshopService.getMyClaimsForWorkshop(UserContextHolder.currentUserId());
        return ResponseEntity.ok(ApiResponse.success(claims, correlationId()));
    }

    @GetMapping("/workshops/my-profile")
    @PreAuthorize("@authz.isAllowed('workshop', 'work-order-read')")
    @Operation(summary = "Get the profile of the currently logged-in workshop",
               description = "Resolves the partner workshop account from the Keycloak subject claim.")
    public ResponseEntity<ApiResponse<WorkshopResponse>> getMyWorkshopProfile() {
        WorkshopResponse profile = workshopService.getMyWorkshopProfile(UserContextHolder.currentUserId());
        return ResponseEntity.ok(ApiResponse.success(profile, correlationId()));
    }

    @GetMapping("/workshops/my-work-orders")
    @PreAuthorize("@authz.isAllowed('workshop', 'work-order-read')")
    @Operation(summary = "List all work orders belonging to the currently logged-in workshop")
    public ResponseEntity<ApiResponse<List<WorkOrderResponse>>> getMyWorkOrders() {
        List<WorkOrderResponse> orders = workshopService.getMyWorkOrders(UserContextHolder.currentUserId());
        return ResponseEntity.ok(ApiResponse.success(orders, correlationId()));
    }

    @GetMapping("/workshops")
    @PreAuthorize("@authz.isAllowed('workshop', 'search')")
    @Operation(
        summary = "Search partner service providers",
        description = "Filter by providerType (REPAIR_WORKSHOP | AUTH_SERVICE_STATION | CAR_RENTAL), " +
                      "zip code, or city name. Results cached 30 min. " +
                      "FR8: Customer can check Partner Service providers by location or zip."
    )
    public ResponseEntity<ApiResponse<List<WorkshopResponse>>> searchWorkshops(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) String providerType) {
        List<WorkshopResponse> workshops = workshopService.searchWorkshops(
                location, zip, providerType, correlationId());
        return ResponseEntity.ok(ApiResponse.success(workshops, correlationId()));
    }

    @GetMapping("/claims/{claimId}/work-order")
    @PreAuthorize("@authz.isAllowed('workshop', 'work-order-read')")
    @Operation(
        summary = "Get the current work order for a claim",
        description = "FR9: Customer can track repair progress based on the work order submitted by the repair agency."
    )
    public ResponseEntity<ApiResponse<WorkOrderResponse>> getWorkOrderForClaim(
            @PathVariable UUID claimId) {
        WorkOrderResponse response = workshopService.getWorkOrderByClaimId(claimId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @PostMapping("/work-orders")
    @PreAuthorize("@authz.isAllowed('workshop', 'work-order-submit')")
    @Operation(summary = "Submit a work order estimate for an approved claim")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> submitWorkOrder(
            @Valid @RequestBody WorkOrderRequest request) {
        WorkOrderResponse response = workshopService.submitWorkOrder(request, correlationId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId()));
    }

    @PatchMapping("/work-orders/{workOrderId}/repair-status")
    @PreAuthorize("@authz.isAllowed('workshop', 'repair-status-update')")
    @Operation(summary = "Update repair status for a work order — optionally set final cost and completion date")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> updateRepairStatus(
            @PathVariable UUID workOrderId,
            @RequestParam String status,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) java.math.BigDecimal finalCost,
            @RequestParam(required = false) java.time.LocalDate estimatedCompletionDate) {
        WorkOrderResponse response = workshopService.updateRepairStatus(
                workOrderId, status, note, finalCost, estimatedCompletionDate, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @PostMapping("/claims/{claimId}/select-workshop")
    @PreAuthorize("@authz.isAllowed('workshop', 'select')")
    @Operation(summary = "Customer selects workshop for repairs - triggers surveyor assignment")
    public ResponseEntity<ApiResponse<Void>> selectWorkshop(
            @PathVariable UUID claimId,
            @Valid @RequestBody SelectWorkshopRequest request) {
        workshopService.selectWorkshopForClaim(
                claimId, request.workshopId(), UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.ok(ApiResponse.success(null, correlationId()));
    }

    @PostMapping("/claims/{claimId}/vehicle-dropoff")
    @PreAuthorize("@authz.isAllowed('workshop', 'vehicle-dropoff')")
    @Operation(summary = "Confirm vehicle drop-off at workshop")
    public ResponseEntity<ApiResponse<UUID>> confirmVehicleDropOff(
            @PathVariable UUID claimId,
            @Valid @RequestBody VehicleDropOffRequest request) {
        UUID dropOffId = workshopService.confirmVehicleDropOff(
                claimId, request, UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dropOffId, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
