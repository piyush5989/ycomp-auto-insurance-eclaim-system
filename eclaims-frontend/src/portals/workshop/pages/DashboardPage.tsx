import React from 'react'
import { Link } from 'react-router-dom'
import { PlusCircle, Wrench, MapPin, Phone, Star, ClipboardList, Car, AlertCircle } from 'lucide-react'
import { useMyWorkshop } from '@/features/workshops/hooks/useMyWorkshop'
import { useMyWorkOrders } from '@/features/workshops/hooks/useMyWorkOrders'
import { useMyClaims } from '@/features/workshops/hooks/useMyClaims'

export default function DashboardPage() {
  const { data: profile, isLoading: profileLoading } = useMyWorkshop()
  const { data: workOrders = [] } = useMyWorkOrders()
  const { data: claims = [] } = useMyClaims()

  const activeOrders = workOrders.filter((wo) =>
    ['PENDING', 'IN_PROGRESS', 'PARTS_ORDERED', 'AWAITING_APPROVAL'].includes(wo.repairStatus)
  )

  const claimsWithoutWorkOrder = claims.filter(
    (c) => !workOrders.some((wo) => wo.claimId === c.claim_id)
  )

  return (
    <div className="space-y-8">
      {profileLoading ? (
        <div className="animate-pulse h-24 bg-gray-100 rounded-xl" />
      ) : profile ? (
        <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">{profile.name}</h1>
              <p className="text-gray-500 text-sm mt-0.5">{profile.providerType.replace('_', ' ')}</p>
            </div>
            <div className="flex items-center gap-1 text-amber-500">
              <Star className="w-4 h-4 fill-amber-400" />
              <span className="font-semibold text-sm">{profile.rating.toFixed(1)}</span>
            </div>
          </div>
          <div className="mt-4 flex flex-wrap gap-4 text-sm text-gray-600">
            <span className="flex items-center gap-1.5">
              <MapPin className="w-4 h-4 text-gray-400" />
              {profile.address}, {profile.city} {profile.zipCode}
            </span>
            <span className="flex items-center gap-1.5">
              <Phone className="w-4 h-4 text-gray-400" />
              {profile.phone}
            </span>
          </div>
        </div>
      ) : (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-5 text-amber-800 text-sm">
          Workshop profile not linked. Contact admin to associate your account with a workshop record.
        </div>
      )}

      <div className="grid grid-cols-2 gap-5">
        <Link
          to="/workshop/work-orders/new"
          className="card hover:shadow-md transition-shadow flex items-center gap-4"
          aria-label="Submit a new work order"
        >
          <div className="p-3 bg-blue-100 rounded-lg">
            <PlusCircle className="w-6 h-6 text-blue-700" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">Submit Work Order</h3>
            <p className="text-sm text-gray-500">Create a repair estimate for an approved claim</p>
          </div>
        </Link>

        <Link
          to="/workshop/work-orders"
          className="card hover:shadow-md transition-shadow flex items-center gap-4"
          aria-label="View active work orders"
        >
          <div className="p-3 bg-green-100 rounded-lg">
            <Wrench className="w-6 h-6 text-green-700" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">Active Work Orders</h3>
            <p className="text-sm text-gray-500">
              {activeOrders.length > 0
                ? `${activeOrders.length} in progress`
                : 'No active repairs'}
            </p>
          </div>
        </Link>

        <Link
          to="/workshop/claims"
          className="card hover:shadow-md transition-shadow flex items-center gap-4"
          aria-label="View claims at this workshop"
        >
          <div className="p-3 bg-purple-100 rounded-lg">
            <Car className="w-6 h-6 text-purple-700" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">Claims at Workshop</h3>
            <p className="text-sm text-gray-500">
              {claims.length > 0 ? `${claims.length} vehicle${claims.length > 1 ? 's' : ''} received` : 'No vehicles yet'}
            </p>
          </div>
        </Link>
      </div>

      {claimsWithoutWorkOrder.length > 0 && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start gap-3">
          <AlertCircle className="w-5 h-5 text-amber-600 mt-0.5 flex-shrink-0" />
          <div className="flex-1">
            <p className="text-sm font-semibold text-amber-800">
              {claimsWithoutWorkOrder.length} claim{claimsWithoutWorkOrder.length > 1 ? 's' : ''} pending work order
            </p>
            <p className="text-xs text-amber-700 mt-0.5">
              Vehicles have been received but no work order has been submitted yet.
            </p>
            <Link to="/workshop/claims" className="text-xs text-amber-800 underline font-medium mt-1 inline-block">
              View claims
            </Link>
          </div>
        </div>
      )}

      {workOrders.length > 0 && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-semibold text-gray-800">Recent Work Orders</h2>
            <Link to="/workshop/work-orders" className="text-sm text-blue-600 hover:underline">
              View all
            </Link>
          </div>
          <div className="space-y-2">
            {workOrders.slice(0, 5).map((wo) => (
              <div
                key={wo.workOrderId}
                className="bg-white border border-gray-200 rounded-lg px-4 py-3 flex items-center justify-between"
              >
                <div>
                  <p className="text-sm font-medium text-gray-900 font-mono">
                    Claim: {wo.claimId.slice(0, 8)}...
                  </p>
                  {wo.workDescription && (
                    <p className="text-xs text-gray-500 mt-0.5 truncate max-w-xs">
                      {wo.workDescription}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  {wo.estimatedCost != null && (
                    <span className="text-sm text-gray-600">${wo.estimatedCost.toFixed(2)}</span>
                  )}
                  <Link
                    to={`/workshop/work-orders/${wo.workOrderId}/update`}
                    className="text-xs font-medium px-2.5 py-1 rounded-full"
                    style={{ background: statusColor(wo.repairStatus).bg, color: statusColor(wo.repairStatus).text }}
                  >
                    {wo.repairStatus.replace('_', ' ')}
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

const statusColor = (status: string): { bg: string; text: string } => {
  const map: Record<string, { bg: string; text: string }> = {
    PENDING:           { bg: '#fef9c3', text: '#713f12' },
    IN_PROGRESS:       { bg: '#dbeafe', text: '#1e40af' },
    PARTS_ORDERED:     { bg: '#ede9fe', text: '#4c1d95' },
    AWAITING_APPROVAL: { bg: '#ffedd5', text: '#7c2d12' },
    COMPLETED:         { bg: '#dcfce7', text: '#14532d' },
  }
  return map[status] ?? { bg: '#f3f4f6', text: '#374151' }
}
