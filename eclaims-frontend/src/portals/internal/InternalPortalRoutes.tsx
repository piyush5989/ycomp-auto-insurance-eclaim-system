import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { KeyByClaimPage } from '@/shared/components/routing/KeyByClaimPage';
import InternalLayout from './InternalLayout';
import { ProtectedRoute } from '@/shared/components/layout/ProtectedRoute';
import DashboardPage from './pages/DashboardPage';
import ClaimsQueuePage from './pages/ClaimsQueuePage';
import ClaimDetailPage from './pages/ClaimDetailPage';
import AuditViewPage from './pages/AuditViewPage';
import ClaimsKpiPage from './pages/reporting/ClaimsKpiPage';
import FraudAgeingPage from './pages/reporting/FraudAgeingPage';
import RegionalReportPage from './pages/reporting/RegionalReportPage';
import ManagementReportPage from './pages/reporting/ManagementReportPage';
import CaseManagerReportPage from './pages/reporting/CaseManagerReportPage';
import MyAssignmentsPage from './pages/surveyor/MyAssignmentsPage';
import AssessClaimPage from './pages/surveyor/AssessClaimPage';
import AdjustorMyClaimsPage from './pages/adjustor/MyClaimsPage';
import AdjudicateClaimPage from './pages/adjustor/AdjudicateClaimPage';
import { UserRole } from '@/shared/types/UserRole';

/**
 * Internal portal routes with role-based access control.
 *
 * Report access matrix:
 *   /reports/my-claims    → CASE_MANAGER only (their own portfolio)
 *   /reports/kpi          → CASE_MANAGER, AUDITOR (organisation-wide KPI)
 *   /reports/fraud        → CASE_MANAGER, AUDITOR
 *   /reports/regional     → REGIONAL_MGR (their region)
 *   /reports/management   → TOP_MANAGEMENT only (all regions)
 */
export default function InternalPortalRoutes() {
  return (
    <InternalLayout>
      <Routes>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard"       element={<DashboardPage />} />
        <Route path="claims-queue"    element={<ClaimsQueuePage />} />
        <Route path="claims/:claimId" element={<KeyByClaimPage Page={ClaimDetailPage} />} />
        <Route path="audit"           element={<AuditViewPage />} />

        {/* ── Case Manager: personal portfolio report ── */}
        <Route
          path="reports/my-claims"
          element={
            <ProtectedRoute requiredRoles={[UserRole.CASE_MANAGER]}>
              <CaseManagerReportPage />
            </ProtectedRoute>
          }
        />

        {/* ── Global KPI + Fraud: Case Manager and Auditor ── */}
        <Route
          path="reports/kpi"
          element={
            <ProtectedRoute requiredRoles={[UserRole.CASE_MANAGER, UserRole.AUDITOR]}>
              <ClaimsKpiPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="reports/fraud"
          element={
            <ProtectedRoute requiredRoles={[UserRole.CASE_MANAGER, UserRole.AUDITOR]}>
              <FraudAgeingPage />
            </ProtectedRoute>
          }
        />

        {/* ── Regional Manager: region-level dashboard ── */}
        <Route
          path="reports/regional"
          element={
            <ProtectedRoute requiredRoles={[UserRole.REGIONAL_MGR, UserRole.TOP_MANAGEMENT]}>
              <RegionalReportPage />
            </ProtectedRoute>
          }
        />

        {/* ── Top Management: all-region comparison ── */}
        <Route
          path="reports/management"
          element={
            <ProtectedRoute requiredRoles={[UserRole.TOP_MANAGEMENT]}>
              <ManagementReportPage />
            </ProtectedRoute>
          }
        />

        {/* ── Surveyor workflow ── */}
        <Route path="surveyor/my-assignments"      element={<MyAssignmentsPage />} />
        <Route path="surveyor/assess/:claimId"     element={<KeyByClaimPage Page={AssessClaimPage} />} />

        {/* ── Adjustor workflow ── */}
        <Route path="adjustor/my-claims"           element={<AdjustorMyClaimsPage />} />
        <Route path="adjustor/adjudicate/:claimId" element={<KeyByClaimPage Page={AdjudicateClaimPage} />} />
      </Routes>
    </InternalLayout>
  );
}
