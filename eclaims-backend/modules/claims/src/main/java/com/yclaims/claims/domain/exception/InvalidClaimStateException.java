package com.yclaims.claims.domain.exception;

import com.yclaims.kernel.exception.DomainException;
import com.yclaims.claims.domain.model.ClaimId;

public class InvalidClaimStateException extends DomainException {
    public InvalidClaimStateException(ClaimId claimId, String message) {
        super("INVALID_CLAIM_STATE", "Claim [" + claimId + "]: " + message);
    }
}
