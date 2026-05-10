import React, { useState, useEffect } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { Upload, X } from 'lucide-react'
import { setWorkshopRepairFlash } from '@/portals/workshop/workshopRepairFlash'
import { workshopsApi } from '@/features/workshops/api/workshopsApi'

const REPAIR_STATUSES = ['PENDING', 'IN_PROGRESS', 'PARTS_ORDERED', 'COMPLETED']

export default function RepairUpdatePage() {
  const { id: workOrderId } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [status, setStatus] = useState('IN_PROGRESS')
  const [note, setNote] = useState('')
  const [finalCost, setFinalCost] = useState(searchParams.get('finalCost') ?? '')
  const [estimatedCompletionDate, setEstimatedCompletionDate] = useState('')
  const [mediaFiles, setMediaFiles] = useState<File[]>([])
  const fileInputRef = React.useRef<HTMLInputElement>(null)

  const [isFormHydrated, setIsFormHydrated] = useState(false)

  // Fetch current work order to pre-populate status/date/final cost
  const { data: currentWorkOrder } = useQuery({
    queryKey: ['work-order', workOrderId],
    queryFn: async () => {
      const response = await workshopsApi.getMyWorkOrders()
      const workOrders = response.data ?? []
      return workOrders.find((wo: { workOrderId: string }) => wo.workOrderId === workOrderId) ?? null
    },
    enabled: !!workOrderId,
  })

  // Pre-populate form with persisted values exactly once
  useEffect(() => {
    if (!currentWorkOrder || isFormHydrated) return

    if (currentWorkOrder.repairStatus) {
      setStatus(currentWorkOrder.repairStatus)
    }

    if (currentWorkOrder.estimatedCompletionDate) {
      setEstimatedCompletionDate(String(currentWorkOrder.estimatedCompletionDate).split('T')[0])
    }

    if (currentWorkOrder.finalCost != null && !searchParams.get('finalCost')) {
      setFinalCost(String(currentWorkOrder.finalCost))
    }

    setIsFormHydrated(true)
  }, [currentWorkOrder, isFormHydrated, searchParams])

  const updateRepair = useMutation({
    mutationFn: async () => {
      // First update the repair status
      const params: Record<string, string> = { status }
      if (note) params.note = note
      if (finalCost) params.finalCost = finalCost
      if (estimatedCompletionDate) params.estimatedCompletionDate = estimatedCompletionDate
      
      const response = await httpClient
        .patch(`/work-orders/${workOrderId}/repair-status`, null, { params })
        .then((r) => r.data)

      // Then upload progress media if any
      for (const file of mediaFiles) {
        const formData = new FormData()
        formData.append('file', file)
        let docType = 'WORKSHOP_PROGRESS_PHOTO'
        if (file.type.startsWith('video/')) {
          docType = 'WORKSHOP_PROGRESS_VIDEO'
        } else if (status === 'COMPLETED') {
          docType = 'WORKSHOP_AFTER_PHOTO'
        }
        formData.append('documentType', docType)
        
        await httpClient.post(`/work-orders/${workOrderId}/upload-media`, formData)
      }

      return response
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['workshop', 'my-work-orders'] })
      setWorkshopRepairFlash()
      navigate('/workshop/work-orders')
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

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Progress Photos/Videos
          </label>
          <p className="text-xs text-gray-500 mb-2">
            {status === 'COMPLETED' 
              ? 'Upload final completion photos/videos' 
              : 'Upload current repair progress media'} (Max 50MB per file)
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
