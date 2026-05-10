import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { formatCurrency } from '@/shared/utils/formatCurrency'
import { MapPin, TrendingUp, Clock, DollarSign, AlertTriangle, Info } from 'lucide-react'

const REGIONS = ['EAST', 'WEST', 'NORTH', 'SOUTH', 'CENTRAL']

interface RegionalKpiData {
  region: string
  totalClaims: number
  submittedToday: number
  pendingAssignment: number
  underSurvey: number
  underAdjudication: number
  approvedThisMonth: number
  rejectedThisMonth: number
  settledThisMonth: number
  totalSettledAmount: number
  avgProcessingTimeHours: number
  fraudFlagged: number
  generatedAt: string
}

export default function RegionalReportPage() {
  const [selectedRegion, setSelectedRegion] = useState('EAST')

  const { data: regionalKpi, isLoading } = useQuery<RegionalKpiData>({
    queryKey: ['regional-kpi', selectedRegion],
    queryFn: () => httpClient.get(`/reports/regional?region=${selectedRegion}`).then((r) => r.data.data),
    staleTime: 15 * 60 * 1000,
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
      </div>
    )
  }

  const tiles = [
    {
      label: 'Total Claims',
      value: regionalKpi?.totalClaims ?? 0,
      icon: <MapPin className="w-5 h-5 text-blue-700" />,
      bg: 'bg-blue-100',
    },
    {
      label: 'Submitted Today',
      value: regionalKpi?.submittedToday ?? 0,
      icon: <TrendingUp className="w-5 h-5 text-green-700" />,
      bg: 'bg-green-100',
    },
    {
      label: 'Avg Processing Time (hrs)',
      value: regionalKpi?.avgProcessingTimeHours.toFixed(1) ?? '0.0',
      icon: <Clock className="w-5 h-5 text-orange-700" />,
      bg: 'bg-orange-100',
    },
    {
      label: 'Total Settled Amount',
      value: formatCurrency(regionalKpi?.totalSettledAmount),
      icon: <DollarSign className="w-5 h-5 text-emerald-700" />,
      bg: 'bg-emerald-100',
    },
    {
      label: 'Approved This Month',
      value: regionalKpi?.approvedThisMonth ?? 0,
      icon: <TrendingUp className="w-5 h-5 text-teal-700" />,
      bg: 'bg-teal-100',
    },
    {
      label: 'Fraud Flagged',
      value: regionalKpi?.fraudFlagged ?? 0,
      icon: <AlertTriangle className="w-5 h-5 text-red-700" />,
      bg: 'bg-red-100',
    },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Regional Reports</h1>
        <p className="text-gray-500 mt-1">Claims performance and metrics by region</p>
      </div>

      <div
        role="note"
        aria-label="Live aggregation notice"
        className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900"
      >
        <Info className="mt-0.5 h-4 w-4 flex-shrink-0 text-emerald-700" aria-hidden="true" />
        <p>
          Numbers are aggregated <strong>live from real claim records</strong> in the database and
          refreshed every minute. In production this read model would be event-driven via Kafka
          instead of a scheduled rebuild.
        </p>
      </div>

      <div className="card">
        <label className="block text-sm font-medium text-gray-700 mb-2">Select Region</label>
        <select
          value={selectedRegion}
          onChange={(e) => setSelectedRegion(e.target.value)}
          className="input max-w-xs"
        >
          {REGIONS.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
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

      <div className="card">
        <h3 className="text-sm font-semibold text-gray-900 mb-4">Claim Status Breakdown</h3>
        <div className="grid grid-cols-2 gap-4">
          <div className="flex justify-between items-center">
            <span className="text-sm text-gray-600">Pending Assignment</span>
            <span className="text-lg font-semibold text-gray-900">{regionalKpi?.pendingAssignment ?? 0}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm text-gray-600">Under Survey</span>
            <span className="text-lg font-semibold text-gray-900">{regionalKpi?.underSurvey ?? 0}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm text-gray-600">Under Adjudication</span>
            <span className="text-lg font-semibold text-gray-900">{regionalKpi?.underAdjudication ?? 0}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm text-gray-600">Settled This Month</span>
            <span className="text-lg font-semibold text-gray-900">{regionalKpi?.settledThisMonth ?? 0}</span>
          </div>
        </div>
      </div>

      <div className="text-xs text-gray-500">
        Last updated: {regionalKpi?.generatedAt ? new Date(regionalKpi.generatedAt).toLocaleString() : 'N/A'}
      </div>
    </div>
  )
}
