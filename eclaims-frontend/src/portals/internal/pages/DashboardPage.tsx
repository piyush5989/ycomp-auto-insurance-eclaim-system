import React from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/shared/auth/KeycloakProvider'
import { getRoleLabel } from '@/shared/auth/roleUtils'
import { getInternalNavItems, getAccentClasses } from '../navItems'

export default function DashboardPage() {
  const { username, roles } = useAuth()
  const primaryRole = roles[0] ? getRoleLabel(roles[0]) : 'Internal User'
  const tiles = getInternalNavItems(roles).filter((item) => item.path !== '/internal/dashboard')

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Welcome, {username}</h1>
        <p className="text-gray-500 mt-1">
          Role: <span className="font-medium">{primaryRole}</span>
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {tiles.map((tile) => (
          <Link
            key={tile.path}
            to={tile.path}
            aria-label={tile.label}
            className="card hover:shadow-md transition-shadow flex items-center gap-4"
          >
            <div className={`p-3 rounded-lg ${getAccentClasses(tile.accent)}`}>
              <span className="block w-6 h-6 [&>svg]:w-6 [&>svg]:h-6">{tile.icon}</span>
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">{tile.label}</h3>
              <p className="text-sm text-gray-500">{tile.description}</p>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
