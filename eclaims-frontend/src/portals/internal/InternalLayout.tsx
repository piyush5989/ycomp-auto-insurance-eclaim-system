import React from 'react';
import { Sidebar } from '@/shared/components/layout/Sidebar';
import {
  Home, List, Shield, BarChart2, AlertTriangle,
  ClipboardList, MapPin, TrendingUp, ClipboardCheck, FileText,
} from 'lucide-react';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { hasRole, hasAnyRole } from '@/shared/auth/roleUtils';
import { UserRole } from '@/shared/types/UserRole';

/**
 * Sidebar nav items are role-gated to match backend @PreAuthorize rules:
 *
 *   SURVEYOR        → My Assignments
 *   ADJUSTOR        → My Claims Queue
 *   CASE_MANAGER    → My Claims Report, KPI Reports, Fraud Ageing
 *   AUDITOR         → Audit Log, KPI Reports, Fraud Ageing
 *   REGIONAL_MGR    → Regional Reports
 *   TOP_MANAGEMENT  → Management Reports, Regional Reports
 *
 * All internal users see: Dashboard, Claims Queue
 */
const getNavItems = (roles: string[]) => {
  const items = [
    { label: 'Dashboard',    path: '/internal/dashboard',   icon: <Home size={16} /> },
    { label: 'Claims Queue', path: '/internal/claims-queue', icon: <List size={16} /> },
  ];

  if (hasRole(roles, UserRole.SURVEYOR)) {
    items.push({
      label: 'My Assignments',
      path: '/internal/surveyor/my-assignments',
      icon: <ClipboardList size={16} />,
    });
  }

  if (hasRole(roles, UserRole.ADJUSTOR)) {
    items.push({
      label: 'My Claims Queue',
      path: '/internal/adjustor/my-claims',
      icon: <ClipboardCheck size={16} />,
    });
  }

  // ── Reporting section ────────────────────────────────────────────────────

  if (hasRole(roles, UserRole.CASE_MANAGER)) {
    items.push({
      label: 'My Claims Report',
      path: '/internal/reports/my-claims',
      icon: <FileText size={16} />,
    });
  }

  if (hasAnyRole(roles, [UserRole.CASE_MANAGER, UserRole.AUDITOR])) {
    items.push(
      { label: 'KPI Reports',  path: '/internal/reports/kpi',   icon: <BarChart2 size={16} /> },
      { label: 'Fraud Ageing', path: '/internal/reports/fraud', icon: <AlertTriangle size={16} /> },
    );
  }

  if (hasAnyRole(roles, [UserRole.REGIONAL_MGR, UserRole.TOP_MANAGEMENT])) {
    items.push({
      label: 'Regional Reports',
      path: '/internal/reports/regional',
      icon: <MapPin size={16} />,
    });
  }

  if (hasRole(roles, UserRole.TOP_MANAGEMENT)) {
    items.push({
      label: 'Management Reports',
      path: '/internal/reports/management',
      icon: <TrendingUp size={16} />,
    });
  }

  // Audit log — Auditors and Case Managers
  if (hasAnyRole(roles, [UserRole.AUDITOR, UserRole.CASE_MANAGER, UserRole.TOP_MANAGEMENT])) {
    items.push({ label: 'Audit Log', path: '/internal/audit', icon: <Shield size={16} /> });
  }

  return items;
};

export default function InternalLayout({ children }: { children: React.ReactNode }) {
  const { roles } = useAuth();
  const navItems = getNavItems(roles);

  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar navItems={navItems} title="Internal Portal" />
      <main className="flex-1 ml-64 overflow-y-auto">
        <div className="max-w-6xl mx-auto p-8">{children}</div>
      </main>
    </div>
  );
}
