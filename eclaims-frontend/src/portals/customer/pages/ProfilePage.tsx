import React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { User, MapPin, RefreshCw, CheckCircle } from 'lucide-react'
import { profileApi } from '@/features/profile/api/profileApi'
import type { UpdateAddressRequest, UpdateBillingCycleRequest } from '@/features/profile/api/profileApi.types'

const addressSchema = z.object({
  addressLine1: z.string().min(1, 'Address line 1 is required').max(200),
  addressLine2: z.string().max(200).optional(),
  city: z.string().min(1, 'City is required').max(100),
  state: z.string().max(100).optional(),
  zipCode: z.string().min(1, 'Zip/Postal code is required').max(20),
  country: z.string().max(100).optional(),
})

const billingSchema = z.object({
  billingCycle: z.enum(['MONTHLY', 'QUARTERLY', 'ANNUALLY']),
})

type AddressFormData = z.infer<typeof addressSchema>
type BillingFormData = z.infer<typeof billingSchema>

const BILLING_OPTIONS: { value: UpdateBillingCycleRequest['billingCycle']; label: string; description: string }[] = [
  { value: 'MONTHLY',   label: 'Monthly',   description: 'Billed every month' },
  { value: 'QUARTERLY', label: 'Quarterly', description: 'Billed every 3 months' },
  { value: 'ANNUALLY',  label: 'Annually',  description: 'Billed once per year' },
]

export default function ProfilePage() {
  const queryClient = useQueryClient()

  const { data: profileData, isLoading } = useQuery({
    queryKey: ['customer-profile'],
    queryFn: () => profileApi.getProfile(),
  })

  const profile = profileData?.data

  const {
    register: registerAddress,
    handleSubmit: handleAddressSubmit,
    formState: { errors: addressErrors, isSubmitSuccessful: addressSaved, isSubmitting: addressSaving },
    reset: resetAddress,
  } = useForm<AddressFormData>({
    resolver: zodResolver(addressSchema),
    values: profile
      ? {
          addressLine1: profile.addressLine1 ?? '',
          addressLine2: profile.addressLine2 ?? '',
          city: profile.city ?? '',
          state: profile.state ?? '',
          zipCode: profile.zipCode ?? '',
          country: profile.country ?? 'US',
        }
      : undefined,
  })

  const {
    register: registerBilling,
    handleSubmit: handleBillingSubmit,
    watch: watchBilling,
    formState: { isSubmitSuccessful: billingSaved, isSubmitting: billingSaving },
  } = useForm<BillingFormData>({
    resolver: zodResolver(billingSchema),
    values: profile ? { billingCycle: profile.billingCycle } : undefined,
  })

  const selectedCycle = watchBilling('billingCycle')

  const addressMutation = useMutation({
    mutationFn: (data: UpdateAddressRequest) => profileApi.updateAddress(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['customer-profile'] }),
  })

  const billingMutation = useMutation({
    mutationFn: (data: UpdateBillingCycleRequest) => profileApi.updateBillingCycle(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['customer-profile'] }),
  })

  const handleAddressSave = (data: AddressFormData) => {
    addressMutation.mutate({
      addressLine1: data.addressLine1,
      addressLine2: data.addressLine2,
      city: data.city,
      state: data.state,
      zipCode: data.zipCode,
      country: data.country,
    })
  }

  const handleBillingSave = (data: BillingFormData) => {
    billingMutation.mutate({ billingCycle: data.billingCycle })
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="w-6 h-6 text-primary-700 animate-spin" />
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center gap-3">
        <User className="w-6 h-6 text-primary-700" />
        <h1 className="text-2xl font-bold text-gray-900">My Profile</h1>
      </div>

      {/* Correspondence Address */}
      <section className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex items-center gap-2 mb-6">
          <MapPin className="w-5 h-5 text-primary-700" />
          <h2 className="text-lg font-semibold text-gray-900">Correspondence Address</h2>
        </div>

        <form onSubmit={handleAddressSubmit(handleAddressSave)} noValidate className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="sm:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Address Line 1 *</label>
              <input {...registerAddress('addressLine1')} className="input-field w-full" aria-label="Address line 1" />
              {addressErrors.addressLine1 && (
                <p className="text-red-500 text-xs mt-1">{addressErrors.addressLine1.message}</p>
              )}
            </div>
            <div className="sm:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Address Line 2</label>
              <input {...registerAddress('addressLine2')} className="input-field w-full" aria-label="Address line 2" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">City *</label>
              <input {...registerAddress('city')} className="input-field w-full" aria-label="City" />
              {addressErrors.city && (
                <p className="text-red-500 text-xs mt-1">{addressErrors.city.message}</p>
              )}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">State / Province</label>
              <input {...registerAddress('state')} className="input-field w-full" aria-label="State or province" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Zip / Postal Code *</label>
              <input {...registerAddress('zipCode')} className="input-field w-full" aria-label="Zip or postal code" />
              {addressErrors.zipCode && (
                <p className="text-red-500 text-xs mt-1">{addressErrors.zipCode.message}</p>
              )}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Country</label>
              <input {...registerAddress('country')} className="input-field w-full" placeholder="US" aria-label="Country" />
            </div>
          </div>

          <div className="flex items-center gap-3 pt-2">
            <button
              type="submit"
              disabled={addressSaving || addressMutation.isPending}
              className="btn-primary"
            >
              {addressSaving || addressMutation.isPending ? 'Saving...' : 'Save Address'}
            </button>
            {addressMutation.isSuccess && (
              <span className="flex items-center gap-1 text-green-600 text-sm">
                <CheckCircle className="w-4 h-4" /> Address saved
              </span>
            )}
            {addressMutation.isError && (
              <span className="text-red-500 text-sm">Failed to save. Please try again.</span>
            )}
          </div>
        </form>
      </section>

      {/* Billing Cycle */}
      <section className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex items-center gap-2 mb-6">
          <RefreshCw className="w-5 h-5 text-primary-700" />
          <h2 className="text-lg font-semibold text-gray-900">Billing Cycle</h2>
        </div>

        <form onSubmit={handleBillingSubmit(handleBillingSave)} noValidate className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {BILLING_OPTIONS.map((option) => (
              <label
                key={option.value}
                className={`relative flex flex-col gap-1 border rounded-xl p-4 cursor-pointer transition-colors ${
                  selectedCycle === option.value
                    ? 'border-primary-600 bg-primary-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
                aria-label={option.label}
              >
                <input
                  type="radio"
                  value={option.value}
                  {...registerBilling('billingCycle')}
                  className="sr-only"
                />
                <span className="font-semibold text-gray-900">{option.label}</span>
                <span className="text-xs text-gray-500">{option.description}</span>
                {selectedCycle === option.value && (
                  <CheckCircle className="absolute top-3 right-3 w-4 h-4 text-primary-600" />
                )}
              </label>
            ))}
          </div>

          <div className="flex items-center gap-3 pt-2">
            <button
              type="submit"
              disabled={billingSaving || billingMutation.isPending}
              className="btn-primary"
            >
              {billingSaving || billingMutation.isPending ? 'Saving...' : 'Save Billing Cycle'}
            </button>
            {billingMutation.isSuccess && (
              <span className="flex items-center gap-1 text-green-600 text-sm">
                <CheckCircle className="w-4 h-4" /> Billing cycle updated
              </span>
            )}
            {billingMutation.isError && (
              <span className="text-red-500 text-sm">Failed to save. Please try again.</span>
            )}
          </div>
        </form>
      </section>
    </div>
  )
}
