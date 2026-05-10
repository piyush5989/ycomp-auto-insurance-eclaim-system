package com.yclaims.workflow.application;

import com.yclaims.workflow.infrastructure.persistence.AdjustorEntity;
import com.yclaims.workflow.infrastructure.persistence.AdjustorJpaRepository;
import com.yclaims.workflow.infrastructure.persistence.SurveyorEntity;
import com.yclaims.workflow.infrastructure.persistence.SurveyorJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Keeps {@code workflow.surveyors} / {@code workflow.adjustors} aligned with Keycloak.
 *
 * <h3>ID-alignment strategy</h3>
 * The canonical identifier for every workforce member is the Keycloak JWT {@code sub}
 * (a UUID).  That value is used as the primary key in workflow tables so that
 * {@code assigned_surveyor_id} / {@code assigned_adjustor_id} and
 * {@code assignedTo=me} queries are always consistent.
 *
 * <h3>Merge-by-email self-healing</h3>
 * When a user logs in we perform:
 * <ol>
 *   <li>Look up row by {@code id = sub} (happy path — IDs already match).</li>
 *   <li>If not found by ID, look up by {@code email}.</li>
 *   <li>If found by email with a <em>different</em> ID (seed/Keycloak drift):
 *       migrate the row — update FK references then swap the PK — so the user
 *       sees their existing assignments immediately without any manual intervention.</li>
 *   <li>If not found at all: insert a new row (new workforce member).</li>
 * </ol>
 *
 * This makes the system self-healing: one login after the Keycloak realm is
 * re-imported (or after running {@code scripts/reset-keycloak-realm.ps1})
 * guarantees all IDs are correct.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkforceProvisioningService {

    private static final String ROLE_SURVEYOR = "ROLE_SURVEYOR";
    private static final String ROLE_ADJUSTOR = "ROLE_ADJUSTOR";

    private final SurveyorJpaRepository surveyorRepository;
    private final AdjustorJpaRepository adjustorRepository;
    private final JdbcTemplate jdbc;

    public boolean shouldProvisionSurveyor(java.util.Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().anyMatch(a -> ROLE_SURVEYOR.equals(a.getAuthority()));
    }

    public boolean shouldProvisionAdjustor(java.util.Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().anyMatch(a -> ROLE_ADJUSTOR.equals(a.getAuthority()));
    }

    @Transactional
    public void upsertSurveyorFromJwt(Jwt jwt) {
        UUID keycloakId = parseUserId(jwt);
        String email    = resolveEmail(jwt);
        String name     = buildDisplayName(jwt);

        surveyorRepository.findById(keycloakId).ifPresentOrElse(
            existing -> updateSurveyorIfChanged(existing, name, email),
            () -> surveyorRepository.findByEmail(email).ifPresentOrElse(
                stale -> migrateSurveyorId(stale, keycloakId, name, email),
                () -> insertSurveyor(keycloakId, name, email)
            )
        );
    }

    private void updateSurveyorIfChanged(SurveyorEntity s, String name, String email) {
        boolean changed = false;
        if (!email.equals(s.getEmail())) { s.setEmail(email); changed = true; }
        if (!name.equals(s.getName()))   { s.setName(name);   changed = true; }
        if (changed) {
            surveyorRepository.save(s);
            log.info("Updated workflow.surveyors name/email for id={}", s.getId());
        }
    }

    /**
     * Seed/Keycloak ID drift detected: an existing row has the same email but
     * a different PK.  Migrate FK references then replace the PK.
     *
     * Steps (all in one transaction):
     *  1. Update workflow.assignments.surveyor_id  (FK reference)
     *  2. Update claims.claims.assigned_surveyor_id (cross-schema, same DB)
     *  3. Delete the stale row (FK is now gone)
     *  4. Re-insert with the Keycloak sub as PK (keeps region/field_office)
     */
    private void migrateSurveyorId(SurveyorEntity stale, UUID newId, String name, String email) {
        UUID oldId = stale.getId();
        log.warn("Surveyor ID drift detected for email={}: DB has {} but Keycloak sub is {}. Migrating...",
                email, oldId, newId);

        jdbc.update("UPDATE workflow.assignments  SET surveyor_id           = ? WHERE surveyor_id           = ?",
                newId, oldId);
        jdbc.update("UPDATE claims.claims         SET assigned_surveyor_id  = ? WHERE assigned_surveyor_id  = ?",
                newId.toString(), oldId.toString());

        surveyorRepository.deleteById(oldId);
        surveyorRepository.flush();

        SurveyorEntity migrated = new SurveyorEntity();
        migrated.setId(newId);
        migrated.setName(name);
        migrated.setEmail(email);
        migrated.setRegion(stale.getRegion());
        migrated.setActive(stale.isActive());
        surveyorRepository.save(migrated);

        log.info("Surveyor migrated: {} -> {} (email={})", oldId, newId, email);
    }

    private void insertSurveyor(UUID id, String name, String email) {
        SurveyorEntity row = new SurveyorEntity();
        row.setId(id);
        row.setEmail(email);
        row.setName(name);
        row.setRegion(null);
        row.setActive(true);
        surveyorRepository.save(row);
        log.info("Inserted workflow.surveyors for Keycloak sub={} ({}) — set region for routing", id, email);
    }

    @Transactional
    public void upsertAdjustorFromJwt(Jwt jwt) {
        UUID keycloakId = parseUserId(jwt);
        String email    = resolveEmail(jwt);
        String name     = buildDisplayName(jwt);

        adjustorRepository.findById(keycloakId).ifPresentOrElse(
            existing -> updateAdjustorIfChanged(existing, name, email),
            () -> adjustorRepository.findByEmail(email).ifPresentOrElse(
                stale -> migrateAdjustorId(stale, keycloakId, name, email),
                () -> insertAdjustor(keycloakId, name, email)
            )
        );
    }

    private void updateAdjustorIfChanged(AdjustorEntity a, String name, String email) {
        boolean changed = false;
        if (!email.equals(a.getEmail())) { a.setEmail(email); changed = true; }
        if (!name.equals(a.getName()))   { a.setName(name);   changed = true; }
        if (changed) {
            adjustorRepository.save(a);
            log.info("Updated workflow.adjustors name/email for id={}", a.getId());
        }
    }

    private void migrateAdjustorId(AdjustorEntity stale, UUID newId, String name, String email) {
        UUID oldId = stale.getId();
        log.warn("Adjustor ID drift detected for email={}: DB has {} but Keycloak sub is {}. Migrating...",
                email, oldId, newId);

        jdbc.update("UPDATE claims.claims SET assigned_adjustor_id = ? WHERE assigned_adjustor_id = ?",
                newId.toString(), oldId.toString());

        adjustorRepository.deleteById(oldId);
        adjustorRepository.flush();

        AdjustorEntity migrated = new AdjustorEntity();
        migrated.setId(newId);
        migrated.setName(name);
        migrated.setEmail(email);
        migrated.setRegion(stale.getRegion());
        migrated.setActive(stale.getActive());
        adjustorRepository.save(migrated);

        log.info("Adjustor migrated: {} -> {} (email={})", oldId, newId, email);
    }

    private void insertAdjustor(UUID id, String name, String email) {
        AdjustorEntity row = new AdjustorEntity();
        row.setId(id);
        row.setEmail(email);
        row.setName(name);
        row.setRegion(null);
        row.setActive(true);
        adjustorRepository.save(row);
        log.info("Inserted workflow.adjustors for Keycloak sub={} ({}) — set region for routing", id, email);
    }

    private static UUID parseUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT subject is not a UUID; cannot provision workflow row: " + jwt.getSubject(), e);
        }
    }

    private static String resolveEmail(Jwt jwt) {
        return firstNonBlank(
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username") + "@provisional.eclaims.invalid");
    }

    private static String buildDisplayName(Jwt jwt) {
        String given  = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
            return (given + " " + family).trim();
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (preferred != null && !preferred.isBlank()) return preferred;
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) return email;
        return "User";
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a.trim() : (b != null ? b.trim() : "unknown@eclaims.local");
    }
}
