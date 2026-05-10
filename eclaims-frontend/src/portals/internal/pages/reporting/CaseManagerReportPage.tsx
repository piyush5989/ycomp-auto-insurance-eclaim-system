import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { formatCurrency } from '@/shared/utils/formatCurrency'
import {
  ClipboardList,
  CheckCircle,
  XCircle,
  Clock,
  AlertTriangle,
  DollarSign,
  TrendingUp,
  FileText,
} from 'lucide-react'

interface CaseManagerReportResponse {
  caseManagerId: string
  totalReceived: number
  totalSettled: number
  totalRejected: number
  inProgress: number
  fraudFlagged: number
  totalPaidOut: number
  avgProcessingHours: number
  submittedThisMonth: number
  generatedAt: string
}

export default function CaseManagerReportPage() {
  const { data, isLoading, isError } = useQuery<CaseManagerReportResponse>({
    queryKey: ['case-manager-report'],
    queryFn: () => httpClient.get('/reports/my-claims').then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="card border-red-200 bg-red-50 text-red-700 text-sm">
        Failed to load your claims report. Please try again.
      </div>
    )
  }

  const settlementRate =
    data && data.totalReceived > 0
      ? ((data.totalSettled / data.totalReceived) * 100).toFixed(1)
      : '0.0'

  const tiles = [
    {
      label: 'Total Claims Received',
      value: data?.totalReceived ?? 0,
      icon: <ClipboardList className="w-5 h-5 text-blue-700" />,
      bg: 'bg-blue-100',
    },
    {
      label: 'Settled',
      value: data?.totalSettled ?? 0,
      icon: <CheckCircle className="w-5 h-5 text-emerald-700" />,
      bg: 'bg-emerald-100',
    },
    {
      label: 'Rejected',
      value: data?.totalRejected ?? 0,
      icon: <XCircle className="w-5 h-5 text-red-700" />,
      bg: 'bg-red-100',
    },
    {
      label: 'In Progress',
      value: data?.inProgress ?? 0,
      icon: <Clock className="w-5 h-5 text-orange-700" />,
      bg: 'bg-orange-100',
    },
    {
      label: 'Total Amount Paid Out',
      value: formatCurrency(data?.totalPaidOut),
      icon: <DollarSign className="w-5 h-5 text-teal-700" />,
      bg: 'bg-teal-100',
    },
    {
      label: 'Fraud Flagged',
      value: data?.fraudFlagged ?? 0,
      icon: <AlertTriangle className="w-5 h-5 text-yellow-700" />,
      bg: 'bg-yellow-100',
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Claims Report</h1>
          <p className="text-gray-500 mt-1">
            Performance metrics for claims you have managed or adjudicated
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-gray-400">
          <FileText className="w-4 h-4" />
          <span>Live data &mdash; refreshed every 5 minutes</span>
        </div>
      </div>

      {/* KPI tiles */}
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

      {/* Summary stats */}
      <div className="grid grid-cols-2 gap-5">
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-900 mb-4">Processing Performance</h3>
          <div className="space-y-4">
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-600">Avg Processing Time</span>
              <span className="text-lg font-semibold text-gray-900">
                {data?.avgProcessingHours != null
                  ? `${Number(data.avgProcessingHours).toFixed(1)} hrs`
                  : '—'}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-600">Settlement Rate</span>
              <span className="text-lg font-semibold text-emerald-700">{settlementRate}%</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-600">Submitted This Month</span>
              <span className="text-lg font-semibold text-gray-900">
                {data?.submittedThisMonth ?? 0}
              </span>
            </div>
          </div>
        </div>

        <div className="card">
          <h3 className="text-sm font-semibold text-gray-900 mb-4">Claims Disposition</h3>
          {data && data.totalReceived > 0 ? (
            <div className="space-y-3">
              {[
                { label: 'Settled', count: data.totalSettled, color: 'bg-emerald-500' },
                { label: 'In Progress', count: data.inProgress, color: 'bg-blue-500' },
                { label: 'Rejected', count: data.totalRejected, color: 'bg-red-400' },
                { label: 'Fraud Flagged', count: data.fraudFlagged, color: 'bg-yellow-500' },
              ].map((item) => {
                const pct = Math.round((item.count / data.totalReceived) * 100)
                return (
                  <div key={item.label}>
                    <div className="flex justify-between text-xs text-gray-500 mb-1">
                      <span>{item.label}</span>
                      <span>
                        {item.count} ({pct}%)
                      </span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2">
                      <div
                        className={`${item.color} h-2 rounded-full transition-all`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                )
              })}
            </div>
          ) : (
            <p className="text-sm text-gray-400 italic">No claims assigned to you yet.</p>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2 text-xs text-gray-400">
        <TrendingUp className="w-3.5 h-3.5" />
        <span>
          Last generated:{' '}
          {data?.generatedAt ? new Date(data.generatedAt).toLocaleString() : 'N/A'}
        </span>
      </div>
    </div>
  )
}
