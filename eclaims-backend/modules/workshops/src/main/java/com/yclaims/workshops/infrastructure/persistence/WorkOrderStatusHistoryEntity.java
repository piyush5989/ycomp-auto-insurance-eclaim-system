package com.yclaims.workshops.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "work_order_status_history", schema = "workshops")
@Getter @Setter
public class WorkOrderStatusHistoryEntity {
    @Id 
    @Column(columnDefinition = "uuid") 
    private UUID id;
    
    @Column(name = "work_order_id", nullable = false, columnDefinition = "uuid") 
    private UUID workOrderId;
    
    @Column(name = "status", nullable = false, length = 30) 
    private String status;
    
    @Column(name = "note", columnDefinition = "TEXT") 
    private String note;
    
    @Column(name = "estimated_completion_date") 
    private LocalDate estimatedCompletionDate;
    
    @Column(name = "changed_by_user_id", length = 255) 
    private String changedByUserId;
    
    @Column(name = "changed_at", nullable = false) 
    private Instant changedAt;

    @Override 
    public boolean equals(Object o) { 
        if (this == o) return true; 
        if (!(o instanceof WorkOrderStatusHistoryEntity x)) return false; 
        return id != null && id.equals(x.id); 
    }
    
    @Override 
    public int hashCode() { 
        return id != null ? id.hashCode() : 0; 
    }
}
