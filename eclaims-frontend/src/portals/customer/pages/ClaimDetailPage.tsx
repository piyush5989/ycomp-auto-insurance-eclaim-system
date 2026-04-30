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
  Pencil, MessageSquarePlus, MessageSquare, Save, X,
  Building2, Car, KeyRound
} from 'lucide-react'
import { documentsApi } from '@/features/documents/api/documentsApi'
import type { DocumentType } from '@/features/documents/api/documentsApi.types'
import { claimsApi } from '@/features/claims/api/claimsApi'
import { httpClient } from '@/shared/api/httpClient'
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
  'APPROVED','REJECTED','PAYMENT_INITIATED','SETTLED',
]

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

  // Journey steps logic
  const needsWorkshopSelection = claim.status === 'SUBMITTED'
  const needsVehicleDropOff = claim.status === 'WORKSHOP_SELECTED'
  const canSelectRental = claim.status === 'VEHICLE_AT_WORKSHOP'
  
  // Journey is complete when claim moves beyond initial setup stages
  const journeyComplete = ['ASSIGNED', 'UNDER_SURVEY', 'SURVEYED', 'UNDER_ADJUDICATION', 
                          'APPROVED', 'REJECTED', 'PAYMENT_INITIATED', 'SETTLED'].includes(claim.status)
  
  const journeySteps = [
    {
      step: 1,
      label: 'Select Workshop',
      completed: !needsWorkshopSelection,
      current: needsWorkshopSelection,
      icon: <Building2 className="w-4 h-4" />,
    },
    {
      step: 2,
      label: 'Drop Off Vehicle',
      completed: !needsVehicleDropOff && !needsWorkshopSelection,
      current: needsVehicleDropOff,
      icon: <Car className="w-4 h-4" />,
    },
    {
      step: 3,
      label: 'Rental (Optional)',
      completed: journeyComplete,
      current: canSelectRental,
      icon: <KeyRound className="w-4 h-4" />,
    },
  ]
  
  const showJourneyProgress = needsWorkshopSelection || needsVehicleDropOff || canSelectRental

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

          {/* Success Message */}
          {journeyComplete && (
            <div className="bg-white rounded-lg p-4 border border-green-200">
              <div className="flex items-center gap-3">
                <CheckCircle2 className="w-6 h-6 text-green-600" />
                <div>
                  <p className="font-semibold text-gray-900">Setup Complete!</p>
                  <p className="text-sm text-gray-600">
                    Your vehicle is at the workshop. A surveyor has been assigned and will inspect it soon.
                  </p>
                </div>
              </div>
            </div>
          )}

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

      {/* Payment CTA */}
      {claim.status === 'APPROVED' && (
        <div className="card border-l-4 border-l-green-500">
          <p className="text-green-700 font-medium mb-3">
            Your claim has been approved! Proceed to payment.
          </p>
          <Link to={`/customer/payment/${claim.claimId}`} className="btn-primary">
            Proceed to Payment
          </Link>
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
                  <a
                    href={doc.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary-700 hover:underline text-xs"
                    aria-label={`Download ${doc.filename}`}
                  >
                    Download
                  </a>
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

            <p className="text-xs text-gray-400">
              Last updated: {format(new Date(workOrderData.updatedAt), 'dd MMM yyyy, HH:mm')}
            </p>
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
