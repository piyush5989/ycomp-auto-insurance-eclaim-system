import React, { useRef, useState as useLocalState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'
import { StatusBadge } from '@/shared/components/ui/Badge'
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel'
import { formatCurrency } from '@/shared/utils/formatCurrency'
import { format } from 'date-fns'
import {
  ArrowLeft, AlertTriangle, Paperclip, Upload, Wrench,
  Calendar, FileText, CheckCircle2, Clock, Trash2,
  Pencil, MessageSquarePlus, MessageSquare, Save, X, XCircle,
  Building2, Car, KeyRound, CreditCard
} from 'lucide-react'
import { documentsApi } from '@/features/documents/api/documentsApi'
import type { DocumentType } from '@/features/documents/api/documentsApi.types'
import { claimsApi } from '@/features/claims/api/claimsApi'
import { workshopsApi } from '@/features/workshops/api/workshopsApi'
import { httpClient } from '@/shared/api/httpClient'
import { downloadClaimReceiptPdf } from '@/shared/api/paymentReceiptApi'
import type { ApiResponse } from '@/shared/types/ApiResponse'
import imageCompression from 'browser-image-compression'

interface WorkOrderResponse {
  workOrderId: string
  claimId: string
  workshopId: string
  workshopName?: string
  repairStatus: string
  estimatedCompletionDate?: string
  workDescription?: string
  estimatedCost?: number
  finalCost?: number
  createdAt: string
  updatedAt: string
}

const REPAIR_STATUS_COLOR: Record<string, string> = {
  PENDING:     'bg-yellow-100 text-yellow-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  COMPLETED:   'bg-green-100 text-green-800',
  ON_HOLD:     'bg-orange-100 text-orange-800',
  CANCELLED:   'bg-red-100 text-red-800',
}

const DOCUMENT_TYPES: { value: DocumentType; label: string }[] = [
  { value: 'PHOTO_DAMAGE',    label: 'Photo of Damage' },
  { value: 'POLICE_REPORT',   label: 'Police Report' },
  { value: 'REPAIR_ESTIMATE', label: 'Repair Estimate' },
  { value: 'MEDICAL_REPORT',  label: 'Medical Report' },
  { value: 'INVOICE',         label: 'Invoice' },
  { value: 'OTHER',           label: 'Other' },
]

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024       // 5 MB hard limit
const WARN_FILE_SIZE_BYTES = 3 * 1024 * 1024       // 3 MB — show compression tip
const ALLOWED_MIME_TYPES = new Set([
  'image/jpeg', 'image/png', 'image/webp',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
])

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const compressImageIfNeeded = async (file: File): Promise<File> => {
  const imageTypes = ['image/jpeg', 'image/png', 'image/webp']
  
  if (!imageTypes.includes(file.type)) {
    return file
  }

  const options = {
    maxSizeMB: 5,
    maxWidthOrHeight: 1920,
    useWebWorker: true,
    fileType: 'image/webp',
    initialQuality: 0.82,
  }

  try {
    const compressedFile = await imageCompression(file, options)
    return compressedFile
  } catch (error) {
    console.warn('Image compression failed, using original file:', error)
    return file
  }
}

// Statuses where customer may still edit incident fields directly
const EDITABLE_STATUSES: string[] = ['SUBMITTED']
// Statuses where we show the endorsements/notes section
const ENDORSEMENT_STATUSES: string[] = [
  'ASSIGNED','UNDER_SURVEY','SURVEYED','UNDER_ADJUDICATION',
  'APPROVED','REJECTED','PAYMENT_INITIATED','PAYMENT_PROCESSED','SETTLED',
]

interface StatusHistoryEntry {
  id: string
  status: string
  note?: string
  estimatedCompletionDate?: string
  changedAt: string
}

function RepairProgressHistory({ workOrderId }: { workOrderId: string }) {
  const { data: history = [], isLoading } = useQuery<StatusHistoryEntry[]>({
    queryKey: ['work-order-history', workOrderId],
    queryFn: async () => {
      const response = await httpClient.get(`/work-orders/${workOrderId}/status-history`)
      return response.data.data
    },
    staleTime: 30000,
  })

  if (isLoading) {
    return <div className="text-xs text-gray-400">Loading progress history...</div>
  }

  if (!history || history.length === 0) {
    return null
  }

  return (
    <div className="border-t border-gray-100 pt-4 mt-4">
      <h3 className="text-xs font-medium text-gray-700 mb-3">Progress Timeline</h3>
      <div className="space-y-3">
        {history.map((entry, index) => (
          <div key={entry.id} className="flex gap-3">
            <div className="flex flex-col items-center">
              <div className={`w-2 h-2 rounded-full shrink-0 ${
                entry.status === 'COMPLETED' ? 'bg-green-500' :
                entry.status === 'IN_PROGRESS' ? 'bg-blue-500' :
                'bg-gray-300'
              }`} />
              {index < history.length - 1 && (
                <div className="w-px h-full bg-gray-200 mt-1" />
              )}
            </div>
            <div className="flex-1 pb-3">
              <div className="flex items-start justify-between mb-1">
                <p className="text-sm font-medium text-gray-900 capitalize">
                  {entry.status.replace(/_/g, ' ').toLowerCase()}
                </p>
                <p className="text-xs text-gray-400 shrink-0 ml-2">
                  {format(new Date(entry.changedAt), 'dd MMM, HH:mm')}
                </p>
              </div>
              {entry.note && (
                <p className="text-xs text-gray-600 leading-relaxed">{entry.note}</p>
              )}
              {entry.estimatedCompletionDate && (
                <p className="text-xs text-gray-500 mt-1">
                  Est. completion: {format(new Date(entry.estimatedCompletionDate), 'dd MMM yyyy')}
                </p>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function ClaimDetailPage() {
  const { claimId } = useParams<{ claimId: string }>()
  const { data: claim, isLoading, error } = useClaimDetails(claimId)
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [selectedDocType, setSelectedDocType] = React.useState<DocumentType>('PHOTO_DAMAGE')
  const [uploadError, setUploadError] = React.useState<string | null>(null)
  const [uploadWarning, setUploadWarning] = React.useState<string | null>(null)
  const [isCompressing, setIsCompressing] = React.useState(false)

  // Incident-detail edit state
  const [editingIncident, setEditingIncident] = useLocalState(false)
  const [editLocation, setEditLocation] = useLocalState('')
  const [editDescription, setEditDescription] = useLocalState('')
  const [editSaveError, setEditSaveError] = useLocalState<string | null>(null)

  // Endorsement note state
  const [newNote, setNewNote] = useLocalState('')
  const [noteError, setNoteError] = useLocalState<string | null>(null)

  const { data: documentsData } = useQuery({
    queryKey: ['documents', claimId],
    queryFn: () => documentsApi.listByClaimId(claimId!),
    enabled: !!claimId,
    staleTime: 0,
    select: (r) => r.data ?? [],
  })

  const { data: workOrderData } = useQuery({
    queryKey: ['work-order', claimId],
    queryFn: () =>
      httpClient
        .get<ApiResponse<WorkOrderResponse>>(`/claims/${claimId}/work-order`)
        .then((r) => r.data),
    enabled: !!claimId,
    staleTime: 0,
    retry: false,
    select: (r) => r.data,
  })

  const { data: workshopData } = useQuery({
    queryKey: ['workshop', claim?.workshopId],
    queryFn: () => workshopsApi.getById(claim!.workshopId!).then((r) => r.data),
    enabled: !!claim?.workshopId,
    staleTime: 5 * 60_000,
    retry: false,
  })

  const uploadMutation = useMutation({
    mutationFn: ({ file, docType }: { file: File; docType: DocumentType }) =>
      documentsApi.uploadDocument(claimId!, file, docType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', claimId] })
      setUploadError(null)
    },
    onError: () => setUploadError('Upload failed. Please try again.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (documentId: string) => documentsApi.deleteDocument(documentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents', claimId] }),
  })

  const updateIncidentMutation = useMutation({
    mutationFn: ({ location, description }: { location: string; description: string }) =>
      claimsApi.updateIncidentDetails(claimId!, { incidentLocation: location, description }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      setEditingIncident(false)
      setEditSaveError(null)
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setEditSaveError(msg ?? 'Failed to save. Please try again.')
    },
  })

  const { data: endorsementsData } = useQuery({
    queryKey: ['endorsements', claimId],
    queryFn: () => claimsApi.getEndorsements(claimId!),
    enabled: !!claimId && !!claim && ENDORSEMENT_STATUSES.includes(claim.status),
    staleTime: 0,
    select: (r) => r.data ?? [],
  })

  const addEndorsementMutation = useMutation({
    mutationFn: (note: string) => claimsApi.addEndorsement(claimId!, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['endorsements', claimId] })
      setNewNote('')
      setNoteError(null)
    },
    onError: () => setNoteError('Failed to save note. Please try again.'),
  })

  const downloadReceiptMutation = useMutation({
    mutationFn: () => downloadClaimReceiptPdf(claimId!),
  })


  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploadError(null)
    setUploadWarning(null)

    if (!ALLOWED_MIME_TYPES.has(file.type)) {
      setUploadError('Invalid file type. Allowed: JPEG, PNG, WebP, PDF, DOC, DOCX.')
      e.target.value = ''
      return
    }

    if (file.size > MAX_FILE_SIZE_BYTES) {
      setUploadError(`File is too large (${formatBytes(file.size)}). Maximum allowed size is 5 MB.`)
      e.target.value = ''
      return
    }

    try {
      setIsCompressing(true)
      const originalSize = file.size
      const processedFile = await compressImageIfNeeded(file)
      
      if (processedFile.size < originalSize) {
        const savingsPct = Math.round((1 - processedFile.size / originalSize) * 100)
        setUploadWarning(`Image optimized: ${formatBytes(originalSize)} → ${formatBytes(processedFile.size)} (${savingsPct}% smaller)`)
      } else if (file.size > WARN_FILE_SIZE_BYTES) {
        setUploadWarning(`Large file (${formatBytes(file.size)}). Consider compressing images before upload for faster processing.`)
      }

      uploadMutation.mutate({ file: processedFile, docType: selectedDocType })
    } catch (error) {
      setUploadError('Failed to process file. Please try again.')
      console.error('File processing error:', error)
    } finally {
      setIsCompressing(false)
      e.target.value = ''
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-800" />
      </div>
    )
  }

  if (error || !claim) {
    return (
      <div className="card text-center py-12">
        <AlertTriangle className="w-12 h-12 text-red-400 mx-auto mb-3" />
        <p className="text-gray-700">Claim not found or you don't have access to this claim.</p>
        <Link to="/customer/claims" className="btn-primary mt-4 inline-flex">
          Back to Claims
        </Link>
      </div>
    )
  }

  // Journey steps — statuses that count as each step being done
  const workshopDone = !['SUBMITTED'].includes(claim.status)
  const dropOffDone  = !['SUBMITTED', 'WORKSHOP_SELECTED'].includes(claim.status)
  
  // Rental step is complete if customer reserved or skipped
  const rentalStepComplete = claim.rentalStatus === 'RESERVED' || claim.rentalStatus === 'SKIPPED'
  
  const journeyComplete = rentalStepComplete || ['ASSIGNED', 'UNDER_SURVEY', 'SURVEYED', 'UNDER_ADJUDICATION',
                           'APPROVED', 'REJECTED', 'PAYMENT_INITIATED', 'PAYMENT_PROCESSED', 'SETTLED'].includes(claim.status)

  const needsWorkshopSelection = claim.status === 'SUBMITTED'
  const needsVehicleDropOff    = claim.status === 'WORKSHOP_SELECTED'
  const canSelectRental        = claim.status === 'VEHICLE_AT_WORKSHOP' && !rentalStepComplete

  const journeySteps = [
    {
      step: 1,
      label: 'Select Workshop',
      completed: workshopDone,
      current: needsWorkshopSelection,
      icon: <Building2 className="w-4 h-4" />,
    },
    {
      step: 2,
      label: 'Drop Off Vehicle',
      completed: dropOffDone,
      current: needsVehicleDropOff,
      icon: <Car className="w-4 h-4" />,
    },
    {
      step: 3,
      label: 'Rental (Optional)',
      completed: rentalStepComplete,
      current: canSelectRental,
      icon: <KeyRound className="w-4 h-4" />,
    },
  ]

  // Show the banner during the journey AND once complete (so user sees a success message)
  const showJourneyProgress = needsWorkshopSelection || needsVehicleDropOff || canSelectRental || journeyComplete

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link to="/customer/claims" className="text-gray-400 hover:text-gray-700">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-xl font-bold text-gray-900">Claim Details</h1>
          <p className="text-xs text-gray-400 font-mono">{claim.claimId}</p>
        </div>
        <StatusBadge status={claim.status as ClaimStatus} className="ml-auto" />
      </div>

      {/* Journey Progress Indicator */}
      {showJourneyProgress && (
        <div className="card bg-gradient-to-r from-primary-50 to-blue-50 border-primary-200">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">
            {journeyComplete ? '✅ Claim Setup Complete' : 'Complete Your Claim Journey'}
          </h2>
          <div className="flex items-center justify-between mb-6">
            {journeySteps.map((step, idx) => (
              <React.Fragment key={step.step}>
                <div className="flex flex-col items-center">
                  <div
                    className={`flex items-center justify-center w-10 h-10 rounded-full transition-all ${
                      step.completed
                        ? 'bg-green-600 text-white'
                        : step.current
                        ? 'bg-primary-600 text-white ring-4 ring-primary-100'
                        : 'bg-gray-200 text-gray-500'
                    }`}
                  >
                    {step.completed ? (
                      <CheckCircle2 className="w-5 h-5" />
                    ) : (
                      step.icon
                    )}
                  </div>
                  <p
                    className={`text-xs mt-2 font-medium text-center ${
                      step.current ? 'text-primary-700' : 'text-gray-600'
                    }`}
                  >
                    {step.label}
                  </p>
                </div>
                {idx < journeySteps.length - 1 && (
                  <div
                    className={`flex-1 h-0.5 mx-2 transition-all ${
                      step.completed ? 'bg-green-600' : 'bg-gray-200'
                    }`}
                  />
                )}
              </React.Fragment>
            ))}
          </div>


          {/* Action Buttons */}
          {needsWorkshopSelection && (
            <div className="bg-white rounded-lg p-4 border border-primary-200">
              <p className="text-sm text-gray-700 mb-3">
                <strong>Next Step:</strong> Select a repair workshop where you'll drop off your vehicle for inspection and repairs.
              </p>
              <Link
                to={`/customer/claims/${claim.claimId}/select-workshop`}
                className="btn-primary w-full flex items-center justify-center gap-2"
              >
                <Building2 className="w-4 h-4" />
                Select Repair Workshop
              </Link>
            </div>
          )}

          {needsVehicleDropOff && (
            <div className="bg-white rounded-lg p-4 border border-green-200">
              <p className="text-sm text-gray-700 mb-3">
                <strong>Next Step:</strong> Confirm that you've dropped off your vehicle at the selected workshop. This will trigger the surveyor assignment.
              </p>
              <Link
                to={`/customer/claims/${claim.claimId}/vehicle-dropoff`}
                className="btn-primary w-full flex items-center justify-center gap-2"
              >
                <Car className="w-4 h-4" />
                Confirm Vehicle Drop-Off
              </Link>
            </div>
          )}

          {canSelectRental && (
            <div className="bg-white rounded-lg p-4 border border-purple-200">
              <p className="text-sm text-gray-700 mb-3">
                <strong>Optional:</strong> Need a temporary vehicle while yours is being repaired? Reserve a rental car from our partner providers.
              </p>
              <div className="flex gap-2">
                <Link
                  to={`/customer/claims/${claim.claimId}/rental-vehicle`}
                  className="btn-primary flex-1 flex items-center justify-center gap-2"
                >
                  <KeyRound className="w-4 h-4" />
                  Get Rental Vehicle
                </Link>
                <button
                  onClick={() => {/* Mark as skipped */}}
                  className="btn-secondary px-6"
                >
                  Skip
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Claim Status Messages */}
      <div className="bg-white rounded-lg p-4 border border-gray-200">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-primary-100 rounded-full flex items-center justify-center shrink-0">
            {claim.status === 'APPROVED' ? (
              <CheckCircle2 className="w-5 h-5 text-primary-700" />
            ) : claim.status === 'REJECTED' ? (
              <XCircle className="w-5 h-5 text-red-600" />
            ) : claim.status === 'PAYMENT_PROCESSED' || claim.status === 'SETTLED' ? (
              <CheckCircle2 className="w-5 h-5 text-green-600" />
            ) : (
              <Clock className="w-5 h-5 text-blue-600" />
            )}
          </div>
          <div className="flex-1">
            {claim.status === 'SUBMITTED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Claim Submitted Successfully</p>
                <p className="text-sm text-gray-600 mt-1">
                  Your claim is being reviewed. Please select a repair workshop to proceed.
                </p>
              </div>
            ) : claim.status === 'WORKSHOP_SELECTED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Workshop Selected</p>
                <p className="text-sm text-gray-600 mt-1">
                  Please drop off your vehicle at the selected workshop to trigger surveyor assignment.
                </p>
                {workshopData && (
                  <div className="mt-2 text-sm text-gray-700">
                    <strong>{workshopData.name}</strong> - {workshopData.address}, {workshopData.city}
                  </div>
                )}
              </div>
            ) : claim.status === 'VEHICLE_AT_WORKSHOP' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Vehicle at Workshop</p>
                <p className="text-sm text-gray-600 mt-1">
                  Your vehicle has been dropped off. Assigning surveyor for inspection...
                </p>
                {workshopData && (
                  <div className="mt-2 text-sm text-gray-700">
                    <strong>{workshopData.name}</strong> - {workshopData.address}, {workshopData.city}
                  </div>
                )}
              </div>
            ) : claim.assignedSurveyorId && ['ASSIGNED', 'UNDER_SURVEY'].includes(claim.status) ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Surveyor Assigned</p>
                <p className="text-sm text-gray-600 mt-1">
                  A surveyor has been assigned to inspect your vehicle. Inspection will begin soon.
                </p>
              </div>
            ) : claim.status === 'SURVEYED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Survey Completed</p>
                <p className="text-sm text-gray-600 mt-1">
                  Surveyor has submitted the assessment. Waiting for adjustor input.
                </p>
              </div>
            ) : claim.status === 'UNDER_ADJUDICATION' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Under Review</p>
                <p className="text-sm text-gray-600 mt-1">
                  Your claim is being reviewed by an adjustor for final decision.
                </p>
              </div>
            ) : claim.status === 'APPROVED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Claim Approved!</p>
                <p className="text-sm text-gray-600 mt-1">
                  {workOrderData?.repairStatus === 'COMPLETED' 
                    ? 'Your claim has been approved and repairs are completed. You can now proceed to payment.'
                    : 'Your claim has been approved! Workshop will start repairing and keep you posted on progress.'
                  }
                </p>
                {claim.approvedAmount && (
                  <p className="text-sm text-green-700 font-medium mt-1">
                    Approved Amount: {formatCurrency(claim.approvedAmount)}
                  </p>
                )}
              </div>
            ) : claim.status === 'REJECTED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Claim not approved</p>
                <p className="text-sm text-gray-600 mt-1">
                  We are sorry to inform you that your claim has been rejected.
                </p>
                {claim.rejectionReason ? (
                  <div className="mt-2 text-sm text-gray-800">
                    <span className="font-medium text-gray-900">Reason:</span>{' '}
                    {claim.rejectionReason}
                  </div>
                ) : (
                  <p className="text-sm text-gray-600 mt-2">No additional reason was recorded.</p>
                )}
                {workshopData && (
                  <div className="mt-3 pt-3 border-t border-gray-100 text-sm text-gray-700 space-y-1">
                    <p className="font-medium text-gray-900">Arrange vehicle pickup</p>
                    <p>
                      Contact <strong>{workshopData.name}</strong> to arrange pickup of your vehicle.
                    </p>
                    <p>
                      <a href={`tel:${workshopData.phone}`} className="text-primary-700 hover:underline">
                        {workshopData.phone}
                      </a>
                    </p>
                    <p className="text-gray-600">
                      {workshopData.address}, {workshopData.city}
                      {workshopData.zipCode ? ` ${workshopData.zipCode}` : ''}
                    </p>
                  </div>
                )}
                {!workshopData && claim.workshopId && (
                  <p className="text-sm text-gray-600 mt-2">
                    Contact your selected workshop to arrange pickup of your vehicle.
                  </p>
                )}
                {!claim.workshopId && (
                  <p className="text-sm text-gray-600 mt-2">
                    If your vehicle is at a workshop, contact them directly to arrange pickup.
                  </p>
                )}
              </div>
            ) : claim.status === 'PAYMENT_INITIATED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Payment in Progress</p>
                <p className="text-sm text-gray-600 mt-1">
                  Your payment is being processed. You'll receive confirmation once completed.
                </p>
              </div>
            ) : claim.status === 'PAYMENT_PROCESSED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Payment Received</p>
                <p className="text-sm text-gray-600 mt-1">
                  Your payment was successful. Use the receipt section below if you need a PDF, and follow the pickup instructions for your vehicle.
                </p>
              </div>
            ) : claim.status === 'SETTLED' ? (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Claim Settled</p>
                <p className="text-sm text-gray-600 mt-1">
                  Your claim has been fully settled. Thank you for choosing our services.
                </p>
              </div>
            ) : (
              <div>
                <p className="font-semibold text-gray-900 text-sm">Processing Claim</p>
                <p className="text-sm text-gray-600 mt-1">
                  Your claim is being processed. We'll update you as it progresses.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Vehicle at Workshop Banner — from drop-off through payment; hidden after payment is processed or settled */}
      {['VEHICLE_AT_WORKSHOP', 'ASSIGNED', 'UNDER_SURVEY', 'SURVEYED', 'UNDER_ADJUDICATION', 'APPROVED', 'PAYMENT_INITIATED'].includes(claim.status) && workshopData && (
        <div className="card bg-gradient-to-r from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-start gap-3">
            <div className="p-2 bg-green-100 rounded-lg shrink-0">
              <Wrench className="w-5 h-5 text-green-700" />
            </div>
            <div className="flex-1">
              <h3 className="text-sm font-semibold text-gray-900 mb-1">Your vehicle is at the workshop</h3>
              <div className="space-y-1.5 text-sm">
                <p className="font-medium text-gray-800">{workshopData.name}</p>
                <p className="text-gray-600">{workshopData.address}, {workshopData.city} {workshopData.zipCode}</p>
                <p className="text-gray-600">
                  <span className="text-gray-500">Phone:</span>{' '}
                  <a href={`tel:${workshopData.phone}`} className="text-primary-700 hover:underline">
                    {workshopData.phone}
                  </a>
                </p>
                {workOrderData && (
                  <p className="text-xs text-gray-500 mt-2">
                    Work Order #{workOrderData.workOrderId.substring(0, 8).toUpperCase()} &bull;{' '}
                    Status: <span className="font-medium capitalize">
                      {workOrderData.repairStatus.replace(/_/g, ' ').toLowerCase()}
                    </span>
                  </p>
                )}
              </div>
              {['APPROVED', 'PAYMENT_INITIATED', 'SETTLED'].includes(claim.status) && workOrderData?.repairStatus !== 'COMPLETED' && (
                <p className="text-xs text-green-700 mt-3 bg-green-50/50 rounded px-2 py-1 inline-block">
                  Your claim has been approved! Workshop started repairing and will keep you posted as progress...
                </p>
              )}
              {workOrderData?.repairStatus === 'COMPLETED' && workOrderData?.finalCost && (
                <div className="mt-4 p-3 bg-white border border-green-300 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm font-medium text-gray-900">Repairs Completed!</p>
                    <CheckCircle2 className="w-5 h-5 text-green-600" />
                  </div>
                  <div className="text-sm text-gray-700 space-y-1">
                    <p className="flex justify-between">
                      <span>Workshop Final Bill:</span>
                      <span className="font-semibold">{formatCurrency(workOrderData.finalCost)}</span>
                    </p>
                    <p className="flex justify-between">
                      <span>Approved Amount:</span>
                      <span className="font-semibold">{formatCurrency(claim.approvedAmount ?? 0)}</span>
                    </p>
                    <p className="flex justify-between text-base font-bold text-green-700 pt-2 border-t border-gray-200">
                      <span>Your Payment:</span>
                      <span>{formatCurrency(Math.max(0, (workOrderData.finalCost ?? 0) - (claim.approvedAmount ?? 0)))}</span>
                    </p>
                  </div>
                  {claim.status === 'PAYMENT_INITIATED' ? (
                    <p className="text-sm text-sky-700 mt-3 text-center">
                      Payment is processing...
                    </p>
                  ) : (
                    <Link
                      to={`/customer/payment/${claim.claimId}`}
                      className="btn-primary w-full mt-3 flex items-center justify-center gap-2"
                    >
                      <CreditCard className="w-4 h-4" />
                      Pay Now
                    </Link>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}


      <div className="grid grid-cols-2 gap-5">
        {/* Claim Information */}
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Claim Information</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Policy Number</dt>
              <dd className="font-medium">{claim.policyNumber}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Vehicle</dt>
              <dd className="font-medium">{claim.vehicleRegistration}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Claim Type</dt>
              <dd className="font-medium">{claim.claimType.replace('_', ' ')}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Incident Date</dt>
              <dd className="font-medium">{format(new Date(claim.incidentDate), 'dd MMM yyyy')}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Submitted</dt>
              <dd className="font-medium">{format(new Date(claim.createdAt), 'dd MMM yyyy, HH:mm')}</dd>
            </div>
          </dl>
        </div>

        {/* Financial */}
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Assessment & Payment</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Assessed Amount</dt>
              <dd className="font-medium">{formatCurrency(claim.assessedAmount)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Approved Amount</dt>
              <dd className={`font-medium ${claim.approvedAmount ? 'text-green-700' : ''}`}>
                {formatCurrency(claim.approvedAmount)}
              </dd>
            </div>
            {claim.rejectionReason && (
              <div>
                <dt className="text-gray-500 mb-1">Rejection Reason</dt>
                <dd className="text-red-600 bg-red-50 p-2 rounded">{claim.rejectionReason}</dd>
              </div>
            )}
          </dl>
        </div>
      </div>

      {/* Incident Details — editable when SUBMITTED, read-only thereafter */}
      <div className="card">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <FileText className="w-5 h-5 text-primary-700" />
            <h2 className="text-sm font-semibold text-gray-900">Incident Details</h2>
          </div>
          {EDITABLE_STATUSES.includes(claim.status) && !editingIncident && (
            <button
              onClick={() => {
                setEditLocation(claim.incidentLocation ?? '')
                setEditDescription(claim.description ?? '')
                setEditingIncident(true)
                setEditSaveError(null)
              }}
              className="flex items-center gap-1 text-xs text-primary-700 hover:text-primary-900"
              aria-label="Edit incident details"
            >
              <Pencil className="w-3.5 h-3.5" /> Edit
            </button>
          )}
          {EDITABLE_STATUSES.includes(claim.status) && editingIncident && (
            <div className="flex items-center gap-2">
              <button
                onClick={() => {
                  updateIncidentMutation.mutate({ location: editLocation, description: editDescription })
                }}
                disabled={updateIncidentMutation.isPending}
                className="flex items-center gap-1 text-xs bg-primary-700 text-white px-2.5 py-1 rounded-lg disabled:opacity-50"
              >
                <Save className="w-3.5 h-3.5" />
                {updateIncidentMutation.isPending ? 'Saving…' : 'Save'}
              </button>
              <button
                onClick={() => { setEditingIncident(false); setEditSaveError(null) }}
                className="flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700"
              >
                <X className="w-3.5 h-3.5" /> Cancel
              </button>
            </div>
          )}
        </div>

        {editingIncident ? (
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Location</label>
              <input
                value={editLocation}
                onChange={(e) => setEditLocation(e.target.value)}
                maxLength={500}
                className="input text-sm"
                placeholder="e.g. Highway 101, Exit 42"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Description</label>
              <textarea
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                rows={4}
                maxLength={2000}
                className="input text-sm resize-none"
                placeholder="Describe what happened…"
              />
            </div>
            {editSaveError && (
              <p className="text-xs text-red-600 bg-red-50 border border-red-100 rounded px-3 py-2">
                {editSaveError}
              </p>
            )}
          </div>
        ) : (
          <dl className="space-y-2 text-sm">
            {claim.incidentLocation && (
              <div>
                <dt className="text-xs text-gray-500">Location</dt>
                <dd className="text-gray-800 mt-0.5">{claim.incidentLocation}</dd>
              </div>
            )}
            {claim.description ? (
              <div>
                <dt className="text-xs text-gray-500">Description</dt>
                <dd className="text-gray-800 leading-relaxed mt-0.5">{claim.description}</dd>
              </div>
            ) : (
              <p className="text-xs text-gray-400 italic">No description provided.</p>
            )}
            {EDITABLE_STATUSES.includes(claim.status) && (
              <p className="text-xs text-blue-600 bg-blue-50 rounded px-2 py-1 mt-2">
                You can edit incident details while the claim is in SUBMITTED status.
              </p>
            )}
          </dl>
        )}
      </div>

      {['PAYMENT_PROCESSED', 'SETTLED'].includes(claim.status) && (
        <div className="card border-l-4 border-l-blue-500">
          <p className="text-blue-700 font-medium mb-3">
            Payment completed. You can download your receipt PDF anytime.
          </p>
          <button
            type="button"
            onClick={() => downloadReceiptMutation.mutate()}
            disabled={downloadReceiptMutation.isPending}
            className="btn-secondary"
          >
            {downloadReceiptMutation.isPending ? 'Preparing receipt...' : 'Download Receipt'}
          </button>
        </div>
      )}

      {/* Vehicle Pickup Info */}
      {claim.status === 'PAYMENT_PROCESSED' && workshopData && (
        <div className="card border-l-4 border-l-blue-500">
          <div className="flex items-start gap-4">
            <KeyRound className="w-6 h-6 text-blue-600 mt-1 flex-shrink-0" />
            <div className="flex-1">
              <p className="text-blue-700 font-medium mb-2">
                🎉 Payment Complete! Your vehicle is ready for pickup
              </p>
              <p className="text-sm text-gray-600 mb-3">
                Contact <strong>{workshopData.name}</strong> at <strong>{workshopData.phone}</strong> to arrange pickup.
              </p>
              <div className="flex gap-3">
                <a href={`tel:${workshopData.phone}`} className="btn-primary">
                  Call Workshop
                </a>
                <button
                  onClick={() => window.location.href = '/customer/claims'}
                  className="btn-secondary"
                >
                  Back to Claims
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Documents Section — FR6 */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Paperclip className="w-5 h-5 text-primary-700" />
            <h2 className="text-sm font-semibold text-gray-900">Supporting Documents</h2>
          </div>
          <div className="flex items-center gap-2">
            <select
              value={selectedDocType}
              onChange={(e) => setSelectedDocType(e.target.value as DocumentType)}
              className="text-xs border border-gray-200 rounded-lg px-2 py-1.5 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-400"
              aria-label="Document type"
            >
              {DOCUMENT_TYPES.map((dt) => (
                <option key={dt.value} value={dt.value}>{dt.label}</option>
              ))}
            </select>
            <input
              ref={fileInputRef}
              type="file"
              className="sr-only"
              accept=".pdf,.jpg,.jpeg,.png,.webp,.doc,.docx"
              onChange={handleFileChange}
              aria-label="Upload document"
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadMutation.isPending || isCompressing}
              className="flex items-center gap-1.5 text-xs bg-primary-700 hover:bg-primary-800 text-white px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
              aria-label="Upload document file"
            >
              <Upload className="w-3.5 h-3.5" />
              {isCompressing ? 'Optimizing...' : uploadMutation.isPending ? 'Uploading...' : 'Upload'}
            </button>
          </div>
        </div>

        <p className="text-xs text-gray-400 mb-3">
          Accepted: JPEG, PNG, WebP, PDF, DOC, DOCX &nbsp;&bull;&nbsp; Max 5 MB per file &nbsp;&bull;&nbsp; Up to 10 documents per claim &nbsp;&bull;&nbsp; Images automatically optimized
        </p>

        {uploadError && (
          <div className="flex items-center gap-1.5 text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2 text-xs mb-3">
            <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {uploadError}
          </div>
        )}
        {uploadWarning && !uploadError && (
          <div className="flex items-center gap-1.5 text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 text-xs mb-3">
            <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {uploadWarning}
          </div>
        )}
        {uploadMutation.isSuccess && (
          <div className="flex items-center gap-1.5 text-green-600 text-xs mb-3">
            <CheckCircle2 className="w-3.5 h-3.5" /> Document uploaded successfully
          </div>
        )}

        {!documentsData || documentsData.length === 0 ? (
          <div className="text-center py-8 text-gray-400 text-sm border border-dashed border-gray-200 rounded-lg">
            <FileText className="w-8 h-8 mx-auto mb-2 opacity-40" />
            No documents uploaded yet
          </div>
        ) : (
          <ul className="divide-y divide-gray-50">
            {documentsData.map((doc) => (
              <li key={doc.documentId} className="flex items-center justify-between py-2.5 text-sm">
                <div className="flex items-center gap-2 min-w-0">
                  <FileText className="w-4 h-4 text-gray-400 shrink-0" />
                  <span className="text-gray-800 truncate max-w-xs">{doc.filename}</span>
                  <span className="text-xs text-gray-400 shrink-0">
                    {doc.documentType.replace(/_/g, ' ')}
                  </span>
                  <span className="text-xs text-gray-300 shrink-0">{formatBytes(doc.fileSizeBytes)}</span>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button
                    type="button"
                    onClick={() => void documentsApi.openDocumentInNewTabWithAuth(doc.documentId)}
                    className="text-primary-700 hover:underline text-xs"
                    aria-label={`View ${doc.filename}`}
                  >
                    View
                  </button>
                  <button
                    type="button"
                    onClick={() => void documentsApi.downloadDocumentWithAuth(doc.documentId, doc.filename)}
                    className="text-primary-700 hover:underline text-xs"
                    aria-label={`Download ${doc.filename}`}
                  >
                    Download
                  </button>
                  <button
                    onClick={() => {
                      if (window.confirm(`Remove "${doc.filename}"?`)) {
                        deleteMutation.mutate(doc.documentId)
                      }
                    }}
                    disabled={deleteMutation.isPending}
                    className="text-red-400 hover:text-red-600 disabled:opacity-40 transition-colors"
                    aria-label={`Delete ${doc.filename}`}
                    title="Remove document"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Repair Tracking Section — FR9 */}
      <div className="card">
        <div className="flex items-center gap-2 mb-4">
          <Wrench className="w-5 h-5 text-primary-700" />
          <h2 className="text-sm font-semibold text-gray-900">Repair Tracking</h2>
        </div>

        {!workOrderData ? (
          <div className="text-center py-8 text-gray-400 text-sm border border-dashed border-gray-200 rounded-lg">
            <Wrench className="w-8 h-8 mx-auto mb-2 opacity-40" />
            No work order has been assigned to this claim yet
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-gray-900">{workOrderData.workshopName ?? 'Assigned Workshop'}</p>
                <p className="text-xs text-gray-500 font-mono">WO #{workOrderData.workOrderId.substring(0, 8).toUpperCase()}</p>
              </div>
              <span
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${
                  REPAIR_STATUS_COLOR[workOrderData.repairStatus] ?? 'bg-gray-100 text-gray-700'
                }`}
              >
                {workOrderData.repairStatus === 'IN_PROGRESS' ? (
                  <Clock className="w-3 h-3" />
                ) : workOrderData.repairStatus === 'COMPLETED' ? (
                  <CheckCircle2 className="w-3 h-3" />
                ) : null}
                {workOrderData.repairStatus.replace('_', ' ')}
              </span>
            </div>

            <dl className="grid grid-cols-2 gap-3 text-sm">
              {workOrderData.estimatedCompletionDate && (
                <div className="flex items-start gap-2">
                  <Calendar className="w-4 h-4 text-gray-400 mt-0.5 shrink-0" />
                  <div>
                    <dt className="text-xs text-gray-500">Est. Completion</dt>
                    <dd className="font-medium">
                      {format(new Date(workOrderData.estimatedCompletionDate), 'dd MMM yyyy')}
                    </dd>
                  </div>
                </div>
              )}
              {workOrderData.estimatedCost !== undefined && workOrderData.estimatedCost !== null && (
                <div>
                  <dt className="text-xs text-gray-500">Estimated Cost</dt>
                  <dd className="font-medium">{formatCurrency(workOrderData.estimatedCost)}</dd>
                </div>
              )}
              {workOrderData.finalCost !== undefined && workOrderData.finalCost !== null && (
                <div>
                  <dt className="text-xs text-gray-500">Final Cost</dt>
                  <dd className="font-medium text-green-700">{formatCurrency(workOrderData.finalCost)}</dd>
                </div>
              )}
            </dl>

            {workOrderData.workDescription && (
              <div className="bg-gray-50 rounded-lg p-3">
                <p className="text-xs text-gray-500 mb-1">Work Description</p>
                <p className="text-sm text-gray-700 leading-relaxed">{workOrderData.workDescription}</p>
              </div>
            )}

            {/* Progress Timeline */}
            <RepairProgressHistory workOrderId={workOrderData.workOrderId} />
          </div>
        )}
      </div>

      {/* Notes & Endorsements — shown once claim is assigned/beyond */}
      {ENDORSEMENT_STATUSES.includes(claim.status) && (
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <MessageSquare className="w-5 h-5 text-primary-700" />
            <h2 className="text-sm font-semibold text-gray-900">Notes &amp; Endorsements</h2>
          </div>

          <p className="text-xs text-gray-400 mb-4">
            The claim is now with our assessors. You can no longer edit incident details directly,
            but you may add notes here. Our team will see them alongside your claim.
          </p>

          {/* Add note form */}
          <div className="space-y-2 mb-5">
            <textarea
              value={newNote}
              onChange={(e) => setNewNote(e.target.value)}
              rows={3}
              maxLength={2000}
              className="input text-sm resize-none"
              placeholder="Add additional context, corrections, or updates…"
              aria-label="Add endorsement note"
            />
            {noteError && (
              <p className="text-xs text-red-600">{noteError}</p>
            )}
            <button
              onClick={() => {
                if (newNote.trim().length < 5) {
                  setNoteError('Note must be at least 5 characters.')
                  return
                }
                addEndorsementMutation.mutate(newNote.trim())
              }}
              disabled={addEndorsementMutation.isPending || !newNote.trim()}
              className="flex items-center gap-1.5 text-sm bg-primary-700 hover:bg-primary-800 text-white px-4 py-2 rounded-lg transition-colors disabled:opacity-50"
            >
              <MessageSquarePlus className="w-4 h-4" />
              {addEndorsementMutation.isPending ? 'Saving…' : 'Add Note'}
            </button>
          </div>

          {/* Endorsement history */}
          {endorsementsData && endorsementsData.length > 0 ? (
            <ul className="space-y-3">
              {endorsementsData.map((e) => (
                <li key={e.endorsementId} className="bg-gray-50 rounded-xl p-3 text-sm">
                  <p className="text-gray-800 leading-relaxed">{e.note}</p>
                  <p className="text-xs text-gray-400 mt-2">
                    {format(new Date(e.createdAt), 'dd MMM yyyy, HH:mm')}
                    {' · '}
                    <span className="capitalize">{e.endorsementType.replace(/_/g, ' ').toLowerCase()}</span>
                  </p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-xs text-gray-400 italic">No notes yet.</p>
          )}
        </div>
      )}
    </div>
  )
}
