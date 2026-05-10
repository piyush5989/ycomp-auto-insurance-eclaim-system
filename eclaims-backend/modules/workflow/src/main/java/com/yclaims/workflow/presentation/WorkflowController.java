package com.yclaims.workflow.presentation;

import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.workflow.application.AutoAssignmentService;
import com.yclaims.workflow.infrastructure.persistence.SurveyorEntity;
import com.yclaims.workflow.infrastructure.persistence.SurveyorJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Workflow API — surveyors, adjustors, assignments
 */
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow", description = "Surveyor and adjustor management")
public class WorkflowController {

    private final SurveyorJpaRepository surveyorRepository;
    private final AutoAssignmentService autoAssignmentService;

    @GetMapping("/surveyors")
    @PreAuthorize("@authz.isAllowed('workflow', 'list-surveyors')")
    @Operation(summary = "List all active surveyors")
    public ResponseEntity<ApiResponse<List<SurveyorResponse>>> listSurveyors(
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        List<SurveyorEntity> surveyors;
        if (activeOnly) {
            surveyors = surveyorRepository.findByActiveTrue();
        } else {
            surveyors = surveyorRepository.findAll();
        }

        if (region != null && !region.isBlank()) {
            surveyors = surveyors.stream()
                    .filter(s -> region.equalsIgnoreCase(s.getRegion()))
                    .toList();
        }

        List<SurveyorResponse> response = surveyors.stream()
                .map(s -> new SurveyorResponse(
                        s.getId().toString(),
                        s.getName(),
                        s.getEmail(),
                        s.getRegion(),
                        s.isActive()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @PostMapping("/claims/{claimId}/assign-adjustor")
    @PreAuthorize("@authz.isAllowed('workflow', 'manual-assign')")
    @Operation(summary = "Manually trigger adjustor assignment for a claim")
    public ResponseEntity<ApiResponse<String>> manuallyAssignAdjustor(@PathVariable UUID claimId) {
        String correlationId = correlationId();
        autoAssignmentService.manuallyAssignAdjustor(claimId, correlationId);
        return ResponseEntity.ok(ApiResponse.success("Adjustor assignment triggered for claim " + claimId, correlationId));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }

    public record SurveyorResponse(
            String id,
            String name,
            String email,
            String region,
            boolean active
    ) {}
}
