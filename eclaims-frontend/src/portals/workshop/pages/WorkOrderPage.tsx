import React from 'react'
import { useMutation } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { httpClient } from '@/shared/api/httpClient'
import { CheckCircle, Building2, Upload, X } from 'lucide-react'
import { useMyWorkshop } from '@/features/workshops/hooks/useMyWorkshop'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'

export default function WorkOrderPage() {
  const { data: profile, isLoading: profileLoading } = useMyWorkshop()
  const [searchParams] = useSearchParams()

  const [form, setForm] = React.useState({
    claimId: searchParams.get('claimId') ?? '',
    estimatedCost: '',
    workDescription: '',
    estimatedCompletionDate: '',
  })

  const [mediaFiles, setMediaFiles] = React.useState<File[]>([])
  const fileInputRef = React.useRef<HTMLInputElement>(null)

  const claimIdForLookup = form.claimId.trim() || undefined
  const hasClaimId = !!claimIdForLookup
  const {
    data: linkedClaim,
    isSuccess: claimLoaded,
    isPending: claimPending,
    isError: claimLookupError,
  } = useClaimDetails(claimIdForLookup)
  const isRejectedClaim = claimLoaded && linkedClaim?.status === 'REJECTED'
  const isApprovedForWorkOrder = claimLoaded && linkedClaim?.status === 'APPROVED'
  const isWrongStatusForWorkOrder =
    claimLoaded && linkedClaim && !isApprovedForWorkOrder && !isRejectedClaim

  const submitWorkOrder = useMutation({
    mutationFn: async () => {
      // First create the work order
      const workOrderResponse = await httpClient
        .post('/work-orders', {
          claimId: form.claimId,
          workshopId: profile?.id,
          estimatedCost: parseFloat(form.estimatedCost),
          workDescription: form.workDescription,
          estimatedCompletionDate: form.estimatedCompletionDate || null,
        })
        .then((r) => r.data)

      // Then upload media files if any
      const workOrderId = workOrderResponse.data.workOrderId
      for (const file of mediaFiles) {
        const formData = new FormData()
        formData.append('file', file)
        const docType = file.type.startsWith('video/') 
          ? 'WORKSHOP_PROGRESS_VIDEO' 
          : 'WORKSHOP_PROGRESS_PHOTO'
        formData.append('documentType', docType)
        
        await httpClient.post(`/work-orders/${workOrderId}/upload-media`, formData)
      }

      return workOrderResponse
    },
  })

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const validFiles = Array.from(e.target.files).filter(file => {
        const isImage = file.type.startsWith('image/')
        const isVideo = file.type.startsWith('video/')
        const isValidSize = file.size <= 50 * 1024 * 1024 // 50MB limit
        return (isImage || isVideo) && isValidSize
      })
      setMediaFiles(prev => [...prev, ...validFiles])
    }
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const removeFile = (index: number) => {
    setMediaFiles(prev => prev.filter((_, i) => i !== index))
  }

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

      {isRejectedClaim && (
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-800">
          This claim was rejected. You cannot create a work order for it.
        </div>
      )}

      {isWrongStatusForWorkOrder && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-sm text-amber-900">
          This claim is not approved yet (current status:{' '}
          <strong>{linkedClaim?.status}</strong>). Work orders can only be submitted after an adjustor
          sets the claim to <strong>APPROVED</strong>.
        </div>
      )}

      {hasClaimId && claimLookupError && (
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-800">
          Could not load this claim. Check the claim ID or your access, then try again.
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

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Progress Photos/Videos
          </label>
          <p className="text-xs text-gray-500 mb-2">
            Upload before/after photos or progress videos (Max 50MB per file)
          </p>
          
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/*,video/*"
            onChange={handleFileSelect}
            className="sr-only"
          />
          
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50"
          >
            <Upload className="w-4 h-4" />
            Select Files
          </button>

          {mediaFiles.length > 0 && (
            <div className="mt-3 space-y-2">
              {mediaFiles.map((file, index) => (
                <div key={index} className="flex items-center justify-between bg-gray-50 rounded-lg px-3 py-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded">
                      {file.type.startsWith('video/') ? 'VIDEO' : 'PHOTO'}
                    </span>
                    <span className="text-sm text-gray-700">{file.name}</span>
                    <span className="text-xs text-gray-500">
                      ({(file.size / (1024 * 1024)).toFixed(1)} MB)
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                    className="text-red-400 hover:text-red-600"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {submitWorkOrder.isError && (
          <p className="text-sm text-red-600">
            Failed to submit work order. Please check the claim ID and try again.
          </p>
        )}

        <button
          onClick={() => submitWorkOrder.mutate()}
          disabled={
            !form.claimId ||
            !form.estimatedCost ||
            !isReady ||
            submitWorkOrder.isPending ||
            isRejectedClaim ||
            (hasClaimId && claimPending) ||
            (hasClaimId && claimLookupError) ||
            (hasClaimId && claimLoaded && !isApprovedForWorkOrder)
          }
          className="btn-primary w-full justify-center"
        >
          {submitWorkOrder.isPending ? 'Submitting...' : 'Submit Work Order'}
        </button>
      </div>
    </div>
  )
}
