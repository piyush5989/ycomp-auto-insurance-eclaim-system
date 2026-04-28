package com.yclaims.workshops.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "work_orders", schema = "workshops")
@Getter @Setter
public class WorkOrderEntity {
    @Id @Column(columnDefinition = "uuid") private UUID id;
    @Column(name = "claim_id", nullable = false, columnDefinition = "uuid") private UUID claimId;
    @Column(name = "workshop_id", nullable = false, columnDefinition = "uuid") private UUID workshopId;
    @Column(name = "estimated_cost", precision = 12, scale = 2) private BigDecimal estimatedCost;
    @Column(name = "final_cost", precision = 12, scale = 2) private BigDecimal finalCost;
    @Column(name = "repair_status", length = 30) private String repairStatus;
    @Column(name = "estimated_completion_date") private LocalDate estimatedCompletionDate;
    @Column(name = "work_description", length = 2000) private String workDescription;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof WorkOrderEntity x)) return false; return id != null && id.equals(x.id); }
    @Override public int hashCode() { return id != null ? id.hashCode() : 0; }
}
