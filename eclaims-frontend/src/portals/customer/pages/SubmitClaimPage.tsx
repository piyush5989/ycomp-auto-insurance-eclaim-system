import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient, useMutation } from '@tanstack/react-query'
import { useSubmitClaim } from '@/features/claims/hooks/useSubmitClaim'
import { submitClaimSchema, type SubmitClaimFormData } from '@/features/claims/validation/submitClaimSchema'
import { CheckCircle, AlertCircle, ChevronRight, ChevronLeft, AlertTriangle, ArrowRight } from 'lucide-react'
import { claimsApi } from '@/features/claims/api/claimsApi'
import type { PotentialDuplicate } from '@/features/claims/api/claimsApi.types'
import { format } from 'date-fns'

const STEPS = ['Policy & Vehicle', 'Incident Details', 'Review & Submit'] as const
const CLAIM_TYPES = ['COLLISION','COMPREHENSIVE','THEFT','FIRE','FLOOD','VANDALISM','GLASS_DAMAGE','ROADSIDE_ASSISTANCE']

// ─── Duplicate Warning Modal ──────────────────────────────────────────────────

interface DuplicateWarningModalProps {
  duplicates: PotentialDuplicate[]
  onCreateNew: () => void
  onGoToExisting: (claimId: string) => void
}

const DuplicateWarningModal = ({ duplicates, onCreateNew, onGoToExisting }: DuplicateWarningModalProps) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
    <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6">
      <div className="flex items-start gap-3 mb-4">
        <div className="p-2 bg-amber-100 rounded-full shrink-0">
          <AlertTriangle className="w-5 h-5 text-amber-600" />
        </div>
        <div>
          <h3 className="font-semibold text-gray-900 text-base">Similar claim found</h3>
          <p className="text-sm text-gray-500 mt-0.5">
            We found {duplicates.length === 1 ? 'a claim' : `${duplicates.length} claims`} for
            the same vehicle around this date. Is this a new incident?
          </p>
        </div>
      </div>

      <div className="space-y-2 mb-5 max-h-48 overflow-y-auto">
        {duplicates.map((d) => (
          <button
            key={d.claimId}
            onClick={() => onGoToExisting(d.claimId)}
            className="w-full text-left border border-gray-200 hover:border-primary-400 rounded-xl p-3 transition-colors group"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-800">
                  {d.claimType.replace(/_/g, ' ')} &bull;{' '}
                  {format(new Date(d.incidentDate), 'dd MMM yyyy')}
                </p>
                {d.incidentLocation && (
                  <p className="text-xs text-gray-400 mt-0.5 truncate">{d.incidentLocation}</p>
                )}
                <span className="inline-block mt-1 text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full">
                  {d.status.replace(/_/g, ' ')}
                </span>
              </div>
              <ArrowRight className="w-4 h-4 text-gray-300 group-hover:text-primary-600 shrink-0 ml-2" />
            </div>
          </button>
        ))}
      </div>

      <div className="flex flex-col gap-2">
        <button
          onClick={onCreateNew}
          className="btn-primary w-full"
        >
          This is a new incident — continue
        </button>
        <p className="text-xs text-center text-gray-400">
          Or click an existing claim above to attach documents to it
        </p>
      </div>
    </div>
  </div>
)

// ─── Main page ────────────────────────────────────────────────────────────────

