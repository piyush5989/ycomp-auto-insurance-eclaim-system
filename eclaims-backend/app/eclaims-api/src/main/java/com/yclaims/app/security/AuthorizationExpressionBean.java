package com.yclaims.app.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Spring Security expression bean used in {@code @PreAuthorize("@authz.isAllowed(...)")}
 * to delegate permission checks to Keycloak UMA Authorization Services.
 */
@Component("authz")
@RequiredArgsConstructor
public class AuthorizationExpressionBean {

    private final KeycloakAuthorizationService authorizationService;

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
