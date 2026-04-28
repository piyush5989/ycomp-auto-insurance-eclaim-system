package com.yclaims.claims.domain.exception;

import com.yclaims.kernel.exception.NotFoundException;

public class ClaimNotFoundException extends NotFoundException {
    public ClaimNotFoundException(String claimId) {
        super("Claim", claimId);
    }
}
