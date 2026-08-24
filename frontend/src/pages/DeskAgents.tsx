import { Fragment, useEffect, useState, useMemo, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { deskAgents, agents as agentsApi, specializations as specApi, type DeskAgent, type DayHoursEntry, type Agent, type Specialization, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

type SortField = 'firstName' | 'lastName'
type SortDir = 'asc' | 'desc'

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const

const DAY_LABELS: Record<(typeof DAY_ORDER)[number], string> = {
  MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun',
}

function getFirstName(name: string) { return name.split(' ')[0] ?? '' }
function getLastName(name: string) { return name.split(' ').slice(1).join(' ') }

/** Capitalised weekday name for error copy, e.g. "WEDNESDAY" -> "Wednesday". */
function capitalizeDay(day: string) {
  return day.charAt(0) + day.slice(1).toLowerCase()
}

/** Seeds the per-cell editor from the entry's current state (13-UI-SPEC.md Section 3):
 * empty when not set, the literal label when MANDATORY/PTO, otherwise the formatted number. */
function seedValueForEntry(entry: DayHoursEntry): string {
  if (entry.dayOffType === 'MANDATORY') return 'MANDATORY'
  if (entry.dayOffType === 'PTO') return 'PTO'
  if (entry.hasRow && entry.hours !== null) return formatHours(entry.hours)
  return ''
}

/** Renders a resolved hours value with no trailing zeros, e.g. 8.00 -> "8", 7.50 -> "7.5". */
function formatHours(n: number) {
  return Number(n.toFixed(2)).toString()
}

/** Collapsed Hours/Day summary (13-UI-SPEC.md Section 1): a single value when the whole week
 * matches, otherwise a min-max range. Never renders a MANDATORY/PTO marker. */
function formatHoursSummary(da: DeskAgent) {
  const values = DAY_ORDER.map(d => da.dayHours[d].effectiveHours)
  const min = Math.min(...values)
  const max = Math.max(...values)
  return min === max ? formatHours(min) : `${formatHours(min)}-${formatHours(max)}`
}

/** True when none of the agent's seven weekdays has a stored agent_day_hours row — the shared
 * predicate driving both the collapsed cell's muted treatment and the expanded row's empty-state
 * note (13-UI-SPEC.md E1 empty). */
function isEveryDayNotSet(da: DeskAgent): boolean {
  return DAY_ORDER.every(d => !da.dayHours[d].hasRow)
}

/** Per-day cell display (13-UI-SPEC.md Section 3, rules 1-5) — this branch order is
 * load-bearing (13-RESEARCH.md Pitfalls 1 and 2): a MANDATORY/PTO label always wins over the
 * stored hours value, and a stored 0.00 row is never conflated with "not set". */
function DayCell({ entry, onClick }: { entry: DayHoursEntry; onClick: () => void }) {
  if (entry.dayOffType === 'MANDATORY') {
    return (
      <span onClick={onClick} style={{ cursor: 'pointer', display: 'inline-block', background: '#e5e7eb', color: '#374151', padding: '4px', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 600 }}>
        MAND
      </span>
    )
  }
  if (entry.dayOffType === 'PTO') {
    return (
      <span onClick={onClick} style={{ cursor: 'pointer', display: 'inline-block', background: '#fef9c3', color: '#92400e', padding: '4px', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 600 }}>
        PTO
      </span>
    )
  }
  if (entry.hasRow && entry.hours === 0) {
    return <span onClick={onClick} style={{ cursor: 'pointer', color: '#6b7280', fontSize: '0.85rem', fontWeight: 400 }}>0</span>
  }
  if (entry.hasRow && entry.hours !== null) {
    return <span onClick={onClick} style={{ cursor: 'pointer', fontSize: '0.85rem', fontWeight: 400 }}>{formatHours(entry.hours)}</span>
  }
  return (
    <span
      onClick={onClick}
      title="Not set — using schedule default"
      style={{ cursor: 'pointer', color: '#9ca3af', fontStyle: 'italic', fontSize: '0.85rem', fontWeight: 400 }}
    >
      {formatHours(entry.effectiveHours)}
    </span>
  )
}

export default function DeskAgents() {
  const { deskId } = useParams<{ deskId: string }>()
  const [agentList, setAgentList] = useState<DeskAgent[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [showActiveOnly, setShowActiveOnly] = useState(true)

  // Pagination
  const [pageSize, setPageSize] = useState(20)
  const [currentPage, setCurrentPage] = useState(1)

  // Sorting
  const [sortField, setSortField] = useState<SortField | null>(null)
  const [sortDir, setSortDir] = useState<SortDir>('asc')

  // Assign modal
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [unassigned, setUnassigned] = useState<Agent[]>([])
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [searchTerm, setSearchTerm] = useState('')
  const [assigning, setAssigning] = useState(false)

  // Specialization edit
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [editSpecAgentId, setEditSpecAgentId] = useState<string | null>(null)
  const [editPrimary, setEditPrimary] = useState('')
  const [editSecondary, setEditSecondary] = useState<string[]>([])

  // Contracted hours edit — relocated into the expanded row as the "Set all days to…" bulk
  // action (D-07); editHours is a string so the input can open blank (13-UI-SPEC.md E5 empty)
  const [editHoursAgentId, setEditHoursAgentId] = useState<string | null>(null)
  const [editHours, setEditHours] = useState('')
  const [applyingBulk, setApplyingBulk] = useState(false)

  // Per-day hours expandable row (13-UI-SPEC.md D-01)
  const [expandedAgentId, setExpandedAgentId] = useState<string | null>(null)

  // Per-cell type-or-pick combo (13-UI-SPEC.md Section 3 / D-03/D-04/D-05)
  const [editCell, setEditCell] = useState<{ agentId: string; day: string } | null>(null)
  const [editCellValue, setEditCellValue] = useState('')
  const [cellError, setCellError] = useState<string | null>(null)
  const [savingCell, setSavingCell] = useState(false)
  const cellEscapedRef = useRef(false)

  // Days off modal
  const [showDaysOff, setShowDaysOff] = useState<{ agentId: string; agentName: string; daysOff: Array<{ id: string; date: string; type: string }> } | null>(null)

  // Employment type filter
  const [empTypeFilter, setEmpTypeFilter] = useState('')

  // Export
  const [exporting, setExporting] = useState(false)

  // Preference upload
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const loadAgents = async () => {
    if (!deskId) return
    try {
      const res = await deskAgents.list(deskId)
      setAgentList(res.data)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAgents() }, [deskId])

  useEffect(() => {
    if (deskId) specApi.list(deskId).then(setSpecs).catch(() => {})
  }, [deskId])

  const handleRefresh = async () => {
    if (!deskId) return
    setRefreshing(true)
    try {
      await deskAgents.refresh(deskId)
      await loadAgents()
      showToast('success', 'Agents refreshed from BambooHR')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setRefreshing(false)
    }
  }

  const openAssignModal = async () => {
    try {
      const res = await agentsApi.list({ unassigned: true, limit: 200 })
      setUnassigned(res.data)
      setSelectedIds(new Set())
      setSearchTerm('')
      setShowAssignModal(true)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const handleAssign = async () => {
    if (!deskId || selectedIds.size === 0) return
    setAssigning(true)
    try {
      await deskAgents.assign(deskId, Array.from(selectedIds))
      setShowAssignModal(false)
      await loadAgents()
      showToast('success', `${selectedIds.size} agent(s) assigned`)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setAssigning(false)
    }
  }

  const handleRemove = async (agentId: string) => {
    if (!deskId || !confirm('Remove this agent from the desk?')) return
    try {
      await deskAgents.remove(deskId, agentId)
      setAgentList(agentList.filter(da => da.id !== agentId))
      showToast('success', 'Agent removed')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const startEditSpec = (da: DeskAgent) => {
    setEditSpecAgentId(da.id)
    setEditPrimary(da.primarySpecialization?.id || '')
    setEditSecondary(da.secondarySpecializations.map(s => s.id))
  }

  const saveSpec = async () => {
    if (!deskId || !editSpecAgentId) return
    try {
      const updated = await deskAgents.setSpecializations(deskId, editSpecAgentId, {
        primarySpecializationId: editPrimary,
        secondarySpecializationIds: editSecondary,
      })
      setAgentList(agentList.map(da => da.id === editSpecAgentId ? updated : da))
      setEditSpecAgentId(null)
      showToast('success', 'Specializations updated')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const startEditHours = (agentId: string) => {
    setEditHoursAgentId(agentId)
    setEditHours('')
  }

  const saveHours = async (da: DeskAgent) => {
    if (!deskId || !editHoursAgentId) return
    const hours = Number(editHours)
    if (editHours.trim() === '' || Number.isNaN(hours)) return
    const labelledDayCount = DAY_ORDER.filter(d => da.dayHours[d].dayOffType !== null).length
    if (labelledDayCount > 0) {
      const confirmed = confirm(`This will overwrite ${labelledDayCount} day(s) currently marked MANDATORY or PTO with a single value. Continue?`)
      if (!confirmed) return
    }
    setApplyingBulk(true)
    try {
      const updated = await deskAgents.setContractedHours(deskId, editHoursAgentId, hours)
      setAgentList(agentList.map(a => a.id === editHoursAgentId ? updated : a))
      setEditHoursAgentId(null)
      showToast('success', 'Contracted hours updated')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setApplyingBulk(false)
    }
  }

  const startEditCell = (da: DeskAgent, day: string) => {
    setEditCell({ agentId: da.id, day })
    setEditCellValue(seedValueForEntry(da.dayHours[day]))
    setCellError(null)
  }

  const cancelEditCell = () => {
    setEditCell(null)
    setCellError(null)
  }

  const saveDayHours = async (da: DeskAgent, day: string) => {
    if (!deskId) return
    const raw = editCellValue.trim()
    const upper = raw.toUpperCase()
    let body: { hours?: number; dayOffType?: 'MANDATORY' | 'PTO'; clearRow?: boolean }
    if (upper === 'PTO') {
      body = { dayOffType: 'PTO' }
    } else if (upper === 'MANDATORY') {
      body = { dayOffType: 'MANDATORY' }
    } else if (raw === '' || raw === 'Not set (default)') {
      body = { clearRow: true }
    } else {
      const numeric = Number(raw)
      if (Number.isNaN(numeric) || numeric < 0 || numeric > 24) {
        setEditCellValue(seedValueForEntry(da.dayHours[day]))
        setCellError('Enter a number from 0 to 24, or choose PTO / MANDATORY / Not set (default).')
        return
      }
      body = { hours: numeric }
    }
    setCellError(null)
    setSavingCell(true)
    try {
      const updated = await deskAgents.setDayHours(deskId, da.id, day, body)
      setAgentList(agentList.map(a => a.id === da.id ? updated : a))
      setEditCell(null)
      showToast('success', 'Day hours updated')
    } catch (err) {
      showToast('error', `Couldn't save ${capitalizeDay(day)} — ${getErrorMessage(err)}`)
      setEditCell(null)
    } finally {
      setSavingCell(false)
    }
  }

  const openDaysOff = async (agentId: string, agentName: string) => {
    try {
      const daysOff = await agentsApi.daysOff(agentId)
      setShowDaysOff({ agentId, agentName, daysOff })
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const handleUploadPreferences = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !deskId) return
    setUploading(true)
    try {
      const result = await deskAgents.uploadPreferences(deskId, file)
      showToast('success', `Preferences loaded: ${result.savedCount} saved, ${result.skippedCount} skipped`)
      if (result.skippedDetails.length > 0) {
        console.warn('Skipped rows:', result.skippedDetails)
      }
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleExport = async () => {
    if (!deskId) return
    setExporting(true)
    try {
      const res = await deskAgents.exportToExcel(deskId)
      if (!res.ok) {
        showToast('error', 'Export failed')
        return
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `desk-agents-${deskId}.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setExporting(false)
    }
  }

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc')
    } else {
      setSortField(field)
      setSortDir('asc')
    }
    setCurrentPage(1)
  }

  const sortIndicator = (field: SortField) => {
    if (sortField !== field) return ' \u2195'
    return sortDir === 'asc' ? ' \u2191' : ' \u2193'
  }

  const filteredAgents = (showActiveOnly ? agentList.filter(da => da.active) : agentList)
    .filter(da => empTypeFilter === '' || da.employmentType === empTypeFilter)

  const sortedAgents = useMemo(() => {
    if (!sortField) return filteredAgents
    const sorted = [...filteredAgents]
    sorted.sort((a, b) => {
      const aVal = sortField === 'firstName' ? getFirstName(a.name) : getLastName(a.name)
      const bVal = sortField === 'firstName' ? getFirstName(b.name) : getLastName(b.name)
      const cmp = aVal.localeCompare(bVal)
      return sortDir === 'asc' ? cmp : -cmp
    })
    return sorted
  }, [filteredAgents, sortField, sortDir])

  const totalPages = Math.max(1, Math.ceil(sortedAgents.length / pageSize))
  const paginatedAgents = sortedAgents.slice((currentPage - 1) * pageSize, currentPage * pageSize)

  const filteredUnassigned = searchTerm
    ? unassigned.filter(a => a.name.toLowerCase().includes(searchTerm.toLowerCase()))
    : unassigned

  if (loading) return <p>Loading agents...</p>

  return (
    <>
      <datalist id="day-hours-options">
        <option value="PTO" />
        <option value="MANDATORY" />
        <option value="Not set (default)" />
      </datalist>
      <h1>Desk Agents</h1>
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <button className="primary" onClick={handleRefresh} disabled={refreshing}>
          {refreshing ? 'Refreshing...' : 'Refresh from BambooHR'}
        </button>
        <button className="primary" onClick={openAssignModal}>Assign Agents</button>
        <button className="primary" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
          {uploading ? 'Uploading...' : 'Load Preferences'}
        </button>
        <button className="primary" onClick={handleExport} disabled={exporting}>
          {exporting ? 'Exporting...' : 'Export to XLSX'}
        </button>
        <input ref={fileInputRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleUploadPreferences} />
        <label style={{ marginLeft: 'auto', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
          <input type="checkbox" checked={showActiveOnly} onChange={e => setShowActiveOnly(e.target.checked)} />
          Active only
        </label>
        <select value={empTypeFilter} onChange={e => setEmpTypeFilter(e.target.value)} style={{ fontSize: '0.85rem' }}>
          <option value="">All</option>
          <option value="FULL_TIME">Full-time</option>
          <option value="PART_TIME">Part-time</option>
        </select>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', fontSize: '0.85rem' }}>
        <label>
          Rows per page:{' '}
          <select value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); setCurrentPage(1) }}>
            {[10, 20, 50, 100].map(n => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <span style={{ marginLeft: 'auto' }}>
          {sortedAgents.length} agent{sortedAgents.length !== 1 ? 's' : ''}
        </span>
      </div>

      <table>
        <thead>
          <tr>
            <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleSort('firstName')}>
              First Name{sortIndicator('firstName')}
            </th>
            <th style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => handleSort('lastName')}>
              Last Name{sortIndicator('lastName')}
            </th>
            <th>ID</th><th>Email</th><th>Department</th><th>Job Title</th><th>Emp Type</th>
            <th>Primary Spec</th><th>Secondary Specs</th><th>Hours/Day</th>
            <th>Active</th><th>Last Refreshed</th><th>PTO</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {paginatedAgents.map(da => (
            <Fragment key={da.id}>
            <tr>
              <td>{getFirstName(da.name)}</td>
              <td>{getLastName(da.name)}</td>
              <td>{da.bamboohrId}</td>
              <td>{da.email}</td>
              <td>{da.department}</td>
              <td>{da.jobTitle}</td>
              <td>{da.employmentType === 'FULL_TIME' ? 'Full-time' : da.employmentType === 'PART_TIME' ? 'Part-time' : '—'}</td>
              <td>
                {editSpecAgentId === da.id ? (
                  <select value={editPrimary} onChange={e => setEditPrimary(e.target.value)}>
                    <option value="">—</option>
                    {specs.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                ) : (
                  <span onClick={() => startEditSpec(da)} style={{ cursor: 'pointer', textDecoration: 'underline dotted' }}>
                    {da.primarySpecialization?.name || '—'}
                  </span>
                )}
              </td>
              <td>
                {editSpecAgentId === da.id ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                    {specs.map(s => (
                      <label key={s.id} style={{ fontSize: '0.8rem' }}>
                        <input type="checkbox" checked={editSecondary.includes(s.id)}
                          onChange={e => setEditSecondary(e.target.checked
                            ? [...editSecondary, s.id]
                            : editSecondary.filter(id => id !== s.id)
                          )} />
                        {s.name}
                      </label>
                    ))}
                    <div style={{ display: 'flex', gap: '0.25rem', marginTop: '2px' }}>
                      <button className="primary" onClick={saveSpec} style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}>Save</button>
                      <button onClick={() => setEditSpecAgentId(null)} style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}>Cancel</button>
                    </div>
                  </div>
                ) : (
                  <span onClick={() => startEditSpec(da)} style={{ cursor: 'pointer', textDecoration: 'underline dotted' }}>
                    {da.secondarySpecializations.map(s => s.name).join(', ') || '—'}
                  </span>
                )}
              </td>
              <td>
                <span
                  onClick={() => setExpandedAgentId(expandedAgentId === da.id ? null : da.id)}
                  style={{ cursor: 'pointer', textDecoration: 'underline dotted' }}
                >
                  <span
                    aria-label={expandedAgentId === da.id ? 'Collapse hours detail' : `Expand hours detail for ${da.name}`}
                    style={{ color: '#9ca3af', marginRight: '4px' }}
                  >
                    {expandedAgentId === da.id ? '▾' : '▸'}
                  </span>
                  <span
                    style={isEveryDayNotSet(da)
                      ? { color: '#9ca3af', fontStyle: 'italic' }
                      : undefined}
                    title={isEveryDayNotSet(da) ? 'Not set — using schedule default' : undefined}
                  >
                    {formatHoursSummary(da)}
                  </span>
                </span>
              </td>
              <td>{da.active ? 'Yes' : 'No'}</td>
              <td style={{ fontSize: '0.8rem' }}>{da.lastRefreshedAt ? new Date(da.lastRefreshedAt).toLocaleDateString() : '—'}</td>
              <td>
                {da.pendingPtoCount > 0 ? (
                  <span
                    title={da.pendingPtoDates.join(', ')}
                    style={{
                      display: 'inline-block',
                      background: '#fef9c3',
                      color: '#92400e',
                      padding: '0.25rem 0.5rem',
                      borderRadius: '4px',
                      fontSize: '0.85rem',
                      fontWeight: 400,
                      cursor: 'default',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {da.pendingPtoCount} pending PTO
                  </span>
                ) : '—'}
              </td>
              <td>
                <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                  <Link to={`/desks/${deskId}/agents/${da.id}/preferences`} style={{ fontSize: '0.8rem' }}>Prefs</Link>
                  <Link to={`/desks/${deskId}/agents/${da.id}/exceptions`} style={{ fontSize: '0.8rem' }}>Exc</Link>
                  <button onClick={() => openDaysOff(da.id, da.name)} style={{ fontSize: '0.75rem', padding: '0.15rem 0.4rem' }}>Days Off</button>
                  <button className="danger" onClick={() => handleRemove(da.id)} style={{ fontSize: '0.75rem', padding: '0.15rem 0.4rem' }}>Remove</button>
                </div>
              </td>
            </tr>
            {expandedAgentId === da.id && (
              <tr>
                <td colSpan={14} style={{ background: '#fff', padding: '0.75rem' }}>
                  {isEveryDayNotSet(da) && (
                    <div style={{ marginBottom: '16px', fontSize: '0.85rem' }}>
                      No per-day hours uploaded — every day shows the schedule default ({formatHours(da.dayHours[DAY_ORDER[0]].effectiveHours)}h).
                    </div>
                  )}
                  <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                    {DAY_ORDER.map(d => (
                      <div key={d} style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: '0.8rem', fontWeight: 600 }}>{DAY_LABELS[d]}</div>
                        <div>
                          {editCell && editCell.agentId === da.id && editCell.day === d ? (
                            <div style={{ textAlign: 'left' }}>
                              <input
                                type="text"
                                list="day-hours-options"
                                value={editCellValue}
                                disabled={savingCell}
                                placeholder="(type a number)"
                                autoFocus
                                onChange={e => setEditCellValue(e.target.value)}
                                onKeyDown={e => {
                                  if (e.key === 'Enter') {
                                    e.currentTarget.blur()
                                  } else if (e.key === 'Escape') {
                                    cellEscapedRef.current = true
                                    cancelEditCell()
                                    e.currentTarget.blur()
                                  }
                                }}
                                onBlur={() => {
                                  if (cellEscapedRef.current) {
                                    cellEscapedRef.current = false
                                    return
                                  }
                                  saveDayHours(da, d)
                                }}
                                style={{ width: '90px', fontSize: '0.85rem' }}
                              />
                              {cellError && (
                                <div style={{ fontSize: '0.75rem', color: '#92400e', marginTop: '2px' }}>{cellError}</div>
                              )}
                            </div>
                          ) : (
                            <DayCell entry={da.dayHours[d]} onClick={() => startEditCell(da, d)} />
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                  <div style={{ marginTop: '12px' }}>
                    {editHoursAgentId === da.id ? (
                      <>
                        <div style={{ background: '#fffbeb', border: '1px solid #fde68a', color: '#92400e', borderRadius: '4px', padding: '0.5rem', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
                          Set all days replaces all seven weekdays with this value and clears any MANDATORY or PTO labels.
                        </div>
                        <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                          <input
                            type="number"
                            value={editHours}
                            onChange={e => setEditHours(e.target.value)}
                            step="0.25"
                            min="0"
                            max="24"
                            disabled={applyingBulk}
                            placeholder="(type a number)"
                            style={{ width: '80px' }}
                          />
                          <button
                            className="primary"
                            onClick={() => saveHours(da)}
                            disabled={editHours.trim() === '' || applyingBulk}
                            style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}
                          >
                            {applyingBulk ? 'Applying...' : 'Apply'}
                          </button>
                          <button
                            onClick={() => setEditHoursAgentId(null)}
                            disabled={applyingBulk}
                            style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}
                          >
                            Cancel
                          </button>
                        </div>
                      </>
                    ) : (
                      <button onClick={() => startEditHours(da.id)} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>
                        Set all days to…
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            )}
            </Fragment>
          ))}
        </tbody>
      </table>

      {totalPages > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', marginTop: '0.75rem' }}>
          <button disabled={currentPage <= 1} onClick={() => setCurrentPage(1)} style={{ padding: '0.25rem 0.5rem' }}>&laquo;</button>
          <button disabled={currentPage <= 1} onClick={() => setCurrentPage(p => p - 1)} style={{ padding: '0.25rem 0.5rem' }}>&lsaquo;</button>
          <span style={{ fontSize: '0.85rem' }}>Page {currentPage} of {totalPages}</span>
          <button disabled={currentPage >= totalPages} onClick={() => setCurrentPage(p => p + 1)} style={{ padding: '0.25rem 0.5rem' }}>&rsaquo;</button>
          <button disabled={currentPage >= totalPages} onClick={() => setCurrentPage(totalPages)} style={{ padding: '0.25rem 0.5rem' }}>&raquo;</button>
        </div>
      )}

      {/* Assign Modal */}
      {showAssignModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '500px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
            <h3>Assign Agents</h3>
            <input placeholder="Search agents..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)}
              style={{ margin: '0.5rem 0', padding: '0.4rem' }} />
            <div style={{ flex: 1, overflowY: 'auto', border: '1px solid #e5e7eb', borderRadius: '4px', maxHeight: '400px' }}>
              {filteredUnassigned.length === 0 ? (
                <p style={{ padding: '1rem', color: '#6b7280' }}>No unassigned agents found.</p>
              ) : filteredUnassigned.map(a => (
                <label key={a.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.4rem 0.5rem', borderBottom: '1px solid #f3f4f6', cursor: 'pointer' }}>
                  <input type="checkbox" checked={selectedIds.has(a.id)}
                    onChange={e => {
                      const next = new Set(selectedIds)
                      e.target.checked ? next.add(a.id) : next.delete(a.id)
                      setSelectedIds(next)
                    }} />
                  <span>{a.name}</span>
                  <span style={{ color: '#9ca3af', fontSize: '0.8rem' }}>{a.email}</span>
                </label>
              ))}
            </div>
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', justifyContent: 'flex-end' }}>
              <button onClick={() => setShowAssignModal(false)}>Cancel</button>
              <button className="primary" onClick={handleAssign} disabled={assigning || selectedIds.size === 0}>
                {assigning ? 'Assigning...' : `Assign (${selectedIds.size})`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Days Off Modal */}
      {showDaysOff && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '400px', maxHeight: '60vh', display: 'flex', flexDirection: 'column' }}>
            <h3>Days Off — {showDaysOff.agentName}</h3>
            <div style={{ flex: 1, overflowY: 'auto', marginTop: '0.5rem' }}>
              {showDaysOff.daysOff.length === 0 ? (
                <p style={{ color: '#6b7280' }}>No days off recorded.</p>
              ) : (
                <table style={{ width: '100%', fontSize: '0.85rem' }}>
                  <thead><tr><th>Date</th><th>Type</th></tr></thead>
                  <tbody>
                    {showDaysOff.daysOff.map(d => (
                      <tr key={d.id}><td>{d.date}</td><td>{d.type}</td></tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            <button onClick={() => setShowDaysOff(null)} style={{ marginTop: '0.75rem', alignSelf: 'flex-end' }}>Close</button>
          </div>
        </div>
      )}
    </>
  )
}
