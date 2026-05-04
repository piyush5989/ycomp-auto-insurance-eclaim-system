import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Wrench, ArrowRight, CheckCircle, X } from 'lucide-react'
import { useMyWorkOrders } from '@/features/workshops/hooks/useMyWorkOrders'
import { consumeWorkshopRepairFlash } from '@/portals/workshop/workshopRepairFlash'

const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  PENDING:           { bg: '#fef9c3', text: '#713f12' },
  IN_PROGRESS:       { bg: '#dbeafe', text: '#1e40af' },
  PARTS_ORDERED:     { bg: '#ede9fe', text: '#4c1d95' },
  AWAITING_APPROVAL: { bg: '#ffedd5', text: '#7c2d12' },
  COMPLETED:         { bg: '#dcfce7', text: '#14532d' },
}

export default function WorkOrdersListPage() {
  const { data: workOrders = [], isLoading } = useMyWorkOrders()
  const [showRepairToast, setShowRepairToast] = useState(false)

  useEffect(() => {
    if (consumeWorkshopRepairFlash()) {
      setShowRepairToast(true)
      const id = window.setTimeout(() => setShowRepairToast(false), 4500)
      return () => window.clearTimeout(id)
    }
    return undefined
  }, [])

  const handleDismissRepairToast = () => {
    setShowRepairToast(false)
  }

  return (
    <div className="space-y-6">
      {showRepairToast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed top-4 left-1/2 z-50 flex max-w-md -translate-x-1/2 items-center gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3 shadow-lg"
        >
          <CheckCircle className="h-5 w-5 shrink-0 text-green-600" aria-hidden />
          <p className="text-sm font-medium text-green-900">Repair status updated successfully</p>
          <button
            type="button"
            onClick={handleDismissRepairToast}
            className="ml-2 rounded p-1 text-green-700 hover:bg-green-100"
            aria-label="Dismiss notification"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">My Work Orders</h1>
        <Link to="/workshop/work-orders/new" className="btn-primary">
          + New Work Order
        </Link>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="animate-pulse h-16 bg-gray-100 rounded-lg" />
          ))}
        </div>
      )}

      {!isLoading && workOrders.length === 0 && (
        <div className="card text-center py-16">
          <Wrench className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">No work orders yet. Submit one for an approved claim.</p>
        </div>
      )}

      {!isLoading && workOrders.length > 0 && (
        <div className="space-y-3">
          {workOrders.map((wo) => {
            const colors = STATUS_COLORS[wo.repairStatus] ?? { bg: '#f3f4f6', text: '#374151' }
            return (
              <div
                key={wo.workOrderId}
                className="bg-white border border-gray-200 rounded-xl px-5 py-4 flex items-center justify-between hover:shadow-sm transition-shadow"
              >
                <div className="space-y-1">
                  <p className="text-xs text-gray-400 font-mono">
                    Claim: {wo.claimId}
                  </p>
                  {wo.workDescription && (
                    <p className="text-sm text-gray-700 max-w-sm truncate">{wo.workDescription}</p>
                  )}
                  <div className="flex items-center gap-3 text-xs text-gray-500">
                    {wo.estimatedCost != null && (
                      <span>Est. ${wo.estimatedCost.toFixed(2)}</span>
                    )}
                    {wo.finalCost != null && (
                      <span className="text-green-700 font-medium">Final ${wo.finalCost.toFixed(2)}</span>
                    )}
                    {wo.estimatedCompletionDate && (
                      <span>Due {wo.estimatedCompletionDate}</span>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    className="text-xs font-medium px-2.5 py-1 rounded-full"
                    style={{ background: colors.bg, color: colors.text }}
                  >
                    {wo.repairStatus.replace(/_/g, ' ')}
                  </span>
                  <Link
                    to={`/workshop/work-orders/${wo.workOrderId}/update`}
                    aria-label={`Update work order ${wo.workOrderId}`}
                    className="text-gray-400 hover:text-blue-600 transition-colors"
                  >
                    <ArrowRight className="w-4 h-4" />
                  </Link>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
