import { useState, useEffect, useRef, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { clientManagement, desks as desksApi, deskAgents, type BambooEmployeeResponse, type Desk, type DeskAgent, type DeskAssignmentUploadResult, type SkippedRow, type SheetSummary, type SkippedSheet, type MergeReportEntry, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

type EmpSortField = 'id' | 'displayName' | 'workEmail' | 'department' | 'jobTitle' | 'status'
type AgentSortField = 'name' | 'email' | 'department' | 'jobTitle' | 'active'
type SortDir = 'asc' | 'desc'

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

  // Export
  const [exporting, setExporting] = useState(false)

  // Desk assignment upload
  const [uploading, setUploading] = useState(false)
  const [downloadingTemplate, setDownloadingTemplate] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Upload result modal
  const [uploadResult, setUploadResult] = useState<DeskAssignmentUploadResult | null>(null)

  // Employees table sorting & search
  const [empSortField, setEmpSortField] = useState<EmpSortField | null>(null)
  const [empSortDir, setEmpSortDir] = useState<SortDir>('asc')
  const [empSearch, setEmpSearch] = useState('')

  // Desk agents table sorting & search
  const [agentSortField, setAgentSortField] = useState<AgentSortField | null>(null)
  const [agentSortDir, setAgentSortDir] = useState<SortDir>('asc')
  const [agentSearch, setAgentSearch] = useState('')

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
      setUploadResult(result)
      // Refresh desk agents view in parallel if a desk is selected
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

  const handleDownloadTemplate = async () => {
    setDownloadingTemplate(true)
    try {
      const res = await clientManagement.downloadDeskAssignmentTemplate()
      if (!res.ok) {
        showToast('error', 'Template download failed')
        return
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'desk-assignment-template.xlsx'
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setDownloadingTemplate(false)
    }
  }

  const handleExportEmployees = async () => {
    if (!department.trim()) return
    setExporting(true)
    try {
      const res = await clientManagement.exportEmployees(department.trim())
      if (!res.ok) {
        showToast('error', 'Export failed')
        return
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${department.trim().replace(/[^a-zA-Z0-9_\-]/g, '_')}-employees.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setExporting(false)
    }
  }

  const handleDownloadSkippedCsv = () => {
    if (!uploadResult) return
    const sanitize = (val: string | null | undefined): string => {
      if (val == null) return ''
      // CSV-injection mitigation (T-05-05-02/WR-05): prefix dangerous leading chars with
      // single quote. Includes leading tab/CR per OWASP CSV-injection guidance, matching
      // the backend's shared FormulaInjectionSanitizer.
      const s = String(val)
      const sanitized = /^[=+\-@\t\r]/.test(s) ? "'" + s : s
      // Escape inner double-quotes by doubling them
      return sanitized.replace(/"/g, '""')
    }
    const csvRows: string[] = [
      'Row,BambooHR ID,Name,Reason',
      ...uploadResult.skippedDetails.map((r: SkippedRow) =>
        `${r.rowNumber},"${sanitize(r.bamboohrId)}","${sanitize(r.name)}","${sanitize(r.reason)}"`
      ),
    ]
    const csv = csvRows.join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `skipped-assignments-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  // Employee sort helpers
  const handleEmpSort = (field: EmpSortField) => {
    if (empSortField === field) {
      setEmpSortDir(d => d === 'asc' ? 'desc' : 'asc')
    } else {
      setEmpSortField(field)
      setEmpSortDir('asc')
    }
  }
  const empSortIndicator = (field: EmpSortField) => {
    if (empSortField !== field) return ' ↕'
    return empSortDir === 'asc' ? ' ↑' : ' ↓'
  }

  const filteredEmployees = useMemo(() => {
    if (!empSearch.trim()) return employees
    const q = empSearch.toLowerCase()
    return employees.filter(e =>
      (e.id ?? '').toLowerCase().includes(q) ||
      (e.displayName ?? '').toLowerCase().includes(q) ||
      (e.workEmail ?? '').toLowerCase().includes(q) ||
      (e.department ?? '').toLowerCase().includes(q) ||
      (e.jobTitle ?? '').toLowerCase().includes(q) ||
      (e.status ?? '').toLowerCase().includes(q)
    )
  }, [employees, empSearch])

  const sortedEmployees = useMemo(() => {
    if (!empSortField) return filteredEmployees
    const sorted = [...filteredEmployees]
    sorted.sort((a, b) => {
      const aVal = String(a[empSortField] ?? '')
      const bVal = String(b[empSortField] ?? '')
      const cmp = aVal.localeCompare(bVal)
      return empSortDir === 'asc' ? cmp : -cmp
    })
    return sorted
  }, [filteredEmployees, empSortField, empSortDir])

  // Desk agent sort helpers
  const handleAgentSort = (field: AgentSortField) => {
    if (agentSortField === field) {
      setAgentSortDir(d => d === 'asc' ? 'desc' : 'asc')
    } else {
      setAgentSortField(field)
      setAgentSortDir('asc')
    }
  }
  const agentSortIndicator = (field: AgentSortField) => {
    if (agentSortField !== field) return ' ↕'
    return agentSortDir === 'asc' ? ' ↑' : ' ↓'
  }

  const filteredAgents = useMemo(() => {
    if (!agentSearch.trim()) return deskAgentList
    const q = agentSearch.toLowerCase()
    return deskAgentList.filter(a =>
      (a.name ?? '').toLowerCase().includes(q) ||
      (a.email ?? '').toLowerCase().includes(q) ||
      (a.department ?? '').toLowerCase().includes(q) ||
      (a.jobTitle ?? '').toLowerCase().includes(q)
    )
  }, [deskAgentList, agentSearch])

  const sortedAgents = useMemo(() => {
    if (!agentSortField) return filteredAgents
    const sorted = [...filteredAgents]
    sorted.sort((a, b) => {
      let aVal: string, bVal: string
      if (agentSortField === 'active') {
        aVal = a.active ? 'Yes' : 'No'
        bVal = b.active ? 'Yes' : 'No'
      } else {
        aVal = String(a[agentSortField] ?? '')
        bVal = String(b[agentSortField] ?? '')
      }
      const cmp = aVal.localeCompare(bVal)
      return agentSortDir === 'asc' ? cmp : -cmp
    })
    return sorted
  }, [filteredAgents, agentSortField, agentSortDir])

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
          <button onClick={handleDownloadTemplate} disabled={downloadingTemplate}>
            {downloadingTemplate ? 'Downloading...' : 'Download template'}
          </button>
          <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
            Upload an .xlsx workbook with one worksheet per desk (sheet name = desk name). Each sheet needs a BambooHR ID
            column and one column per day (Monday…Sunday) — each day cell holds a number of hours (0–24), MANDATORY, or PTO.
            Specialty columns (Specialty 1, Specialty 2, …) are optional. Download the template above to get a workbook
            pre-seeded with your current roster's identity columns. Only active agents are seeded, and if a Job Title
            Allowlist is configured on the Configuration page, only matching job titles are seeded — the same rules are
            enforced on upload, so non-matching rows are reported as skipped. The old 6-column and flat enriched shapes
            are no longer accepted — re-download the template if your file uses either.
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
            <button
              onClick={handleExportEmployees}
              disabled={exporting || totalCount === 0}
              style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
            >
              {exporting ? 'Exporting...' : 'Export to XLSX'}
            </button>
            <label style={{ marginLeft: '1rem' }}>
              Rows per page:{' '}
              <select value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); }}>
                {[10, 20, 50, 100].map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <span style={{ marginLeft: 'auto' }}>
              {sortedEmployees.length} employee{sortedEmployees.length !== 1 ? 's' : ''}{empSearch ? ' (filtered)' : ' on this page'}
            </span>
          </div>

          <div style={{ marginBottom: '0.5rem' }}>
            <input
              value={empSearch}
              onChange={e => setEmpSearch(e.target.value)}
              placeholder="Filter employees..."
              style={{ padding: '0.4rem', border: '1px solid #d1d5db', borderRadius: '4px', width: '300px' }}
            />
          </div>

          <table>
            <thead>
              <tr>
                <th style={{ width: '40px' }}>
                  <input
                    type="checkbox"
                    checked={sortedEmployees.length > 0 && selectedEmployeeIds.size === sortedEmployees.length}
                    onChange={toggleAll}
                  />
                </th>
                <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleEmpSort('id')}>ID{empSortIndicator('id')}</th>
                <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleEmpSort('displayName')}>Name{empSortIndicator('displayName')}</th>
                <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleEmpSort('workEmail')}>Email{empSortIndicator('workEmail')}</th>
                <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleEmpSort('department')}>Department{empSortIndicator('department')}</th>
                <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleEmpSort('jobTitle')}>Job Title{empSortIndicator('jobTitle')}</th>
                <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleEmpSort('status')}>Status{empSortIndicator('status')}</th>
              </tr>
            </thead>
            <tbody>
              {sortedEmployees.length === 0 ? (
                <tr><td colSpan={7} style={{ textAlign: 'center', color: '#6b7280' }}>No employees found{empSearch ? ' matching filter' : ' for this department'}.</td></tr>
              ) : sortedEmployees.map(emp => (
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

      {/* Upload Result Modal */}
      {uploadResult !== null && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '760px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
            <h3>Upload Results</h3>
            <div style={{ marginBottom: '0.5rem' }}>
              <span style={{ color: '#16a34a', fontWeight: 600, fontSize: '1.1rem' }}>
                {uploadResult.assignedCount} assigned
              </span>
              {uploadResult.skippedCount > 0 && (
                <>
                  {' '}
                  <span style={{ color: '#dc2626', fontWeight: 600, fontSize: '1.1rem' }}>
                    {uploadResult.skippedCount} skipped
                  </span>
                </>
              )}
            </div>
            {uploadResult.sheetSummaries.length > 0 && (
              <div style={{ marginTop: '0.5rem' }}>
                <div style={{ fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>Per-desk rollup</div>
                <ul style={{ fontSize: '0.85rem', margin: 0, paddingLeft: '1.25rem' }}>
                  {uploadResult.sheetSummaries.map((sheet: SheetSummary, idx: number) => (
                    <li key={idx}>
                      {sheet.deskName}: {sheet.importedCount} imported, {sheet.skippedCount} skipped
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {uploadResult.mergeReport.length > 0 && (
              <div style={{ marginTop: '0.75rem' }}>
                <div style={{ fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>Merge Report</div>
                <div style={{ overflowY: 'auto', maxHeight: '300px', border: '1px solid #e5e7eb', borderRadius: '4px' }}>
                  <table style={{ width: '100%', fontSize: '0.85rem' }}>
                    <thead>
                      <tr>
                        <th>BambooHR ID</th>
                        <th>Agent</th>
                        <th>Field</th>
                        <th>BambooHR value</th>
                        <th>Sheet value</th>
                        <th>Outcome</th>
                      </tr>
                    </thead>
                    <tbody>
                      {uploadResult.mergeReport.map((entry: MergeReportEntry, idx: number) => (
                        <tr key={idx}>
                          <td>{entry.bamboohrId}</td>
                          <td>{entry.agentName}</td>
                          <td>{entry.field}</td>
                          <td style={{ wordWrap: 'break-word' }}>{entry.bambooValue}</td>
                          <td style={{ wordWrap: 'break-word' }}>{entry.sheetValue}</td>
                          <td>
                            <span style={{
                              display: 'inline-block',
                              padding: '0.125rem 0.5rem',
                              borderRadius: '9999px',
                              fontSize: '0.8rem',
                              fontWeight: 600,
                              color: '#ffffff',
                              background: entry.outcome === 'BambooHR override' ? '#92400e' : '#3b82f6',
                            }}>
                              {entry.outcome}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
            {(uploadResult.warnings.length > 0 || uploadResult.skippedSheets.length > 0) && (
              <div style={{ marginTop: '0.75rem', padding: '0.5rem', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '4px' }}>
                <div style={{ fontWeight: 600, fontSize: '0.85rem', color: '#92400e', marginBottom: '0.25rem' }}>Warnings</div>
                <ul style={{ fontSize: '0.85rem', color: '#92400e', margin: 0, paddingLeft: '1.25rem' }}>
                  {uploadResult.warnings.map((warning: string, idx: number) => (
                    <li key={`warning-${idx}`}>{warning}</li>
                  ))}
                  {uploadResult.skippedSheets.map((sheet: SkippedSheet, idx: number) => (
                    <li key={`skipped-sheet-${idx}`}>Sheet "{sheet.sheetName}": {sheet.reason}</li>
                  ))}
                </ul>
              </div>
            )}
            {uploadResult.skippedCount > 0 && (
              <div style={{ overflowY: 'auto', maxHeight: '300px', marginTop: '1rem', border: '1px solid #e5e7eb', borderRadius: '4px' }}>
                <table style={{ width: '100%', fontSize: '0.85rem' }}>
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>BambooHR ID</th>
                      <th>Name</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {uploadResult.skippedDetails.map((row: SkippedRow, idx: number) => (
                      <tr key={idx}>
                        <td>{row.rowNumber}</td>
                        <td>{row.bamboohrId ?? '—'}</td>
                        <td>{row.name ?? '—'}</td>
                        <td>{row.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', justifyContent: 'flex-end' }}>
              {uploadResult.skippedCount > 0 && (
                <button onClick={handleDownloadSkippedCsv}>Download skipped as CSV</button>
              )}
              <button onClick={() => setUploadResult(null)}>Close</button>
            </div>
          </div>
        </div>
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
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>
                {sortedAgents.length} agent{sortedAgents.length !== 1 ? 's' : ''} assigned to {viewDeskName}{agentSearch ? ' (filtered)' : ''}
              </div>
              <input
                value={agentSearch}
                onChange={e => setAgentSearch(e.target.value)}
                placeholder="Filter agents..."
                style={{ padding: '0.4rem', border: '1px solid #d1d5db', borderRadius: '4px', width: '250px' }}
              />
            </div>
            <table>
              <thead>
                <tr>
                  <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleAgentSort('name')}>Name{agentSortIndicator('name')}</th>
                  <th>ID</th>
                  <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleAgentSort('email')}>Email{agentSortIndicator('email')}</th>
                  <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleAgentSort('department')}>Department{agentSortIndicator('department')}</th>
                  <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleAgentSort('jobTitle')}>Job Title{agentSortIndicator('jobTitle')}</th>
                  <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleAgentSort('active')}>Active{agentSortIndicator('active')}</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sortedAgents.length === 0 ? (
                  <tr><td colSpan={7} style={{ textAlign: 'center', color: '#6b7280' }}>No agents{agentSearch ? ' matching filter' : ' assigned to this desk'}.</td></tr>
                ) : sortedAgents.map(agent => (
                  <tr key={agent.id}>
                    <td>{agent.name}</td>
                    <td>{agent.bamboohrId}</td>
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
