package com.yclaims.documents.infrastructure.persistence;

import com.yclaims.documents.domain.model.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    /** Active documents for a claim - shown in default listings. */
    List<DocumentEntity> findByClaimIdAndStatus(UUID claimId, DocumentStatus status);

    /** All versions (active + superseded + archived) - used by auditors. */
    List<DocumentEntity> findByClaimId(UUID claimId);

    /** Count active documents only to enforce per-claim upload limits. */
    long countByClaimIdAndStatus(UUID claimId, DocumentStatus status);
}
