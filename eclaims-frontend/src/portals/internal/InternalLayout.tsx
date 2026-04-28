import React from 'react';
import { Sidebar } from '@/shared/components/layout/Sidebar';
import { Home, List, Shield, BarChart2, AlertTriangle } from 'lucide-react';

const navItems = [
  { label: 'Dashboard',       path: '/internal/dashboard',      icon: <Home size={16} /> },
  { label: 'Claims Queue',    path: '/internal/claims-queue',   icon: <List size={16} /> },
  { label: 'Audit Log',       path: '/internal/audit',          icon: <Shield size={16} /> },
  { label: 'KPI Reports',     path: '/internal/reports/kpi',    icon: <BarChart2 size={16} /> },
  { label: 'Fraud Ageing',    path: '/internal/reports/fraud',  icon: <AlertTriangle size={16} /> },
];

export default function InternalLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar navItems={navItems} title="Internal Portal" />
      <main className="flex-1 ml-64 overflow-y-auto">
        <div className="max-w-6xl mx-auto p-8">{children}</div>
      </main>
    </div>
  );
}
