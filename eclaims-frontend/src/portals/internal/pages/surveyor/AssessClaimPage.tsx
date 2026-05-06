import React, { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useClaimDetails } from '@/features/claims/hooks/useClaimDetails'
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { StatusBadge } from '@/shared/components/ui/Badge'
import type { ClaimStatus } from '@/shared/utils/claimStatusLabel'
import { formatCurrency } from '@/shared/utils/formatCurrency'
import { useAuth } from '@/shared/auth/KeycloakProvider'
import { documentsApi } from '@/features/documents/api/documentsApi'
import type { DocumentType } from '@/features/documents/api/documentsApi.types'
import { ArrowLeft, CheckCircle, Camera, Upload, X, FileImage, FileVideo } from 'lucide-react'

export default function AssessClaimPage() {
  const { claimId } = useParams<{ claimId: string }>()
  const navigate = useNavigate()
  const { data: claim, isLoading } = useClaimDetails(claimId)
  const { username } = useAuth()
  const queryClient = useQueryClient()

  const [assessedAmount, setAssessedAmount] = useState('')
  const [damageNotes, setDamageNotes] = useState('')
  const [uploadingFiles, setUploadingFiles] = useState<File[]>([])
  const [uploadedDocumentIds, setUploadedDocumentIds] = useState<string[]>([])
  const [fileErrors, setFileErrors] = useState<string[]>([])

  const { data: documents = [] } = useQuery({
    queryKey: ['claim-documents', claimId],
    queryFn: () => documentsApi.listByClaimId(claimId!).then((r) => r.data || []),
    enabled: !!claimId,
  })

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || [])
    const errors: string[] = []
    const validFiles = files.filter((file) => {
      const isImage = file.type.startsWith('image/')
      const isVideo = file.type.startsWith('video/')
      const isUnder5MB = file.size <= 5 * 1024 * 1024

      if (!isImage && !isVideo) {
        errors.push(`"${file.name}" is not an image or video file.`)
        return false
      }
      if (!isUnder5MB) {
        errors.push(`"${file.name}" exceeds the 5 MB limit.`)
        return false
      }
      return true
    })
    setFileErrors(errors)
    setUploadingFiles((prev) => [...prev, ...validFiles].slice(0, 10))
    // Reset the input so the same file can be re-selected after an error
    event.target.value = ''
  }

  const removeFile = (index: number) => {
    setUploadingFiles((prev) => prev.filter((_, i) => i !== index))
  }

  const uploadAllFiles = async () => {
    const uploadPromises = uploadingFiles.map((file) =>
      documentsApi.uploadDocument(claimId!, file, 'PHOTO_DAMAGE' as DocumentType)
    )
    const results = await Promise.all(uploadPromises)
    const ids = results.map((r) => r.data.documentId)
    setUploadedDocumentIds((prev) => [...prev, ...ids])
    setUploadingFiles([])
    queryClient.invalidateQueries({ queryKey: ['claim-documents', claimId] })
  }

  const submitAssessment = useMutation({
    mutationFn: async () => {
      // Upload any pending files first
      if (uploadingFiles.length > 0) {
        await uploadAllFiles()
      }
      
      // Then submit assessment
      return httpClient.patch(`/claims/${claimId}/status`, {
        targetStatus: 'SURVEYED',
        amount: parseFloat(assessedAmount),
        reason: damageNotes,
      }).then((r) => r.data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      queryClient.invalidateQueries({ queryKey: ['my-assignments'] })
      navigate('/internal/surveyor/my-assignments')
    },
  })

  const startSurvey = useMutation({
    mutationFn: () =>
      httpClient.patch(`/claims/${claimId}/status`, {
        targetStatus: 'UNDER_SURVEY',
      }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
    },
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-20">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-800" />
      </div>
    )
  }

  if (!claim) {
    return <div className="card text-center py-12">Claim not found</div>
  }

  const canAssess = claim.status === 'ASSIGNED' || claim.status === 'UNDER_SURVEY'

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link to="/internal/surveyor/my-assignments" className="text-gray-400 hover:text-gray-700">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-xl font-bold text-gray-900">Survey Assessment</h1>
          <p className="text-xs text-gray-400 font-mono">{claim.claimId}</p>
        </div>
        <div className="ml-auto">
          <StatusBadge status={claim.status as ClaimStatus} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-5">
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Claim Information</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-gray-500">Policy</dt>
              <dd className="font-medium">{claim.policyNumber}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Customer</dt>
              <dd className="font-medium">{claim.customerEmail}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Vehicle</dt>
              <dd className="font-medium">{claim.vehicleRegistration}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Type</dt>
              <dd className="font-medium">{claim.claimType}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Incident Date</dt>
              <dd className="font-medium">{claim.incidentDate}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-gray-500">Location</dt>
              <dd className="font-medium text-right">{claim.incidentLocation || '—'}</dd>
            </div>
          </dl>
        </div>

        <div className="card">
          <h2 className="text-sm font-semibold text-gray-900 mb-4">Incident Description</h2>
          <p className="text-sm text-gray-700">{claim.description || 'No description provided'}</p>
        </div>
      </div>

      {canAssess && claim.status === 'ASSIGNED' && (
        <div className="card border-t-4 border-t-blue-500">
          <div className="flex items-center gap-3 mb-4">
            <Camera className="w-5 h-5 text-blue-600" />
            <h2 className="text-sm font-semibold text-gray-900">Start Survey</h2>
          </div>
          <p className="text-sm text-gray-600 mb-4">
            You need to start the survey before submitting your assessment. This will update the claim status to "Under Survey".
          </p>
          <button
            onClick={() => startSurvey.mutate()}
            disabled={startSurvey.isPending}
            className="btn-primary"
          >
            Start Survey
          </button>
        </div>
      )}

      {canAssess && claim.status === 'UNDER_SURVEY' && (
        <div className="card border-t-4 border-t-green-500">
          <div className="flex items-center gap-3 mb-4">
            <CheckCircle className="w-5 h-5 text-green-600" />
            <h2 className="text-sm font-semibold text-gray-900">Submit Assessment</h2>
          </div>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Assessed Damage Amount (USD) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                value={assessedAmount}
                onChange={(e) => setAssessedAmount(e.target.value)}
                placeholder="0.00"
                step="0.01"
                min="0"
                className="input max-w-xs"
                required
              />
              <p className="text-xs text-gray-500 mt-1">
                Enter the total estimated cost for repairs based on your inspection
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Damage Photos/Videos
              </label>
              <div className="border-2 border-dashed border-gray-300 rounded-lg p-6 text-center">
                <input
                  type="file"
                  id="damage-photos"
                  accept="image/*,video/*"
                  multiple
                  onChange={handleFileSelect}
                  className="hidden"
                />
                <label htmlFor="damage-photos" className="cursor-pointer">
                  <Upload className="w-12 h-12 text-gray-400 mx-auto mb-3" />
                  <p className="text-sm text-gray-600 mb-1">
                    Click to upload damage photos or videos
                  </p>
                  <p className="text-xs text-gray-500">
                    Max 5MB per file, up to 10 files (JPEG, PNG, MP4)
                  </p>
                </label>
              </div>

              {/* Inline validation errors — replaces browser alert() */}
              {fileErrors.length > 0 && (
                <div className="mt-3 rounded-md bg-red-50 border border-red-200 p-3">
                  <ul className="list-disc list-inside space-y-1">
                    {fileErrors.map((err, i) => (
                      <li key={i} className="text-xs text-red-700">{err}</li>
                    ))}
                  </ul>
                  <button
                    type="button"
                    onClick={() => setFileErrors([])}
                    className="mt-2 text-xs text-red-600 underline hover:text-red-800"
                  >
                    Dismiss
                  </button>
                </div>
              )}

              {/* Preview pending uploads */}
              {uploadingFiles.length > 0 && (
                <div className="mt-4 grid grid-cols-2 md:grid-cols-3 gap-3">
                  {uploadingFiles.map((file, idx) => (
                    <div key={idx} className="relative border rounded-lg p-2 bg-gray-50">
                      <button
                        onClick={() => removeFile(idx)}
                        className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full p-1 hover:bg-red-600"
                      >
                        <X className="w-3 h-3" />
                      </button>
                      <div className="flex items-center gap-2">
                        {file.type.startsWith('image/') ? (
                          <FileImage className="w-8 h-8 text-blue-500" />
                        ) : (
                          <FileVideo className="w-8 h-8 text-purple-500" />
                        )}
                        <div className="flex-1 min-w-0">
                          <p className="text-xs font-medium text-gray-900 truncate">{file.name}</p>
                          <p className="text-xs text-gray-500">{(file.size / 1024).toFixed(1)} KB</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Show already uploaded documents */}
              {documents.length > 0 && (
                <div className="mt-4">
                  <p className="text-xs font-medium text-gray-700 mb-2">Already Uploaded ({documents.length})</p>
                  <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                    {documents.map((doc) => (
                      <div key={doc.documentId} className="border rounded-lg p-2 bg-green-50 border-green-200">
                        <div className="flex items-center gap-2">
                          {doc.contentType.startsWith('image/') ? (
                            <FileImage className="w-8 h-8 text-green-600" />
                          ) : (
                            <FileVideo className="w-8 h-8 text-green-600" />
                          )}
                          <div className="flex-1 min-w-0">
                            <p className="text-xs font-medium text-gray-900 truncate">{doc.filename}</p>
                            <p className="text-xs text-green-700">✓ Uploaded</p>
                            <div className="flex gap-2 mt-1">
                              <button
                                type="button"
                                onClick={() => void documentsApi.openDocumentInNewTabWithAuth(doc.documentId)}
                                className="text-xs text-primary-600 hover:underline"
                              >
                                View
                              </button>
                              <button
                                type="button"
                                onClick={() => void documentsApi.downloadDocumentWithAuth(doc.documentId, doc.filename)}
                                className="text-xs text-primary-600 hover:underline"
                              >
                                Download
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Damage Assessment Notes <span className="text-red-500">*</span>
              </label>
              <textarea
                value={damageNotes}
                onChange={(e) => setDamageNotes(e.target.value)}
                rows={5}
                placeholder="Describe the damage observed, parts affected, recommended repairs..."
                className="input resize-none"
                required
              />
              <p className="text-xs text-gray-500 mt-1">
                Provide detailed notes about damage severity, affected parts, and repair recommendations
              </p>
            </div>

            <div className="pt-2">
              <button
                onClick={() => submitAssessment.mutate()}
                disabled={!assessedAmount || !damageNotes || submitAssessment.isPending}
                className="btn-primary"
              >
                <CheckCircle className="w-4 h-4 mr-2" />
                Submit Assessment
              </button>
            </div>
          </div>
        </div>
      )}

      {!canAssess && (
        <div className="card bg-gray-50">
          <p className="text-sm text-gray-600">
            {claim.status === 'SURVEYED' && (
              <>Assessment already submitted. Assessed amount: <strong>{formatCurrency(claim.assessedAmount)}</strong></>
            )}
            {claim.status !== 'SURVEYED' && claim.status !== 'ASSIGNED' && claim.status !== 'UNDER_SURVEY' && (
              <>This claim is no longer available for assessment (Status: {claim.status})</>
            )}
          </p>
        </div>
      )}
    </div>
  )
}
