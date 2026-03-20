import { useState } from 'react'
import { Link } from 'react-router-dom'
import { clientManagement, type BambooEmployeeResponse, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

export default function ClientManagement() {
  const [department, setDepartment] = useState('')
  const [employees, setEmployees] = useState<BambooEmployeeResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [hasMore, setHasMore] = useState(false)
  const [searched, setSearched] = useState(false)

  const fetchEmployees = async (page = 1) => {
    if (!department.trim()) {
      showToast('error', 'Please enter a department name')
      return
    }
    setLoading(true)
    try {
      const res = await clientManagement.listEmployees(department.trim(), page, pageSize)
      setEmployees(res.data)
      setHasMore(res.hasMore)
      setCurrentPage(page)
      setSearched(true)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = () => {
    setCurrentPage(1)
    fetchEmployees(1)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch()
  }

  return (
    <div className="main-content">
      <p style={{ marginBottom: '1rem' }}><Link to="/">Back to Desk Selector</Link></p>
      <h1>Client Management</h1>
      <p style={{ color: '#6b7280', marginBottom: '1rem' }}>
        Search BambooHR employees by department name.
      </p>

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
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', fontSize: '0.85rem' }}>
            <label>
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
                <tr><td colSpan={6} style={{ textAlign: 'center', color: '#6b7280' }}>No employees found for this department.</td></tr>
              ) : employees.map(emp => (
                <tr key={emp.id}>
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
    </div>
  )
}
