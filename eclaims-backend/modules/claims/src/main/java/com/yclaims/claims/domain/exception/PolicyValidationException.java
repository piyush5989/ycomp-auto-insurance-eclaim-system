package com.yclaims.claims.domain.exception;

import com.yclaims.kernel.exception.DomainException;

public class PolicyValidationException extends DomainException {
    public PolicyValidationException(String policyNumber, String reason) {
        super("POLICY_VALIDATION_FAILED",
              "Policy validation failed for [" + policyNumber + "]: " + reason);
    }
}
