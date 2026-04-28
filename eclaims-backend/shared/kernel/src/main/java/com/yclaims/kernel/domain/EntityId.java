package com.yclaims.kernel.domain;

import java.util.UUID;

/**
 * Base typed ID backed by UUID — globally unique, safe across microservice boundaries.
 * Subclass per aggregate: ClaimId, PaymentId, WorkshopId, etc.
 */
public abstract class EntityId {

    private final UUID value;

    protected EntityId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("EntityId value must not be null");
        }
        this.value = value;
    }

    protected EntityId(String value) {
        this(UUID.fromString(value));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityId entityId = (EntityId) o;
        return value.equals(entityId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
