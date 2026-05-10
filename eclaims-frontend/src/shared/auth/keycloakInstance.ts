import Keycloak from 'keycloak-js';

/**
 * Singleton Keycloak instance shared across the app.
 * Kept in its own module (no React exports) so Vite Fast Refresh works correctly.
 * httpClient imports getToken() from here — zero circular deps.
 */
const keycloak =
  (window as any).__eclaimsKeycloak ??
  ((window as any).__eclaimsKeycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
    realm: import.meta.env.VITE_KEYCLOAK_REALM || 'eclaims',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'eclaims-web',
  }));

export default keycloak;

/** Called by Axios request interceptor — avoids importing React context into infra layer. */
export function getToken(): string | null {
  return keycloak.token ?? null;
}
