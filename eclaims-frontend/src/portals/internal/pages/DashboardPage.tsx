import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { getRoleLabel } from '@/shared/auth/roleUtils';
import { List, BarChart2, Shield, AlertTriangle } from 'lucide-react';

export default function DashboardPage() {
  const { username, roles } = useAuth();
  const primaryRole = roles[0] ? getRoleLabel(roles[0]) : 'Internal User';

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Welcome, {username}</h1>
        <p className="text-gray-500 mt-1">Role: <span className="font-medium">{primaryRole}</span></p>
      </div>

      <div className="grid grid-cols-2 gap-5">
        <Link to="/internal/claims-queue" className="card hover:shadow-md transition-shadow flex items-center gap-4">
          <div className="p-3 bg-blue-100 rounded-lg"><List className="w-6 h-6 text-blue-700" /></div>
          <div>
            <h3 className="font-semibold text-gray-900">Claims Queue</h3>
            <p className="text-sm text-gray-500">Review and process pending claims</p>
          </div>
        </Link>
        <Link to="/internal/reports/kpi" className="card hover:shadow-md transition-shadow flex items-center gap-4">
          <div className="p-3 bg-green-100 rounded-lg"><BarChart2 className="w-6 h-6 text-green-700" /></div>
          <div>
            <h3 className="font-semibold text-gray-900">KPI Reports</h3>
            <p className="text-sm text-gray-500">Claims performance and settlement metrics</p>
          </div>
        </Link>
        <Link to="/internal/reports/fraud" className="card hover:shadow-md transition-shadow flex items-center gap-4">
          <div className="p-3 bg-red-100 rounded-lg"><AlertTriangle className="w-6 h-6 text-red-700" /></div>
          <div>
            <h3 className="font-semibold text-gray-900">Fraud Ageing</h3>
            <p className="text-sm text-gray-500">Monitor flagged claims by age bucket</p>
          </div>
        </Link>
        <Link to="/internal/audit" className="card hover:shadow-md transition-shadow flex items-center gap-4">
          <div className="p-3 bg-purple-100 rounded-lg"><Shield className="w-6 h-6 text-purple-700" /></div>
          <div>
            <h3 className="font-semibold text-gray-900">Audit Log</h3>
            <p className="text-sm text-gray-500">Immutable audit trail for compliance</p>
          </div>
        </Link>
      </div>
    </div>
  );
}
