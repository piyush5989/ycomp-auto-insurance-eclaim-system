import React from 'react'
import {
  Home, List, Shield, BarChart2, AlertTriangle,
  ClipboardList, MapPin, TrendingUp, ClipboardCheck, FileText,
} from 'lucide-react'
import { hasRole, hasAnyRole } from '@/shared/auth/roleUtils'
import { UserRole } from '@/shared/types/UserRole'

/**
 * Single source of truth for the internal portal navigation.
 *
 * Both the left sidebar (compact - icon + label) and the home dashboard
 * (rich tiles - icon + label + description) render from this list, so they
 * can never drift apart. Each entry is gated by a `visibleTo` predicate that
 * mirrors the backend @PreAuthorize rules:
 *
 *   SURVEYOR        -> My Assignments
 *   ADJUSTOR        -> My Claims Queue
 *   CASE_MANAGER    -> My Claims Report, KPI Reports, Fraud Ageing, Audit Log
 *   AUDITOR         -> KPI Reports, Fraud Ageing, Audit Log
 *   REGIONAL_MGR    -> Regional Reports
 *   TOP_MANAGEMENT  -> Regional Reports, Management Reports, Audit Log
 *
 * All internal users see Dashboard + Claims Queue.
 */
export interface InternalNavItem {
  label: string
  path: string
  icon: React.ReactNode
  description: string
  accent: AccentKey
  visibleTo: (roles: string[]) => boolean
}

type AccentKey = 'blue' | 'indigo' | 'green' | 'red' | 'amber' | 'teal' | 'purple' | 'slate'

const accentClasses: Record<AccentKey, string> = {
  blue:   'bg-blue-100 text-blue-700',
  indigo: 'bg-indigo-100 text-indigo-700',
  green:  'bg-green-100 text-green-700',
  red:    'bg-red-100 text-red-700',
  amber:  'bg-amber-100 text-amber-700',
  teal:   'bg-teal-100 text-teal-700',
  purple: 'bg-purple-100 text-purple-700',
  slate:  'bg-slate-100 text-slate-700',
}

export const getAccentClasses = (accent: AccentKey): string => accentClasses[accent]

const ALL_INTERNAL: InternalNavItem[] = [
  {
    label:       'Dashboard',
    path:        '/internal/dashboard',
    icon:        <Home size={16} />,
    description: 'Personalised landing page with your shortcuts',
    accent:      'slate',
    visibleTo:   () => true,
  },
  {
    label:       'Claims Queue',
    path:        '/internal/claims-queue',
    icon:        <List size={16} />,
    description: 'Review and process pending claims',
    accent:      'blue',
    visibleTo:   () => true,
  },
  {
    label:       'My Assignments',
    path:        '/internal/surveyor/my-assignments',
    icon:        <ClipboardList size={16} />,
    description: 'Field surveys assigned to you',
    accent:      'indigo',
    visibleTo:   (roles) => hasRole(roles, UserRole.SURVEYOR),
  },
  {
    label:       'My Claims Queue',
    path:        '/internal/adjustor/my-claims',
    icon:        <ClipboardCheck size={16} />,
    description: 'Claims awaiting your adjudication',
    accent:      'indigo',
    visibleTo:   (roles) => hasRole(roles, UserRole.ADJUSTOR),
  },
  {
    label:       'My Claims Report',
    path:        '/internal/reports/my-claims',
    icon:        <FileText size={16} />,
    description: 'Performance summary for the claims you manage',
    accent:      'teal',
    visibleTo:   (roles) => hasRole(roles, UserRole.CASE_MANAGER),
  },
  {
    label:       'KPI Reports',
    path:        '/internal/reports/kpi',
    icon:        <BarChart2 size={16} />,
    description: 'Claims performance and settlement metrics',
    accent:      'green',
    visibleTo:   (roles) => hasAnyRole(roles, [UserRole.CASE_MANAGER, UserRole.AUDITOR]),
  },
  {
    label:       'Fraud Ageing',
    path:        '/internal/reports/fraud',
    icon:        <AlertTriangle size={16} />,
    description: 'Monitor flagged claims by age bucket',
    accent:      'red',
    visibleTo:   (roles) => hasAnyRole(roles, [UserRole.CASE_MANAGER, UserRole.AUDITOR]),
  },
  {
    label:       'Regional Reports',
    path:        '/internal/reports/regional',
    icon:        <MapPin size={16} />,
    description: 'KPI roll-ups for each region',
    accent:      'amber',
    visibleTo:   (roles) => hasAnyRole(roles, [UserRole.REGIONAL_MGR, UserRole.TOP_MANAGEMENT]),
  },
  {
    label:       'Management Reports',
    path:        '/internal/reports/management',
    icon:        <TrendingUp size={16} />,
    description: 'Cross-region executive comparison',
    accent:      'green',
    visibleTo:   (roles) => hasRole(roles, UserRole.TOP_MANAGEMENT),
  },
  {
    label:       'Audit Log',
    path:        '/internal/audit',
    icon:        <Shield size={16} />,
    description: 'Immutable audit trail for compliance',
    accent:      'purple',
    visibleTo:   (roles) => hasAnyRole(roles, [UserRole.AUDITOR, UserRole.CASE_MANAGER, UserRole.TOP_MANAGEMENT]),
  },
]

export const getInternalNavItems = (roles: string[]): InternalNavItem[] =>
  ALL_INTERNAL.filter((item) => item.visibleTo(roles))
