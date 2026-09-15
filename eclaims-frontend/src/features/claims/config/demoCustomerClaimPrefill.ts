import type { SubmitClaimFormData } from '@/features/claims/validation/submitClaimSchema'

/**
 * Demo-only: maps logged-in Keycloak users to policy + vehicle that match
 * `PolicyServiceStubAdapter` (local/test backend). Production PMS will drive this from APIs later.
 *
 * Two demo customers - distinct policy numbers and plates for parallel testing.
 */
export type DemoClaimPrefill = Pick<SubmitClaimFormData, 'policyNumber' | 'vehicleRegistration'> & {
  /** Shown in a small banner on Submit Claim */
  hint: string
}

const DEMO_CUSTOMERS: Record<string, DemoClaimPrefill> = {
  // Keycloak imported users (stable subs from eclaims-realm.json)
  '10000000-0000-0000-0000-000000000001': {
    policyNumber: 'POL-00000001',
    vehicleRegistration: 'CA7H2K901',
    hint: 'John Customer - auto policy (POL-00000001), vehicle CA7H2K901',
  },
  '10000000-0000-0000-0000-000000000002': {
    policyNumber: 'POL-00000002',
    vehicleRegistration: 'TX9K4M882',
    hint: 'Jane Customer - auto policy (POL-00000002), vehicle TX9K4M882',
  },
  customer1: {
    policyNumber: 'POL-00000001',
    vehicleRegistration: 'CA7H2K901',
    hint: 'John Customer - auto policy (POL-00000001), vehicle CA7H2K901',
  },
  customer2: {
    policyNumber: 'POL-00000002',
    vehicleRegistration: 'TX9K4M882',
    hint: 'Jane Customer - auto policy (POL-00000002), vehicle TX9K4M882',
  },
  'customer1@eclaims.test': {
    policyNumber: 'POL-00000001',
    vehicleRegistration: 'CA7H2K901',
    hint: 'John Customer - auto policy (POL-00000001), vehicle CA7H2K901',
  },
  'customer2@eclaims.test': {
    policyNumber: 'POL-00000002',
    vehicleRegistration: 'TX9K4M882',
    hint: 'Jane Customer - auto policy (POL-00000002), vehicle TX9K4M882',
  },
}

const disabled = import.meta.env.VITE_DISABLE_DEMO_CLAIM_PREFILL === 'true'

export const saveCustomerClaimPrefill = (data: {
  email?: string | null
  policyNumber: string
  vehicleRegistration: string
}) => {
  try {
    const payload = JSON.stringify({
      policyNumber: data.policyNumber.trim(),
      vehicleRegistration: data.vehicleRegistration.trim(),
    })
    if (data.email) {
      localStorage.setItem(`eclaims_user_prefill_${data.email.trim().toLowerCase()}`, payload)
    }
    localStorage.setItem('eclaims_last_registered_prefill', payload)
  } catch {
    // Ignore storage errors
  }
}

/**
 * Returns prefill when the current user is a known demo customer or registered user.
 */
export const resolveDemoCustomerClaimPrefill = (input: {
  userId: string | null
  username: string | null
  email: string | null
}): DemoClaimPrefill | null => {
  if (disabled) return null

  const { userId, username, email } = input
  const emailKey = email?.trim().toLowerCase() ?? ''
  const usernameKey = username?.trim().toLowerCase() ?? ''

  if (userId && DEMO_CUSTOMERS[userId]) {
    return DEMO_CUSTOMERS[userId]
  }
  if (username && DEMO_CUSTOMERS[username]) {
    return DEMO_CUSTOMERS[username]
  }
  if (emailKey && DEMO_CUSTOMERS[emailKey]) {
    return DEMO_CUSTOMERS[emailKey]
  }

  try {
    const keys = [
      emailKey ? `eclaims_user_prefill_${emailKey}` : null,
      usernameKey ? `eclaims_user_prefill_${usernameKey}` : null,
      'eclaims_last_registered_prefill',
    ].filter(Boolean) as string[]

    for (const k of keys) {
      const raw = localStorage.getItem(k)
      if (raw) {
        const item = JSON.parse(raw)
        if (item?.policyNumber && item?.vehicleRegistration) {
          return {
            policyNumber: item.policyNumber,
            vehicleRegistration: item.vehicleRegistration,
            hint: `Account policy (${item.policyNumber}), vehicle ${item.vehicleRegistration}`,
          }
        }
      }
    }
  } catch {
    // Ignore storage errors
  }

  return null
}
