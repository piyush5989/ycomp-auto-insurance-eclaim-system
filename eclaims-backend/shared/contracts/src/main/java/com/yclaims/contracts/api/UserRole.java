package com.yclaims.contracts.api;

/**
 * All RBAC roles in the eClaims system.
 * Seeded in Keycloak realm. Mapped to Spring Security authorities as ROLE_{name.toUpperCase()}.
 *
 * Keycloak realm role  →  Spring Security authority
 * customer             →  ROLE_CUSTOMER
 * surveyor             →  ROLE_SURVEYOR
 * adjustor             →  ROLE_ADJUSTOR
 * case_manager         →  ROLE_CASE_MANAGER
 * auditor              →  ROLE_AUDITOR
 * workshop             →  ROLE_WORKSHOP
 * regional_mgr         →  ROLE_REGIONAL_MGR
 * top_management       →  ROLE_TOP_MANAGEMENT
 */
public enum UserRole {
    CUSTOMER,
    SURVEYOR,
    ADJUSTOR,
    CASE_MANAGER,
    AUDITOR,
    WORKSHOP,
    REGIONAL_MGR,
    TOP_MANAGEMENT;

    public String toSpringRole() {
        return "ROLE_" + this.name();
    }
}
