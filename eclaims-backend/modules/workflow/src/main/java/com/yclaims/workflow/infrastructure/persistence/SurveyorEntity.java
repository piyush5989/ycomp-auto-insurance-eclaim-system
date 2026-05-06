package com.yclaims.workflow.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "surveyors", schema = "workflow")
@Getter
@Setter
public class SurveyorEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "active")
    private boolean active;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SurveyorEntity other)) return false;
        return id != null && id.equals(other.id);
    }
    @Override public int hashCode() { return id != null ? id.hashCode() : 0; }
}
