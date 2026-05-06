package com.yclaims.workflow.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "surveyor_zip_coverage", schema = "workflow")
@Getter
public class SurveyorZipCoverageEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "zip_prefix", nullable = false, length = 5)
    private String zipPrefix;
}
