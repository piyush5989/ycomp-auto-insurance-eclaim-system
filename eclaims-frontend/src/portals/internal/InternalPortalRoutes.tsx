import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import InternalLayout from './InternalLayout';
import DashboardPage from './pages/DashboardPage';
import ClaimsQueuePage from './pages/ClaimsQueuePage';
import ClaimDetailPage from './pages/ClaimDetailPage';
import AuditViewPage from './pages/AuditViewPage';
import ClaimsKpiPage from './pages/reporting/ClaimsKpiPage';
import FraudAgeingPage from './pages/reporting/FraudAgeingPage';
import RegionalReportPage from './pages/reporting/RegionalReportPage';
import ManagementReportPage from './pages/reporting/ManagementReportPage';
import MyAssignmentsPage from './pages/surveyor/MyAssignmentsPage';
import AssessClaimPage from './pages/surveyor/AssessClaimPage';

export default function InternalPortalRoutes() {
  return (
    <InternalLayout>
      <Routes>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard"           element={<DashboardPage />} />
        <Route path="claims-queue"        element={<ClaimsQueuePage />} />
        <Route path="claims/:claimId"     element={<ClaimDetailPage />} />
        <Route path="audit"               element={<AuditViewPage />} />
        <Route path="reports/kpi"         element={<ClaimsKpiPage />} />
        <Route path="reports/fraud"       element={<FraudAgeingPage />} />
        <Route path="reports/regional"    element={<RegionalReportPage />} />
        <Route path="reports/management"  element={<ManagementReportPage />} />
        <Route path="surveyor/my-assignments" element={<MyAssignmentsPage />} />
        <Route path="surveyor/assess/:claimId" element={<AssessClaimPage />} />
      </Routes>
    </InternalLayout>
  );
}
