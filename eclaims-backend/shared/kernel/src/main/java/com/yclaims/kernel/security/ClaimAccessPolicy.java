package com.yclaims.kernel.security;

import java.util.UUID;

public interface ClaimAccessPolicy {

    void assertCanAccessClaim(UUID claimId);

    /**
     * When the caller is a customer, they must not pass another user's customer id
     * (defense in depth for list or stats APIs).
     */
    void assertCustomerMayOnlyAccessOwnData(String customerId);
}
