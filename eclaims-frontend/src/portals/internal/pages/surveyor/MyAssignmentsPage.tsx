import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { DataTable, type Column } from '@/shared/components/ui/DataTable'
import { StatusBadge } from '@/shared/components/ui/Badge'
import type { ClaimResponse } from '@/features/claims/api/claimsApi.types'
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel'
import { format } from 'date-fns'
import { useAuth } from '@/shared/auth/KeycloakProvider'
import { ClipboardList } from 'lucide-react'

const STATUS_FILTERS = ['ALL', 'ASSIGNED', 'UNDER_SURVEY', 'SURVEYED']

export default function MyAssignmentsPage() {
  const navigate = useNavigate()
  const { username } = useAuth()
  const [statusFilter, setStatusFilter] = useState('ALL')

  const { data: claims = [], isLoading } = useQuery({
    queryKey: ['my-assignments', username, statusFilter],
    queryFn: () => {
      const params = new URLSearchParams({ assignedTo: username })
      if (statusFilter !== 'ALL') {
        params.append('status', statusFilter)
      }
      return httpClient.get(`/claims?${params.toString()}`).then((r) => r.data.data?.data ?? [])
    },
    staleTime: 30_000,
  })

  const columns: Column<ClaimResponse>[] = [
    { header: 'Claim ID', accessor: (r) => <span className="font-mono text-xs">{r.claimId.substring(0, 8)}…</span> },
    { header: 'Policy', accessor: 'policyNumber' },
    { header: 'Vehicle', accessor: 'vehicleRegistration' },
    { header: 'Type', accessor: (r) => r.claimType.replace('_', ' ') },
    { header: 'Incident Date', accessor: (r) => format(new Date(r.incidentDate), 'dd MMM yyyy') },
    { header: 'Status', accessor: (r) => <StatusBadge status={r.status as ClaimStatus} /> },
    {
      header: 'Action',
      accessor: (r) => (
        r.status === 'ASSIGNED' || r.status === 'UNDER_SURVEY' ? (
          <button
            onClick={(e) => {
              e.stopPropagation()
              navigate(`/internal/surveyor/assess/${r.claimId}`)
            }}
            className="btn-primary text-xs py-1 px-2"
          >
            Assess
          </button>
        ) : (
          <span className="text-xs text-gray-400">Completed</span>
        )
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <div className="p-3 bg-blue-100 rounded-lg">
          <ClipboardList className="w-6 h-6 text-blue-700" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Assignments</h1>
          <p className="text-gray-500 mt-1">Claims assigned to you for survey assessment</p>
        </div>
      </div>

      <div className="flex gap-2 flex-wrap">
        {STATUS_FILTERS.map((s) => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
              statusFilter === s
                ? 'bg-primary-800 text-white'
                : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
            }`}
          >
            {s.replace('_', ' ')}
          </button>
        ))}
      </div>

      <DataTable
        columns={columns}
        data={claims}
        isLoading={isLoading}
        onRowClick={(row) => navigate(`/internal/surveyor/assess/${row.claimId}`)}
        emptyMessage="No claims assigned to you for this status."
      />
    </div>
  )
}
