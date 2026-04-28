import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { hasAnyRole } from '@/shared/auth/roleUtils';

interface ProtectedRouteProps {
  requiredRoles: string[];
  children: React.ReactNode;
}

/**
 * Role-based route guard.
 * Redirects to login if not authenticated.
 * Redirects to /login with an "unauthorized" param if authenticated but missing role.
 */
export function ProtectedRoute({ requiredRoles, children }: ProtectedRouteProps) {
  const { authenticated, roles } = useAuth();
  const location = useLocation();

  if (!authenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (!hasAnyRole(roles, requiredRoles)) {
    return <Navigate to="/login?error=unauthorized" replace />;
  }

  return <>{children}</>;
}
