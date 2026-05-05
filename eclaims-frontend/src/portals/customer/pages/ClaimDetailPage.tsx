import React from 'react';
import { useParams, Link } from 'react-router-dom';
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails';
import { useClaimStatusStream } from '@/features/claims/hooks/useClaimStatusStream';
import { StatusBadge } from '@/shared/components/ui/Badge';
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel';
import { formatCurrency } from '@/shared/utils/formatCurrency';
import { format } from 'date-fns';
import { ArrowLeft, AlertTriangle, Radio } from 'lucide-react';

/**
 * Claim detail page — shows full claim lifecycle status timeline.
 * Status is updated in real-time via SSE (useClaimStatusStream) — no polling needed.
 * When a claim.status.changed event arrives, the React Query cache is invalidated
 * and the UI updates instantly without any page refresh.
 */
export default function ClaimDetailPage() {
  const { claimId } = useParams<{ claimId: string }>();
  const { data: claim, isLoading, error } = useClaimDetails(claimId);

  // Subscribe to real-time status updates via SSE — replaces 30s polling
  useClaimStatusStream(claimId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-800" />
      </div>
    );
  }

  if (error || !claim) {
    return (
      <div className="card text-center py-12">
        <AlertTriangle className="w-12 h-12 text-red-400 mx-auto mb-3" />
        <p className="text-gray-700">Claim not found or you don't have access to this claim.</p>
        <Link to="/customer/claims" className="btn-primary mt-4 inline-flex">Back to Claims</Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link to="/customer/claims" className="text-gray-400 hover:text-gray-700">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-xl font-bold text-gray-900">Claim Details</h1>
          <p className="text-xs text-gray-400 font-mono">{claim.claimId}</p>
        </div>
        <StatusBadge status={claim.status as ClaimStatus} className="ml-auto" />
        <span className="flex items-center gap-1 text-xs text-green-600 font-medium">
          <Radio className="w-3 h-3 animate-pulse" />
          Live
        </span>
      </div>

      <div className="grid grid-cols-2 gap-5">
        {/* Claim Information */}
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Claim Information</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between"><dt className="text-gray-500">Policy Number</dt><dd className="font-medium">{claim.policyNumber}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Vehicle</dt><dd className="font-medium">{claim.vehicleRegistration}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Claim Type</dt><dd className="font-medium">{claim.claimType.replace('_', ' ')}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Incident Date</dt><dd className="font-medium">{format(new Date(claim.incidentDate), 'dd MMM yyyy')}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Submitted</dt><dd className="font-medium">{format(new Date(claim.createdAt), 'dd MMM yyyy, HH:mm')}</dd></div>
          </dl>
        </div>

        {/* Financial */}
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Assessment & Payment</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between"><dt className="text-gray-500">Assessed Amount</dt><dd className="font-medium">{formatCurrency(claim.assessedAmount)}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-500">Approved Amount</dt><dd className={`font-medium ${claim.approvedAmount ? 'text-green-700' : ''}`}>{formatCurrency(claim.approvedAmount)}</dd></div>
            {claim.rejectionReason && (
              <div>
                <dt className="text-gray-500 mb-1">Rejection Reason</dt>
                <dd className="text-red-600 bg-red-50 p-2 rounded">{claim.rejectionReason}</dd>
              </div>
            )}
          </dl>
        </div>
      </div>

      {/* Incident Details */}
      {claim.description && (
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-3">Incident Description</h2>
          <p className="text-sm text-gray-700 leading-relaxed">{claim.description}</p>
        </div>
      )}

      {/* Payment CTA */}
      {claim.status === 'APPROVED' && (
        <div className="card border-l-4 border-l-green-500">
          <p className="text-green-700 font-medium mb-3">Your claim has been approved! Proceed to payment.</p>
          <Link to={`/customer/payment/${claim.claimId}`} className="btn-primary">
            Proceed to Payment
          </Link>
        </div>
      )}
    </div>
  );
}
