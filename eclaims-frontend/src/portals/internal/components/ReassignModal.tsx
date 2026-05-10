import React, { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { httpClient } from '@/shared/api/httpClient'
import { X, UserCheck } from 'lucide-react'

interface ReassignModalProps {
  claimId: string
  type: 'surveyor' | 'adjustor'
  currentAssignee: string | null
  region?: string
  onClose: () => void
}

interface SurveyorResponse {
  id: string
  name: string
  email: string
  region: string
  active: boolean
}

export const ReassignModal: React.FC<ReassignModalProps> = ({
  claimId,
  type,
  currentAssignee,
  region,
  onClose,
}) => {
  const queryClient = useQueryClient()
  const [newUserId, setNewUserId] = useState('')
  const [reason, setReason] = useState('')

  const { data: users = [], isLoading } = useQuery<SurveyorResponse[]>({
    queryKey: ['workflow', type === 'surveyor' ? 'surveyors' : 'adjustors', region],
    queryFn: () => {
      const params = region ? `?region=${region}` : ''
      return httpClient.get(`/workflow/surveyors${params}`).then((r) => r.data.data)
    },
  })

  const reassignMutation = useMutation({
    mutationFn: () =>
      httpClient.post(`/claims/${claimId}/reassign-${type}`, {
        newUserId,
        reason,
      }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['claim', claimId] })
      queryClient.invalidateQueries({ queryKey: ['internal-claims'] })
      onClose()
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (newUserId && reason) {
      reassignMutation.mutate()
    }
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        <div className="flex items-center justify-between p-6 border-b">
          <div className="flex items-center gap-3">
            <UserCheck className="w-5 h-5 text-primary-800" />
            <h2 className="text-lg font-semibold text-gray-900">
              Reassign {type === 'surveyor' ? 'Surveyor' : 'Adjustor'}
            </h2>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {currentAssignee && (
            <div className="bg-blue-50 border border-blue-200 rounded p-3">
              <p className="text-sm text-blue-800">
                <strong>Current assignee:</strong> {currentAssignee}
              </p>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Select New {type === 'surveyor' ? 'Surveyor' : 'Adjustor'} <span className="text-red-500">*</span>
            </label>
            {isLoading ? (
              <p className="text-sm text-gray-500">Loading...</p>
            ) : (
              <select
                value={newUserId}
                onChange={(e) => setNewUserId(e.target.value)}
                className="input"
                required
              >
                <option value="">-- Select --</option>
                {users.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.name} ({user.region})
                  </option>
                ))}
              </select>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Reason for Reassignment <span className="text-red-500">*</span>
            </label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              placeholder="e.g., Original assignee unavailable, workload balancing..."
              className="input resize-none"
              required
            />
          </div>

          <div className="flex gap-3 pt-2">
            <button
              type="submit"
              disabled={!newUserId || !reason || reassignMutation.isPending}
              className="btn-primary flex-1"
            >
              Reassign
            </button>
            <button
              type="button"
              onClick={onClose}
              className="btn-secondary flex-1"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
