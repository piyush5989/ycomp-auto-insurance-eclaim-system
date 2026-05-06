package com.yclaims.workshops.presentation;

import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.workshops.application.WorkshopApplicationService;
import com.yclaims.workshops.presentation.dto.WorkOrderRequest;
import com.yclaims.workshops.presentation.dto.WorkOrderResponse;
import com.yclaims.workshops.presentation.dto.WorkshopResponse;
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
@Tag(name = "Workshops", description = "Workshop search and work order management")
public class WorkshopController {

    private final WorkshopApplicationService workshopService;

    @GetMapping("/workshops")
    @PreAuthorize("hasAnyRole('CUSTOMER','CASE_MANAGER')")
    @Operation(summary = "Search partner workshops — cached by zip code (p95 < 500ms)")
    public ResponseEntity<ApiResponse<List<WorkshopResponse>>> searchWorkshops(
            @RequestParam(required = false) String location) {
        List<WorkshopResponse> workshops = workshopService.searchWorkshops(location, correlationId());
        return ResponseEntity.ok(ApiResponse.success(workshops, correlationId()));
    }

    @PostMapping("/work-orders")
    @PreAuthorize("hasRole('WORKSHOP')")
    @Operation(summary = "Submit a work order estimate for an approved claim")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> submitWorkOrder(
            @Valid @RequestBody WorkOrderRequest request) {
        WorkOrderResponse response = workshopService.submitWorkOrder(request, correlationId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId()));
    }

    @PatchMapping("/work-orders/{workOrderId}/repair-status")
    @PreAuthorize("hasRole('WORKSHOP')")
    @Operation(summary = "Update repair status for a work order")
    public ResponseEntity<ApiResponse<WorkOrderResponse>> updateRepairStatus(
            @PathVariable("workOrderId") UUID workOrderId,
            @RequestParam String status,
            @RequestParam(required = false) String note) {
        WorkOrderResponse response = workshopService.updateRepairStatus(workOrderId, status, note, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
