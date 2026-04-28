package com.yclaims.claims.domain.model;

import com.yclaims.kernel.domain.EntityId;

import java.util.UUID;

/**
 * Typed ID for Claim aggregate — prevents primitive obsession and ID mix-ups across aggregates.
 */
public class ClaimId extends EntityId {

    private ClaimId(UUID value) {
        super(value);
    }

    public static ClaimId generate() {
        return new ClaimId(UUID.randomUUID());
    }

    public static ClaimId of(UUID value) {
        return new ClaimId(value);
    }

    public static ClaimId of(String value) {
        return new ClaimId(UUID.fromString(value));
    }
}
