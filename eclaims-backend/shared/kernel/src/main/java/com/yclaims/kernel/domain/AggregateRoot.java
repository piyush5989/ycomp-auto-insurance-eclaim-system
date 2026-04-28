package com.yclaims.kernel.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all aggregate roots.
 * Holds a list of uncommitted domain events that the Application layer publishes after save.
 * Pattern: aggregate records what happened; application service publishes it.
 */
public abstract class AggregateRoot {

    private final List<Object> domainEvents = new ArrayList<>();

    protected void registerEvent(Object event) {
        domainEvents.add(event);
    }

    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
