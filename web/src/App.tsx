import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { AdminShell } from './layouts/AdminShell'
import { FieldShell } from './layouts/FieldShell'
import { RequireAuth } from './auth/RequireAuth'
import { AlarmPage } from './pages/AlarmPage'
import { AuthLayout } from './pages/auth/AuthLayout'
import { ApprovalsPage } from './pages/auth/ApprovalsPage'
import { MyRequestsPage } from './pages/auth/MyRequestsPage'
import { PermsPage } from './pages/auth/PermsPage'
import { RolesPage } from './pages/auth/RolesPage'
import { UsersPage } from './pages/auth/UsersPage'
import { AccountPage } from './pages/account/AccountPage'
import { ForcePasswordPage } from './pages/account/ForcePasswordPage'
import { CarrierPage } from './pages/CarrierPage'
import { DashboardPage } from './pages/DashboardPage'
import { DispatchPage } from './pages/DispatchPage'
import { EquipmentPage } from './pages/EquipmentPage'
import { HistoryPage } from './pages/HistoryPage'
import { HoldPage } from './pages/HoldPage'
import { ReportPage } from './pages/ReportPage'
import { LoginPage } from './pages/LoginPage'
import { LotsPage } from './pages/LotsPage'
import { EdcPage } from './pages/EdcPage'
import { RecipePage } from './pages/RecipePage'
import { SpcPage } from './pages/SpcPage'
import { RouteEditorPage } from './pages/RouteEditorPage'
import { RoutePage } from './pages/RoutePage'
import { TrackPage } from './pages/TrackPage'
import { WipPage } from './pages/WipPage'

function AdminOrForce() {
  const location = useLocation()
  if (location.pathname === '/app/account/password') {
    return <Outlet />
  }
  return <AdminShell />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/app"
        element={
          <RequireAuth>
            <AdminOrForce />
          </RequireAuth>
        }
      >
        <Route path="account/password" element={<ForcePasswordPage />} />
        <Route index element={<Navigate to="/app/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="lots" element={<LotsPage />} />
        <Route path="wip" element={<WipPage />} />
        <Route path="equipment" element={<EquipmentPage />} />
        <Route path="carrier" element={<CarrierPage />} />
        <Route path="dispatch" element={<DispatchPage />} />
        <Route path="recipe" element={<RecipePage />} />
        <Route path="edc" element={<EdcPage />} />
        <Route path="spc" element={<SpcPage />} />
        <Route path="route">
          <Route index element={<RoutePage />} />
          <Route path=":routeId" element={<RouteEditorPage />} />
        </Route>
        <Route path="hold" element={<HoldPage />} />
        <Route path="alarm" element={<AlarmPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="report" element={<ReportPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="auth" element={<AuthLayout />}>
          <Route index element={<Navigate to="users" replace />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="roles" element={<RolesPage />} />
          <Route path="perms" element={<PermsPage />} />
          <Route path="requests" element={<MyRequestsPage />} />
          <Route path="approvals" element={<ApprovalsPage />} />
        </Route>
      </Route>
      <Route
        path="/track"
        element={
          <RequireAuth>
            <FieldShell />
          </RequireAuth>
        }
      >
        <Route index element={<TrackPage />} />
        <Route path="lots" element={<LotsPage />} />
        <Route path="eqp" element={<EquipmentPage />} />
        <Route path="hold" element={<HoldPage />} />
      </Route>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
