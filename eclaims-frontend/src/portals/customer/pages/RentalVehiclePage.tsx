import React, { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'
import { ArrowLeft, Car, Users, DollarSign, CheckCircle2, AlertTriangle, X } from 'lucide-react'
import { formatCurrency } from '@/shared/utils/formatCurrency'

interface RentalVehicle {
  vehicleId: string
  vehicleType: string
  make: string
  model: string
  year: number
  seatingCapacity: number
  transmissionType: string
  fuelType: string
  dailyRate: number
  available: boolean
  providerId: string
  providerName: string
}

export default function RentalVehiclePage() {
  const { claimId } = useParams<{ claimId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: claim, isLoading: claimLoading } = useClaimDetails(claimId)
  
  const [selectedVehicleId, setSelectedVehicleId] = useState<string | null>(null)
  const [rentalDays, setRentalDays] = useState('7')

  const { data: rentalVehicles = [], isLoading: vehiclesLoading } = useQuery({
    queryKey: ['rental-vehicles'],
    queryFn: () =>
      httpClient.get('/rentals/vehicles?availableOnly=true').then((r) => r.data.data ?? []),
    staleTime: 5 * 60 * 1000,
  })

  const reserveMutation = useMutation({
    mutationFn: (vehicleId: string) =>
      httpClient.post('/rentals/reserve', {
        claimId,
        vehicleId,
        rentalDays: parseInt(rentalDays),
      }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      navigate(`/customer/claims/${claimId}`)
    },
  })

  const skipRental = () => {
    navigate(`/customer/claims/${claimId}`)
  }

  if (claimLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-800" />
      </div>
    )
  }

  if (!claim) {
    return (
      <div className="card text-center py-12">
        <AlertTriangle className="w-12 h-12 text-red-400 mx-auto mb-3" />
        <p className="text-gray-700">Claim not found</p>
        <Link to="/customer/claims" className="btn-primary mt-4 inline-flex">
          Back to Claims
        </Link>
      </div>
    )
  }

  const selectedVehicle = rentalVehicles.find((v: RentalVehicle) => v.vehicleId === selectedVehicleId)
  const totalCost = selectedVehicle ? selectedVehicle.dailyRate * parseInt(rentalDays || '1') : 0

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Link to={`/customer/claims/${claimId}`} className="text-gray-400 hover:text-gray-700">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-gray-900">Rental Vehicle (Optional)</h1>
          <p className="text-sm text-gray-500 mt-1">
            Claim {claim.claimId.substring(0, 8).toUpperCase()}
          </p>
        </div>
        <button
          onClick={skipRental}
          className="flex items-center gap-2 text-sm text-gray-600 hover:text-gray-900"
        >
          <X className="w-4 h-4" />
          Skip This Step
        </button>
      </div>

      {/* Step Indicator */}
      <div className="bg-purple-50 border border-purple-200 rounded-lg p-4">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-8 h-8 rounded-full bg-purple-600 text-white font-semibold text-sm">
            3
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">Step 3: Rental Vehicle (Optional)</h3>
            <p className="text-sm text-gray-600">Get a temporary vehicle while yours is being repaired</p>
          </div>
        </div>
      </div>

      {/* Rental Duration */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-3">Rental Duration</h2>
        <div className="flex items-center gap-3">
          <label htmlFor="rentalDays" className="text-sm text-gray-700">
            Number of days:
          </label>
          <input
            id="rentalDays"
            type="number"
            value={rentalDays}
            onChange={(e) => setRentalDays(e.target.value)}
            min="1"
            max="30"
            className="input w-24"
          />
          <span className="text-sm text-gray-500">days</span>
        </div>
        <p className="text-xs text-gray-500 mt-2">
          You can extend or return early. Actual charges based on actual usage.
        </p>
      </div>

      {/* Available Vehicles */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Available Vehicles</h2>

        {vehiclesLoading ? (
          <div className="flex justify-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
          </div>
        ) : rentalVehicles.length === 0 ? (
          <div className="text-center py-12 text-gray-400 border border-dashed border-gray-200 rounded-lg">
            <Car className="w-12 h-12 mx-auto mb-3 opacity-40" />
            <p>No rental vehicles available at this time</p>
            <p className="text-sm mt-2">Please check back later or contact support</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {rentalVehicles.map((vehicle: RentalVehicle) => (
              <div
                key={vehicle.vehicleId}
                onClick={() => setSelectedVehicleId(vehicle.vehicleId)}
                className={`border rounded-lg p-4 cursor-pointer transition-all ${
                  selectedVehicleId === vehicle.vehicleId
                    ? 'border-primary-600 bg-primary-50 shadow-sm'
                    : 'border-gray-200 hover:border-primary-300 hover:shadow-sm'
                }`}
              >
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <h3 className="font-semibold text-gray-900">
                      {vehicle.year} {vehicle.make} {vehicle.model}
                    </h3>
                    <p className="text-sm text-gray-500">{vehicle.vehicleType}</p>
                  </div>
                  <input
                    type="radio"
                    checked={selectedVehicleId === vehicle.vehicleId}
                    onChange={() => setSelectedVehicleId(vehicle.vehicleId)}
                    className="mt-1 w-5 h-5 text-primary-600"
                    aria-label={`Select ${vehicle.make} ${vehicle.model}`}
                  />
                </div>

                <div className="space-y-2 text-sm text-gray-600">
                  <div className="flex items-center gap-2">
                    <Users className="w-4 h-4" />
                    <span>{vehicle.seatingCapacity} seats</span>
                    <span className="text-gray-300">•</span>
                    <span>{vehicle.transmissionType}</span>
                    <span className="text-gray-300">•</span>
                    <span>{vehicle.fuelType}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <DollarSign className="w-4 h-4" />
                    <span className="font-semibold text-primary-700">
                      {formatCurrency(vehicle.dailyRate)}/day
                    </span>
                  </div>
                  <p className="text-xs text-gray-500 mt-1">Provider: {vehicle.providerName}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Selection Summary */}
      {selectedVehicle && (
        <div className="card bg-primary-50 border-primary-200">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <h3 className="font-semibold text-gray-900 mb-1">Selected Vehicle</h3>
              <p className="text-sm text-gray-700">
                {selectedVehicle.year} {selectedVehicle.make} {selectedVehicle.model}
              </p>
              <p className="text-sm text-gray-600 mt-2">
                {rentalDays} days × {formatCurrency(selectedVehicle.dailyRate)}/day
              </p>
              <p className="text-lg font-bold text-primary-700 mt-1">
                Estimated Total: {formatCurrency(totalCost)}
              </p>
              <p className="text-xs text-gray-500 mt-2">
                Final charges may vary based on actual usage. Insurance may cover part or all of the rental cost.
              </p>
            </div>
            <button
              onClick={() => reserveMutation.mutate(selectedVehicleId!)}
              disabled={reserveMutation.isPending}
              className="btn-primary flex items-center gap-2 shrink-0"
            >
              {reserveMutation.isPending ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" />
                  Reserving...
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-4 h-4" />
                  Reserve Vehicle
                </>
              )}
            </button>
          </div>
        </div>
      )}

      {/* Info Banner */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <div className="flex gap-3">
          <CheckCircle2 className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />
          <div className="text-sm text-blue-900">
            <p className="font-semibold mb-1">Rental Vehicle Benefits</p>
            <ul className="space-y-1 text-blue-800">
              <li>• Get mobile while your vehicle is being repaired</li>
              <li>• May be covered under your insurance policy</li>
              <li>• Flexible duration - extend or return early</li>
              <li>• Full insurance coverage included</li>
            </ul>
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex gap-3">
        <button
          onClick={skipRental}
          className="btn-secondary flex-1"
        >
          Skip - I Don't Need a Rental
        </button>
      </div>
    </div>
  )
}
