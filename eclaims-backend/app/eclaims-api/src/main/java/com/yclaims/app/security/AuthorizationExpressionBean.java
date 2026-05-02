package com.yclaims.app.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Spring Security expression extension bean — registered as "authz".
 *
 * Controllers reference it in @PreAuthorize as:
 *   @PreAuthorize("@authz.isAllowed('claim', 'submit')")
 *
 * This replaces hardcoded hasRole() / hasAnyRole() checks with a Keycloak
 * Authorization Services call, so permissions are configurable in Keycloak
 * Admin UI without any code change or redeployment.
 */
@Component("authz")
@RequiredArgsConstructor
public class AuthorizationExpressionBean {

    private final KeycloakAuthorizationService authorizationService;

    /**
     * Returns true if the currently authenticated user is allowed to perform
     * {@code scope} on {@code resource}, as evaluated by Keycloak Authorization Services.
     *
     * @param resource Keycloak resource name (e.g. "claim", "document", "report")
     * @param scope    Keycloak scope name  (e.g. "submit", "read", "adjudicate")
     */
    public boolean isAllowed(String resource, String scope) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return false;
        }
        var jwt = jwtAuth.getToken();
        return authorizationService.isAllowed(
                jwt.getSubject(),
                resource,
                scope,
                jwt.getTokenValue()
        );
    }
}
