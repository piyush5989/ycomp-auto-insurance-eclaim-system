import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { Search, Star, MapPin, Phone } from 'lucide-react';

interface Workshop {
  id: string;
  name: string;
  address: string;
  city: string;
  zipCode: string;
  phone: string;
  email: string;
  rating: number;
  active: boolean;
}

export default function WorkshopSearchPage() {
  const [search, setSearch] = useState('');

  const { data: workshops = [], isLoading } = useQuery({
    queryKey: ['workshops', search],
    queryFn: () =>
      httpClient.get(`/workshops${search ? `?location=${encodeURIComponent(search)}` : ''}`).then((r) => r.data.data ?? []),
    staleTime: 30 * 60 * 1000,  // 30 min — workshop data changes infrequently
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Find a Workshop</h1>
        <p className="text-gray-500 mt-1">Search for approved repair workshops near you.</p>
      </div>

      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by city…"
          className="input pl-10"
        />
      </div>

      {isLoading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {(workshops as Workshop[]).map((workshop) => (
            <div key={workshop.id} className="card hover:shadow-md transition-shadow">
              <div className="flex justify-between items-start mb-3">
                <h3 className="font-semibold text-gray-900">{workshop.name}</h3>
                <div className="flex items-center gap-1 text-amber-500">
                  <Star className="w-3.5 h-3.5 fill-current" />
                  <span className="text-sm font-medium">{workshop.rating.toFixed(1)}</span>
                </div>
              </div>
              <div className="space-y-1.5 text-sm text-gray-500">
                <div className="flex items-center gap-2"><MapPin className="w-3.5 h-3.5" />{workshop.address}, {workshop.city}</div>
                <div className="flex items-center gap-2"><Phone className="w-3.5 h-3.5" />{workshop.phone}</div>
              </div>
            </div>
          ))}
          {workshops.length === 0 && (
            <div className="col-span-2 text-center py-12 text-gray-500">
              No workshops found{search ? ` in "${search}"` : ''}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
