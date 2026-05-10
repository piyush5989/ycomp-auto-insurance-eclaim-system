package com.yclaims.workflow.infrastructure.web;

import com.yclaims.workflow.application.WorkforceProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * After JWT authentication, upserts {@code workflow.surveyors} / {@code workflow.adjustors}
 * so {@code id} matches Keycloak {@code sub} for auto-assignment and {@code assignedTo=me} filters.
 */
@Slf4j
@RequiredArgsConstructor
public class WorkforceProvisioningFilter extends OncePerRequestFilter {

    private final WorkforceProvisioningService workforceProvisioningService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                var authorities = auth.getAuthorities();
                if (workforceProvisioningService.shouldProvisionSurveyor(authorities)) {
                    workforceProvisioningService.upsertSurveyorFromJwt(jwt);
                }
                if (workforceProvisioningService.shouldProvisionAdjustor(authorities)) {
                    workforceProvisioningService.upsertAdjustorFromJwt(jwt);
                }
            } catch (Exception e) {
                log.warn("Workforce provisioning skipped or failed for {}: {}", jwt.getSubject(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
