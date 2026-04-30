import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { Shield, Search, ChevronDown, ChevronRight } from 'lucide-react'
import { format } from 'date-fns'

interface AuditEvent {
  eventId: string
  correlationId: string
  userId: string
  userRole: string
  action: string
  entityType: string
  entityId: string
  oldValue: string | null
  newValue: string | null
  ipAddress: string | null
  reason: string | null
  userAgent: string | null
  timestamp: string
}

export default function AuditViewPage() {
  const [claimIdFilter, setClaimIdFilter] = useState('')
  const [searchClaimId, setSearchClaimId] = useState('')
  const [expandedEvent, setExpandedEvent] = useState<string | null>(null)

  const { data: auditEvents = [], isLoading } = useQuery<AuditEvent[]>({
    queryKey: ['audit-events', searchClaimId],
    queryFn: () => {
      if (!searchClaimId) return Promise.resolve([])
      return httpClient.get(`/audit/events?claimId=${searchClaimId}`).then((r) => r.data.data ?? [])
    },
    enabled: !!searchClaimId,
  })

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    if (claimIdFilter.trim()) {
      setSearchClaimId(claimIdFilter.trim())
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Audit Log</h1>
        <p className="text-gray-500 mt-1">
          Immutable, append-only audit trail. 7-year retention for regulatory compliance.
        </p>
      </div>

      <div className="card flex items-center gap-4 border-l-4 border-l-purple-500">
        <Shield className="w-8 h-8 text-purple-600 flex-shrink-0" />
        <div>
          <p className="font-medium text-gray-900">
            Audit trail is written to the{' '}
            <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">audit.audit_log</code> table
          </p>
          <p className="text-sm text-gray-500 mt-1">
            Every claim action (submit, approve, reject, pay) generates an immutable audit event via the
            Kafka <code className="bg-gray-100 px-1 rounded text-xs">audit-events</code> topic with
            oldValue/newValue JSON snapshots, IP address, and session ID for fraud investigation.
          </p>
        </div>
      </div>

      {/* Search Form */}
      <form onSubmit={handleSearch} className="card">
        <h3 className="text-sm font-semibold text-gray-900 mb-3">Search Audit Events</h3>
        <div className="flex gap-3">
          <div className="flex-1">
            <label className="block text-xs font-medium text-gray-700 mb-1">Claim ID</label>
            <input
              type="text"
              value={claimIdFilter}
              onChange={(e) => setClaimIdFilter(e.target.value)}
              placeholder="Enter claim ID (UUID)"
              className="input"
            />
          </div>
          <div className="flex items-end">
            <button type="submit" className="btn-primary flex items-center gap-2">
              <Search className="w-4 h-4" />
              Search
            </button>
          </div>
        </div>
      </form>

      {/* Audit Events List */}
      {isLoading && (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-800" />
        </div>
      )}

      {!isLoading && searchClaimId && auditEvents.length === 0 && (
        <div className="card text-center py-12">
          <p className="text-gray-500">No audit events found for claim ID: {searchClaimId}</p>
          <p className="text-xs text-gray-400 mt-2">
            Note: This is a placeholder. In production, audit events would be retrieved from the audit.audit_log table.
          </p>
        </div>
      )}

      {!isLoading && auditEvents.length > 0 && (
        <div className="space-y-3">
          <p className="text-sm font-medium text-gray-700">
            Found {auditEvents.length} audit event(s) for claim {searchClaimId}
          </p>
          {auditEvents.map((event) => (
            <div key={event.eventId} className="card border-l-4 border-l-purple-400">
              <div
                className="flex items-center justify-between cursor-pointer"
                onClick={() =>
                  setExpandedEvent(expandedEvent === event.eventId ? null : event.eventId)
                }
              >
                <div className="flex items-center gap-3">
                  {expandedEvent === event.eventId ? (
                    <ChevronDown className="w-4 h-4 text-gray-400" />
                  ) : (
                    <ChevronRight className="w-4 h-4 text-gray-400" />
                  )}
                  <div>
                    <p className="text-sm font-medium text-gray-900">{event.action}</p>
                    <p className="text-xs text-gray-500">
                      {format(new Date(event.timestamp), 'MMM dd, yyyy HH:mm:ss')} · {event.userId} ·{' '}
                      {event.userRole}
                    </p>
                  </div>
                </div>
                <span className="text-xs font-mono text-gray-400">{event.eventId.substring(0, 8)}…</span>
              </div>

              {expandedEvent === event.eventId && (
                <div className="mt-4 pt-4 border-t border-gray-200 space-y-3">
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <span className="text-gray-500">Entity Type:</span>{' '}
                      <span className="font-medium text-gray-900">{event.entityType}</span>
                    </div>
                    <div>
                      <span className="text-gray-500">Entity ID:</span>{' '}
                      <span className="font-mono text-gray-900">{event.entityId}</span>
                    </div>
                    <div>
                      <span className="text-gray-500">Correlation ID:</span>{' '}
                      <span className="font-mono text-gray-900">{event.correlationId}</span>
                    </div>
                    <div>
                      <span className="text-gray-500">IP Address:</span>{' '}
                      <span className="font-mono text-gray-900">{event.ipAddress || 'N/A'}</span>
                    </div>
                  </div>

                  {event.reason && (
                    <div>
                      <p className="text-xs text-gray-500 mb-1">Reason:</p>
                      <p className="text-sm text-gray-900">{event.reason}</p>
                    </div>
                  )}

                  {event.oldValue && (
                    <div>
                      <p className="text-xs text-gray-500 mb-1">Old Value:</p>
                      <pre className="bg-gray-50 p-2 rounded text-xs overflow-auto">
                        {event.oldValue}
                      </pre>
                    </div>
                  )}

                  {event.newValue && (
                    <div>
                      <p className="text-xs text-gray-500 mb-1">New Value:</p>
                      <pre className="bg-gray-50 p-2 rounded text-xs overflow-auto">
                        {event.newValue}
                      </pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {!searchClaimId && (
        <div className="card text-center py-12 bg-gray-50">
          <Shield className="w-12 h-12 text-gray-400 mx-auto mb-3" />
          <p className="text-gray-600">Enter a claim ID above to view its audit trail</p>
        </div>
      )}
    </div>
  )
}
