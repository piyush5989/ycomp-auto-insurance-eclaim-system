import React, { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'
import { ArrowLeft, Car, CheckCircle2, Camera, AlertTriangle, Gauge, Droplet } from 'lucide-react'
import { format } from 'date-fns'

const FUEL_LEVELS = [
  { value: 'FULL', label: 'Full' },
  { value: 'THREE_QUARTERS', label: '3/4' },
  { value: 'HALF', label: '1/2' },
  { value: 'QUARTER', label: '1/4' },
  { value: 'EMPTY', label: 'Empty' },
]

export default function VehicleDropOffPage() {
  const { claimId } = useParams<{ claimId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: claim, isLoading: claimLoading } = useClaimDetails(claimId)
  
  const [mileage, setMileage] = useState('')
  const [fuelLevel, setFuelLevel] = useState('HALF')
  const [dropOffNotes, setDropOffNotes] = useState('')
  const [photosUploaded, setPhotosUploaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const dropOffMutation = useMutation({
    mutationFn: () =>
      httpClient.post(`/claims/${claimId}/vehicle-dropoff`, {
        mileage: mileage ? parseInt(mileage) : undefined,
        fuelLevel,
        dropOffNotes: dropOffNotes.trim() || undefined,
        photosUploaded,
      }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      navigate(`/customer/claims/${claimId}/rental-vehicle`)
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || 'Failed to confirm drop-off. Please try again.')
    },
  })

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

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (mileage && (parseInt(mileage) < 0 || parseInt(mileage) > 999999)) {
      setError('Please enter a valid mileage (0-999,999)')
      return
    }

    dropOffMutation.mutate()
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Link to={`/customer/claims/${claimId}`} className="text-gray-400 hover:text-gray-700">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Confirm Vehicle Drop-Off</h1>
          <p className="text-sm text-gray-500 mt-1">
            Claim {claim.claimId.substring(0, 8).toUpperCase()}
          </p>
        </div>
      </div>

      {/* Step Indicator */}
      <div className="bg-green-50 border border-green-200 rounded-lg p-4">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-8 h-8 rounded-full bg-green-600 text-white font-semibold text-sm">
            2
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">Step 2: Drop Off Vehicle</h3>
            <p className="text-sm text-gray-600">Confirm your vehicle has been delivered to the workshop</p>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Vehicle Condition */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Car className="w-5 h-5 text-primary-700" />
            <h2 className="text-lg font-semibold text-gray-900">Vehicle Condition</h2>
          </div>

          <div className="space-y-4">
            {/* Mileage */}
            <div>
              <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
                <Gauge className="w-4 h-4" />
                Current Mileage (Optional)
              </label>
              <input
                type="number"
                value={mileage}
                onChange={(e) => setMileage(e.target.value)}
                placeholder="e.g., 45000"
                min="0"
                max="999999"
                className="input w-full"
              />
              <p className="text-xs text-gray-500 mt-1">Enter the current odometer reading</p>
            </div>

            {/* Fuel Level */}
            <div>
              <label className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
                <Droplet className="w-4 h-4" />
                Fuel Level
              </label>
              <div className="grid grid-cols-5 gap-2">
                {FUEL_LEVELS.map((level) => (
                  <button
                    key={level.value}
                    type="button"
                    onClick={() => setFuelLevel(level.value)}
                    className={`px-3 py-2 text-sm font-medium rounded-lg border transition-all ${
                      fuelLevel === level.value
                        ? 'bg-primary-600 text-white border-primary-600'
                        : 'bg-white text-gray-700 border-gray-300 hover:border-primary-400'
                    }`}
                  >
                    {level.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Photos Uploaded */}
            <div className="flex items-start gap-3">
              <input
                type="checkbox"
                id="photosUploaded"
                checked={photosUploaded}
                onChange={(e) => setPhotosUploaded(e.target.checked)}
                className="mt-1 w-4 h-4 text-primary-600 rounded"
              />
              <div className="flex-1">
                <label htmlFor="photosUploaded" className="flex items-center gap-2 text-sm font-medium text-gray-700 cursor-pointer">
                  <Camera className="w-4 h-4" />
                  I have taken photos of the vehicle
                </label>
                <p className="text-xs text-gray-500 mt-1">
                  It's recommended to take photos of all sides, dashboard, and any existing damage
                </p>
              </div>
            </div>

            {/* Additional Notes */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Additional Notes (Optional)
              </label>
              <textarea
                value={dropOffNotes}
                onChange={(e) => setDropOffNotes(e.target.value)}
                rows={4}
                maxLength={500}
                placeholder="Any additional information about the vehicle condition, location of keys, etc."
                className="input w-full resize-none"
              />
              <p className="text-xs text-gray-500 mt-1">{dropOffNotes.length}/500 characters</p>
            </div>
          </div>
        </div>

        {/* Info Banner */}
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <div className="flex gap-3">
            <CheckCircle2 className="w-5 h-5 text-blue-600 shrink-0 mt-0.5" />
            <div className="text-sm text-blue-900">
              <p className="font-semibold mb-1">What happens next?</p>
              <ul className="space-y-1 text-blue-800">
                <li>• A surveyor will be automatically assigned to inspect your vehicle</li>
                <li>• You'll receive a notification once the inspection is scheduled</li>
                <li>• The workshop will contact you about repair timeline</li>
              </ul>
            </div>
          </div>
        </div>

        {/* Error Message */}
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-red-600 shrink-0" />
            <p className="text-sm text-red-800">{error}</p>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex gap-3">
          <Link
            to={`/customer/claims/${claimId}`}
            className="btn-secondary flex-1"
          >
            Back to Claim
          </Link>
          <button
            type="submit"
            disabled={dropOffMutation.isPending}
            className="btn-primary flex-1 flex items-center justify-center gap-2"
          >
            {dropOffMutation.isPending ? (
              <>
                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" />
                Confirming...
              </>
            ) : (
              <>
                Confirm Drop-Off
                <ArrowLeft className="w-4 h-4 rotate-180" />
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  )
}
