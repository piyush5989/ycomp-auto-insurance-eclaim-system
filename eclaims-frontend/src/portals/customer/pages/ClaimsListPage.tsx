import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useCustomerClaimsPage } from '@/features/claims/hooks/useClaimsList';
import { DataTable, type Column } from '@/shared/components/ui/DataTable';
import { StatusBadge } from '@/shared/components/ui/Badge';
import type { ClaimResponse } from '@/features/claims/api/claimsApi.types';
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel';
import { format } from 'date-fns';
import { PlusCircle, ChevronLeft, ChevronRight } from 'lucide-react';

export default function ClaimsListPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);

  const { data: pageData, isLoading } = useCustomerClaimsPage(page, pageSize);
  const claims = pageData?.data ?? [];
  const totalPages = pageData?.totalPages ?? 0;
  const totalElements = pageData?.totalElements ?? 0;

  const columns: Column<ClaimResponse>[] = [
    { header: 'Claim ID', accessor: (r) => <span className="font-mono text-xs">{r.claimId.substring(0, 8)}…</span> },
    { header: 'Policy', accessor: 'policyNumber' },
    { header: 'Vehicle', accessor: 'vehicleRegistration' },
    { header: 'Type', accessor: (r) => r.claimType.replace('_', ' ') },
    { header: 'Incident Date', accessor: (r) => format(new Date(r.incidentDate), 'dd MMM yyyy') },
    { header: 'Status', accessor: (r) => <StatusBadge status={r.status as ClaimStatus} /> },
    { header: 'Submitted', accessor: (r) => format(new Date(r.createdAt), 'dd MMM yyyy') },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Claims</h1>
          <p className="text-gray-500 mt-1">
            {totalElements} total claim{totalElements !== 1 ? 's' : ''}
            {totalPages > 1 ? ` · page ${page + 1} of ${totalPages}` : ''}
          </p>
        </div>
        <Link to="/customer/claims/submit" className="btn-primary">
          <PlusCircle className="w-4 h-4 mr-2" />
          New Claim
        </Link>
      </div>

      <DataTable
        columns={columns}
        data={claims}
        isLoading={isLoading}
        onRowClick={(row) => navigate(`/customer/claims/${row.claimId}`)}
        emptyMessage="No claims found. Submit your first claim above."
      />

      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-gray-600">
            Showing {claims.length ? page * pageSize + 1 : 0}
            - {page * pageSize + claims.length} of {totalElements}
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="btn-secondary text-sm disabled:opacity-50"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              type="button"
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
