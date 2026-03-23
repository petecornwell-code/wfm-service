import { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { clientManagement, desks as desksApi, deskAgents, type BambooEmployeeResponse, type Desk, type DeskAgent, type DeskAssignmentUploadResult, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

export default function ClientManagement() {
  const [department, setDepartment] = useState('')
  const [employees, setEmployees] = useState<BambooEmployeeResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [hasMore, setHasMore] = useState(false)
  const [totalCount, setTotalCount] = useState(0)
  const [searched, setSearched] = useState(false)

  // Desk assignment
  const [deskList, setDeskList] = useState<Desk[]>([])
  const [selectedDeskId, setSelectedDeskId] = useState('')
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<Set<string>>(new Set())
  const [assigning, setAssigning] = useState(false)

  // Desk agents (for remove feature)
  const [viewDeskId, setViewDeskId] = useState('')
  const [deskAgentList, setDeskAgentList] = useState<DeskAgent[]>([])
  const [loadingAgents, setLoadingAgents] = useState(false)

  // Desk assignment upload
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    desksApi.list().then(setDeskList).catch(() => {})
  }, [])

  const fetchEmployees = async (page = 1, refresh = false) => {
    if (!department.trim()) {
      showToast('error', 'Please enter a department name')
      return
    }
    setLoading(true)
    try {
      const res = await clientManagement.listEmployees(department.trim(), page, pageSize, refresh)
      setEmployees(res.data)
      setHasMore(res.hasMore)
      setTotalCount(res.totalCount)
      setCurrentPage(page)
      setSearched(true)
      setSelectedEmployeeIds(new Set())
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = () => {
    setCurrentPage(1)
    fetchEmployees(1, true)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch()
  }

  const toggleEmployee = (id: string) => {
    setSelectedEmployeeIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleAll = () => {
    if (selectedEmployeeIds.size === employees.length) {
      setSelectedEmployeeIds(new Set())
    } else {
      setSelectedEmployeeIds(new Set(employees.map(e => e.id)))
    }
  }

  const handleAssignToDesk = async () => {
    if (!selectedDeskId) {
      showToast('error', 'Please select a desk')
      return
    }
    if (selectedEmployeeIds.size === 0) {
      showToast('error', 'Please select at least one employee')
      return
    }
    setAssigning(true)
    try {
      const assigned = await clientManagement.assignToDesk(selectedDeskId, Array.from(selectedEmployeeIds))
      showToast('success', `${assigned.length} agent(s) assigned to desk`)
      setSelectedEmployeeIds(new Set())
      // Refresh desk agents if viewing the same desk
      if (viewDeskId === selectedDeskId) {
        loadDeskAgents(viewDeskId)
      }
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setAssigning(false)
    }
  }

  const loadDeskAgents = async (deskId: string) => {
    if (!deskId) {
      setDeskAgentList([])
      return
    }
    setLoadingAgents(true)
    try {
      const res = await deskAgents.list(deskId)
      setDeskAgentList(res.data)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoadingAgents(false)
    }
  }

  const handleViewDeskChange = (deskId: string) => {
    setViewDeskId(deskId)
    loadDeskAgents(deskId)
  }

  const handleRemoveAgent = async (agentId: string, agentName: string) => {
    if (!viewDeskId || !confirm(`Remove ${agentName} from this desk?`)) return
    try {
      await clientManagement.removeAgentFromDesk(viewDeskId, agentId)
      setDeskAgentList(prev => prev.filter(a => a.id !== agentId))
      showToast('success', `${agentName} removed from desk`)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const handleUploadDeskAssignments = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const result: DeskAssignmentUploadResult = await clientManagement.uploadDeskAssignments(file)
      showToast('success', `Desk assignments: ${result.assignedCount} assigned, ${result.skippedCount} skipped`)
      if (result.skippedDetails.length > 0) {
        console.warn('Skipped rows:', result.skippedDetails)
      }
      // Refresh desk agents view if a desk is selected
      if (viewDeskId) {
        loadDeskAgents(viewDeskId)
      }
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const viewDeskName = deskList.find(d => d.id === viewDeskId)?.name

  return (
    <div className="main-content">
      <p style={{ marginBottom: '1rem' }}><Link to="/">Back to Desk Selector</Link></p>
      <h1>Client Management</h1>
      <p style={{ color: '#6b7280', marginBottom: '1rem' }}>
        Search BambooHR employees by department name, then assign them to a desk.
      </p>

      {/* Upload Desk Assignments */}
      <div style={{ marginBottom: '1.5rem', padding: '0.75rem', background: '#f0fdf4', borderRadius: '6px', border: '1px solid #bbf7d0' }}>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <button className="primary" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
            {uploading ? 'Uploading...' : 'Upload Desk Assignments'}
          </button>
          <input ref={fileInputRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleUploadDeskAssignments} />
          <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
            Upload an .xlsx file with columns: BambooHR ID, Name, Email, Desk Assignment
          </span>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <div>
          <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem', fontSize: '0.85rem' }}>Department</label>
          <input
            value={department}
            onChange={e => setDepartment(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="e.g. Support"
            style={{ padding: '0.5rem', border: '1px solid #d1d5db', borderRadius: '4px', width: '250px' }}
          />
        </div>
        <button className="primary" onClick={handleSearch} disabled={loading}>
          {loading ? 'Loading...' : 'Fetch Employees'}
        </button>
      </div>

      {searched && (
        <>
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-end', marginBottom: '0.75rem', flexWrap: 'wrap', padding: '0.75rem', background: '#f9fafb', borderRadius: '6px', border: '1px solid #e5e7eb' }}>
            <div>
              <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem', fontSize: '0.85rem' }}>Assign to Desk</label>
              <select
                value={selectedDeskId}
                onChange={e => setSelectedDeskId(e.target.value)}
                style={{ padding: '0.4rem', border: '1px solid #d1d5db', borderRadius: '4px', minWidth: '200px' }}
              >
                <option value="">-- Select a desk --</option>
                {deskList.map(d => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
            </div>
            <button
              className="primary"
              onClick={handleAssignToDesk}
              disabled={assigning || selectedEmployeeIds.size === 0 || !selectedDeskId}
            >
              {assigning ? 'Assigning...' : `Assign Selected (${selectedEmployeeIds.size})`}
            </button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', fontSize: '0.85rem' }}>
            <div style={{ fontWeight: 600 }}>
              Total records: {totalCount.toLocaleString()}
            </div>
            <label style={{ marginLeft: '1rem' }}>
              Rows per page:{' '}
              <select value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); }}>
                {[10, 20, 50, 100].map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <span style={{ marginLeft: 'auto' }}>
              {employees.length} employee{employees.length !== 1 ? 's' : ''} on this page
            </span>
          </div>

          <table>
            <thead>
              <tr>
                <th style={{ width: '40px' }}>
                  <input
                    type="checkbox"
                    checked={employees.length > 0 && selectedEmployeeIds.size === employees.length}
                    onChange={toggleAll}
                  />
                </th>
                <th>ID</th>
                <th>Name</th>
                <th>Email</th>
                <th>Department</th>
                <th>Job Title</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {employees.length === 0 ? (
                <tr><td colSpan={7} style={{ textAlign: 'center', color: '#6b7280' }}>No employees found for this department.</td></tr>
              ) : employees.map(emp => (
                <tr key={emp.id} style={{ background: selectedEmployeeIds.has(emp.id) ? '#eff6ff' : undefined }}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selectedEmployeeIds.has(emp.id)}
                      onChange={() => toggleEmployee(emp.id)}
                    />
                  </td>
                  <td>{emp.id}</td>
                  <td>{emp.displayName}</td>
                  <td>{emp.workEmail}</td>
                  <td>{emp.department}</td>
                  <td>{emp.jobTitle}</td>
                  <td>{emp.status}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', marginTop: '0.75rem' }}>
            <button disabled={currentPage <= 1 || loading} onClick={() => fetchEmployees(currentPage - 1)} style={{ padding: '0.25rem 0.5rem' }}>&lsaquo; Prev</button>
            <span style={{ fontSize: '0.85rem' }}>Page {currentPage}</span>
            <button disabled={!hasMore || loading} onClick={() => fetchEmployees(currentPage + 1)} style={{ padding: '0.25rem 0.5rem' }}>Next &rsaquo;</button>
          </div>
        </>
      )}

      {/* Desk Agents - View & Remove */}
      <div style={{ marginTop: '2rem', padding: '1rem', background: '#f9fafb', borderRadius: '6px', border: '1px solid #e5e7eb' }}>
        <h2 style={{ marginTop: 0, marginBottom: '0.75rem', fontSize: '1.1rem' }}>Desk Agents</h2>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
          <div>
            <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem', fontSize: '0.85rem' }}>View Desk</label>
            <select
              value={viewDeskId}
              onChange={e => handleViewDeskChange(e.target.value)}
              style={{ padding: '0.4rem', border: '1px solid #d1d5db', borderRadius: '4px', minWidth: '200px' }}
            >
              <option value="">-- Select a desk --</option>
              {deskList.map(d => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          {viewDeskId && (
            <button onClick={() => loadDeskAgents(viewDeskId)} disabled={loadingAgents} style={{ padding: '0.4rem 0.75rem' }}>
              {loadingAgents ? 'Loading...' : 'Refresh'}
            </button>
          )}
        </div>

        {viewDeskId && (
          <>
            <div style={{ fontSize: '0.85rem', marginBottom: '0.5rem', fontWeight: 600 }}>
              {deskAgentList.length} agent{deskAgentList.length !== 1 ? 's' : ''} assigned to {viewDeskName}
            </div>
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Department</th>
                  <th>Job Title</th>
                  <th>Active</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {deskAgentList.length === 0 ? (
                  <tr><td colSpan={6} style={{ textAlign: 'center', color: '#6b7280' }}>No agents assigned to this desk.</td></tr>
                ) : deskAgentList.map(agent => (
                  <tr key={agent.id}>
                    <td>{agent.name}</td>
                    <td>{agent.email}</td>
                    <td>{agent.department}</td>
                    <td>{agent.jobTitle}</td>
                    <td>{agent.active ? 'Yes' : 'No'}</td>
                    <td>
                      <button className="danger" onClick={() => handleRemoveAgent(agent.id, agent.name)} style={{ fontSize: '0.75rem', padding: '0.15rem 0.4rem' }}>
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
    </div>
  )
}
