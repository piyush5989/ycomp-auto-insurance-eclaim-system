package com.yclaims.workflow.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "adjustors", schema = "workflow")
@Getter
@Setter
public class AdjustorEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 50)
    private String region;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "field_office", length = 100)
    private String fieldOffice;

    @Column(name = "service_areas", columnDefinition = "TEXT")
    private String serviceAreas;
}