export default function SubmitClaimPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [step, setStep] = useState(0)
  const [duplicates, setDuplicates] = useState<PotentialDuplicate[] | null>(null)
  const submitClaim = useSubmitClaim()

  const { register, handleSubmit, watch, formState: { errors } } = useForm<SubmitClaimFormData>({
    resolver: zodResolver(submitClaimSchema),
    defaultValues: { policeReportFiled: false },
    mode: 'onChange',
  })

  const formValues = watch()

  const isStep0Valid = Boolean(formValues.policyNumber && formValues.vehicleRegistration)
  const isStep1Valid = Boolean(formValues.incidentDate && formValues.claimType)
  const isContinueDisabled = (step === 0 && !isStep0Valid) || (step === 1 && !isStep1Valid)

  const dupCheckMutation = useMutation({
    mutationFn: () =>
      claimsApi.checkDuplicates(
        formValues.vehicleRegistration,
        formValues.incidentDate,
        formValues.policyNumber,
      ),
    onSuccess: (res) => {
      const found = res.data ?? []
      if (found.length > 0) {
        setDuplicates(found)
      } else {
        setStep(2)
      }
    },
    onError: () => {
      // duplicate check failure must never block submission
      setStep(2)
    },
  })

  const handleStep1Continue = () => {
    if (step === 1) {
      dupCheckMutation.mutate()
    } else {
      setStep((s) => s + 1)
    }
  }

  const handleFinalSubmit = handleSubmit((data: SubmitClaimFormData) => {
    submitClaim.mutate(data, {
      onSuccess: (response) => {
        const claimId = response.data?.claimId
        queryClient.invalidateQueries({ queryKey: ['claims'] })
        if (claimId) {
          queryClient.removeQueries({ queryKey: ['claim', claimId] })
          queryClient.removeQueries({ queryKey: ['documents', claimId] })
        }
        navigate(`/customer/claims/${claimId}`)
      },
    })
  })

  if (submitClaim.isSuccess) {
    return (
      <div className="card max-w-lg mx-auto text-center py-12">
        <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-gray-900 mb-2">Claim Submitted Successfully</h2>
        <p className="text-gray-500">
          Your claim ID:{' '}
          <code className="bg-gray-100 px-2 py-0.5 rounded text-sm">
            {submitClaim.data?.data?.claimId}
          </code>
        </p>
        <p className="text-gray-500 text-sm mt-2">A confirmation email has been sent to you.</p>
        <div className="flex gap-3 justify-center mt-6">
          <button
            onClick={() => navigate(`/customer/claims/${submitClaim.data?.data?.claimId}`)}
            className="btn-primary"
          >
            View Claim Details
          </button>
          <button onClick={() => navigate('/customer/claims')} className="btn-secondary">
            View All Claims
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Duplicate warning modal */}
      {duplicates && (
        <DuplicateWarningModal
          duplicates={duplicates}
          onCreateNew={() => {
            setDuplicates(null)
            setStep(2)
          }}
          onGoToExisting={(claimId) => navigate(`/customer/claims/${claimId}`)}
        />
      )}

      <div>
        <h1 className="text-2xl font-bold text-gray-900">Submit a New Claim</h1>
        <p className="text-gray-500 mt-1">Complete the form below to file your insurance claim.</p>
      </div>

      {/* Stepper */}
      <div className="flex items-center gap-2">
        {STEPS.map((label, i) => (
          <React.Fragment key={i}>
            <div className={`flex items-center gap-2 ${i <= step ? 'text-primary-800' : 'text-gray-400'}`}>
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold border-2
                ${i < step ? 'bg-primary-800 border-primary-800 text-white'
                : i === step ? 'border-primary-800 text-primary-800'
                : 'border-gray-300 text-gray-400'}`}>
                {i < step ? '✓' : i + 1}
              </div>
              <span className="text-sm font-medium hidden sm:block">{label}</span>
            </div>
            {i < STEPS.length - 1 && <div className="flex-1 h-px bg-gray-200" />}
          </React.Fragment>
        ))}
      </div>

      <form onSubmit={(e) => e.preventDefault()}>
        <div className="card space-y-5">

          {/* Step 1 — Policy & Vehicle */}
          {step === 0 && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Policy Number <span className="text-red-500">*</span>
                </label>
                <input {...register('policyNumber')} placeholder="ABC-12345678" className="input" />
                {errors.policyNumber && (
                  <p className="text-red-500 text-xs mt-1">{errors.policyNumber.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Vehicle Registration <span className="text-red-500">*</span>
                </label>
                <input {...register('vehicleRegistration')} placeholder="e.g. TN01-1234" className="input" />
                {errors.vehicleRegistration && (
                  <p className="text-red-500 text-xs mt-1">{errors.vehicleRegistration.message}</p>
                )}
              </div>
            </>
          )}

          {/* Step 2 — Incident Details */}
          {step === 1 && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Incident Date <span className="text-red-500">*</span>
                </label>
                <input
                  type="date"
                  {...register('incidentDate')}
                  className="input"
                  max={new Date().toISOString().split('T')[0]}
                />
                {errors.incidentDate && (
                  <p className="text-red-500 text-xs mt-1">{errors.incidentDate.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Claim Type <span className="text-red-500">*</span>
                </label>
                <select {...register('claimType')} className="input">
                  <option value="">Select type…</option>
                  {CLAIM_TYPES.map((t) => (
                    <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                  ))}
                </select>
                {errors.claimType && (
                  <p className="text-red-500 text-xs mt-1">{errors.claimType.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Incident Location</label>
                <input
                  {...register('incidentLocation')}
                  placeholder="e.g. Highway 101, Exit 42"
                  className="input"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea
                  {...register('description')}
                  rows={4}
                  className="input resize-none"
                  placeholder="Describe what happened in detail…"
                />
              </div>
              <div className="flex items-center gap-3">
                <input
                  type="checkbox"
                  id="policeReport"
                  {...register('policeReportFiled')}
                  className="w-4 h-4 rounded border-gray-300 text-primary-800"
                />
                <label htmlFor="policeReport" className="text-sm text-gray-700">
                  Police report has been filed
                </label>
              </div>
              {formValues.policeReportFiled && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Police Report Number
                  </label>
                  <input {...register('policeReportNumber')} placeholder="PD-2024-001" className="input" />
                </div>
              )}
            </>
          )}

          {/* Step 3 — Review */}
          {step === 2 && (
            <div className="space-y-3">
              <h3 className="font-semibold text-gray-900">Review Your Claim</h3>
              <div className="bg-gray-50 rounded-lg p-4 space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">Policy Number</span>
                  <span className="font-medium">{formValues.policyNumber}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Vehicle</span>
                  <span className="font-medium">{formValues.vehicleRegistration}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Incident Date</span>
                  <span className="font-medium">{formValues.incidentDate}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Claim Type</span>
                  <span className="font-medium">{formValues.claimType?.replace(/_/g, ' ')}</span>
                </div>
                {formValues.incidentLocation && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Location</span>
                    <span className="font-medium">{formValues.incidentLocation}</span>
                  </div>
                )}
                {formValues.description && (
                  <div className="flex flex-col gap-0.5">
                    <span className="text-gray-500">Description</span>
                    <span className="font-medium text-xs bg-white border border-gray-100 rounded p-2">
                      {formValues.description}
                    </span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-gray-500">Police Report</span>
                  <span className="font-medium">{formValues.policeReportFiled ? 'Filed' : 'Not filed'}</span>
                </div>
              </div>
              {submitClaim.isError && (
                <div className="flex items-center gap-2 text-red-600 bg-red-50 p-3 rounded-lg text-sm">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  {(submitClaim.error as Error)?.message || 'Submission failed. Please try again.'}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Navigation */}
        <div className="flex justify-between mt-4">
          <button
            type="button"
            onClick={() => setStep((s) => s - 1)}
            disabled={step === 0}
            className="btn-secondary disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <ChevronLeft className="w-4 h-4 mr-1" /> Back
          </button>

          {step < STEPS.length - 1 ? (
            <button
              type="button"
              onClick={step === 1 ? handleStep1Continue : () => setStep((s) => s + 1)}
              disabled={isContinueDisabled || dupCheckMutation.isPending}
              className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {dupCheckMutation.isPending ? 'Checking…' : 'Continue'}
              <ChevronRight className="w-4 h-4 ml-1" />
            </button>
          ) : (
            <button
              type="button"
              onClick={handleFinalSubmit}
              disabled={submitClaim.isPending}
              className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {submitClaim.isPending ? 'Submitting…' : 'Submit Claim'}
            </button>
          )}
        </div>
      </form>
    </div>
  )
}
