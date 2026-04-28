package com.yclaims.documents.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {
    List<DocumentEntity> findByClaimId(UUID claimId);
}
