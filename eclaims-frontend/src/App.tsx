import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';

import { KeycloakProvider } from '@/shared/auth/KeycloakProvider';
import { ProtectedRoute } from '@/shared/components/layout/ProtectedRoute';

import CustomerPortalRoutes from '@/portals/customer/CustomerPortalRoutes';
import InternalPortalRoutes from '@/portals/internal/InternalPortalRoutes';
import WorkshopPortalRoutes from '@/portals/workshop/WorkshopPortalRoutes';
import LoginPage from '@/shared/pages/LoginPage';

/**
 * App entry point:
 *   - QueryClientProvider: React Query for all server state (API data, caching, background refresh)
 *   - KeycloakProvider:    Auth context (Keycloak JWT, roles)
 *   - BrowserRouter:       Role-based portal routing
 *
 * Portal routing:
 *   /customer/*  → Customer Portal  (ROLE_CUSTOMER)
 *   /internal/*  → Internal Portal  (SURVEYOR, ADJUSTOR, CASE_MANAGER, AUDITOR, REGIONAL_MGR, TOP_MANAGEMENT)
 *   /workshop/*  → Workshop Portal  (ROLE_WORKSHOP)
 *   /            → Role-based redirect after login
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      staleTime: 30_000,      // 30s — balance between freshness and cache efficiency
      refetchOnWindowFocus: true,
    },
    mutations: {
      retry: 0,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <KeycloakProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
              path="/customer/*"
              element={
                <ProtectedRoute requiredRoles={['ROLE_CUSTOMER']}>
                  <CustomerPortalRoutes />
                </ProtectedRoute>
              }
            />

            <Route
              path="/internal/*"
              element={
                <ProtectedRoute requiredRoles={[
                  'ROLE_SURVEYOR', 'ROLE_ADJUSTOR', 'ROLE_CASE_MANAGER',
                  'ROLE_AUDITOR', 'ROLE_REGIONAL_MGR', 'ROLE_TOP_MANAGEMENT'
                ]}>
                  <InternalPortalRoutes />
                </ProtectedRoute>
              }
            />

            <Route
              path="/workshop/*"
              element={
                <ProtectedRoute requiredRoles={['ROLE_WORKSHOP']}>
                  <WorkshopPortalRoutes />
                </ProtectedRoute>
              }
            />

            <Route path="/" element={<Navigate to="/login" replace />} />
          </Routes>
        </BrowserRouter>
      </KeycloakProvider>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
