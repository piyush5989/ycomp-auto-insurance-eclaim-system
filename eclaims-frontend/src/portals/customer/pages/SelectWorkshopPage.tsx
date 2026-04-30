import React, { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'
import { Search, MapPin, Phone, Star, ArrowLeft, CheckCircle2, Building2, AlertTriangle } from 'lucide-react'

interface WorkshopOption {
  id: string
  name: string
  address: string
  city: string
  zipCode: string
  phone: string
  email: string
  rating: number
  active: boolean
  providerType: string
}

export default function SelectWorkshopPage() {
  const { claimId } = useParams<{ claimId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: claim, isLoading: claimLoading } = useClaimDetails(claimId)
  
  const [searchZip, setSearchZip] = useState('')
  const [searchCity, setSearchCity] = useState('')
  const [selectedWorkshopId, setSelectedWorkshopId] = useState<string | null>(null)

  const { data: workshops = [], isLoading: workshopsLoading } = useQuery({
    queryKey: ['workshops-for-claim', searchZip, searchCity],
    queryFn: () => {
      const params = new URLSearchParams()
      if (searchZip.trim()) params.append('zip', searchZip.trim())
      if (searchCity.trim()) params.append('location', searchCity.trim())
      params.append('providerType', 'REPAIR_WORKSHOP')
      return httpClient.get(`/workshops?${params.toString()}`).then((r) => r.data.data ?? [])
    },
    enabled: !!(searchZip.trim() || searchCity.trim()),
    staleTime: 5 * 60 * 1000,
  })

  const selectWorkshopMutation = useMutation({
    mutationFn: (workshopId: string) =>
      httpClient.post(`/claims/${claimId}/select-workshop`, { workshopId }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      navigate(`/customer/claims/${claimId}/vehicle-dropoff`)
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

  const selectedWorkshop = workshops.find((w: WorkshopOption) => w.id === selectedWorkshopId)

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Link to={`/customer/claims/${claimId}`} className="text-gray-400 hover:text-gray-700">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Select Repair Workshop</h1>
          <p className="text-sm text-gray-500 mt-1">
            Claim {claim.claimId.substring(0, 8).toUpperCase()}
          </p>
        </div>
      </div>

      {/* Step Indicator */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-8 h-8 rounded-full bg-blue-600 text-white font-semibold text-sm">
            1
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">Step 1: Choose Workshop</h3>
            <p className="text-sm text-gray-600">Select where you want your vehicle repaired</p>
          </div>
        </div>
      </div>

      {/* Search Section */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Search Partner Workshops</h2>
        <div className="flex flex-col sm:flex-row gap-3 mb-6">
          <div className="relative flex-1">
            <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              value={searchZip}
              onChange={(e) => setSearchZip(e.target.value)}
              placeholder="Enter ZIP code (e.g., 02101)"
              className="input pl-10 w-full"
              aria-label="Search by ZIP code"
            />
          </div>
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              value={searchCity}
              onChange={(e) => setSearchCity(e.target.value)}
              placeholder="Or search by city (e.g., Boston)"
              className="input pl-10 w-full"
              aria-label="Search by city"
            />
          </div>
        </div>

        {workshopsLoading ? (
          <div className="flex justify-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
          </div>
        ) : !searchZip.trim() && !searchCity.trim() ? (
          <div className="text-center py-12 text-gray-400 border border-dashed border-gray-200 rounded-lg">
            <Building2 className="w-12 h-12 mx-auto mb-3 opacity-40" />
            <p>Enter a ZIP code or city name to find workshops near you</p>
          </div>
        ) : workshops.length === 0 ? (
          <div className="text-center py-12 text-gray-400 border border-dashed border-gray-200 rounded-lg">
            <Building2 className="w-12 h-12 mx-auto mb-3 opacity-40" />
            <p>No workshops found in this area</p>
            <p className="text-sm mt-2">Try searching with a different location</p>
          </div>
        ) : (
          <div className="space-y-3">
            {workshops.map((workshop: WorkshopOption) => (
              <div
                key={workshop.id}
                onClick={() => setSelectedWorkshopId(workshop.id)}
                className={`border rounded-lg p-4 cursor-pointer transition-all ${
                  selectedWorkshopId === workshop.id
                    ? 'border-primary-600 bg-primary-50 shadow-sm'
                    : 'border-gray-200 hover:border-primary-300 hover:shadow-sm'
                }`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <h3 className="font-semibold text-gray-900">{workshop.name}</h3>
                      <span className="inline-flex items-center gap-1 text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full">
                        <CheckCircle2 className="w-3 h-3" />
                        Partner
                      </span>
                      <div className="flex items-center gap-1 text-amber-500 text-xs">
                        <Star className="w-3 h-3 fill-current" />
                        <span>{workshop.rating.toFixed(1)}</span>
                      </div>
                    </div>
                    <div className="space-y-1 text-sm text-gray-600">
                      <div className="flex items-center gap-2">
                        <MapPin className="w-4 h-4 shrink-0" />
                        <span>
                          {workshop.address}, {workshop.city} {workshop.zipCode}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Phone className="w-4 h-4 shrink-0" />
                        <span>{workshop.phone}</span>
                      </div>
                    </div>
                  </div>
                  <input
                    type="radio"
                    name="workshop-selection"
                    checked={selectedWorkshopId === workshop.id}
                    onChange={() => setSelectedWorkshopId(workshop.id)}
                    onClick={(e) => e.stopPropagation()}
                    className="mt-1 w-5 h-5 text-primary-600"
                    aria-label={`Select ${workshop.name}`}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* External Workshop Option */}
      <div className="card bg-gray-50">
        <h3 className="font-semibold text-gray-900 mb-2">Can't find your preferred workshop?</h3>
        <p className="text-sm text-gray-600 mb-3">
          You can also choose an external workshop not listed here. Please contact our support team
          to register your preferred workshop.
        </p>
        <button className="text-sm text-primary-700 hover:text-primary-900 font-medium">
          Contact Support
        </button>
      </div>

      {/* Action Buttons */}
      {selectedWorkshop && (
        <div className="card bg-primary-50 border-primary-200">
          <div className="flex items-start justify-between">
            <div>
              <h3 className="font-semibold text-gray-900 mb-1">Selected Workshop</h3>
              <p className="text-sm text-gray-700">{selectedWorkshop.name}</p>
              <p className="text-xs text-gray-500 mt-1">
                {selectedWorkshop.address}, {selectedWorkshop.city} {selectedWorkshop.zipCode}
              </p>
            </div>
            <button
              onClick={() => selectWorkshopMutation.mutate(selectedWorkshopId!)}
              disabled={selectWorkshopMutation.isPending}
              className="btn-primary flex items-center gap-2"
            >
              {selectWorkshopMutation.isPending ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" />
                  Confirming...
                </>
              ) : (
                <>
                  Confirm Workshop
                  <ArrowLeft className="w-4 h-4 rotate-180" />
                </>
              )}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
