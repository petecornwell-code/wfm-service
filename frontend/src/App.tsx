import { useState, useEffect } from 'react'
import { Routes, Route, Link, useParams, Navigate, useLocation } from 'react-router-dom'
import { desks as desksApi, getTenantId, setTenantId } from './api/client'
import { ToastContainer } from './components/Toast'
import DeskSelector from './pages/DeskSelector'
import DeskManagement from './pages/DeskManagement'
import Specializations from './pages/Specializations'
import ShiftLibrary from './pages/ShiftLibrary'
import DeskAgents from './pages/DeskAgents'
import AgentPreferences from './pages/AgentPreferences'
import AgentExceptions from './pages/AgentExceptions'
import StaffingRequirements from './pages/StaffingRequirements'
import ConstraintWeightsPage from './pages/ConstraintWeightsPage'
import ScheduleSetup from './pages/ScheduleSetup'
import ScheduleResults from './pages/ScheduleResults'
import Configuration from './pages/Configuration'
import ClientManagement from './pages/ClientManagement'

function DeskLayout() {
  const { deskId } = useParams()
  const location = useLocation()
  const [deskName, setDeskName] = useState<string>('')

  useEffect(() => {
    if (deskId) {
      desksApi.get(deskId).then(d => setDeskName(d.name)).catch(() => {})
    }
  }, [deskId])

  const isActive = (path: string) => location.pathname.includes(path)

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <h2>{deskName || 'WFM Service'}</h2>
        <nav>
          <Link to="/">Switch Desk</Link>
          <Link to="/desk-management">Desk Management</Link>
          <Link to={`/desks/${deskId}/schedule-setup`} className={isActive('schedule-setup') ? 'active' : ''}>Schedule Setup</Link>
          <Link to={`/desks/${deskId}/agents`} className={isActive('/agents') && !isActive('preferences') && !isActive('exceptions') ? 'active' : ''}>Desk Agents</Link>
          <Link to={`/desks/${deskId}/specializations`} className={isActive('specializations') ? 'active' : ''}>Specializations</Link>
          <Link to={`/desks/${deskId}/shift-library`} className={isActive('shift-library') ? 'active' : ''}>Shift Library</Link>
          <Link to={`/desks/${deskId}/staffing`} className={isActive('staffing') ? 'active' : ''}>Staffing Requirements</Link>
          <Link to={`/desks/${deskId}/constraint-weights`} className={isActive('constraint-weights') ? 'active' : ''}>Constraint Weights</Link>
        </nav>
      </aside>
      <main className="main-content">
        <Routes>
          <Route index element={<Navigate to="schedule-setup" replace />} />
          <Route path="schedule-setup" element={<ScheduleSetup />} />
          <Route path="agents" element={<DeskAgents />} />
          <Route path="agents/:agentId/preferences" element={<AgentPreferences />} />
          <Route path="agents/:agentId/exceptions" element={<AgentExceptions />} />
          <Route path="specializations" element={<Specializations />} />
          <Route path="shift-library" element={<ShiftLibrary />} />
          <Route path="staffing" element={<StaffingRequirements />} />
          <Route path="constraint-weights" element={<ConstraintWeightsPage />} />
          <Route path="schedules/:scheduleId" element={<ScheduleResults />} />
        </Routes>
      </main>
    </div>
  )
}

function TenantSelector() {
  const [tenantId, setTenant] = useState(getTenantId())
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 1rem', background: '#f3f4f6', borderBottom: '1px solid #e5e7eb', fontSize: '0.85rem' }}>
      <label style={{ fontWeight: 500 }}>Tenant ID:</label>
      <input
        value={tenantId}
        onChange={e => { setTenant(e.target.value); setTenantId(e.target.value) }}
        style={{ width: '80px', padding: '0.2rem 0.4rem', border: '1px solid #d1d5db', borderRadius: '4px' }}
      />
    </div>
  )
}

function App() {
  return (
    <>
      <TenantSelector />
      <ToastContainer />
      <Routes>
        <Route path="/" element={<DeskSelector />} />
        <Route path="/desk-management" element={<DeskManagement />} />
        <Route path="/configuration" element={<Configuration />} />
        <Route path="/client-management" element={<ClientManagement />} />
        <Route path="/desks/:deskId/*" element={<DeskLayout />} />
      </Routes>
    </>
  )
}

export default App
