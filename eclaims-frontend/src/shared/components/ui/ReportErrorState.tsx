import React from 'react'
import { Lock, AlertOctagon, RefreshCw } from 'lucide-react'

type ApiLikeError = { statusCode?: number; message?: string; correlationId?: string }

interface ReportErrorStateProps {
  error: unknown
  /** What the user was trying to view, e.g. "KPI report", "regional reports". */
  resourceLabel: string
  onRetry?: () => void
}

const isForbidden = (err: ApiLikeError) => err.statusCode === 403 || err.statusCode === 401

/**
 * Friendly error card for report pages.
 * Distinguishes "you don't have access" (403) from generic load failures so users don't see all-zero tiles.
 */
export const ReportErrorState: React.FC<ReportErrorStateProps> = ({ error, resourceLabel, onRetry }) => {
  const err = (error ?? {}) as ApiLikeError
  const forbidden = isForbidden(err)

  return (
    <div className="card border-l-4 border-l-amber-500">
      <div className="flex items-start gap-4">
        <div
          className={
            forbidden
              ? 'p-3 rounded-lg bg-amber-100 text-amber-700'
              : 'p-3 rounded-lg bg-red-100 text-red-700'
          }
          aria-hidden="true"
        >
          {forbidden ? <Lock className="w-6 h-6" /> : <AlertOctagon className="w-6 h-6" />}
        </div>
        <div className="flex-1 space-y-2">
          <h3 className="text-base font-semibold text-gray-900">
            {forbidden ? `You don't have access to the ${resourceLabel}` : `Could not load the ${resourceLabel}`}
          </h3>
          {forbidden ? (
            <p className="text-sm text-gray-600">
              Your role is not authorised to view this report. Ask an administrator to grant the matching report
              permission, then refresh the page.
            </p>
          ) : (
            <p className="text-sm text-gray-600">
              {err.message || 'An unexpected error occurred while loading the report.'}{' '}
              Try again in a moment.
            </p>
          )}
          {err.correlationId && (
            <p className="text-xs text-gray-400">
              Correlation ID: <code className="font-mono">{err.correlationId}</code>
            </p>
          )}
          {!forbidden && onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="inline-flex items-center gap-1 text-sm font-medium text-primary-700 hover:text-primary-900"
              aria-label={`Retry loading ${resourceLabel}`}
            >
              <RefreshCw className="w-4 h-4" />
              Retry
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export default ReportErrorState
