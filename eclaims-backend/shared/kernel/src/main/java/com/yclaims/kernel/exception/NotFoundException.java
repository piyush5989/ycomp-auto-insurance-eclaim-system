package com.yclaims.kernel.exception;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404 Not Found.
 */
public class NotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public NotFoundException(String resourceType, String resourceId) {
        super(resourceType + " not found: " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
