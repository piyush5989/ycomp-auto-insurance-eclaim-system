const STORAGE_KEY_PREFIX = 'eclaims-registration-prefill:'

export interface RegistrationPrefill {
  policyNumber: string
  vehicleRegistration: string
}

const keyFor = (email: string) => `${STORAGE_KEY_PREFIX}${email.trim().toLowerCase()}`

/**
 * Demo/POC-scoped: stashes the policy + vehicle the user just verified during
 * self-registration so the claim submission form can prefill it in this browser
 * session. Production replacement is a persisted customer/policy lookup (see
 * OnboardingApplicationService) — this is intentionally session-scoped only.
 */
export const saveRegistrationPrefill = (email: string, data: RegistrationPrefill) => {
  try {
    sessionStorage.setItem(keyFor(email), JSON.stringify(data))
  } catch {
    /* ignore private mode / quota */
  }
}

export const getRegistrationPrefill = (email: string | null): RegistrationPrefill | null => {
  if (!email) return null
  try {
    const raw = sessionStorage.getItem(keyFor(email))
    if (!raw) return null
    return JSON.parse(raw) as RegistrationPrefill
  } catch {
    return null
  }
}
