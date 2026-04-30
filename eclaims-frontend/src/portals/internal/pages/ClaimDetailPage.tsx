import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { StatusBadge } from '@/shared/components/ui/Badge';
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel';
import { formatCurrency } from '@/shared/utils/formatCurrency';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { hasRole } from '@/shared/auth/roleUtils';
import { ArrowLeft, CheckCircle, XCircle, UserCheck, Users } from 'lucide-react';
import { ReassignModal } from '../components/ReassignModal';

export default function ClaimDetailPage() {
  const { claimId } = useParams<{ claimId: string }>();
  const { data: claim, isLoading } = useClaimDetails(claimId);
  const { roles } = useAuth();
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [showReassignModal, setShowReassignModal] = useState<'surveyor' | 'adjustor' | null>(null);
  const [overrideAmount, setOverrideAmount] = useState('');
  const [overrideReason, setOverrideReason] = useState('');

  const updateStatus = useMutation({
    mutationFn: (body: { targetStatus: string; amount?: number; reason?: string }) =>
      httpClient.patch(`/claims/${claimId}/status`, body).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] });
      queryClient.invalidateQueries({ queryKey: ['internal-claims'] });
    },
  });

  const overrideDecision = useMutation({
    mutationFn: () =>
      httpClient.post(`/claims/${claimId}/override`, {
        newAmount: parseFloat(overrideAmount),
        reason: overrideReason,
      }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] });
      queryClient.invalidateQueries({ queryKey: ['internal-claims'] });
      setOverrideAmount('');
      setOverrideReason('');
    },
  });

  if (isLoading) return <div className="flex justify-center py-20"><div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-800" /></div>;

  if (!claim) return <div className="card text-center py-12">Claim not found</div>;

  const canAdjudicate = hasRole(roles, 'ROLE_ADJUSTOR') || hasRole(roles, 'ROLE_CASE_MANAGER');
  const canReassign = hasRole(roles, 'ROLE_CASE_MANAGER');

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link to="/internal/claims-queue" className="text-gray-400 hover:text-gray-700"><ArrowLeft className="w-5 h-5" /></Link>
        <div>
          <h1 className="text-xl font-bold text-gray-900">Claim Review</h1>
          <p className="text-xs text-gray-400 font-mono">{claim.claimId}</p>
        </div>
        <div className="ml-auto flex items-center gap-3">
          {claim.fraudFlag && <span className="text-red-600 text-xs font-medium bg-red-50 px-2 py-1 rounded">⚠ Fraud Flagged</span>}
          <StatusBadge status={claim.status as ClaimStatus} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-5">
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Claim Information</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between"><dt className="text-gray-500">Policy</dt><dd className="font-medium">{claim.policyNumber}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Vehicle</dt><dd className="font-medium">{claim.vehicleRegistration}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Type</dt><dd className="font-medium">{claim.claimType}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Incident</dt><dd className="font-medium">{claim.incidentDate}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Surveyor</dt><dd className="font-medium">{claim.assignedSurveyorId || '—'}</dd></div>
          </dl>
        </div>
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Assessment</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between"><dt className="text-gray-500">Assessed</dt><dd className="font-medium">{formatCurrency(claim.assessedAmount)}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Approved</dt><dd className="font-medium text-green-700">{formatCurrency(claim.approvedAmount)}</dd></div>
          </dl>
        </div>
      </div>

      {claim.description && (
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-2">Description</h2>
          <p className="text-sm text-gray-700">{claim.description}</p>
        </div>
      )}

      {/* Reassignment Actions */}
      {canReassign && (
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
      {canReassign && (claim.status === 'APPROVED' || claim.status === 'UNDER_ADJUDICATION') && (
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
              <label className="block text-sm font-medium text-gray-700 mb-1">
                New Approved Amount (USD)
              </label>
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
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Override Reason (Required)
              </label>
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

      {/* Adjudication Panel */}
      {canAdjudicate && claim.status === 'UNDER_ADJUDICATION' && (
        <div className="card border-t-4 border-t-primary-800">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Adjudication Decision</h2>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Approved Amount (USD)</label>
              <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" className="input max-w-xs" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Notes / Reason</label>
              <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} className="input resize-none" />
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
          region={claim.region}
          onClose={() => setShowReassignModal(null)}
        />
      )}
    </div>
  );
}
