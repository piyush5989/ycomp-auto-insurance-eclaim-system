import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import CustomerLayout from './CustomerLayout';
import DashboardPage from './pages/DashboardPage';
import SubmitClaimPage from './pages/SubmitClaimPage';
import ClaimDetailPage from './pages/ClaimDetailPage';
import ClaimsListPage from './pages/ClaimsListPage';
import WorkshopSearchPage from './pages/WorkshopSearchPage';
import PaymentPage from './pages/PaymentPage';

export default function CustomerPortalRoutes() {
  return (
    <CustomerLayout>
      <Routes>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard"        element={<DashboardPage />} />
        <Route path="claims"           element={<ClaimsListPage />} />
        <Route path="claims/submit"    element={<SubmitClaimPage />} />
        <Route path="claims/:claimId"  element={<ClaimDetailPage />} />
        <Route path="workshops"        element={<WorkshopSearchPage />} />
        <Route path="payment/:claimId" element={<PaymentPage />} />
      </Routes>
    </CustomerLayout>
  );
}
