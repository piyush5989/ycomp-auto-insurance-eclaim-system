import React from 'react';
import { useMutation } from '@tanstack/react-query';
import { httpClient } from '@/shared/api/httpClient';
import { CheckCircle } from 'lucide-react';

export default function WorkOrderPage() {
  const [form, setForm] = React.useState({ claimId: '', workshopId: '', estimatedCost: '', workDescription: '' });

  const submitWorkOrder = useMutation({
    mutationFn: () => httpClient.post('/work-orders', {
      claimId: form.claimId,
      workshopId: form.workshopId,
      estimatedCost: parseFloat(form.estimatedCost),
      workDescription: form.workDescription,
    }).then((r) => r.data),
  });

  if (submitWorkOrder.isSuccess) {
    return (
      <div className="card max-w-lg mx-auto text-center py-12">
        <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-gray-900">Work Order Submitted</h2>
        <p className="text-gray-500 mt-2">Work Order ID: <code className="bg-gray-100 px-2 py-0.5 rounded text-sm">{submitWorkOrder.data?.data?.workOrderId}</code></p>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Submit Work Order</h1>

      <div className="card space-y-4">
        {[
          { label: 'Claim ID', key: 'claimId', placeholder: 'UUID of approved claim' },
          { label: 'Workshop ID', key: 'workshopId', placeholder: 'Your workshop UUID' },
          { label: 'Estimated Cost (USD)', key: 'estimatedCost', placeholder: '0.00', type: 'number' },
        ].map(({ label, key, placeholder, type }) => (
          <div key={key}>
            <label className="block text-sm font-medium text-gray-700 mb-1">{label} *</label>
            <input type={type ?? 'text'} value={form[key as keyof typeof form]}
              onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
              placeholder={placeholder} className="input" />
          </div>
        ))}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Work Description</label>
          <textarea value={form.workDescription} onChange={(e) => setForm((f) => ({ ...f, workDescription: e.target.value }))}
            rows={4} className="input resize-none" placeholder="Describe the repair work required…" />
        </div>

        <button onClick={() => submitWorkOrder.mutate()}
          disabled={!form.claimId || !form.workshopId || !form.estimatedCost || submitWorkOrder.isPending}
          className="btn-primary w-full justify-center">
          {submitWorkOrder.isPending ? 'Submitting…' : 'Submit Work Order'}
        </button>
      </div>
    </div>
  );
}
