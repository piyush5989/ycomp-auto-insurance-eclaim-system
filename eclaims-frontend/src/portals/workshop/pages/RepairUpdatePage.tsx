import React, { useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { CheckCircle } from 'lucide-react'

const REPAIR_STATUSES = ['PENDING', 'IN_PROGRESS', 'PARTS_ORDERED', 'AWAITING_APPROVAL', 'COMPLETED']

export default function RepairUpdatePage() {
  const { id: workOrderId } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()

  const [status, setStatus] = useState('IN_PROGRESS')
  const [note, setNote] = useState('')
  const [finalCost, setFinalCost] = useState(searchParams.get('finalCost') ?? '')
  const [estimatedCompletionDate, setEstimatedCompletionDate] = useState('')

  const updateRepair = useMutation({
    mutationFn: () => {
      const params: Record<string, string> = { status }
      if (note) params.note = note
      if (finalCost) params.finalCost = finalCost
      if (estimatedCompletionDate) params.estimatedCompletionDate = estimatedCompletionDate
      return httpClient
        .patch(`/work-orders/${workOrderId}/repair-status`, null, { params })
        .then((r) => r.data)
    },
  })

  if (updateRepair.isSuccess) {
    return (
      <div className="card max-w-lg mx-auto text-center py-12">
        <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-gray-900">Repair Status Updated</h2>
        <p className="text-gray-500 mt-2">Customer will be notified of the update.</p>
        {status === 'COMPLETED' && finalCost && (
          <p className="text-sm text-green-700 mt-2 font-medium">
            Final bill of ${parseFloat(finalCost).toFixed(2)} has been recorded.
          </p>
        )}
      </div>
    )
  }

  return (
    <div className="max-w-lg mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Update Repair Status</h1>
      <p className="text-gray-500 text-sm font-mono">Work Order: {workOrderId}</p>

      <div className="card space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Repair Status *</label>
          <select value={status} onChange={(e) => setStatus(e.target.value)} className="input">
            {REPAIR_STATUSES.map((s) => (
              <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Estimated Completion Date
          </label>
          <input
            type="date"
            value={estimatedCompletionDate}
            onChange={(e) => setEstimatedCompletionDate(e.target.value)}
            className="input"
          />
        </div>

        {status === 'COMPLETED' && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Final Cost (USD) *
            </label>
            <input
              type="number"
              value={finalCost}
              onChange={(e) => setFinalCost(e.target.value)}
              placeholder="0.00"
              className="input"
            />
            <p className="text-xs text-gray-500 mt-1">
              Required when marking as COMPLETED. Customer will be notified before payment.
            </p>
          </div>
        )}

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Status Note</label>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={3}
            className="input resize-none"
            placeholder="Add an update for the customer..."
          />
        </div>

        {updateRepair.isError && (
          <p className="text-sm text-red-600">Failed to update status. Please try again.</p>
        )}

        <button
          onClick={() => updateRepair.mutate()}
          disabled={updateRepair.isPending || (status === 'COMPLETED' && !finalCost)}
          className="btn-primary w-full justify-center"
        >
          {updateRepair.isPending ? 'Updating...' : 'Update Status'}
        </button>
      </div>
    </div>
  )
}
