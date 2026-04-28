import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { DataTable, type Column } from '@/shared/components/ui/DataTable';
import { StatusBadge } from '@/shared/components/ui/Badge';
import type { ClaimResponse } from '@/features/claims/api/claimsApi.types';
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel';
import { format } from 'date-fns';

const STATUS_FILTERS = ['ALL', 'SUBMITTED', 'ASSIGNED', 'UNDER_SURVEY', 'SURVEYED', 'UNDER_ADJUDICATION'];

export default function ClaimsQueuePage() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState('ALL');

  const { data: claims = [], isLoading } = useQuery({
    queryKey: ['internal-claims', statusFilter],
    queryFn: () =>
      httpClient.get(`/claims${statusFilter !== 'ALL' ? `?status=${statusFilter}` : ''}`).then((r) => r.data.data ?? []),
    staleTime: 30_000,
  });

  const columns: Column<ClaimResponse>[] = [
    { header: 'Claim ID', accessor: (r) => <span className="font-mono text-xs">{r.claimId.substring(0, 8)}…</span> },
    { header: 'Policy', accessor: 'policyNumber' },
    { header: 'Vehicle', accessor: 'vehicleRegistration' },
    { header: 'Type', accessor: (r) => r.claimType.replace('_', ' ') },
    { header: 'Incident Date', accessor: (r) => format(new Date(r.incidentDate), 'dd MMM yyyy') },
    { header: 'Status', accessor: (r) => <StatusBadge status={r.status as ClaimStatus} /> },
    { header: 'Fraud', accessor: (r) => r.fraudFlag ? <span className="text-red-600 font-medium text-xs">⚠ Flagged</span> : null },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Claims Queue</h1>
        <p className="text-gray-500 mt-1">Process and manage claim assignments</p>
      </div>

      {/* Status Filter */}
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
        onRowClick={(row) => navigate(`/internal/claims/${row.claimId}`)}
        emptyMessage="No claims in queue for this status."
      />
    </div>
  );
}
