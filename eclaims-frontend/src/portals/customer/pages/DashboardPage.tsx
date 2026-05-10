import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCustomerClaimsPage, useCustomerClaimsStats } from '@/features/claims/hooks/useClaimsList';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { StatusBadge } from '@/shared/components/ui/Badge';
import { DataTable } from '@/shared/components/ui/DataTable';
import type { ClaimResponse } from '@/features/claims/api/claimsApi.types';
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel';
import { formatCurrency } from '@/shared/utils/formatCurrency';
import { format } from 'date-fns';
import { PlusCircle, FileText, TrendingUp } from 'lucide-react';

export default function DashboardPage() {
  const { username } = useAuth();
  const navigate = useNavigate();
  const { data: stats, isLoading: statsLoading } = useCustomerClaimsStats();
  const { data: recentPage, isLoading: recentLoading } = useCustomerClaimsPage(0, 5);
  const isLoading = statsLoading || recentLoading;
  const recentClaims = recentPage?.data ?? [];

  const columns = [
    { header: 'Claim ID', accessor: (row: ClaimResponse) => row.claimId.substring(0, 8) + '…' },
    { header: 'Policy',   accessor: 'policyNumber' as keyof ClaimResponse },
    { header: 'Type',     accessor: 'claimType' as keyof ClaimResponse },
    { header: 'Status',   accessor: (row: ClaimResponse) => <StatusBadge status={row.status as ClaimStatus} /> },
    { header: 'Incident', accessor: (row: ClaimResponse) => format(new Date(row.incidentDate), 'dd MMM yyyy') },
    { header: 'Amount',   accessor: (row: ClaimResponse) => formatCurrency(row.approvedAmount) },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Welcome back, {username}</h1>
        <p className="text-gray-500 mt-1">Manage your insurance claims and track their progress.</p>
      </div>

      <div className="grid grid-cols-3 gap-5">
        <div className="card">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 rounded-lg"><FileText className="w-5 h-5 text-blue-700" /></div>
            <div>
              <p className="text-sm text-gray-500">Active Claims</p>
              <p className="text-2xl font-bold text-gray-900">{stats?.active ?? 0}</p>
            </div>
          </div>
        </div>
        <div className="card">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-green-100 rounded-lg"><TrendingUp className="w-5 h-5 text-green-700" /></div>
            <div>
              <p className="text-sm text-gray-500">Settled Claims</p>
              <p className="text-2xl font-bold text-gray-900">{stats?.settled ?? 0}</p>
            </div>
          </div>
        </div>
        <div className="card">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary-100 rounded-lg"><FileText className="w-5 h-5 text-primary-700" /></div>
            <div>
              <p className="text-sm text-gray-500">Total Claims</p>
              <p className="text-2xl font-bold text-gray-900">{stats?.total ?? 0}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="flex gap-3">
        <Link to="/customer/claims/submit" className="btn-primary">
          <PlusCircle className="w-4 h-4 mr-2" />
          Submit New Claim
        </Link>
        <Link to="/customer/claims" className="btn-secondary">
          View All Claims
        </Link>
      </div>

      <div>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Recent Claims</h2>
        <DataTable
          columns={columns}
          data={recentClaims}
          isLoading={isLoading}
          onRowClick={(row) => navigate(`/customer/claims/${row.claimId}`)}
          emptyMessage="No claims yet. Submit your first claim to get started."
        />
      </div>
    </div>
  );
}
