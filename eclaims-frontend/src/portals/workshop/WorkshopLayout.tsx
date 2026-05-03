import React from 'react'
import { Sidebar } from '@/shared/components/layout/Sidebar'
import { Home, PlusCircle, ClipboardList, Car } from 'lucide-react'
import { useMyWorkshop } from '@/features/workshops/hooks/useMyWorkshop'

const navItems = [
  { label: 'Dashboard',      path: '/workshop/dashboard',        icon: <Home size={16} /> },
  { label: 'My Claims',      path: '/workshop/claims',           icon: <Car size={16} /> },
  { label: 'New Work Order', path: '/workshop/work-orders/new',  icon: <PlusCircle size={16} /> },
  { label: 'My Work Orders', path: '/workshop/work-orders',      icon: <ClipboardList size={16} /> },
]

export default function WorkshopLayout({ children }: { children: React.ReactNode }) {
  const { data: profile } = useMyWorkshop()

  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar
        navItems={navItems}
        title={profile ? profile.name : 'Workshop Portal'}
        subtitle={profile ? profile.city : undefined}
      />
      <main className="flex-1 ml-64 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-8">{children}</div>
      </main>
    </div>
  )
}
