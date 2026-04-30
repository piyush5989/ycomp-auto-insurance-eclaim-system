import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { Search, Star, MapPin, Phone, Wrench, Settings, Car } from 'lucide-react'

interface Workshop {
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

type ProviderType = 'REPAIR_WORKSHOP' | 'AUTH_SERVICE_STATION' | 'CAR_RENTAL'

const PROVIDER_TABS: { type: ProviderType; label: string; icon: React.ReactNode; description: string }[] = [
  {
    type: 'REPAIR_WORKSHOP',
    label: 'Repair Workshop',
    icon: <Wrench className="w-4 h-4" />,
    description: 'Authorised vehicle repair centres',
  },
  {
    type: 'AUTH_SERVICE_STATION',
    label: 'Service Station',
    icon: <Settings className="w-4 h-4" />,
    description: 'Authorised service & maintenance',
  },
  {
    type: 'CAR_RENTAL',
    label: 'Car Rental',
    icon: <Car className="w-4 h-4" />,
    description: 'Partner vehicle rental providers',
  },
]

const buildSearchUrl = (city: string, zip: string, providerType: ProviderType) => {
  const params = new URLSearchParams()
  if (city.trim()) params.append('location', city.trim())
  if (zip.trim()) params.append('zip', zip.trim())
  params.append('providerType', providerType)
  return `/workshops?${params.toString()}`
}

export default function WorkshopSearchPage() {
  const [city, setCity] = useState('')
  const [zip, setZip] = useState('')
  const [activeTab, setActiveTab] = useState<ProviderType>('REPAIR_WORKSHOP')

  const searchUrl = buildSearchUrl(city, zip, activeTab)

  const { data: workshops = [], isLoading } = useQuery({
    queryKey: ['workshops', city, zip, activeTab],
    queryFn: () =>
      httpClient.get(searchUrl).then((r) => (r.data.data ?? []) as Workshop[]),
    staleTime: 30 * 60 * 1000,
  })

  const activeTabConfig = PROVIDER_TABS.find((t) => t.type === activeTab)!

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Find Partner Service Providers</h1>
        <p className="text-gray-500 mt-1">
          Search for approved repair workshops, service stations, and car rental partners near you.
        </p>
      </div>

      {/* Provider Type Tabs */}
      <div className="flex gap-2 border-b border-gray-200">
        {PROVIDER_TABS.map((tab) => (
          <button
            key={tab.type}
            onClick={() => setActiveTab(tab.type)}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab.type
                ? 'border-primary-700 text-primary-700'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            }`}
            aria-label={tab.label}
            tabIndex={0}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      <p className="text-sm text-gray-500 -mt-2">{activeTabConfig.description}</p>

      {/* Search inputs */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            value={city}
            onChange={(e) => setCity(e.target.value)}
            placeholder="Search by city…"
            className="input-field pl-10 w-full"
            aria-label="Search by city"
          />
        </div>
        <div className="relative sm:w-40">
          <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            value={zip}
            onChange={(e) => setZip(e.target.value)}
            placeholder="Zip code"
            className="input-field pl-10 w-full"
            aria-label="Filter by zip code"
          />
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {workshops.map((workshop) => (
            <div
              key={workshop.id}
              className="card hover:shadow-md transition-shadow"
            >
              <div className="flex justify-between items-start mb-3">
                <h3 className="font-semibold text-gray-900">{workshop.name}</h3>
                <div className="flex items-center gap-1 text-amber-500 shrink-0">
                  <Star className="w-3.5 h-3.5 fill-current" />
                  <span className="text-sm font-medium">{workshop.rating.toFixed(1)}</span>
                </div>
              </div>
              <div className="space-y-1.5 text-sm text-gray-500">
                <div className="flex items-center gap-2">
                  <MapPin className="w-3.5 h-3.5 shrink-0" />
                  {workshop.address}, {workshop.city}{workshop.zipCode ? `, ${workshop.zipCode}` : ''}
                </div>
                <div className="flex items-center gap-2">
                  <Phone className="w-3.5 h-3.5 shrink-0" />
                  {workshop.phone}
                </div>
              </div>
            </div>
          ))}
          {workshops.length === 0 && !isLoading && (
            <div className="col-span-2 text-center py-12 text-gray-500 border border-dashed border-gray-200 rounded-xl">
              <span className="block mb-2 text-2xl">🔍</span>
              No {activeTabConfig.label.toLowerCase()} providers found
              {city || zip ? ` near "${city || zip}"` : ''}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
