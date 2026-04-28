import React from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useClaimsList } from '@/features/claims/hooks/useClaimsList';
import { DataTable, type Column } from '@/shared/components/ui/DataTable';
import { StatusBadge } from '@/shared/components/ui/Badge';
import type { ClaimResponse } from '@/features/claims/api/claimsApi.types';
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel';
import { format } from 'date-fns';
import { PlusCircle } from 'lucide-react';

export default function ClaimsListPage() {
  const navigate = useNavigate();
  const { data: claims = [], isLoading } = useClaimsList();

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
          <p className="text-gray-500 mt-1">{claims.length} total claim{claims.length !== 1 ? 's' : ''}</p>
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
    </div>
  );
}
