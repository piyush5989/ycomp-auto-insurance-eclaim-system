import React from 'react';
import { Sidebar } from '@/shared/components/layout/Sidebar';
import { Home, List, Shield, BarChart2, AlertTriangle, ClipboardList, MapPin, TrendingUp } from 'lucide-react';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { hasRole } from '@/shared/auth/roleUtils';

const getNavItems = (roles: string[]) => {
  const items = [
    { label: 'Dashboard',       path: '/internal/dashboard',      icon: <Home size={16} /> },
  ];

  if (hasRole(roles, 'ROLE_SURVEYOR')) {
    items.push({ label: 'My Assignments', path: '/internal/surveyor/my-assignments', icon: <ClipboardList size={16} /> });
  }

  items.push({ label: 'Claims Queue',    path: '/internal/claims-queue',   icon: <List size={16} /> });

  if (hasRole(roles, 'ROLE_TOP_MANAGEMENT')) {
    items.push({ label: 'Management Reports', path: '/internal/reports/management', icon: <TrendingUp size={16} /> });
  }

  items.push(
    { label: 'Audit Log',       path: '/internal/audit',          icon: <Shield size={16} /> },
    { label: 'KPI Reports',     path: '/internal/reports/kpi',    icon: <BarChart2 size={16} /> },
    { label: 'Regional Reports', path: '/internal/reports/regional', icon: <MapPin size={16} /> },
    { label: 'Fraud Ageing',    path: '/internal/reports/fraud',  icon: <AlertTriangle size={16} /> }
  );

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
