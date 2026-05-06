package com.yclaims.claims.infrastructure.integration;

import com.yclaims.claims.domain.port.out.WorkshopEmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads workshop contact email via a cross-schema SELECT.
 * The workshops schema lives in the same PostgreSQL instance, making this a
 * lightweight read with no network hop — an accepted pattern in a modular monolith.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkshopEmailJdbcAdapter implements WorkshopEmailPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<String> findEmailByWorkshopId(UUID workshopId) {
        if (workshopId == null) return Optional.empty();
        try {
            String email = jdbcTemplate.queryForObject(
                    "SELECT email FROM workshops.workshops WHERE id = ?::uuid",
                    String.class,
                    workshopId.toString()
            );
            return Optional.ofNullable(email);
        } catch (Exception e) {
            log.warn("Could not look up workshop email for id={}: {}", workshopId, e.getMessage());
            return Optional.empty();
        }
    }
}
