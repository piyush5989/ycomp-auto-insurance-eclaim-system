package com.yclaims.notifications.presentation;

import com.yclaims.kernel.security.UserContextHolder;
import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.notifications.infrastructure.persistence.CustomerNotificationEntity;
import com.yclaims.notifications.infrastructure.persistence.CustomerNotificationJpaRepository;
import com.yclaims.notifications.presentation.dto.CustomerNotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * In-app notification API for customers.
 * Populated by Kafka consumers (claim-events, repair-events).
 * Frontend polls every 30s; upgrade to WebSocket/SSE for real-time at scale.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app customer notification center")
public class NotificationController {

    private static final int PAGE_SIZE = 20;
    private final CustomerNotificationJpaRepository notificationRepository;

    @GetMapping("/me")
    @PreAuthorize("@authz.isAllowed('notification', 'list-own')")
    @Operation(summary = "Get notifications for the authenticated customer (last 20)")
    public ResponseEntity<ApiResponse<List<CustomerNotificationResponse>>> getMyNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        String customerId = UserContextHolder.currentUserId();
        PageRequest page = PageRequest.of(0, PAGE_SIZE);

        List<CustomerNotificationEntity> entities = unreadOnly
                ? notificationRepository.findByCustomerIdAndReadFalseOrderByCreatedAtDesc(customerId, page)
                : notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, page);

        List<CustomerNotificationResponse> response = entities.stream()
                .map(this::toResponse).toList();

        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("@authz.isAllowed('notification', 'mark-read')")
    @Transactional
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID notificationId) {
        String customerId = UserContextHolder.currentUserId();
        notificationRepository.markAsRead(notificationId, customerId);
        return ResponseEntity.ok(ApiResponse.success(null, correlationId()));
    }

    private CustomerNotificationResponse toResponse(CustomerNotificationEntity e) {
        return CustomerNotificationResponse.builder()
                .id(e.getId())
                .type(e.getType())
                .title(e.getTitle())
                .message(e.getMessage())
                .claimId(e.getClaimId())
                .read(e.isRead())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
