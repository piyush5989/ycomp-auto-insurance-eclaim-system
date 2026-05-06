package com.yclaims.documents.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentAuditLogJpaRepository extends JpaRepository<DocumentAuditLogEntity, UUID> {

    List<DocumentAuditLogEntity> findByDocumentIdOrderByOccurredAtDesc(UUID documentId);

    List<DocumentAuditLogEntity> findByClaimIdOrderByOccurredAtDesc(UUID claimId);
}
