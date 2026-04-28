import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { formatCurrency } from '@/shared/utils/formatCurrency';
import { BarChart2, TrendingUp, AlertTriangle, CheckCircle } from 'lucide-react';

interface KpiResponse {
  region: string;
  totalClaims: number;
  submittedToday: number;
  pendingAssignment: number;
  approvedThisMonth: number;
  rejectedThisMonth: number;
  settledThisMonth: number;
  totalSettledAmount: number;
  fraudFlagged: number;
  generatedAt: string;
}

export default function ClaimsKpiPage() {
  const { data: kpi, isLoading } = useQuery<KpiResponse>({
    queryKey: ['kpi', 'global'],
    queryFn: () => httpClient.get('/reports/kpi?region=global').then((r) => r.data.data),
    staleTime: 15 * 60 * 1000,  // 15 min — pre-aggregated snapshots
  });

  if (isLoading) return <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" /></div>;

  const tiles = [
    { label: 'Total Claims', value: kpi?.totalClaims ?? 0, icon: <BarChart2 className="w-5 h-5 text-blue-700" />, bg: 'bg-blue-100' },
    { label: 'Submitted Today', value: kpi?.submittedToday ?? 0, icon: <TrendingUp className="w-5 h-5 text-green-700" />, bg: 'bg-green-100' },
    { label: 'Approved This Month', value: kpi?.approvedThisMonth ?? 0, icon: <CheckCircle className="w-5 h-5 text-emerald-700" />, bg: 'bg-emerald-100' },
    { label: 'Fraud Flagged', value: kpi?.fraudFlagged ?? 0, icon: <AlertTriangle className="w-5 h-5 text-red-700" />, bg: 'bg-red-100' },
    { label: 'Settled This Month', value: kpi?.settledThisMonth ?? 0, icon: <CheckCircle className="w-5 h-5 text-teal-700" />, bg: 'bg-teal-100' },
    { label: 'Total Settled Amount', value: formatCurrency(kpi?.totalSettledAmount), icon: <TrendingUp className="w-5 h-5 text-indigo-700" />, bg: 'bg-indigo-100' },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Claims KPI Dashboard</h1>
        <p className="text-gray-500 mt-1">Pre-aggregated snapshot — refreshed every 15 minutes.</p>
      </div>

      <div className="grid grid-cols-3 gap-5">
        {tiles.map((tile) => (
          <div key={tile.label} className="card">
            <div className="flex items-center gap-3">
              <div className={`p-2 rounded-lg ${tile.bg}`}>{tile.icon}</div>
              <div>
                <p className="text-sm text-gray-500">{tile.label}</p>
                <p className="text-2xl font-bold text-gray-900">{tile.value}</p>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
