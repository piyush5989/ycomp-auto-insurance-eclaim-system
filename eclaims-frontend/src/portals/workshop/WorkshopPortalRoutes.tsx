import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import WorkshopLayout from './WorkshopLayout';
import DashboardPage from './pages/DashboardPage';
import WorkOrderPage from './pages/WorkOrderPage';
import RepairUpdatePage from './pages/RepairUpdatePage';

export default function WorkshopPortalRoutes() {
  return (
    <WorkshopLayout>
      <Routes>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard"           element={<DashboardPage />} />
        <Route path="work-orders/new"     element={<WorkOrderPage />} />
        <Route path="work-orders/:id/update" element={<RepairUpdatePage />} />
      </Routes>
    </WorkshopLayout>
  );
}
