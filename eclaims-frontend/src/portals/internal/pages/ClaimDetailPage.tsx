import React, { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { StatusBadge } from '@/shared/components/ui/Badge'
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel'
import { formatCurrency } from '@/shared/utils/formatCurrency'
import { format } from 'date-fns'
import { useAuth } from '@/shared/auth/KeycloakProvider'
import { hasRole, hasAnyRole } from '@/shared/auth/roleUtils'
import {
  ArrowLeft, CheckCircle, XCircle, Users, FileText,
  MessageSquare, MessageSquarePlus, Paperclip, Download, AlertTriangle
} from 'lucide-react'
import { ReassignModal } from '../components/ReassignModal'
import { documentsApi } from '@/features/documents/api/documentsApi'
import { claimsApi } from '@/features/claims/api/claimsApi'
import type { ClaimEndorsement } from '@/features/claims/api/claimsApi.types'
import type { ApiResponse } from '@/shared/types/ApiResponse'

const ENDORSEMENT_TYPE_LABEL: Record<string, string> = {
  CUSTOMER_NOTE:   'Customer',
  SURVEYOR_NOTE:   'Surveyor',
  ADJUDICATOR_NOTE: 'Adjustor',
  OVERRIDE:        'Override',
  REASSIGNMENT:    'Reassignment',
}

const ENDORSEMENT_TYPE_COLOR: Record<string, string> = {
  CUSTOMER_NOTE:   'bg-blue-50 border-blue-200',
  SURVEYOR_NOTE:   'bg-purple-50 border-purple-200',
  ADJUDICATOR_NOTE: 'bg-green-50 border-green-200',
  OVERRIDE:        'bg-orange-50 border-orange-200',
  REASSIGNMENT:    'bg-gray-50 border-gray-200',
}

export default function ClaimDetailPage() {
  const { claimId } = useParams<{ claimId: string }>()
  const { data: claim, isLoading } = useClaimDetails(claimId)
  const { roles } = useAuth()
  const queryClient = useQueryClient()

  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [showReassignModal, setShowReassignModal] = useState<'surveyor' | 'adjustor' | null>(null)
  const [overrideAmount, setOverrideAmount] = useState('')
  const [overrideReason, setOverrideReason] = useState('')
  const [newNote, setNewNote] = useState('')
  const [noteError, setNoteError] = useState<string | null>(null)

  const isCaseManager = hasRole(roles, 'ROLE_CASE_MANAGER')
  const isAuditor = hasRole(roles, 'ROLE_AUDITOR')
  const isAdjustor = hasRole(roles, 'ROLE_ADJUSTOR')
  const canAdjudicate = isAdjustor || isCaseManager
  const canViewFull = isCaseManager || isAuditor || isAdjustor || hasAnyRole(roles, ['ROLE_REGIONAL_MGR', 'ROLE_TOP_MANAGEMENT'])

  const { data: documents = [] } = useQuery({
    queryKey: ['internal-documents', claimId],
    queryFn: () => documentsApi.listByClaimId(claimId!).then((r) => r.data ?? []),
    enabled: !!claimId && canViewFull,
  })

  const { data: endorsements = [] } = useQuery<ClaimEndorsement[]>({
    queryKey: ['internal-endorsements', claimId],
    queryFn: () => claimsApi.getEndorsements(claimId!).then((r) => r.data ?? []),
    enabled: !!claimId && canViewFull,
    staleTime: 0,
  })

  const updateStatus = useMutation({
    mutationFn: (body: { targetStatus: string; amount?: number; reason?: string }) =>
      httpClient.patch(`/claims/${claimId}/status`, body).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      queryClient.invalidateQueries({ queryKey: ['internal-claims'] })
      queryClient.invalidateQueries({ queryKey: ['internal-endorsements', claimId] })
    },
  })

  const overrideDecision = useMutation({
    mutationFn: () =>
      httpClient.post(`/claims/${claimId}/override`, {
        newAmount: parseFloat(overrideAmount),
        reason: overrideReason,
      }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      queryClient.invalidateQueries({ queryKey: ['internal-claims'] })
      queryClient.invalidateQueries({ queryKey: ['internal-endorsements', claimId] })
      setOverrideAmount('')
      setOverrideReason('')
    },
  })

  const addNoteMutation = useMutation({
    mutationFn: (note: string) => claimsApi.addEndorsement(claimId!, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['internal-endorsements', claimId] })
      setNewNote('')
      setNoteError(null)
    },
    onError: () => setNoteError('Failed to save note. Please try again.'),
  })

  if (isLoading) return (
    <div className="flex justify-center py-20">
      <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-800" />
    </div>
  )

  if (!claim) return <div className="card text-center py-12">Claim not found</div>

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Link to="/internal/claims-queue" className="text-gray-400 hover:text-gray-700">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-xl font-bold text-gray-900">Claim Review</h1>
          <p className="text-xs text-gray-400 font-mono">{claim.claimId}</p>
        </div>
        <div className="ml-auto flex items-center gap-3">
          {claim.fraudFlag && (
            <span className="flex items-center gap-1 text-red-600 text-xs font-medium bg-red-50 px-2 py-1 rounded">
              <AlertTriangle className="w-3 h-3" /> Fraud Flagged
            </span>
          )}
          <StatusBadge status={claim.status as ClaimStatus} />
        </div>
      </div>

      {/* Core claim details */}
      <div className="grid grid-cols-2 gap-5">
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Claim Information</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Policy</dt>
              <dd className="font-medium">{claim.policyNumber}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Vehicle</dt>
              <dd className="font-medium">{claim.vehicleRegistration}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Type</dt>
              <dd className="font-medium">{claim.claimType}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Incident Date</dt>
              <dd className="font-medium">{claim.incidentDate}</dd>
            </div>
            {claim.incidentLocation && (
              <div className="flex justify-between">
                <dt className="text-gray-500">Location</dt>
                <dd className="font-medium text-right max-w-[200px] truncate" title={claim.incidentLocation}>{claim.incidentLocation}</dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-gray-500">Police Report</dt>
              <dd className="font-medium">{claim.policeReportFiled ? 'Filed' : 'Not filed'}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Assigned Surveyor</dt>
              <dd className="font-medium">{claim.assignedSurveyorId || '—'}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Assigned Adjustor</dt>
              <dd className="font-medium">{claim.assignedAdjustorId || '—'}</dd>
            </div>
          </dl>
        </div>

        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Financial Assessment</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Estimated</dt>
              <dd className="font-medium">{formatCurrency(claim.estimatedAmount)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Assessed (Surveyor)</dt>
              <dd className="font-medium">{formatCurrency(claim.assessedAmount)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Approved (Adjustor)</dt>
              <dd className="font-medium text-green-700">{formatCurrency(claim.approvedAmount)}</dd>
            </div>
            {claim.rejectionReason && (
              <div className="pt-2 border-t border-gray-100">
                <dt className="text-gray-500 mb-1">Rejection Reason</dt>
                <dd className="text-red-700 text-xs leading-relaxed">{claim.rejectionReason}</dd>
              </div>
            )}
          </dl>
        </div>
      </div>

      {/* Description */}
      {claim.description && (
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-2">Incident Description</h2>
          <p className="text-sm text-gray-700 leading-relaxed">{claim.description}</p>
        </div>
      )}

      {/* Documents */}
      {canViewFull && (
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Paperclip className="w-4 h-4 text-gray-500" />
            <h2 className="text-sm font-semibold text-gray-900">Supporting Documents</h2>
            <span className="ml-auto text-xs text-gray-400">{documents.length} file{documents.length !== 1 ? 's' : ''}</span>
          </div>
          {documents.length === 0 ? (
            <p className="text-sm text-gray-400">No documents uploaded yet.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {documents.map((doc) => (
                <li key={doc.documentId} className="flex items-center justify-between py-2 text-sm">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-gray-400 flex-shrink-0" />
                    <div>
                      <p className="font-medium text-gray-800">{doc.filename}</p>
                      <p className="text-xs text-gray-400">
                        {doc.documentType.replace(/_/g, ' ')} · {(doc.fileSizeBytes / 1024).toFixed(0)} KB
                      </p>
                    </div>
                  </div>
                  <a
                    href={doc.downloadUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 text-xs text-primary-700 hover:underline"
                    aria-label={`Download ${doc.filename}`}
                  >
                    <Download className="w-3.5 h-3.5" /> Download
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* Notes & Endorsements */}
      {canViewFull && (
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <MessageSquare className="w-4 h-4 text-gray-500" />
            <h2 className="text-sm font-semibold text-gray-900">Notes &amp; Endorsements</h2>
          </div>

          {endorsements.length === 0 ? (
            <p className="text-sm text-gray-400 mb-4">No notes recorded yet.</p>
          ) : (
            <ul className="space-y-3 mb-5">
              {endorsements.map((e) => (
                <li
                  key={e.endorsementId}
                  className={`rounded-xl border p-3 text-sm ${ENDORSEMENT_TYPE_COLOR[e.endorsementType] ?? 'bg-gray-50 border-gray-200'}`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xs font-semibold text-gray-600">
                      {ENDORSEMENT_TYPE_LABEL[e.endorsementType] ?? e.endorsementType}
                    </span>
                    <span className="text-xs text-gray-400">· {e.addedBy}</span>
                    <span className="ml-auto text-xs text-gray-400">
                      {format(new Date(e.createdAt), 'dd MMM yyyy, HH:mm')}
                    </span>
                  </div>
                  <p className="text-gray-800 leading-relaxed">{e.note}</p>
                </li>
              ))}
            </ul>
          )}

          {/* Case managers can add internal notes */}
          {isCaseManager && (
            <div className="space-y-2 pt-4 border-t border-gray-100">
              <label className="block text-xs font-medium text-gray-600">Add Internal Note</label>
              <textarea
                value={newNote}
                onChange={(e) => setNewNote(e.target.value)}
                rows={3}
                placeholder="Add a case management note…"
                className="input text-sm resize-none w-full"
                maxLength={2000}
                aria-label="Add internal note"
              />
              {noteError && <p className="text-xs text-red-600">{noteError}</p>}
              <button
                onClick={() => {
                  if (newNote.trim().length < 5) {
                    setNoteError('Note must be at least 5 characters.')
                    return
                  }
                  addNoteMutation.mutate(newNote.trim())
                }}
                disabled={addNoteMutation.isPending || !newNote.trim()}
                className="flex items-center gap-1.5 text-sm bg-primary-700 hover:bg-primary-800 text-white px-4 py-2 rounded-lg transition-colors disabled:opacity-50"
              >
                <MessageSquarePlus className="w-4 h-4" />
                {addNoteMutation.isPending ? 'Saving…' : 'Add Note'}
              </button>
            </div>
          )}
        </div>
      )}

      {/* Reassignment Actions (Case Manager only) */}
      {isCaseManager && (
        <div className="card bg-gray-50">
          <div className="flex items-center gap-3 mb-4">
            <Users className="w-5 h-5 text-gray-700" />
            <h2 className="text-sm font-semibold text-gray-900">Case Manager Actions</h2>
          </div>
          <div className="flex gap-3">
            <button
              onClick={() => setShowReassignModal('surveyor')}
              className="btn-secondary text-sm"
              disabled={!claim.assignedSurveyorId}
            >
              Reassign Surveyor
            </button>
            <button
              onClick={() => setShowReassignModal('adjustor')}
              className="btn-secondary text-sm"
              disabled={!claim.assignedAdjustorId}
            >
              Reassign Adjustor
            </button>
          </div>
        </div>
      )}

      {/* Case Manager Override */}
      {isCaseManager && (claim.status === 'APPROVED' || claim.status === 'UNDER_ADJUDICATION') && (
        <div className="card border-t-4 border-t-orange-500">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Override Adjudication Decision</h2>
          {claim.approvedAmount && (
            <div className="bg-orange-50 border border-orange-200 rounded p-3 mb-4">
              <p className="text-sm text-orange-800">
                <strong>Current approved amount:</strong> {formatCurrency(claim.approvedAmount)}
              </p>
            </div>
          )}
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">New Approved Amount (USD)</label>
              <input
                type="number"
                value={overrideAmount}
                onChange={(e) => setOverrideAmount(e.target.value)}
                placeholder="0.00"
                step="0.01"
                min="0"
                className="input max-w-xs"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Override Reason (Required)</label>
              <textarea
                value={overrideReason}
                onChange={(e) => setOverrideReason(e.target.value)}
                rows={3}
                placeholder="Provide detailed justification for overriding the decision..."
                className="input resize-none"
              />
            </div>
            <button
              onClick={() => overrideDecision.mutate()}
              disabled={!overrideAmount || !overrideReason || overrideDecision.isPending}
              className="btn-primary bg-orange-600 hover:bg-orange-700"
            >
              Override Decision
            </button>
          </div>
        </div>
      )}

      {/* Adjudication Panel (Adjustor / Case Manager) */}
      {canAdjudicate && claim.status === 'UNDER_ADJUDICATION' && (
        <div className="card border-t-4 border-t-primary-800">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Adjudication Decision</h2>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Approved Amount (USD)</label>
              <input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                className="input max-w-xs"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Notes / Reason</label>
              <textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                className="input resize-none"
              />
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => updateStatus.mutate({ targetStatus: 'APPROVED', amount: parseFloat(amount), reason })}
                disabled={!amount || updateStatus.isPending}
                className="btn-primary"
              >
                <CheckCircle className="w-4 h-4 mr-2" /> Approve
              </button>
              <button
                onClick={() => updateStatus.mutate({ targetStatus: 'REJECTED', reason })}
                disabled={!reason || updateStatus.isPending}
                className="btn-secondary border-red-200 text-red-700 hover:bg-red-50"
              >
                <XCircle className="w-4 h-4 mr-2" /> Reject
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reassignment Modal */}
      {showReassignModal && (
        <ReassignModal
          claimId={claimId!}
          type={showReassignModal}
          currentAssignee={
            showReassignModal === 'surveyor' ? claim.assignedSurveyorId : claim.assignedAdjustorId
          }
          region={(claim as any).region}
          onClose={() => setShowReassignModal(null)}
        />
      )}
    </div>
  )
}
