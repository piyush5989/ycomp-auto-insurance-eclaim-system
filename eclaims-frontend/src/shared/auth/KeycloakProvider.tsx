import React, { createContext, useContext, useEffect, useState } from 'react';
import Keycloak from 'keycloak-js';

interface AuthContextValue {
  keycloak: Keycloak | null;
  authenticated: boolean;
  token: string | null;
  userId: string | null;
  username: string | null;
  roles: string[];
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue>({
  keycloak: null,
  authenticated: false,
  token: null,
  userId: null,
  username: null,
  roles: [],
  logout: () => {},
});

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'eclaims',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'eclaims-web',
});

/**
 * Keycloak auth provider.
 * Initialises Keycloak on mount. Token stored in memory (not localStorage — OWASP XSS mitigation).
 * Axios interceptor picks up the token via getToken().
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
    keycloak
      .init({ onLoad: 'check-sso', silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html' })
      .then((authenticated) => {
        setAuthState({
          keycloak,
          authenticated,
          token: authenticated ? (keycloak.token ?? null) : null,
          userId: authenticated ? (keycloak.subject ?? null) : null,
          username: authenticated ? keycloak.tokenParsed?.['preferred_username'] : null,
          roles: extractRoles(keycloak),
          logout: () => keycloak.logout(),
        });
      })
      .catch(console.error);

    // Token refresh — keep JWT fresh
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(60).then((refreshed) => {
        if (refreshed) {
          setAuthState((prev) => ({ ...prev, token: keycloak.token ?? null }));
        }
      }).catch(() => keycloak.logout());
    };
  }, []);

  return <AuthContext.Provider value={authState}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

export function getToken(): string | null {
  return keycloak.token ?? null;
}

function extractRoles(kc: Keycloak): string[] {
  const realmRoles = kc.realmAccess?.roles ?? [];
  return realmRoles.map((r) => `ROLE_${r.toUpperCase()}`);
}
