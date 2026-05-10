package com.yclaims.claims.application;

import com.yclaims.claims.domain.exception.ClaimNotFoundException;
import com.yclaims.claims.domain.model.Claim;
import com.yclaims.claims.domain.model.ClaimId;
import com.yclaims.claims.domain.port.out.ClaimRepository;
import com.yclaims.kernel.exception.UnauthorisedException;
import com.yclaims.kernel.security.ClaimAccessPolicy;
import com.yclaims.kernel.security.UserContext;
import com.yclaims.kernel.security.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClaimAccessPolicyImpl implements ClaimAccessPolicy {

    private final ClaimRepository claimRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void assertCanAccessClaim(UUID claimId) {
        Optional<UserContext> ctxOpt = UserContextHolder.current();
        if (ctxOpt.isEmpty()) {
            return;
        }
        UserContext ctx = ctxOpt.get();
        if (isPrivileged(ctx)) {
            return;
        }
        Claim claim = claimRepository.findById(ClaimId.of(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId.toString()));
        String uid = ctx.userId();
        if (ctx.isCustomer() && uid.equals(claim.getCustomerId())) {
            return;
        }
        if (ctx.isSurveyor() && Objects.equals(uid, claim.getAssignedSurveyorId())) {
            return;
        }
        if (ctx.isAdjustor() && Objects.equals(uid, claim.getAssignedAdjustorId())) {
            return;
        }
        if (ctx.isWorkshop()) {
            UUID workshopEntityId = lookupWorkshopEntityId(uid);
            String wid = claim.getWorkshopId();
            if (workshopEntityId != null && wid != null && workshopEntityId.toString().equalsIgnoreCase(wid)) {
                return;
            }
        }
        throw new UnauthorisedException("You do not have access to this claim.");
    }

    @Override
    public void assertCustomerMayOnlyAccessOwnData(String customerId) {
        Optional<UserContext> ctxOpt = UserContextHolder.current();
        if (ctxOpt.isEmpty()) {
            return;
        }
        UserContext ctx = ctxOpt.get();
        if (isPrivileged(ctx)) {
            return;
        }
        if (ctx.isCustomer() && !Objects.equals(ctx.userId(), customerId)) {
            throw new UnauthorisedException("You may only access your own account data.");
        }
    }

    private static boolean isPrivileged(UserContext ctx) {
        return ctx.isCaseManager() || ctx.isAuditor()
                || ctx.hasRole("ROLE_REGIONAL_MGR") || ctx.hasRole("ROLE_TOP_MANAGEMENT");
    }

    private UUID lookupWorkshopEntityId(String keycloakUserId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM workshops.workshops WHERE keycloak_user_id = ? LIMIT 1",
                    UUID.class,
                    keycloakUserId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
