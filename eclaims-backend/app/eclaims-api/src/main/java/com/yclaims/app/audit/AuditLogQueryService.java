package com.yclaims.app.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final JdbcTemplate jdbcTemplate;

    public List<AuditEventResponse> findByClaimId(UUID claimId) {
        return jdbcTemplate.query(
                """
                SELECT event_id, correlation_id, user_id, user_role, action, entity_type, entity_id,
                       old_value, new_value, ip_address, user_agent, session_id, occurred_at
                FROM audit.audit_log
                WHERE entity_id = ?
                ORDER BY occurred_at DESC
                """,
                (rs, rn) -> mapRow(rs),
                claimId.toString());
    }

    private static AuditEventResponse mapRow(ResultSet rs) throws SQLException {
        String action = rs.getString("action");
        String userAgent = rs.getString("user_agent");
        String reason = "CLAIM_OVERRIDDEN".equals(action) ? userAgent : null;
        Timestamp occurred = rs.getTimestamp("occurred_at");
        String timestamp = occurred != null ? occurred.toInstant().toString() : null;
        return new AuditEventResponse(
                rs.getString("event_id"),
                rs.getString("correlation_id"),
                rs.getString("user_id"),
                rs.getString("user_role"),
                action,
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("ip_address"),
                reason,
                userAgent,
                timestamp
        );
    }
}
