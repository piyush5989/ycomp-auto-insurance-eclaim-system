package com.yclaims.claims.presentation.dto;

import lombok.Builder;

import java.util.List;

/**
 * Paginated response for claims query endpoint.
 * Used by internal portal claims queue with filtering and pagination.
 */
@Builder
public record ClaimsPageResponse(
        List<ClaimResponse> data,
        long totalElements,
        int totalPages,
        int currentPage,
        int pageSize
) {}
