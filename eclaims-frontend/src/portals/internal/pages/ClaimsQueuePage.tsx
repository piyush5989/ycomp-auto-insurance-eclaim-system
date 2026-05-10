import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { DataTable, type Column } from '@/shared/components/ui/DataTable';
import { StatusBadge } from '@/shared/components/ui/Badge';
import type { ClaimResponse } from '@/features/claims/api/claimsApi.types';
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel';
import { format } from 'date-fns';
import { Download, ChevronLeft, ChevronRight } from 'lucide-react';

const STATUS_FILTERS = ['ALL', 'SUBMITTED', 'ASSIGNED', 'UNDER_SURVEY', 'SURVEYED', 'UNDER_ADJUDICATION'];
const REGIONS = ['ALL', 'EAST', 'WEST', 'NORTH', 'SOUTH', 'CENTRAL'];

interface ClaimsPageData {
  data: ClaimResponse[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
}

export default function ClaimsQueuePage() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [regionFilter, setRegionFilter] = useState('ALL');
  const [fraudFlagFilter, setFraudFlagFilter] = useState<boolean | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);

  const { data: pageData, isLoading } = useQuery<ClaimsPageData>({
    queryKey: ['internal-claims', statusFilter, regionFilter, fraudFlagFilter, page, pageSize],
    queryFn: () => {
      const params = new URLSearchParams({
        page: page.toString(),
        size: pageSize.toString(),
        sortBy: 'createdAt',
        sortOrder: 'desc',
      });
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      if (regionFilter !== 'ALL') params.append('region', regionFilter);
      if (fraudFlagFilter !== null) params.append('fraudFlag', fraudFlagFilter.toString());
      
      return httpClient.get(`/claims?${params.toString()}`).then((r) => r.data.data);
    },
    staleTime: 30_000,
  });

  const claims = pageData?.data ?? [];
  const totalPages = pageData?.totalPages ?? 0;

  const exportToCSV = () => {
    const headers = ['Claim ID', 'Policy', 'Vehicle', 'Type', 'Incident Date', 'Status', 'Fraud Flag'];
    const rows = claims.map(c => [
      c.claimId,
      c.policyNumber,
      c.vehicleRegistration,
      c.claimType,
      c.incidentDate,
      c.status,
      c.fraudFlag ? 'Yes' : 'No',
    ]);
    
    const csvContent = [headers, ...rows].map(row => row.join(',')).join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `claims_export_${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Claims Queue</h1>
          <p className="text-gray-500 mt-1">Process and manage claim assignments</p>
        </div>
        <button
          onClick={exportToCSV}
          disabled={claims.length === 0}
          className="btn-secondary text-sm flex items-center gap-2"
        >
          <Download className="w-4 h-4" />
          Export CSV
        </button>
      </div>

      {/* Filters */}
      <div className="card">
        <h3 className="text-sm font-semibold text-gray-900 mb-3">Filters</h3>
        
        <div className="space-y-3">
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Status</label>
            <div className="flex gap-2 flex-wrap">
              {STATUS_FILTERS.map((s) => (
                <button
                  key={s}
                  onClick={() => {
                    setStatusFilter(s)
                    setPage(0)
                  }}
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
          </div>

          <div className="flex gap-4">
            <div className="flex-1">
              <label className="block text-xs font-medium text-gray-700 mb-1">Region</label>
              <select
                value={regionFilter}
                onChange={(e) => {
                  setRegionFilter(e.target.value)
                  setPage(0)
                }}
                className="input text-sm"
              >
                {REGIONS.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
            </div>

            <div className="flex-1">
              <label className="block text-xs font-medium text-gray-700 mb-1">Fraud Flag</label>
              <select
                value={fraudFlagFilter === null ? 'ALL' : fraudFlagFilter.toString()}
                onChange={(e) => {
                  const val = e.target.value
                  setFraudFlagFilter(val === 'ALL' ? null : val === 'true')
                  setPage(0)
                }}
                className="input text-sm"
              >
                <option value="ALL">All</option>
                <option value="true">Flagged Only</option>
                <option value="false">Not Flagged</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={claims}
        isLoading={isLoading}
        onRowClick={(row) => navigate(`/internal/claims/${row.claimId}`)}
        emptyMessage="No claims in queue for selected filters."
      />

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-gray-600">
            Page {page + 1} of {totalPages} ({pageData?.totalElements ?? 0} total claims)
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="btn-secondary text-sm disabled:opacity-50"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="btn-secondary text-sm disabled:opacity-50"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
