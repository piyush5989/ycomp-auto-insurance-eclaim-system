import React from 'react';
import { Sidebar } from '@/shared/components/layout/Sidebar';
import { Home, PlusCircle, RefreshCw } from 'lucide-react';

const navItems = [
  { label: 'Dashboard',        path: '/workshop/dashboard',          icon: <Home size={16} /> },
  { label: 'New Work Order',   path: '/workshop/work-orders/new',    icon: <PlusCircle size={16} /> },
];

export default function WorkshopLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar navItems={navItems} title="Workshop Portal" />
      <main className="flex-1 ml-64 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-8">{children}</div>
      </main>
    </div>
  );
}
