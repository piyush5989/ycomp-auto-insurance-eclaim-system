import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { DataTable, type Column } from '@/shared/components/ui/DataTable';
import { formatCurrency } from '@/shared/utils/formatCurrency';
import { AlertTriangle } from 'lucide-react';

interface FraudAgeingItem {
  claimId: string;
  policyNumber: string;
  fraudReason: string;
  ageInHours: number;
  ageingBucket: string;
  assessedAmount: number;
  currentStatus: string;
}

const BUCKET_COLORS: Record<string, string> = {
  '< 1hr': 'bg-green-100 text-green-700',
  '1-24hrs': 'bg-yellow-100 text-yellow-700',
  '1-7days': 'bg-orange-100 text-orange-700',
  '> 7days': 'bg-red-100 text-red-700',
};

export default function FraudAgeingPage() {
  const { data: items = [], isLoading } = useQuery<FraudAgeingItem[]>({
    queryKey: ['fraud-ageing'],
    queryFn: () => httpClient.get('/reports/fraud-ageing').then((r) => r.data.data ?? []),
    staleTime: 60_000,
  });

  const columns: Column<FraudAgeingItem>[] = [
    { header: 'Claim ID', accessor: (r) => <span className="font-mono text-xs">{r.claimId.substring(0, 8)}…</span> },
    { header: 'Policy', accessor: 'policyNumber' },
    { header: 'Fraud Reason', accessor: 'fraudReason' },
    { header: 'Age', accessor: (r) => `${r.ageInHours.toFixed(0)}h` },
    { header: 'Bucket', accessor: (r) => (
      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${BUCKET_COLORS[r.ageingBucket] ?? 'bg-gray-100 text-gray-700'}`}>
        {r.ageingBucket}
      </span>
    )},
    { header: 'Assessed', accessor: (r) => formatCurrency(r.assessedAmount) },
    { header: 'Status', accessor: 'currentStatus' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <AlertTriangle className="w-6 h-6 text-red-600" />
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Fraud Ageing Report</h1>
          <p className="text-gray-500 mt-0.5">Active claims flagged for fraud — SLA: resolved within 1 hour</p>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={items}
        isLoading={isLoading}
        emptyMessage="No fraud-flagged claims at this time."
      />
    </div>
  );
}
