import { Routes, Route, Link, useParams, Navigate } from 'react-router-dom'
import DeskSelector from './pages/DeskSelector'
import DeskManagement from './pages/DeskManagement'
import Specializations from './pages/Specializations'
import DeskAgents from './pages/DeskAgents'
import AgentPreferences from './pages/AgentPreferences'
import AgentExceptions from './pages/AgentExceptions'
import StaffingRequirements from './pages/StaffingRequirements'
import ConstraintWeightsPage from './pages/ConstraintWeightsPage'
import ScheduleSetup from './pages/ScheduleSetup'
import ScheduleResults from './pages/ScheduleResults'

function DeskLayout() {
  const { deskId } = useParams()

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <h2>WFM Service</h2>
        <nav>
          <Link to="/">Switch Desk</Link>
          <Link to={`/desks/${deskId}/schedule-setup`}>Schedule Setup</Link>
          <Link to={`/desks/${deskId}/agents`}>Desk Agents</Link>
          <Link to={`/desks/${deskId}/specializations`}>Specializations</Link>
          <Link to={`/desks/${deskId}/staffing`}>Staffing Requirements</Link>
          <Link to={`/desks/${deskId}/constraint-weights`}>Constraint Weights</Link>
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
          <Route path="staffing" element={<StaffingRequirements />} />
          <Route path="constraint-weights" element={<ConstraintWeightsPage />} />
          <Route path="schedules/:scheduleId" element={<ScheduleResults />} />
        </Routes>
      </main>
    </div>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<DeskSelector />} />
      <Route path="/desk-management" element={<DeskManagement />} />
      <Route path="/desks/:deskId/*" element={<DeskLayout />} />
    </Routes>
  )
}

export default App
