import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { PlusCircle, Wrench } from 'lucide-react';

export default function DashboardPage() {
  const { username } = useAuth();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Welcome, {username}</h1>
        <p className="text-gray-500 mt-1">Manage repair work orders and update claim repair statuses.</p>
      </div>

      <div className="grid grid-cols-2 gap-5">
        <Link to="/workshop/work-orders/new" className="card hover:shadow-md transition-shadow flex items-center gap-4">
          <div className="p-3 bg-blue-100 rounded-lg"><PlusCircle className="w-6 h-6 text-blue-700" /></div>
          <div>
            <h3 className="font-semibold text-gray-900">Submit Work Order</h3>
            <p className="text-sm text-gray-500">Create a repair estimate for an approved claim</p>
          </div>
        </Link>
        <div className="card flex items-center gap-4 opacity-60">
          <div className="p-3 bg-green-100 rounded-lg"><Wrench className="w-6 h-6 text-green-700" /></div>
          <div>
            <h3 className="font-semibold text-gray-900">Active Work Orders</h3>
            <p className="text-sm text-gray-500">Update repair status for in-progress jobs</p>
          </div>
        </div>
      </div>
    </div>
  );
}
