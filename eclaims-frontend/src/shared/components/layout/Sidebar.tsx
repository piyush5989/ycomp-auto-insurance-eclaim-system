import React from 'react';
import { NavLink } from 'react-router-dom';
import { clsx } from 'clsx';
import { useAuth } from '@/shared/auth/KeycloakProvider';

interface NavItem {
  label: string;
  path: string;
  icon: React.ReactNode;
}

interface SidebarProps {
  navItems: NavItem[];
  title: string;
}

export function Sidebar({ navItems, title }: SidebarProps) {
  const { username, logout } = useAuth();

  return (
    <aside className="w-64 bg-primary-800 text-white flex flex-col h-screen fixed left-0 top-0">
      <div className="px-6 py-5 border-b border-primary-700">
        <h1 className="text-lg font-bold tracking-tight">eClaims</h1>
        <p className="text-primary-200 text-xs mt-0.5">{title}</p>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-primary-700 text-white'
                  : 'text-primary-100 hover:bg-primary-700 hover:text-white'
              )
            }
          >
            <span className="w-4 h-4 flex-shrink-0">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="px-4 py-4 border-t border-primary-700">
        <div className="flex items-center gap-3 mb-3">
          <div className="w-8 h-8 rounded-full bg-primary-600 flex items-center justify-center text-sm font-medium">
            {username?.charAt(0).toUpperCase() ?? 'U'}
          </div>
          <span className="text-sm text-primary-100 truncate">{username}</span>
        </div>
        <button
          onClick={logout}
          className="w-full text-left text-xs text-primary-300 hover:text-white transition-colors"
        >
          Sign out
        </button>
      </div>
    </aside>
  );
}
