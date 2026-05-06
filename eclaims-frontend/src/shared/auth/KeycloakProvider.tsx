import React, { createContext, useContext, useEffect, useState } from 'react'
import Keycloak from 'keycloak-js'

interface AuthContextValue {
  keycloak: Keycloak | null
  authenticated: boolean
  token: string | null
  userId: string | null
  username: string | null
  email: string | null
  roles: string[]
  logout: () => void
}

const AuthContext = createContext<AuthContextValue>({
  keycloak: null,
  authenticated: false,
  token: null,
  userId: null,
  username: null,
  email: null,
  roles: [],
  logout: () => {},
})

const KC_SINGLETON = '__eclaimsKeycloak'
const KC_INIT_PROMISE = '__eclaimsKeycloakInitPromise'
const KC_TOKEN_HOOK = '__eclaimsKeycloakTokenHook'

type KeycloakGlobal = Window &
  Record<string, Keycloak | Promise<boolean> | boolean | undefined>

const getKeycloakSingleton = (): Keycloak => {
  const w = window as KeycloakGlobal
  if (!w[KC_SINGLETON]) {
    w[KC_SINGLETON] = new Keycloak({
      url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
      realm: import.meta.env.VITE_KEYCLOAK_REALM || 'eclaims',
      clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'eclaims-web',
    })
  }
  return w[KC_SINGLETON] as Keycloak
}

const buildAuthState = (kc: Keycloak, authenticated: boolean): AuthContextValue => ({
  keycloak: kc,
  authenticated,
  token: authenticated ? (kc.token ?? null) : null,
  userId: authenticated ? (kc.subject ?? null) : null,
  username: authenticated ? (kc.tokenParsed?.preferred_username as string | undefined) ?? null : null,
  email: authenticated ? (kc.tokenParsed?.email as string | undefined) ?? null : null,
  roles: extractRoles(kc),
  logout: () => kc.logout(),
})

/**
 * Keycloak auth provider.
 * Uses one Keycloak instance and one init() promise for the whole app so React 18
 * StrictMode (double mount) does not throw "can only be initialized once".
 */
export const KeycloakProvider = ({ children }: { children: React.ReactNode }) => {
  const kc = getKeycloakSingleton()
  const [authState, setAuthState] = useState<AuthContextValue>(() => ({
    keycloak: kc,
    authenticated: false,
    token: null,
    userId: null,
    username: null,
    email: null,
    roles: [],
    logout: () => kc.logout(),
  }))

  useEffect(() => {
    let cancelled = false
    const w = window as KeycloakGlobal

    if (!w[KC_INIT_PROMISE]) {
      if (!w[KC_TOKEN_HOOK]) {
        kc.onTokenExpired = () => {
          kc
            .updateToken(60)
            .then((refreshed) => {
              if (refreshed) {
                setAuthState((prev) => ({ ...prev, token: kc.token ?? null }))
              }
            })
            .catch(() => kc.logout())
        }
        w[KC_TOKEN_HOOK] = true
      }

      w[KC_INIT_PROMISE] = kc.init({
        onLoad: 'check-sso',
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      })
    }

    const initPromise = w[KC_INIT_PROMISE] as Promise<boolean>

    initPromise
      .then((authenticated) => {
        if (cancelled) return
        setAuthState(buildAuthState(kc, authenticated))
      })
      .catch((err) => {
        if (cancelled) return
        console.error(err)
      })

    return () => {
      cancelled = true
    }
  }, [kc])

  return <AuthContext.Provider value={authState}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)

export const getToken = (): string | null => getKeycloakSingleton().token ?? null

const extractRoles = (kc: Keycloak): string[] => {
  const realmRoles = kc.realmAccess?.roles ?? []
  return realmRoles.map((r) => `ROLE_${r.toUpperCase()}`)
}
