import React, { createContext, useContext, useEffect, useState } from 'react';
import Keycloak from 'keycloak-js';
import keycloak from './keycloakInstance';

interface AuthContextValue {
  keycloak: Keycloak | null;
  authenticated: boolean;
  token: string | null;
  userId: string | null;
  username: string | null;
  email: string | null;
  roles: string[];
  logout: () => void;
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
});

/**
 * Keycloak auth provider.
 * Initialises Keycloak on mount. Token stored in memory (not localStorage — OWASP XSS mitigation).
 * Axios interceptor picks up the token via getToken() from keycloakInstance.ts.
 */
export function KeycloakProvider({ children }: { children: React.ReactNode }) {
  const [authState, setAuthState] = useState<AuthContextValue>({
    keycloak: null,
    authenticated: false,
    token: null,
    userId: null,
    username: null,
    roles: [],
    logout: () => {},
  });

  useEffect(() => {
    // React 18 StrictMode + Vite HMR can mount/unmount twice in dev.
    // Keycloak can only be initialised once per instance — memoize init globally.
    const initPromise: Promise<boolean> =
      (window as any).__eclaimsKeycloakInitPromise ??
      ((window as any).__eclaimsKeycloakInitPromise = keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        checkLoginIframe: false,
      }));

    initPromise
      .then((authenticated) => {
        setAuthState({
          keycloak,
          authenticated,
          token: authenticated ? (keycloak.token ?? null) : null,
          userId: authenticated ? (keycloak.subject ?? null) : null,
          username: authenticated ? keycloak.tokenParsed?.['preferred_username'] : null,
          email: authenticated ? (keycloak.tokenParsed?.['email'] as string | undefined) ?? null : null,
          roles: extractRoles(keycloak),
          logout: () => keycloak.logout(),
        });
      })
      .catch(console.error);

    if (!(window as any).__eclaimsKeycloakTokenHandlerAttached) {
      (window as any).__eclaimsKeycloakTokenHandlerAttached = true;
      keycloak.onTokenExpired = () => {
        keycloak
          .updateToken(60)
          .then((refreshed) => {
            if (refreshed) {
              setAuthState((prev) => ({ ...prev, token: keycloak.token ?? null }));
            }
          })
          .catch(() => keycloak.logout());
      };
    }
  }, []);

  return <AuthContext.Provider value={authState}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

function extractRoles(kc: Keycloak): string[] {
  const realmRoles = kc.realmAccess?.roles ?? [];
  return realmRoles.map((r) => `ROLE_${r.toUpperCase()}`);
}
