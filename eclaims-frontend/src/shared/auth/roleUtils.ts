export function hasRole(userRoles: string[], requiredRole: string): boolean {
  return userRoles.includes(requiredRole);
}

export function hasAnyRole(userRoles: string[], requiredRoles: string[]): boolean {
  return requiredRoles.some((role) => userRoles.includes(role));
}

export function getRoleLabel(role: string): string {
  const labels: Record<string, string> = {
    ROLE_CUSTOMER:       'Customer',
    ROLE_SURVEYOR:       'Surveyor',
    ROLE_ADJUSTOR:       'Adjustor',
    ROLE_CASE_MANAGER:   'Case Manager',
    ROLE_AUDITOR:        'Auditor',
    ROLE_WORKSHOP:       'Workshop',
    ROLE_REGIONAL_MGR:   'Regional Manager',
    ROLE_TOP_MANAGEMENT: 'Executive',
  };
  return labels[role] ?? role;
}

export function getPortalPath(roles: string[]): string {
  if (hasRole(roles, 'ROLE_CUSTOMER')) return '/customer';
  if (hasRole(roles, 'ROLE_WORKSHOP')) return '/workshop';
  if (hasAnyRole(roles, ['ROLE_SURVEYOR', 'ROLE_ADJUSTOR', 'ROLE_CASE_MANAGER',
                          'ROLE_AUDITOR', 'ROLE_REGIONAL_MGR', 'ROLE_TOP_MANAGEMENT'])) {
    return '/internal';
  }
  return '/login';
}
