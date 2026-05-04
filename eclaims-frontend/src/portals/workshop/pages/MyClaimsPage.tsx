import React from 'react'
import { Link } from 'react-router-dom'
import { FileText, Car, AlertTriangle, MapPin, Calendar, CheckCircle2, Clock } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { useMyClaims } from '@/features/workshops/hooks/useMyClaims'
import { downloadClaimReceiptPdf } from '@/shared/api/paymentReceiptApi'
import { format } from 'date-fns'

const STATUS_CONFIG: Record<string, { label: string; bg: string; text: string }> = {
  VEHICLE_AT_WORKSHOP:  { label: 'Vehicle Received',   bg: '#dbeafe', text: '#1e40af' },
  ASSIGNED:             { label: 'Surveyor Assigned',  bg: '#ede9fe', text: '#4c1d95' },
  UNDER_SURVEY:         { label: 'Under Survey',       bg: '#fef9c3', text: '#713f12' },
  SURVEYED:             { label: 'Surveyed',           bg: '#e0f2fe', text: '#0369a1' },
  UNDER_ADJUDICATION:   { label: 'Under Review',       bg: '#ffedd5', text: '#7c2d12' },
  APPROVED:             { label: 'Approved',           bg: '#dcfce7', text: '#14532d' },
  REJECTED:             { label: 'Rejected',           bg: '#fee2e2', text: '#991b1b' },
  PAYMENT_INITIATED:    { label: 'Payment Initiated',  bg: '#d1fae5', text: '#065f46' },
  SETTLED:              { label: 'Settled',            bg: '#d1fae5', text: '#065f46' },
}

const formatAmount = (v: number | null) =>
  v != null ? `$${Number(v).toFixed(2)}` : '-'

export default function MyClaimsPage() {
  const { data: claims = [], isLoading } = useMyClaims()
  const downloadReceiptMutation = useMutation({
    mutationFn: (claimId: string) => downloadClaimReceiptPdf(claimId),
  })

  const activeClaims = claims.filter((c) =>
    !['SETTLED', 'REJECTED'].includes(c.status)
  )
  const closedClaims = claims.filter((c) =>
    ['SETTLED', 'REJECTED'].includes(c.status)
  )

  const renderClaim = (claim: typeof claims[0]) => {
    const cfg = STATUS_CONFIG[claim.status] ?? { label: claim.status.replace(/_/g, ' '), bg: '#f3f4f6', text: '#374151' }
    return (
      <div
        key={claim.claim_id}
        className="bg-white border border-gray-200 rounded-xl p-5 space-y-3 hover:shadow-sm transition-shadow"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <Car className="w-4 h-4 text-primary-700" />
              <span className="font-semibold text-gray-900">{claim.vehicle_registration}</span>
            </div>
            <p className="text-xs text-gray-400 font-mono mt-0.5">{claim.claim_id}</p>
          </div>
          <span
            className="text-xs font-medium px-2.5 py-1 rounded-full whitespace-nowrap"
            style={{ background: cfg.bg, color: cfg.text }}
          >
            {cfg.label}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2 text-xs text-gray-600">
          <div className="flex items-center gap-1.5">
            <FileText className="w-3.5 h-3.5 text-gray-400" />
            Policy: <span className="font-medium text-gray-800">{claim.policy_number}</span>
          </div>
          {claim.incident_date && (
            <div className="flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-gray-400" />
              Incident: <span className="font-medium text-gray-800">
                {format(new Date(claim.incident_date), 'dd MMM yyyy')}
              </span>
            </div>
          )}
          {claim.incident_location && (
            <div className="flex items-center gap-1.5 col-span-2">
              <MapPin className="w-3.5 h-3.5 text-gray-400" />
              <span className="text-gray-700">{claim.incident_location}</span>
            </div>
          )}
        </div>

        {claim.description && (
          <div className="bg-gray-50 rounded-lg px-3 py-2 text-xs text-gray-700 leading-relaxed">
            <p className="text-gray-400 mb-1 font-medium uppercase tracking-wide text-[10px]">Accident Details</p>
            {claim.description}
          </div>
        )}

        {claim.fraud_flag && (
          <div className="flex items-center gap-1.5 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-1.5">
            <AlertTriangle className="w-3.5 h-3.5" />
            Flagged for fraud review
          </div>
        )}

        <div className="flex items-center justify-between pt-1 border-t border-gray-100">
          <div className="flex gap-4 text-xs text-gray-500">
            {claim.assessed_amount != null && (
              <span>Assessed: <span className="font-medium text-gray-800">{formatAmount(claim.assessed_amount)}</span></span>
            )}
            {claim.approved_amount != null && (
              <span>Approved: <span className="font-medium text-green-700">{formatAmount(claim.approved_amount)}</span></span>
            )}
          </div>
          <div className="flex items-center gap-3">
            {claim.status === 'SETTLED' && (
              <button
                type="button"
                onClick={() => downloadReceiptMutation.mutate(claim.claim_id)}
                disabled={downloadReceiptMutation.isPending}
                className="text-xs text-green-700 hover:underline font-medium disabled:opacity-60"
              >
                {downloadReceiptMutation.isPending ? 'Preparing receipt...' : 'Download Receipt PDF'}
              </button>
            )}
            <Link
              to={`/workshop/work-orders/new?claimId=${claim.claim_id}`}
              className="text-xs text-blue-600 hover:underline font-medium"
            >
              + Work Order
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Claims at My Workshop</h1>
        <div className="flex items-center gap-4 text-sm text-gray-500">
          {activeClaims.length > 0 && (
            <span className="flex items-center gap-1">
              <Clock className="w-4 h-4 text-blue-500" />
              {activeClaims.length} active
            </span>
          )}
          {closedClaims.length > 0 && (
            <span className="flex items-center gap-1">
              <CheckCircle2 className="w-4 h-4 text-green-500" />
              {closedClaims.length} closed
            </span>
          )}
        </div>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="animate-pulse h-36 bg-gray-100 rounded-xl" />
          ))}
        </div>
      )}

      {!isLoading && claims.length === 0 && (
        <div className="card text-center py-16">
          <Car className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">No vehicles have been dropped off at your workshop yet.</p>
        </div>
      )}

      {activeClaims.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Active</h2>
          {activeClaims.map(renderClaim)}
        </div>
      )}

      {closedClaims.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Closed</h2>
          {closedClaims.map(renderClaim)}
        </div>
      )}
    </div>
  )
}
