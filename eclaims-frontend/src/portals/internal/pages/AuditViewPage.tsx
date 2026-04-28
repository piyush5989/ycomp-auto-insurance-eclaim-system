import React from 'react';
import { Shield } from 'lucide-react';

export default function AuditViewPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Audit Log</h1>
        <p className="text-gray-500 mt-1">Immutable, append-only audit trail. 7-year retention for regulatory compliance.</p>
      </div>
      <div className="card flex items-center gap-4 border-l-4 border-l-purple-500">
        <Shield className="w-8 h-8 text-purple-600 flex-shrink-0" />
        <div>
          <p className="font-medium text-gray-900">Audit trail is written to the <code className="bg-gray-100 px-1.5 py-0.5 rounded text-sm">audit.audit_log</code> table</p>
          <p className="text-sm text-gray-500 mt-1">Every claim action (submit, approve, reject, pay) generates an immutable audit event via the Kafka <code className="bg-gray-100 px-1 rounded text-xs">audit-events</code> topic with oldValue/newValue JSON snapshots, IP address, and session ID for fraud investigation.</p>
        </div>
      </div>
    </div>
  );
}
