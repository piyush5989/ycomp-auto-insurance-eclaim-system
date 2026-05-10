import React, { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { StatusBadge } from '@/shared/components/ui/Badge'
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel'
import { formatCurrency } from '@/shared/utils/formatCurrency'
import { useAuth } from '@/shared/auth/KeycloakProvider'
import { documentsApi } from '@/features/documents/api/documentsApi'
import { ArrowLeft, CheckCircle, XCircle, AlertCircle, FileText, Download } from 'lucide-react'

export default function AdjudicateClaimPage() {
  const { claimId } = useParams<{ claimId: string }>()
  const navigate = useNavigate()
  const { data: claim, isLoading } = useClaimDetails(claimId)
  const { username } = useAuth()
  const queryClient = useQueryClient()

  const [decision, setDecision] = useState<'APPROVED' | 'REJECTED' | null>(null)
  const [approvedAmount, setApprovedAmount] = useState('')
  const [rejectionReason, setRejectionReason] = useState('')

  const { data: documents = [] } = useQuery({
    queryKey: ['claim-documents', claimId],
    queryFn: () => documentsApi.listByClaimId(claimId!).then((r) => r.data || []),
    enabled: !!claimId,
  })

  const adjudicateMutation = useMutation({
    mutationFn: async () => {
      if (!decision) throw new Error('Decision is required')
      if (decision === 'APPROVED' && !approvedAmount) throw new Error('Approved amount is required')
      if (decision === 'REJECTED' && !rejectionReason) throw new Error('Rejection reason is required')

      // First, transition to UNDER_ADJUDICATION if not already there
      if (claim?.status === 'SURVEYED') {
        await httpClient.patch(`/claims/${claimId}/status`, {
          targetStatus: 'UNDER_ADJUDICATION',
        })
      }

      // Then, approve or reject
      return httpClient.patch(`/claims/${claimId}/status`, {
        targetStatus: decision,
        amount: decision === 'APPROVED' ? parseFloat(approvedAmount) : null,
        reason: decision === 'REJECTED' ? rejectionReason : null,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      queryClient.invalidateQueries({ queryKey: ['adjustor-my-claims'] })
      navigate('/internal/adjustor/my-claims')
    },
    onError: (error: any) => {
      console.error('Failed to submit decision:', error)
      alert(error?.response?.data?.message || 'Failed to submit decision. Please try again.')
    },
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    )
  }

  if (!claim) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500">Claim not found</p>
        <Link to="/internal/adjustor/my-claims" className="text-primary-600 hover:underline mt-4 inline-block">
          ← Back to My Claims
        </Link>
      </div>
    )
  }

  const canAdjudicate = claim.status === 'SURVEYED' || claim.status === 'UNDER_ADJUDICATION'
  const alreadyAdjudicated = claim.status === 'APPROVED' || claim.status === 'REJECTED'

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <Link
          to="/internal/adjustor/my-claims"
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to My Claims
        </Link>
        <StatusBadge status={claim.status as ClaimStatus} />
      </div>

      <div className="card">
        <h1 className="text-2xl font-bold text-gray-900 mb-4">Claim Adjudication</h1>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          <div>
            <span className="text-gray-500">Claim ID</span>
            <p className="font-mono font-medium">{claim.claimId.substring(0, 8)}…</p>
          </div>
          <div>
            <span className="text-gray-500">Policy Number</span>
            <p className="font-medium">{claim.policyNumber}</p>
          </div>
          <div>
            <span className="text-gray-500">Vehicle</span>
            <p className="font-medium">{claim.vehicleRegistration}</p>
          </div>
          <div>
            <span className="text-gray-500">Claim Type</span>
            <p className="font-medium">{claim.claimType?.replace('_', ' ')}</p>
          </div>
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Incident Details</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Date</dt>
              <dd className="font-medium">{claim.incidentDate}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Location</dt>
              <dd className="font-medium text-right">{claim.incidentLocation || '—'}</dd>
            </div>
            <div>
              <dt className="text-gray-500 mb-1">Description</dt>
              <dd className="text-gray-700">{claim.description || 'No description provided'}</dd>
            </div>
          </dl>
        </div>

        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Assessment Summary</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Surveyor Assessed Amount</dt>
              <dd className="font-bold text-lg text-primary-700">
                {claim.assessedAmount ? formatCurrency(claim.assessedAmount) : '—'}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Surveyor</dt>
              <dd className="font-medium">{claim.assignedSurveyorId ? 'Assigned' : 'Not assigned'}</dd>
            </div>
            {claim.surveyCompletedAt && (
              <div className="flex justify-between">
                <dt className="text-gray-500">Survey Completed</dt>
                <dd className="font-medium">{new Date(claim.surveyCompletedAt).toLocaleString()}</dd>
              </div>
            )}
          </dl>
        </div>
      </div>

      {documents.length > 0 && (
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Surveyor Documents</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {documents.map((doc) => (
              <div key={doc.documentId} className="border rounded-lg p-3 hover:bg-gray-50">
                <div className="flex items-center gap-2 mb-2">
                  <FileText className="w-4 h-4 text-gray-400" />
                  <span className="text-xs font-medium truncate">{doc.documentType}</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => void documentsApi.openDocumentInNewTabWithAuth(doc.documentId)}
                    className="text-xs text-primary-600 hover:underline flex items-center gap-1"
                  >
                    View
                  </button>
                  <button
                    type="button"
                    onClick={() => void documentsApi.downloadDocumentWithAuth(doc.documentId, doc.filename)}
                    className="text-xs text-primary-600 hover:underline flex items-center gap-1"
                  >
                    <Download className="w-3 h-3" />
                    Download
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {alreadyAdjudicated && (
        <div className={`card border-l-4 ${claim.status === 'APPROVED' ? 'border-l-green-500 bg-green-50' : 'border-l-red-500 bg-red-50'}`}>
          <div className="flex items-center gap-3 mb-2">
            {claim.status === 'APPROVED' ? (
              <CheckCircle className="w-5 h-5 text-green-600" />
            ) : (
              <XCircle className="w-5 h-5 text-red-600" />
            )}
            <h2 className="text-sm font-semibold text-gray-900">
              Claim {claim.status === 'APPROVED' ? 'Approved' : 'Rejected'}
            </h2>
          </div>
          <dl className="space-y-2 text-sm">
            {claim.status === 'APPROVED' && claim.approvedAmount && (
              <div className="flex justify-between">
                <dt className="text-gray-600">Approved Amount</dt>
                <dd className="font-bold text-green-700">{formatCurrency(claim.approvedAmount)}</dd>
              </div>
            )}
            {claim.status === 'REJECTED' && claim.rejectionReason && (
              <div>
                <dt className="text-gray-600 mb-1">Rejection Reason</dt>
                <dd className="text-gray-700">{claim.rejectionReason}</dd>
              </div>
            )}
            {claim.adjudicatedAt && (
              <div className="flex justify-between">
                <dt className="text-gray-600">Adjudicated At</dt>
                <dd className="font-medium">{new Date(claim.adjudicatedAt).toLocaleString()}</dd>
              </div>
            )}
          </dl>
        </div>
      )}

      {canAdjudicate && (
        <div className="card border-t-4 border-t-blue-500">
          <div className="flex items-center gap-3 mb-4">
            <AlertCircle className="w-5 h-5 text-blue-600" />
            <h2 className="text-sm font-semibold text-gray-900">Make Adjudication Decision</h2>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Decision <span className="text-red-500">*</span>
              </label>
              <div className="flex gap-3">
                <button
                  onClick={() => {
                    setDecision('APPROVED')
                    setRejectionReason('')
                  }}
                  className={`flex-1 py-3 px-4 rounded-lg border-2 transition-colors ${
                    decision === 'APPROVED'
                      ? 'border-green-500 bg-green-50 text-green-700'
                      : 'border-gray-200 hover:border-green-300'
                  }`}
                >
                  <CheckCircle className="w-5 h-5 mx-auto mb-1" />
                  <span className="font-medium">Approve Claim</span>
                </button>
                <button
                  onClick={() => {
                    setDecision('REJECTED')
                    setApprovedAmount('')
                  }}
                  className={`flex-1 py-3 px-4 rounded-lg border-2 transition-colors ${
                    decision === 'REJECTED'
                      ? 'border-red-500 bg-red-50 text-red-700'
                      : 'border-gray-200 hover:border-red-300'
                  }`}
                >
                  <XCircle className="w-5 h-5 mx-auto mb-1" />
                  <span className="font-medium">Reject Claim</span>
                </button>
              </div>
            </div>

            {decision === 'APPROVED' && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Approved Amount (USD) <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  value={approvedAmount}
                  onChange={(e) => setApprovedAmount(e.target.value)}
                  placeholder="0.00"
                  step="0.01"
                  min="0"
                  className="input max-w-xs"
                  required
                />
                <p className="text-xs text-gray-500 mt-1">
                  Surveyor assessed: {claim.assessedAmount ? formatCurrency(claim.assessedAmount) : 'N/A'}
                </p>
              </div>
            )}

            {decision === 'REJECTED' && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Rejection Reason <span className="text-red-500">*</span>
                </label>
                <textarea
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  placeholder="Provide a detailed reason for rejecting this claim..."
                  rows={4}
                  className="input"
                  required
                />
              </div>
            )}

            <div className="flex gap-3 pt-4">
              <button
                onClick={() => adjudicateMutation.mutate()}
                disabled={!decision || adjudicateMutation.isPending}
                className="btn-primary flex-1"
              >
                {adjudicateMutation.isPending ? 'Submitting...' : 'Submit Decision'}
              </button>
              <button
                onClick={() => navigate('/internal/adjustor/my-claims')}
                disabled={adjudicateMutation.isPending}
                className="btn-secondary"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
