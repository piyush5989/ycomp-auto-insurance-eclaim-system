import React from 'react'
import { Sidebar } from '@/shared/components/layout/Sidebar'
import { useAuth } from '@/shared/auth/KeycloakProvider'
import { getInternalNavItems } from './navItems'

export default function InternalLayout({ children }: { children: React.ReactNode }) {
  const { roles } = useAuth()
  const navItems = getInternalNavItems(roles).map(({ label, path, icon }) => ({ label, path, icon }))

  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar navItems={navItems} title="Internal Portal" />
      <main className="flex-1 ml-64 overflow-y-auto">
        <div className="max-w-6xl mx-auto p-8">{children}</div>
      </main>
    </div>
  )
}
