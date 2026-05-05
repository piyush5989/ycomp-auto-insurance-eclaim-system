import { useEffect, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../../shared/auth/KeycloakProvider';

interface ClaimStatusUpdate {
  claimId: string;
  previousStatus: string;
  newStatus: string;
  changedByUserId: string;
  reason: string | null;
  occurredAt: string;
}

/**
 * Subscribes to real-time claim status updates via Server-Sent Events.
 *
 * Opens a persistent SSE connection to /api/v1/claims/{claimId}/events.
 * On each 'claim-status' event, invalidates the React Query cache for
 * both the individual claim and the customer's claims list — triggering
 * an instant background refetch and UI update without polling.
 *
 * The SSE connection includes the Keycloak JWT in the Authorization header
 * via a fetch-based EventSource polyfill approach.
 *
 * Auto-reconnect: the browser retries SSE after the server's 5-min timeout.
 * The hook cleans up (closes) the connection on component unmount.
 */
export function useClaimStatusStream(claimId: string | undefined) {
  const queryClient = useQueryClient();
  const { token } = useAuth();

  const handleStatusUpdate = useCallback(
    (update: ClaimStatusUpdate) => {
      // Invalidate the specific claim detail query — triggers silent background refetch
      queryClient.invalidateQueries({ queryKey: ['claim', update.claimId] });
      // Also invalidate the customer's list so the status badge updates there too
      queryClient.invalidateQueries({ queryKey: ['claims', 'list'] });
    },
    [queryClient]
  );

  useEffect(() => {
    if (!claimId || !token) return;

    const apiBase = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8090';
    const url = `${apiBase}/api/v1/claims/${claimId}/events`;

    // EventSource doesn't support custom headers natively.
    // We pass the JWT as a query param here for SSE specifically.
    // The backend SecurityConfig must permit the `token` query param for this path.
    // In production, prefer a cookie-based session token for SSE or use a short-lived SSE token.
    const es = new EventSource(`${url}?access_token=${token}`);

    es.addEventListener('claim-status', (event: MessageEvent) => {
      try {
        const update: ClaimStatusUpdate = JSON.parse(event.data);
        handleStatusUpdate(update);
      } catch (err) {
        console.warn('[SSE] Failed to parse claim-status event:', err);
      }
    });

    es.addEventListener('error', () => {
      // Browser auto-reconnects on transient errors — no manual retry needed.
      console.debug('[SSE] connection error for claim', claimId, '— browser will retry');
    });

    return () => {
      es.close();
    };
  }, [claimId, token, handleStatusUpdate]);
}
