import React from 'react';
import { useParams } from 'react-router-dom';
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails';
import { useMutation, useQuery } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { formatCurrency } from '@/shared/utils/formatCurrency';
import { CheckCircle, CreditCard, Info } from 'lucide-react';

const PROCESSING_FEE = Number(import.meta.env.VITE_ECLAIMS_PROCESSING_FEE ?? '10')

export default function PaymentPage() {
  const { claimId } = useParams<{ claimId: string }>();
  const { data: claim } = useClaimDetails(claimId);

  const { data: finalBillAmount } = useQuery({
    queryKey: ['final-bill', claimId],
    queryFn: () => httpClient.get(`/payments/calculate-bill/${claimId}`).then((r) => r.data.data),
    enabled: !!claimId,
    staleTime: 30_000,
  });

  const { data: workOrder } = useQuery({
    queryKey: ['work-order', claimId],
    queryFn: () => httpClient.get(`/claims/${claimId}/work-order`).then((r) => r.data.data),
    enabled: !!claimId,
    retry: false,
  });

  const initiatePayment = useMutation({
    mutationFn: (data: { claimId: string; amount: number }) =>
      httpClient.post('/payments', { claimId: data.claimId, amount: data.amount, currency: 'USD' }, {
        headers: { 'Idempotency-Key': `payment-${claimId}-${Date.now()}` }
      }).then((r) => r.data),
  });

  const { data: receipt } = useQuery({
    queryKey: ['receipt', initiatePayment.data?.data?.paymentId],
    queryFn: () => httpClient.get(`/payments/${initiatePayment.data?.data?.paymentId}/receipt`).then((r) => r.data.data),
    enabled: !!initiatePayment.data?.data?.paymentId && initiatePayment.isSuccess,
  });

  if (initiatePayment.isSuccess && receipt) {
    return (
      <div className="max-w-2xl mx-auto space-y-6">
        <div className="card text-center py-8">
          <CheckCircle className="w-20 h-20 text-green-500 mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-gray-900 mb-2">Payment Successful!</h1>
          <p className="text-gray-600">Your payment has been processed successfully</p>
        </div>

        <div className="card">
          <div className="flex justify-between items-center mb-6">
            <h2 className="text-xl font-bold text-gray-900">Payment Receipt</h2>
            <button 
              onClick={() => window.print()}
              className="btn-secondary text-sm"
            >
              Download Receipt
            </button>
          </div>

          <div className="grid md:grid-cols-2 gap-6 text-sm">
            <div className="space-y-3">
              <h3 className="font-semibold text-gray-800 border-b pb-2">Claim Details</h3>
              <div className="flex justify-between"><span className="text-gray-500">Claim ID:</span><span className="font-mono">{receipt.claimId?.substring(0, 16)}...</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Policy Number:</span><span>{receipt.policyNumber}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Vehicle:</span><span>{receipt.vehicleRegistration}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Incident Date:</span><span>{new Date(receipt.incidentDate).toLocaleDateString()}</span></div>
            </div>

            <div className="space-y-3">
              <h3 className="font-semibold text-gray-800 border-b pb-2">Workshop Details</h3>
              <div className="flex justify-between"><span className="text-gray-500">Workshop:</span><span>{receipt.workshopName}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Phone:</span><span>{receipt.workshopPhone}</span></div>
            </div>
          </div>

          <div className="mt-6 pt-6 border-t">
            <h3 className="font-semibold text-gray-800 mb-4">Payment Breakdown</h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between"><span className="text-gray-500">Approved Amount:</span><span className="text-green-600">{formatCurrency(receipt.approvedAmount)}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Workshop Final Cost:</span><span className="text-orange-600">{formatCurrency(receipt.workshopFinalCost)}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">Processing Fee:</span><span className="text-gray-600">{formatCurrency(receipt.processingFee)}</span></div>
              <div className="border-t pt-2 mt-3">
                <div className="flex justify-between text-lg font-bold">
                  <span>Total Paid:</span>
                  <span className="text-blue-700">{formatCurrency(receipt.totalPaid)}</span>
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6 pt-6 border-t text-xs text-gray-500 space-y-1">
            <div className="flex justify-between"><span>Transaction ID:</span><span className="font-mono">{receipt.transactionId}</span></div>
            <div className="flex justify-between"><span>Payment Date:</span><span>{new Date(receipt.paymentDate).toLocaleString()}</span></div>
            <div className="flex justify-between"><span>Receipt ID:</span><span className="font-mono">{receipt.receiptId?.substring(0, 16)}...</span></div>
          </div>
        </div>

        <div className="card bg-green-50 border-green-200">
          <h3 className="font-semibold text-green-800 mb-2">Next Steps</h3>
          <p className="text-green-700 text-sm">
            Your vehicle is ready for pickup from <strong>{receipt.workshopName}</strong>. 
            Please contact them at <strong>{receipt.workshopPhone}</strong> to arrange pickup. 
            Once you collect your vehicle, you can mark this claim as complete.
          </p>
        </div>

        <div className="text-center">
          <button
            onClick={() => window.location.href = '/customer/claims'}
            className="btn-primary"
          >
            Back to My Claims
          </button>
        </div>
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
          <div className="flex justify-between"><span className="text-gray-500">Approved Amount</span><span className="text-green-700">{formatCurrency(claim?.approvedAmount)}</span></div>
          <div className="flex justify-between"><span className="text-gray-500">Workshop Final Cost</span><span className="text-orange-600">{formatCurrency(workOrder?.finalCost || claim?.approvedAmount || 0)}</span></div>
          <div className="flex justify-between"><span className="text-gray-500">Processing Fee</span><span className="text-gray-600">{formatCurrency(PROCESSING_FEE)}</span></div>
          <div className="border-t pt-3">
            <div className="flex justify-between">
              <span className="font-semibold">Final Amount Due</span>
              <span className="font-bold text-lg text-blue-700">
                {finalBillAmount ? formatCurrency(finalBillAmount) : 'Calculating...'}
              </span>
            </div>
          </div>
        </div>
        
        {(workOrder?.finalCost || 0) > (claim?.approvedAmount || 0) && (
          <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
            <div className="flex items-start gap-2">
              <Info className="w-4 h-4 text-blue-600 mt-0.5 flex-shrink-0" />
              <div className="text-xs text-blue-800">
                <p className="font-medium mb-1">Additional Cost Notice</p>
                <p>The workshop final cost exceeded your approved amount by {formatCurrency(workOrder.finalCost - (claim?.approvedAmount || 0))}. This difference plus a {formatCurrency(PROCESSING_FEE)} processing fee has been added to your bill.</p>
              </div>
            </div>
          </div>
        )}
      </div>

      <button
        onClick={() => claim && finalBillAmount && initiatePayment.mutate({ claimId: claim.claimId, amount: finalBillAmount })}
        disabled={!claim?.approvedAmount || !finalBillAmount || initiatePayment.isPending}
        className="btn-primary w-full justify-center"
      >
        <CreditCard className="w-4 h-4 mr-2" />
        {initiatePayment.isPending ? 'Processing…' : finalBillAmount ? `Pay ${formatCurrency(finalBillAmount)}` : 'Calculating...'}
      </button>
    </div>
  );
}
