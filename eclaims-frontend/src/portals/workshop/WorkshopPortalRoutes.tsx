import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import WorkshopLayout from './WorkshopLayout'
import DashboardPage from './pages/DashboardPage'
import WorkOrderPage from './pages/WorkOrderPage'
import WorkOrdersListPage from './pages/WorkOrdersListPage'
import RepairUpdatePage from './pages/RepairUpdatePage'
import MyClaimsPage from './pages/MyClaimsPage'

export default function WorkshopPortalRoutes() {
  return (
    <WorkshopLayout>
      <Routes>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard"              element={<DashboardPage />} />
        <Route path="claims"                 element={<MyClaimsPage />} />
        <Route path="work-orders"            element={<WorkOrdersListPage />} />
        <Route path="work-orders/new"        element={<WorkOrderPage />} />
        <Route path="work-orders/:id/update" element={<RepairUpdatePage />} />
      </Routes>
    </WorkshopLayout>
  )
}
