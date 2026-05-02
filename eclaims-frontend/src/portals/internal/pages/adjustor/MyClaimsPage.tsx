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
import { ClipboardCheck } from 'lucide-react'

const STATUS_FILTERS = ['ALL', 'SURVEYED', 'UNDER_ADJUDICATION', 'APPROVED', 'REJECTED']

export default function MyClaimsPage() {
  const navigate = useNavigate()
  const { username } = useAuth()
  const [statusFilter, setStatusFilter] = useState('ALL')

  const { data: claims = [], isLoading } = useQuery({
    queryKey: ['adjustor-my-claims', statusFilter],
    queryFn: () => {
      // Adjustors see claims that need adjudication or were adjudicated by them
      const params = new URLSearchParams()
      if (statusFilter === 'ALL') {
        params.append('status', 'SURVEYED')
        // Note: Backend needs to support OR queries or multiple status values
        // For now, we'll filter client-side
      } else {
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
    { 
      header: 'Assessed Amount', 
      accessor: (r) => {
        const amount = r.assessedAmount || r.estimatedAmount
        return amount ? `$${amount.toLocaleString()}` : '—'
      }
    },
    { header: 'Incident Date', accessor: (r) => format(new Date(r.incidentDate), 'dd MMM yyyy') },
    { header: 'Status', accessor: (r) => <StatusBadge status={r.status as ClaimStatus} /> },
    {
      header: 'Action',
      accessor: (r) => (
        r.status === 'SURVEYED' || r.status === 'UNDER_ADJUDICATION' ? (
          <button
            onClick={(e) => {
              e.stopPropagation()
              navigate(`/internal/adjustor/adjudicate/${r.claimId}`)
            }}
            className="btn-primary text-xs py-1 px-2"
          >
            Review
          </button>
        ) : (
          <span className="text-xs text-gray-400">
            {r.status === 'APPROVED' ? 'Approved' : r.status === 'REJECTED' ? 'Rejected' : 'Completed'}
          </span>
        )
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <div className="p-3 bg-green-100 rounded-lg">
          <ClipboardCheck className="w-6 h-6 text-green-700" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Claims Queue</h1>
          <p className="text-gray-500 mt-1">Claims awaiting your adjudication</p>
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
        onRowClick={(row) => navigate(`/internal/adjustor/adjudicate/${row.claimId}`)}
        emptyMessage="No claims in your queue for this status."
      />
    </div>
  )
}
