import React, { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/shared/auth/KeycloakProvider';
import { getPortalPath } from '@/shared/auth/roleUtils';
import { Shield, LogIn } from 'lucide-react';

export default function LoginPage() {
  const { authenticated, roles, keycloak } = useAuth();
  const navigate = useNavigate();
  const params = new URLSearchParams(window.location.search);
  const isUnauthorised = params.get('error') === 'unauthorized';

  useEffect(() => {
    if (authenticated && roles.length > 0) {
      navigate(getPortalPath(roles), { replace: true });
    }
  }, [authenticated, roles, navigate]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-800 to-primary-900 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-primary-100 rounded-full mb-4">
            <Shield className="w-8 h-8 text-primary-800" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">eClaims</h1>
          <p className="text-gray-500 text-sm mt-2">Digital Insurance Claims Management</p>
        </div>

        {isUnauthorised && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg p-3 text-sm mb-6">
            You don't have permission to access that portal. Please contact your administrator.
          </div>
        )}

        <button
          onClick={() => keycloak?.login()}
          className="btn-primary w-full justify-center text-base py-3"
        >
          <LogIn className="w-5 h-5 mr-2" />
          Sign In with Keycloak
        </button>

        <p className="text-center text-sm text-gray-500 mt-5">
          New customer?{' '}
          <Link to="/register" className="text-primary-700 font-medium hover:underline">
            Register with your policy details
          </Link>
        </p>

        <div className="mt-6 p-4 bg-gray-50 rounded-lg">
          <p className="text-xs text-gray-500 font-medium mb-2">Demo Accounts (password: Test@1234)</p>
          <div className="space-y-1">
            {[
              ['customer1', 'Customer Portal'],
              ['surveyor1', 'Internal (Surveyor)'],
              ['adjustor1', 'Internal (Adjustor)'],
              ['casemanager1', 'Internal (Case Manager)'],
              ['auditor1', 'Internal (Auditor)'],
              ['workshop1', 'Workshop Portal'],
            ].map(([user, portal]) => (
              <div key={user} className="flex justify-between text-xs text-gray-600">
                <code className="bg-white px-1.5 py-0.5 rounded border border-gray-200">{user}</code>
                <span className="text-gray-400">{portal}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
