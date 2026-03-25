import { useEffect, useState, useRef, Fragment } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { schedules, specializations as specApi, type ScheduleDetail, type StaffingSummaryEntry, type AgentScheduleEntry, type ConstraintViolationEntry, type Specialization, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

const MATCH_COLORS: Record<string, string> = {
  PRIMARY: '#dcfce7',
  SECONDARY: '#fef9c3',
  NONE: '#fee2e2',
}

export default function ScheduleResults() {
  const { deskId, scheduleId } = useParams<{ deskId: string; scheduleId: string }>()
  const navigate = useNavigate()
  const [schedule, setSchedule] = useState<ScheduleDetail | null>(null)
  const [activeTab, setActiveTab] = useState<'staffing' | 'agents' | 'allocation' | 'preferences' | 'violations'>('staffing')
  const [dateFilter, setDateFilter] = useState('')
  const [violationFilter, setViolationFilter] = useState<'all' | 'HARD' | 'SOFT'>('all')
  const [expandedConstraint, setExpandedConstraint] = useState<string | null>(null)
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [elapsedSeconds, setElapsedSeconds] = useState<number | null>(null)
  const pollRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (!deskId || !scheduleId) return
    const poll = () => {
      setLoading(true)
      schedules.get(deskId, scheduleId).then(data => {
        setSchedule(data)
        setLoading(false)
        if (data.status === 'RUNNING') {
          pollRef.current = setTimeout(poll, 2000)
        }
      }).catch(err => {
        setError(getErrorMessage(err))
        setLoading(false)
      })
    }
    poll()
    return () => { if (pollRef.current) clearTimeout(pollRef.current) }
  }, [deskId, scheduleId])

  useEffect(() => {
    if (deskId) specApi.list(deskId).then(setSpecs).catch(() => {})
  }, [deskId])

  // Live elapsed-time counter while solver is running
  useEffect(() => {
    if (!schedule?.createdAt) return
    const start = new Date(schedule.createdAt).getTime()
    if (isNaN(start)) return

    const tick = () => setElapsedSeconds(Math.floor((Date.now() - start) / 1000))
    tick()

    if (schedule.status === 'RUNNING') {
      const id = setInterval(tick, 1000)
      return () => clearInterval(id)
    }
  }, [schedule?.createdAt, schedule?.status])

  if (loading && !schedule) return <p>Loading schedule...</p>
  if (error && !schedule) return <p style={{ color: '#dc2626' }}>{error}</p>
  if (!schedule) return null

  const isRunning = schedule.status === 'RUNNING'
  const canAccept = schedule.status === 'COMPLETED' || schedule.status === 'STOPPED'
  const canReject = canAccept || schedule.status === 'FAILED'

  const handleStop = async () => {
    if (!deskId || !scheduleId) return
    try {
      const updated = await schedules.stop(deskId, scheduleId)
      setSchedule({ ...schedule, ...updated })
      showToast('success', 'Solver stopped')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const handleAccept = async () => {
    if (!deskId || !scheduleId || submitting) return
    if (schedule.feasible === false) {
      if (!confirm('This schedule has hard constraint violations and is not optimal. Accept anyway?')) return
    }
    setSubmitting(true)
    try {
      const updated = await schedules.accept(deskId, scheduleId)
      setSchedule({ ...schedule, ...updated })
      showToast('success', 'Schedule accepted')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const handleReject = async () => {
    if (!deskId || !scheduleId || submitting) return
    if (!confirm('Are you sure you want to reject this schedule?')) return
    setSubmitting(true)
    try {
      await schedules.reject(deskId, scheduleId)
      showToast('success', 'Schedule rejected')
      navigate(`/desks/${deskId}/schedule-setup`)
    } catch (err) {
      showToast('error', getErrorMessage(err))
      setSubmitting(false)
    }
  }

  const handleExport = async () => {
    if (!deskId || !scheduleId) return
    try {
      const res = await schedules.export(deskId, scheduleId)
      if (!res.ok) {
        showToast('error', 'Export failed')
        return
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `schedule-${scheduleId}.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  // Filter helpers
  const dates = [...new Set(schedule.agentSchedule?.map(e => e.date) || [])].sort()

  const filteredStaffing: StaffingSummaryEntry[] = (schedule.staffingSummary || []).filter(
    e => !dateFilter || e.date === dateFilter || e.date === null
  )

  const filteredAgents: AgentScheduleEntry[] = (schedule.agentSchedule || []).filter(
    e => !dateFilter || e.date === dateFilter
  )

  const filteredViolations: ConstraintViolationEntry[] = (schedule.constraintViolations || []).filter(
    e => violationFilter === 'all' || e.level === violationFilter
  )

  return (
    <>
      <h1>Schedule Results</h1>

      {/* Header */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <span>
            <strong>Status:</strong>{' '}
            <span style={{ padding: '0.15rem 0.5rem', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 600,
              background: schedule.status === 'ACCEPTED' ? '#f0fdf4' : schedule.status === 'FAILED' ? '#fef2f2' : schedule.status === 'RUNNING' ? '#eff6ff' : '#f3f4f6',
              color: schedule.status === 'ACCEPTED' ? '#16a34a' : schedule.status === 'FAILED' ? '#dc2626' : schedule.status === 'RUNNING' ? '#2563eb' : '#6b7280' }}>
              {schedule.status}
            </span>
          </span>
          <span><strong>Period:</strong> {schedule.periodStartDate} — {schedule.periodEndDate}</span>
          {schedule.score && (
            <span><strong>Score:</strong> Hard: {schedule.score.hardScore}, Soft: {schedule.score.softScore}</span>
          )}
          {schedule.feasible !== null && schedule.feasible !== undefined && (
            <span style={{ color: schedule.feasible ? '#16a34a' : '#dc2626', fontWeight: 600 }}>
              {schedule.feasible ? 'Feasible' : 'NOT FEASIBLE'}
            </span>
          )}
          {elapsedSeconds !== null && (
            <span style={{ color: '#6b7280', fontSize: '0.85rem' }}>
              {formatElapsed(elapsedSeconds)}
            </span>
          )}
        </div>

        {isRunning && (
          <div style={{ marginTop: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div style={{ width: '16px', height: '16px', border: '3px solid #3b82f6', borderTop: '3px solid transparent', borderRadius: '50%', animation: 'spin 1s linear infinite' }} />
            <span style={{ color: '#3b82f6' }}>Solver is running...</span>
          </div>
        )}

        {schedule.warnings && schedule.warnings.length > 0 && (
          <div style={{ background: '#fffbeb', border: '1px solid #fbbf24', padding: '0.75rem', borderRadius: '6px', marginTop: '0.75rem' }}>
            <strong>CAPACITY WARNING</strong>
            <ul style={{ marginTop: '0.25rem', paddingLeft: '1.25rem' }}>
              {schedule.warnings.map((w, i) => <li key={i}>{w}</li>)}
            </ul>
          </div>
        )}

        {schedule.feasible === false && schedule.violatedHardConstraints?.length > 0 && (
          <div style={{ background: '#fef2f2', border: '1px solid #fca5a5', padding: '0.75rem', borderRadius: '6px', marginTop: '0.75rem' }}>
            <strong>NON-OPTIMAL SOLUTION — Violated hard constraints:</strong>
            <ul style={{ marginTop: '0.25rem', paddingLeft: '1.25rem' }}>
              {schedule.violatedHardConstraints.map(c => (
                <li key={c}>
                  {c}
                  {c === 'Unassigned assignment' && schedule.warnings && schedule.warnings.length > 0 && (
                    <span style={{ color: '#92400e' }}> — demand exceeds available agent-hours (see warning above)</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}

        {schedule.errorMessage && (
          <div style={{ background: '#fef2f2', border: '1px solid #fca5a5', padding: '0.75rem', borderRadius: '6px', marginTop: '0.75rem' }}>
            <strong>Error:</strong> {schedule.errorMessage}
          </div>
        )}

        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
          {isRunning && <button className="danger" onClick={handleStop}>Stop Solver</button>}
          {canAccept && <button className="primary" onClick={handleAccept} disabled={submitting}>{submitting ? 'Accepting…' : 'Accept'}</button>}
          {canReject && <button className="danger" onClick={handleReject} disabled={submitting}>Reject</button>}
          {!isRunning && <button onClick={handleExport}>Export to Excel</button>}
        </div>
      </div>

      {/* Date filter */}
      {dates.length > 0 && (
        <div style={{ marginBottom: '0.5rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <label style={{ fontSize: '0.85rem' }}>Filter by date:</label>
          <select value={dateFilter} onChange={e => setDateFilter(e.target.value)}>
            <option value="">All dates</option>
            {dates.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
        </div>
      )}

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '0', marginBottom: '1rem', flexWrap: 'wrap' }}>
        {(['staffing', 'agents', 'allocation', 'preferences', 'violations'] as const).map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            style={{ background: activeTab === tab ? '#3b82f6' : '#e5e7eb', color: activeTab === tab ? '#fff' : '#374151', borderRadius: 0, padding: '0.5rem 1.25rem' }}>
            {tab === 'staffing' ? 'Staffing Summary' : tab === 'agents' ? 'Agent Schedule' : tab === 'allocation' ? 'Agent Allocation' : tab === 'preferences' ? 'Preference Report' : 'Constraint Violations'}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', overflowX: 'auto' }}>
        {activeTab === 'staffing' && <StaffingTab data={filteredStaffing} />}
        {activeTab === 'agents' && <AgentScheduleTab data={filteredAgents} />}
        {activeTab === 'allocation' && <AgentAllocationTab schedule={schedule} dateFilter={dateFilter} specs={specs} />}
        {activeTab === 'preferences' && <PreferenceTab schedule={schedule} dateFilter={dateFilter} />}
        {activeTab === 'violations' && (
          <ViolationsTab
            data={filteredViolations}
            filter={violationFilter}
            onFilterChange={setViolationFilter}
            expandedConstraint={expandedConstraint}
            onToggle={c => setExpandedConstraint(expandedConstraint === c ? null : c)}
          />
        )}
      </div>
    </>
  )
}

function AgentAllocationTab({ schedule, dateFilter, specs }: { schedule: ScheduleDetail; dateFilter: string; specs: Specialization[] }) {
  const agentSchedule = schedule.agentSchedule || []
  const violations = schedule.constraintViolations || []

  // Build specialization name -> color map
  const specColorMap: Record<string, string> = {}
  for (const s of specs) {
    if (s.color) specColorMap[s.name] = s.color
  }
  // Collect all specialization names used in assignments (for the legend)
  const usedSpecNames = new Set<string>()
  for (const entry of agentSchedule) {
    for (const a of entry.assignments) {
      if (a.specializationName) usedSpecNames.add(a.specializationName)
    }
  }

  // Collect agent IDs that have hard constraint violations
  const failedAgentIds = new Set<string>()
  for (const cv of violations) {
    if (cv.level !== 'HARD') continue
    for (const v of cv.violations || []) {
      if (v.agentId) failedAgentIds.add(v.agentId)
    }
  }

  // Build unfilled slot counts from "Unassigned assignment" constraint violations.
  // timeslotLabel format: "YYYY-MM-DD HH:MM-HH:MM"
  const unfilledSlots = new Map<string, number>() // key: "date|HH:MM"
  for (const cv of violations) {
    if (cv.constraintName !== 'Unassigned assignment') continue
    for (const v of cv.violations || []) {
      if (!v.timeslotLabel) continue
      const spaceIdx = v.timeslotLabel.indexOf(' ')
      if (spaceIdx < 0) continue
      const vDate = v.timeslotLabel.substring(0, spaceIdx)
      const vStart = toHHMM(v.timeslotLabel.substring(spaceIdx + 1))
      const key = `${vDate}|${vStart}`
      unfilledSlots.set(key, (unfilledSlots.get(key) || 0) + 1)
    }
  }

  // Filter by date
  const filtered = dateFilter ? agentSchedule.filter(e => e.date === dateFilter) : agentSchedule

  // Get unique dates — include dates from unfilled slots even if no agent is scheduled
  const dateSet = new Set<string>()
  for (const e of filtered) dateSet.add(e.date)
  for (const key of unfilledSlots.keys()) {
    const d = key.split('|')[0]
    if (!dateFilter || d === dateFilter) dateSet.add(d)
  }
  const dates = [...dateSet].sort()

  if (dates.length === 0) return <p style={{ color: '#6b7280' }}>No agent allocation data available.</p>

  return (
    <>
      {dates.map(date => {
        const dayEntries = filtered.filter(e => e.date === date)

        // Collect all timeslot start times for this day from assignments, breaks,
        // AND unfilled slots (so fully-unfilled timeslots still appear as columns)
        const slotSet = new Set<string>()
        for (const entry of dayEntries) {
          for (const a of entry.assignments) slotSet.add(toHHMM(a.startTime))
          for (const b of entry.breaks) {
            // Break may span multiple slots; derive each slot start from the break range
            const inc = entry.assignments.length > 0
              ? timeDiffMinutes(entry.assignments[0].startTime, entry.assignments[0].endTime)
              : 0
            if (inc > 0) {
              let t = toHHMM(b.startTime)
              while (t < toHHMM(b.endTime)) {
                slotSet.add(t)
                t = addMinutes(t, inc)
              }
            } else {
              slotSet.add(toHHMM(b.startTime))
            }
          }
        }
        // Add unfilled slot times for this date
        for (const key of unfilledSlots.keys()) {
          const [d, t] = key.split('|')
          if (d === date) slotSet.add(t)
        }
        const slots = [...slotSet].sort()

        // Sort agents by name
        const sortedEntries = [...dayEntries].sort((a, b) => a.agentName.localeCompare(b.agentName))

        // Count agents working per slot
        const agentsPerSlot: Record<string, number> = {}
        for (const slot of slots) agentsPerSlot[slot] = 0
        for (const entry of sortedEntries) {
          for (const a of entry.assignments) {
            const key = toHHMM(a.startTime)
            if (agentsPerSlot[key] !== undefined) agentsPerSlot[key]++
          }
        }

        // Check which slots have unfilled seats
        const unfilledPerSlot: Record<string, number> = {}
        for (const slot of slots) {
          unfilledPerSlot[slot] = unfilledSlots.get(`${date}|${slot}`) || 0
        }
        const hasAnyUnfilled = Object.values(unfilledPerSlot).some(n => n > 0)

        return (
          <div key={date} style={{ marginBottom: '2rem' }}>
            <h4 style={{ marginBottom: '0.5rem' }}>{date}</h4>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ borderCollapse: 'collapse', fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '4px 8px', position: 'sticky', left: 0, background: '#fff', zIndex: 1 }}>Agent</th>
                    <th style={{ textAlign: 'right', padding: '4px 6px', background: '#f3f4f6', fontWeight: 600 }}>Hours</th>
                    {slots.map(slot => {
                      const unfilled = unfilledPerSlot[slot] > 0
                      return (
                        <th key={slot} style={{
                          padding: '4px 6px', textAlign: 'center', fontWeight: 500, borderLeft: '1px solid #e5e7eb',
                          background: unfilled ? '#fecaca' : undefined,
                          color: unfilled ? '#991b1b' : undefined,
                        }}>
                          {slot.substring(0, 5)}
                        </th>
                      )
                    })}
                  </tr>
                </thead>
                <tbody>
                  {sortedEntries.map(entry => {
                    const isFailed = failedAgentIds.has(entry.agentId)
                    // Build lookup for this agent's slot status
                    const workSlots = new Set(entry.assignments.map(a => toHHMM(a.startTime)))
                    const matchTypes: Record<string, string> = {}
                    const specNames: Record<string, string> = {}
                    for (const a of entry.assignments) {
                      matchTypes[toHHMM(a.startTime)] = a.matchType
                      specNames[toHHMM(a.startTime)] = a.specializationName
                    }

                    const breakSlots = new Set<string>()
                    const inc = entry.assignments.length > 0
                      ? timeDiffMinutes(entry.assignments[0].startTime, entry.assignments[0].endTime)
                      : 0
                    for (const b of entry.breaks) {
                      if (inc > 0) {
                        let t = toHHMM(b.startTime)
                        while (t < toHHMM(b.endTime)) {
                          breakSlots.add(t)
                          t = addMinutes(t, inc)
                        }
                      } else {
                        breakSlots.add(toHHMM(b.startTime))
                      }
                    }

                    return (
                      <tr key={entry.agentId + entry.date}>
                        <td style={{
                          padding: '3px 8px', position: 'sticky', left: 0, background: '#fff', zIndex: 1,
                          fontWeight: 500, color: isFailed ? '#dc2626' : '#111827',
                        }}>
                          {entry.agentName}
                        </td>
                        <td style={{ textAlign: 'right', padding: '3px 6px', fontSize: '0.75rem', color: '#374151', background: '#f9fafb' }}>
                          {Number(entry.totalHours).toFixed(1)}
                        </td>
                        {slots.map(slot => {
                          const isWork = workSlots.has(slot)
                          const isBreak = breakSlots.has(slot)
                          let bg = '#fff'
                          let label = ''
                          if (isWork) {
                            const mt = matchTypes[slot]
                            const sn = specNames[slot]
                            bg = specColorMap[sn] || MATCH_COLORS[mt] || '#dcfce7'
                            label = mt === 'SECONDARY' ? 'S' : ''
                          } else if (isBreak) {
                            bg = '#e5e7eb'
                            label = 'B'
                          }
                          return (
                            <td key={slot} style={{
                              padding: '3px 6px', textAlign: 'center', borderLeft: '1px solid #e5e7eb',
                              background: bg, fontSize: '0.7rem', color: '#6b7280',
                            }}>
                              {label}
                            </td>
                          )
                        })}
                      </tr>
                    )
                  })}
                  {/* Total row */}
                  <tr style={{ fontWeight: 700, background: '#f9fafb', borderTop: '2px solid #d1d5db' }}>
                    <td style={{ padding: '4px 8px', position: 'sticky', left: 0, background: '#f9fafb', zIndex: 1 }}>
                      Total: {sortedEntries.length} agents
                    </td>
                    <td style={{ textAlign: 'right', padding: '3px 6px', fontSize: '0.75rem', background: '#f9fafb' }}>
                      {sortedEntries.reduce((sum, e) => sum + Number(e.totalHours), 0).toFixed(1)}
                    </td>
                    {slots.map(slot => (
                      <td key={slot} style={{ padding: '3px 6px', textAlign: 'center', borderLeft: '1px solid #e5e7eb', fontSize: '0.75rem' }}>
                        {agentsPerSlot[slot] || ''}
                      </td>
                    ))}
                  </tr>
                  {/* Unfilled row — only shown when there are unassigned seats */}
                  {hasAnyUnfilled && (
                    <tr style={{ fontWeight: 700, background: '#fef2f2', borderTop: '1px solid #fca5a5' }}>
                      <td style={{ padding: '4px 8px', position: 'sticky', left: 0, background: '#fef2f2', zIndex: 1, color: '#991b1b' }}>
                        Unfilled
                      </td>
                      <td style={{ background: '#fef2f2' }} />
                      {slots.map(slot => (
                        <td key={slot} style={{
                          padding: '3px 6px', textAlign: 'center', borderLeft: '1px solid #fca5a5',
                          fontSize: '0.75rem', color: '#991b1b', background: unfilledPerSlot[slot] > 0 ? '#fecaca' : '#fef2f2',
                        }}>
                          {unfilledPerSlot[slot] > 0 ? unfilledPerSlot[slot] : ''}
                        </td>
                      ))}
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {/* Legend */}
            <div style={{ display: 'flex', gap: '1rem', fontSize: '0.8rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
              {[...usedSpecNames].sort().map(name => (
                <span key={name} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <span style={{ width: '12px', height: '12px', background: specColorMap[name] || '#dcfce7', border: '1px solid #d1d5db', borderRadius: '2px' }} />
                  {name}
                </span>
              ))}
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ fontSize: '0.7rem', fontWeight: 600 }}>S</span> = secondary match</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#e5e7eb', border: '1px solid #d1d5db', borderRadius: '2px' }} /> Break B</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#fecaca', border: '1px solid #fca5a5', borderRadius: '2px' }} /> Unfilled seat(s)</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ color: '#dc2626', fontWeight: 600 }}>Red name</span> = allocation violation</span>
            </div>
          </div>
        )
      })}
    </>
  )
}

/** Format elapsed seconds as "Xm Ys" or "Xh Ym" */
function formatElapsed(totalSeconds: number): string {
  const mins = Math.floor(totalSeconds / 60)
  const secs = totalSeconds % 60
  if (mins < 60) return `${mins}m ${secs}s`
  const hrs = Math.floor(mins / 60)
  const remMins = mins % 60
  return `${hrs}h ${remMins}m`
}

/** Normalize time to "HH:MM" — strips seconds from "HH:MM:SS" */
function toHHMM(time: string): string {
  return time.substring(0, 5)
}

/** Parse "HH:MM" or "HH:MM:SS" time difference in minutes */
function timeDiffMinutes(start: string, end: string): number {
  const [sh, sm] = start.split(':').map(Number)
  const [eh, em] = end.split(':').map(Number)
  return (eh * 60 + em) - (sh * 60 + sm)
}

/** Add minutes to a "HH:MM" or "HH:MM:SS" time string, return "HH:MM" */
function addMinutes(time: string, minutes: number): string {
  const [h, m] = time.split(':').map(Number)
  const total = h * 60 + m + minutes
  const nh = Math.floor(total / 60) % 24
  const nm = total % 60
  return `${String(nh).padStart(2, '0')}:${String(nm).padStart(2, '0')}`
}

function StaffingTab({ data }: { data: StaffingSummaryEntry[] }) {
  if (!data || data.length === 0) return <p style={{ color: '#6b7280' }}>No staffing summary data available.</p>

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
      <thead>
        <tr>
          <th style={{ textAlign: 'left', padding: '6px 8px' }}>Date</th>
          <th style={{ textAlign: 'left', padding: '6px 8px' }}>Specialization</th>
          <th style={{ textAlign: 'right', padding: '6px 8px' }}>Predicted Hrs</th>
          <th style={{ textAlign: 'right', padding: '6px 8px' }}>Actual Hrs</th>
          <th style={{ textAlign: 'right', padding: '6px 8px' }}>Delta</th>
          <th style={{ textAlign: 'right', padding: '6px 8px' }}>Coverage %</th>
        </tr>
      </thead>
      <tbody>
        {data.map((e, i) => {
          const isTotal = e.specializationName === 'TOTAL' || e.specializationName === 'GRAND TOTAL'
          const coverageColor = e.coveragePct >= 100 ? '#16a34a' : e.coveragePct >= 80 ? '#ca8a04' : '#dc2626'
          return (
            <tr key={i} style={isTotal ? { fontWeight: 700, background: '#f9fafb' } : {}}>
              <td style={{ padding: '4px 8px' }}>{e.date || ''}</td>
              <td style={{ padding: '4px 8px' }}>{e.specializationName}</td>
              <td style={{ textAlign: 'right', padding: '4px 8px' }}>{Number(e.predictedHours).toFixed(2)}</td>
              <td style={{ textAlign: 'right', padding: '4px 8px' }}>{Number(e.actualHours).toFixed(2)}</td>
              <td style={{ textAlign: 'right', padding: '4px 8px', color: Number(e.deltaHours) >= 0 ? '#16a34a' : '#dc2626' }}>
                {Number(e.deltaHours) > 0 ? '+' : ''}{Number(e.deltaHours).toFixed(2)}
              </td>
              <td style={{ textAlign: 'right', padding: '4px 8px', color: coverageColor, fontWeight: 600 }}>
                {Number(e.coveragePct).toFixed(1)}%
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

function AgentScheduleTab({ data }: { data: AgentScheduleEntry[] }) {
  if (!data || data.length === 0) return <p style={{ color: '#6b7280' }}>No agent schedule data available.</p>

  // Group by agent
  const byAgent = data.reduce<Record<string, AgentScheduleEntry[]>>((acc, e) => {
    (acc[e.agentName] ??= []).push(e)
    return acc
  }, {})

  return (
    <>
      {Object.entries(byAgent).map(([agentName, entries]) => (
        <div key={agentName} style={{ marginBottom: '1.5rem' }}>
          <h4 style={{ marginBottom: '0.25rem' }}>{agentName}</h4>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', padding: '4px 8px' }}>Date</th>
                <th style={{ textAlign: 'left', padding: '4px 8px' }}>Shift</th>
                <th style={{ textAlign: 'right', padding: '4px 8px' }}>Hours</th>
                <th style={{ textAlign: 'left', padding: '4px 8px' }}>Assignments</th>
                <th style={{ textAlign: 'left', padding: '4px 8px' }}>Breaks</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e, i) => (
                <tr key={i}>
                  <td style={{ padding: '4px 8px' }}>{e.date}</td>
                  <td style={{ padding: '4px 8px' }}>{e.shiftStart} — {e.shiftEnd}</td>
                  <td style={{ textAlign: 'right', padding: '4px 8px' }}>{Number(e.totalHours).toFixed(2)}</td>
                  <td style={{ padding: '4px 8px' }}>
                    <div style={{ display: 'flex', gap: '2px', flexWrap: 'wrap' }}>
                      {e.assignments.map((a, j) => (
                        <span key={j} title={`${a.specializationName} (${a.matchType})`}
                          style={{ padding: '1px 4px', borderRadius: '2px', fontSize: '0.75rem',
                            background: MATCH_COLORS[a.matchType] || '#f3f4f6' }}>
                          {a.startTime}-{a.endTime} {a.specializationName}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td style={{ padding: '4px 8px' }}>
                    {e.breaks.map((b, j) => (
                      <span key={j} style={{ fontSize: '0.8rem', color: '#6b7280' }}>
                        {b.startTime}-{b.endTime} ({b.durationMinutes}m)
                      </span>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
      <div style={{ display: 'flex', gap: '1rem', fontSize: '0.8rem', marginTop: '0.5rem' }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: MATCH_COLORS.PRIMARY, borderRadius: '2px' }} /> Primary</span>
        <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: MATCH_COLORS.SECONDARY, borderRadius: '2px' }} /> Secondary</span>
        <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: MATCH_COLORS.NONE, borderRadius: '2px' }} /> None</span>
      </div>
    </>
  )
}

function PreferenceTab({ schedule, dateFilter }: { schedule: ScheduleDetail; dateFilter: string }) {
  const report = schedule.preferenceReport
  if (!report || !report.entries || report.entries.length === 0) {
    return <p style={{ color: '#6b7280' }}>No preference report data available.</p>
  }

  const entries = dateFilter ? report.entries.filter(e => e.date === dateFilter) : report.entries

  return (
    <>
      {report.summary && (
        <div style={{ display: 'flex', gap: '1.5rem', marginBottom: '1rem', fontSize: '0.85rem', background: '#f9fafb', padding: '0.75rem', borderRadius: '6px' }}>
          <div>Total preferences: <strong>{report.summary.totalPreferences}</strong></div>
          <div>Start time honoured: <strong>{report.summary.startTimeHonouredCount}</strong></div>
          <div>Break time honoured: <strong>{report.summary.breakTimeHonouredCount}</strong></div>
          <div>Overall honoured: <strong>{Number(report.summary.overallHonouredPct).toFixed(1)}%</strong></div>
        </div>
      )}
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
        <thead>
          <tr>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Agent</th>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Date</th>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Source</th>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Pref Start</th>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Actual Start</th>
            <th style={{ textAlign: 'center', padding: '6px 8px' }}>Honoured</th>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Pref Break</th>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Actual Break</th>
            <th style={{ textAlign: 'center', padding: '6px 8px' }}>Honoured</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((e, i) => (
            <tr key={i}>
              <td style={{ padding: '4px 8px' }}>{e.agentName}</td>
              <td style={{ padding: '4px 8px' }}>{e.date}</td>
              <td style={{ padding: '4px 8px', fontSize: '0.8rem' }}>{e.preferenceSource}</td>
              <td style={{ padding: '4px 8px' }}>{e.preferredStartTime || '—'}</td>
              <td style={{ padding: '4px 8px' }}>{e.actualStartTime || '—'}</td>
              <td style={{ textAlign: 'center', padding: '4px 8px' }}>
                <span style={{ color: e.startTimeHonoured ? '#16a34a' : '#dc2626' }}>{e.startTimeHonoured ? 'Yes' : 'No'}</span>
              </td>
              <td style={{ padding: '4px 8px' }}>{e.preferredBreakTime || '—'}</td>
              <td style={{ padding: '4px 8px' }}>{e.actualBreakTime || '—'}</td>
              <td style={{ textAlign: 'center', padding: '4px 8px' }}>
                <span style={{ color: e.breakTimeHonoured ? '#16a34a' : '#dc2626' }}>{e.breakTimeHonoured ? 'Yes' : 'No'}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

function ViolationsTab({
  data, filter, onFilterChange, expandedConstraint, onToggle
}: {
  data: ConstraintViolationEntry[]
  filter: 'all' | 'HARD' | 'SOFT'
  onFilterChange: (f: 'all' | 'HARD' | 'SOFT') => void
  expandedConstraint: string | null
  onToggle: (c: string) => void
}) {
  if (!data || data.length === 0) return <p style={{ color: '#6b7280' }}>No constraint violations.</p>

  return (
    <>
      <div style={{ marginBottom: '0.75rem', display: 'flex', gap: '0.5rem' }}>
        {(['all', 'HARD', 'SOFT'] as const).map(f => (
          <button key={f} onClick={() => onFilterChange(f)}
            style={{ background: filter === f ? '#3b82f6' : '#e5e7eb', color: filter === f ? '#fff' : '#374151', padding: '0.3rem 0.8rem', borderRadius: '4px', fontSize: '0.8rem' }}>
            {f === 'all' ? 'All' : f}
          </button>
        ))}
      </div>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
        <thead>
          <tr>
            <th style={{ textAlign: 'left', padding: '6px 8px' }}>Constraint</th>
            <th style={{ textAlign: 'center', padding: '6px 8px' }}>Level</th>
            <th style={{ textAlign: 'right', padding: '6px 8px' }}>Violations</th>
            <th style={{ textAlign: 'right', padding: '6px 8px' }}>Penalty (H/S)</th>
            <th style={{ textAlign: 'center', padding: '6px 8px' }}></th>
          </tr>
        </thead>
        <tbody>
          {data.map(e => (
            <Fragment key={e.constraintName}>
              <tr style={{ cursor: 'pointer' }} onClick={() => onToggle(e.constraintName)}>
                <td style={{ padding: '4px 8px', fontWeight: 500 }}>{e.constraintName}</td>
                <td style={{ textAlign: 'center', padding: '4px 8px' }}>
                  <span style={{ padding: '0.1rem 0.4rem', borderRadius: '3px', fontSize: '0.75rem', fontWeight: 600,
                    background: e.level === 'HARD' ? '#fef2f2' : '#f0fdf4',
                    color: e.level === 'HARD' ? '#dc2626' : '#16a34a' }}>
                    {e.level}
                  </span>
                </td>
                <td style={{ textAlign: 'right', padding: '4px 8px' }}>{e.violationCount}</td>
                <td style={{ textAlign: 'right', padding: '4px 8px' }}>
                  {e.totalPenalty ? `${e.totalPenalty.hardScore}/${e.totalPenalty.softScore}` : '—'}
                </td>
                <td style={{ textAlign: 'center', padding: '4px 8px', fontSize: '0.8rem' }}>
                  {expandedConstraint === e.constraintName ? '▾' : '▸'}
                </td>
              </tr>
              {expandedConstraint === e.constraintName && e.violations?.map((v, i) => (
                <tr key={`${e.constraintName}-${i}`} style={{ background: '#f9fafb' }}>
                  <td colSpan={5} style={{ padding: '4px 8px 4px 2rem', fontSize: '0.8rem', color: '#6b7280' }}>
                    {v.agentName && <span style={{ marginRight: '0.5rem' }}><strong>{v.agentName}</strong></span>}
                    {v.timeslotLabel && <span style={{ marginRight: '0.5rem' }}>[{v.timeslotLabel}]</span>}
                    {v.description}
                  </td>
                </tr>
              ))}
            </Fragment>
          ))}
        </tbody>
      </table>
    </>
  )
}
