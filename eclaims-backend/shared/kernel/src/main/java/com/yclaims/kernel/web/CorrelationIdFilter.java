package com.yclaims.kernel.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Sets MDC context for every request:
 *   - correlationId: from X-Correlation-ID header or newly generated UUID
 *   - userId: from JWT 'sub' claim
 *
 * Echoes X-Correlation-ID back in response so clients can trace their request.
 * Must run first (Order 1) before any business filter.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = Optional
                .ofNullable(request.getHeader(CORRELATION_ID_HEADER))
                .filter(id -> !id.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_USER_ID, extractUserId());
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                return jwt.getSubject();
            }
        } catch (Exception ignored) {
            // Security context may not be populated yet at filter time
        }
        return "anonymous";
    }
}
