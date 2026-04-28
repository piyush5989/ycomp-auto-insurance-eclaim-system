package com.yclaims.kernel.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

/**
 * Static accessor for the current authenticated user context.
 * Builds UserContext from the Keycloak JWT stored in Spring's SecurityContextHolder.
 */
public final class UserContextHolder {

    private UserContextHolder() {}

    public static Optional<UserContext> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Optional.of(new UserContext(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                roles,
                jwt.getClaimAsString("sid")
        ));
    }

    public static UserContext require() {
        return current().orElseThrow(() ->
                new IllegalStateException("No authenticated user in security context"));
    }

    public static String currentUserId() {
        return current().map(UserContext::userId).orElse("system");
    }
}
