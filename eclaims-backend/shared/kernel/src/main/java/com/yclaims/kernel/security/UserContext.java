package com.yclaims.kernel.security;

import java.util.List;

/**
 * Holds the authenticated user's identity and roles extracted from the Keycloak JWT.
 * Set by SecurityContextHolder; accessed via UserContextHolder throughout the request.
 */
public record UserContext(
        String userId,
        String username,
        String email,
        List<String> roles,
        String sessionId
) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isCustomer() {
        return hasRole("ROLE_CUSTOMER");
    }

    public boolean isSurveyor() {
        return hasRole("ROLE_SURVEYOR");
    }

    public boolean isAdjustor() {
        return hasRole("ROLE_ADJUSTOR");
    }

    public boolean isCaseManager() {
        return hasRole("ROLE_CASE_MANAGER");
    }

    public boolean isAuditor() {
        return hasRole("ROLE_AUDITOR");
    }

    public boolean isWorkshop() {
        return hasRole("ROLE_WORKSHOP");
    }
}
