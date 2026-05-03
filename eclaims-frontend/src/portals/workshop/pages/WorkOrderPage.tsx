import React from 'react'
import { useMutation } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { httpClient } from '@/shared/api/httpClient'
import { CheckCircle, Building2 } from 'lucide-react'
import { useMyWorkshop } from '@/features/workshops/hooks/useMyWorkshop'

export default function WorkOrderPage() {
  const { data: profile, isLoading: profileLoading } = useMyWorkshop()
  const [searchParams] = useSearchParams()

  const [form, setForm] = React.useState({
    claimId: searchParams.get('claimId') ?? '',
    estimatedCost: '',
    workDescription: '',
    estimatedCompletionDate: '',
  })

  const submitWorkOrder = useMutation({
    mutationFn: () =>
      httpClient
        .post('/work-orders', {
          claimId: form.claimId,
          workshopId: profile?.id,
          estimatedCost: parseFloat(form.estimatedCost),
          workDescription: form.workDescription,
          estimatedCompletionDate: form.estimatedCompletionDate || null,
        })
        .then((r) => r.data),
  })

  if (submitWorkOrder.isSuccess) {
    return (
      <div className="card max-w-lg mx-auto text-center py-12">
        <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-gray-900">Work Order Submitted</h2>
        <p className="text-gray-500 mt-2">
          Work Order ID:{' '}
          <code className="bg-gray-100 px-2 py-0.5 rounded text-sm">
            {submitWorkOrder.data?.data?.workOrderId}
          </code>
        </p>
      </div>
    )
  }

  const isReady = !!profile && !profileLoading

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Submit Work Order</h1>

      {profile && (
        <div className="flex items-center gap-3 bg-blue-50 border border-blue-200 rounded-lg px-4 py-3 text-sm text-blue-800">
          <Building2 className="w-4 h-4 flex-shrink-0" />
          <span>
            Submitting as <strong>{profile.name}</strong> - {profile.city}
          </span>
        </div>
      )}

      {!profile && !profileLoading && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-sm text-amber-800">
          No workshop profile linked to your account. Contact admin before submitting work orders.
        </div>
      )}

      <div className="card space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Claim ID *</label>
          <input
            type="text"
            value={form.claimId}
            onChange={(e) => setForm((f) => ({ ...f, claimId: e.target.value }))}
            placeholder="UUID of the approved claim"
            className="input"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Estimated Cost (USD) *</label>
          <input
            type="number"
            value={form.estimatedCost}
            onChange={(e) => setForm((f) => ({ ...f, estimatedCost: e.target.value }))}
            placeholder="0.00"
            className="input"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Estimated Completion Date</label>
          <input
            type="date"
            value={form.estimatedCompletionDate}
            onChange={(e) => setForm((f) => ({ ...f, estimatedCompletionDate: e.target.value }))}
            className="input"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Work Description</label>
          <textarea
            value={form.workDescription}
            onChange={(e) => setForm((f) => ({ ...f, workDescription: e.target.value }))}
            rows={4}
            className="input resize-none"
            placeholder="Describe the repair work required..."
          />
        </div>

        {submitWorkOrder.isError && (
          <p className="text-sm text-red-600">
            Failed to submit work order. Please check the claim ID and try again.
          </p>
        )}

        <button
          onClick={() => submitWorkOrder.mutate()}
          disabled={!form.claimId || !form.estimatedCost || !isReady || submitWorkOrder.isPending}
          className="btn-primary w-full justify-center"
        >
          {submitWorkOrder.isPending ? 'Submitting...' : 'Submit Work Order'}
        </button>
      </div>
    </div>
  )
}
