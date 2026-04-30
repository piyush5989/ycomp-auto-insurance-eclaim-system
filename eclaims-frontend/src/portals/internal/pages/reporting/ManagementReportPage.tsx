import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { formatCurrency } from '@/shared/utils/formatCurrency'
import { BarChart2, TrendingUp, DollarSign, MapPin } from 'lucide-react'

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

export default function ManagementReportPage() {
  const { data: allRegions = [], isLoading } = useQuery<RegionalKpiData[]>({
    queryKey: ['all-regional-kpis'],
    queryFn: () => httpClient.get('/reports/regional/all').then((r) => r.data.data),
    staleTime: 15 * 60 * 1000,
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
      </div>
    )
  }

  const totalClaims = allRegions.reduce((sum, r) => sum + r.totalClaims, 0)
  const totalSettled = allRegions.reduce((sum, r) => sum + r.totalSettledAmount, 0)
  const avgProcessingTime = allRegions.length > 0
    ? allRegions.reduce((sum, r) => sum + r.avgProcessingTimeHours, 0) / allRegions.length
    : 0

  // Sort regions by total claims descending
  const sortedByVolume = [...allRegions].sort((a, b) => b.totalClaims - a.totalClaims)
  const sortedByAmount = [...allRegions].sort((a, b) => b.totalSettledAmount - a.totalSettledAmount)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Top Management Dashboard</h1>
        <p className="text-gray-500 mt-1">Multi-region comparison and performance analytics</p>
      </div>

      {/* Overall Summary */}
      <div className="grid grid-cols-3 gap-5">
        <div className="card">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-blue-100">
              <BarChart2 className="w-5 h-5 text-blue-700" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Total Claims (All Regions)</p>
              <p className="text-2xl font-bold text-gray-900">{totalClaims}</p>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-emerald-100">
              <DollarSign className="w-5 h-5 text-emerald-700" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Total Settled Amount</p>
              <p className="text-2xl font-bold text-gray-900">{formatCurrency(totalSettled)}</p>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-orange-100">
              <TrendingUp className="w-5 h-5 text-orange-700" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Avg Processing Time (hrs)</p>
              <p className="text-2xl font-bold text-gray-900">{avgProcessingTime.toFixed(1)}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Regional Comparison Table */}
      <div className="card">
        <h3 className="text-sm font-semibold text-gray-900 mb-4">Regional Performance Comparison</h3>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Region
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Total Claims
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Avg Processing (hrs)
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Settled Amount
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Fraud Flagged
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Approved This Month
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {allRegions.map((region) => (
                <tr key={region.region} className="hover:bg-gray-50">
                  <td className="px-4 py-3 whitespace-nowrap">
                    <div className="flex items-center gap-2">
                      <MapPin className="w-4 h-4 text-gray-400" />
                      <span className="text-sm font-medium text-gray-900">{region.region}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-right text-sm text-gray-900">
                    {region.totalClaims}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-right text-sm text-gray-900">
                    {region.avgProcessingTimeHours.toFixed(1)}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-right text-sm text-gray-900">
                    {formatCurrency(region.totalSettledAmount)}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-right text-sm">
                    <span className={`${region.fraudFlagged > 0 ? 'text-red-600 font-medium' : 'text-gray-900'}`}>
                      {region.fraudFlagged}
                    </span>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-right text-sm text-gray-900">
                    {region.approvedThisMonth}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Top Regions by Volume */}
      <div className="grid grid-cols-2 gap-5">
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-900 mb-4">Top Regions by Claim Volume</h3>
          <div className="space-y-3">
            {sortedByVolume.slice(0, 5).map((region, idx) => (
              <div key={region.region} className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-gray-400 w-6">{idx + 1}.</span>
                  <span className="text-sm font-medium text-gray-900">{region.region}</span>
                </div>
                <span className="text-sm font-semibold text-gray-900">{region.totalClaims} claims</span>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h3 className="text-sm font-semibold text-gray-900 mb-4">Top Regions by Settlement Amount</h3>
          <div className="space-y-3">
            {sortedByAmount.slice(0, 5).map((region, idx) => (
              <div key={region.region} className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-gray-400 w-6">{idx + 1}.</span>
                  <span className="text-sm font-medium text-gray-900">{region.region}</span>
                </div>
                <span className="text-sm font-semibold text-gray-900">
                  {formatCurrency(region.totalSettledAmount)}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="text-xs text-gray-500">
        Data refreshed every 15 minutes. Last update:{' '}
        {allRegions[0]?.generatedAt ? new Date(allRegions[0].generatedAt).toLocaleString() : 'N/A'}
      </div>
    </div>
  )
}
