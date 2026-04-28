import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { CheckCircle } from 'lucide-react';

const REPAIR_STATUSES = ['PENDING', 'IN_PROGRESS', 'PARTS_ORDERED', 'AWAITING_APPROVAL', 'COMPLETED'];

export default function RepairUpdatePage() {
  const { id: workOrderId } = useParams<{ id: string }>();
  const [status, setStatus] = useState('IN_PROGRESS');
  const [note, setNote] = useState('');

  const updateRepair = useMutation({
    mutationFn: () =>
      httpClient.patch(`/work-orders/${workOrderId}/repair-status`, null, {
        params: { status, note }
      }).then((r) => r.data),
  });

  if (updateRepair.isSuccess) {
    return (
      <div className="card max-w-lg mx-auto text-center py-12">
        <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-gray-900">Repair Status Updated</h2>
        <p className="text-gray-500 mt-2">Customer will be notified of the update.</p>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Update Repair Status</h1>
      <p className="text-gray-500 text-sm font-mono">Work Order: {workOrderId}</p>

      <div className="card space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Repair Status *</label>
          <select value={status} onChange={(e) => setStatus(e.target.value)} className="input">
            {REPAIR_STATUSES.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Status Note</label>
          <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3} className="input resize-none"
            placeholder="Add a note for the customer…" />
        </div>
        <button onClick={() => updateRepair.mutate()} disabled={updateRepair.isPending} className="btn-primary w-full justify-center">
          {updateRepair.isPending ? 'Updating…' : 'Update Status'}
        </button>
      </div>
    </div>
  );
}
