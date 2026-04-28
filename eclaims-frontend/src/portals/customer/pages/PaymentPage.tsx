import React from 'react';
import { useParams } from 'react-router-dom';
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails';
import { useMutation } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { formatCurrency } from '@/shared/utils/formatCurrency';
import { CheckCircle, CreditCard } from 'lucide-react';

export default function PaymentPage() {
  const { claimId } = useParams<{ claimId: string }>();
  const { data: claim } = useClaimDetails(claimId);

  const initiatePayment = useMutation({
    mutationFn: (data: { claimId: string; amount: number }) =>
      httpClient.post('/payments', { claimId: data.claimId, amount: data.amount, currency: 'USD' }, {
        headers: { 'Idempotency-Key': `payment-${claimId}-${Date.now()}` }
      }).then((r) => r.data),
  });

  if (initiatePayment.isSuccess) {
    return (
      <div className="card max-w-lg mx-auto text-center py-12">
        <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-gray-900 mb-2">Payment Successful</h2>
        <p className="text-gray-500">Transaction ID: <code className="bg-gray-100 px-2 py-0.5 rounded text-sm">{initiatePayment.data?.data?.gatewayTransactionId}</code></p>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Payment</h1>

      <div className="card">
        <h2 className="text-sm font-semibold text-gray-900 mb-4">Payment Summary</h2>
        <div className="space-y-3 text-sm">
          <div className="flex justify-between"><span className="text-gray-500">Claim ID</span><span className="font-mono text-xs">{claimId?.substring(0, 16)}…</span></div>
          <div className="flex justify-between"><span className="text-gray-500">Approved Amount</span><span className="font-bold text-lg text-green-700">{formatCurrency(claim?.approvedAmount)}</span></div>
        </div>
      </div>

      <button
        onClick={() => claim && initiatePayment.mutate({ claimId: claim.claimId, amount: claim.approvedAmount! })}
        disabled={!claim?.approvedAmount || initiatePayment.isPending}
        className="btn-primary w-full justify-center"
      >
        <CreditCard className="w-4 h-4 mr-2" />
        {initiatePayment.isPending ? 'Processing…' : `Pay ${formatCurrency(claim?.approvedAmount)}`}
      </button>
    </div>
  );
}
