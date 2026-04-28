package com.yclaims.workflow.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assignments", schema = "workflow")
@Getter
@Setter
public class AssignmentEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "claim_id", nullable = false, columnDefinition = "uuid")
    private UUID claimId;

    @Column(name = "surveyor_id", nullable = false, columnDefinition = "uuid")
    private UUID surveyorId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "active")
    private boolean active;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssignmentEntity other)) return false;
        return id != null && id.equals(other.id);
    }
    @Override public int hashCode() { return id != null ? id.hashCode() : 0; }
}
