import React from 'react';
import { Sidebar } from '@/shared/components/layout/Sidebar';
import { Home, FileText, Search, CreditCard, PlusCircle } from 'lucide-react';

const navItems = [
  { label: 'Dashboard',      path: '/customer/dashboard',     icon: <Home size={16} /> },
  { label: 'My Claims',      path: '/customer/claims',        icon: <FileText size={16} /> },
  { label: 'Submit Claim',   path: '/customer/claims/submit', icon: <PlusCircle size={16} /> },
  { label: 'Find Workshop',  path: '/customer/workshops',     icon: <Search size={16} /> },
];

export default function CustomerLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar navItems={navItems} title="Customer Portal" />
      <main className="flex-1 ml-64 overflow-y-auto">
        <div className="max-w-5xl mx-auto p-8">{children}</div>
      </main>
    </div>
  );
}
