import { useEffect, useState, useRef, Fragment } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { schedules, specializations as specApi, daysOff as daysOffApi, type ScheduleDetail, type StaffingSummaryEntry, type AgentScheduleEntry, type ConstraintViolationEntry, type Specialization, type DayOffWithAgent, getErrorMessage } from '../api/client'
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
  const [activeTab, setActiveTab] = useState<'staffing' | 'agents' | 'allocation' | 'preferences' | 'violations' | 'pto'>('staffing')
  const [dateFilter, setDateFilter] = useState('')
  const [violationFilter, setViolationFilter] = useState<'all' | 'HARD' | 'SOFT'>('all')
  const [expandedConstraint, setExpandedConstraint] = useState<string | null>(null)
  const [specFilter, setSpecFilter] = useState('')
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [ptoData, setPtoData] = useState<DayOffWithAgent[]>([])
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

  // Fetch PTO/days-off data for the schedule period, filtered to agents at this desk
  useEffect(() => {
    if (!deskId || !schedule?.periodStartDate || !schedule?.periodEndDate) return
    daysOffApi.listForDesk(deskId, schedule.periodStartDate, schedule.periodEndDate)
      .then(data => setPtoData(data))
      .catch(err => console.error('Failed to fetch PTO data:', err))
  }, [deskId, schedule?.periodStartDate, schedule?.periodEndDate])

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
      const updated = await schedules.accept(deskId, scheduleId, schedule.version)
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
          {schedule.deskName && <span><strong>Desk:</strong> {schedule.deskName}</span>}
          <span><strong>Period:</strong> {schedule.periodStartDate} — {schedule.periodEndDate}</span>
          {schedule.score && (
            <span><strong>Score:</strong> Hard: {schedule.score.hardScore}, Soft: {schedule.score.softScore}</span>
          )}
          {schedule.feasible !== null && schedule.feasible !== undefined && (
            <span style={{ color: schedule.feasible ? '#16a34a' : '#dc2626', fontWeight: 600 }}>
              {schedule.feasible ? 'Feasible' : 'NOT FEASIBLE'}
            </span>
          )}
          {schedule.feasibleAt && schedule.createdAt && (
            <span style={{ color: '#6b7280', fontSize: '0.85rem' }}>
              Feasible after {formatElapsed(Math.floor((new Date(schedule.feasibleAt).getTime() - new Date(schedule.createdAt).getTime()) / 1000))}
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
        {(['staffing', 'agents', 'allocation', 'preferences', 'violations', 'pto'] as const).map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            style={{ background: activeTab === tab ? '#3b82f6' : '#e5e7eb', color: activeTab === tab ? '#fff' : '#374151', borderRadius: 0, padding: '0.5rem 1.25rem' }}>
            {tab === 'staffing' ? 'Staffing Summary' : tab === 'agents' ? 'Agent Schedule' : tab === 'allocation' ? 'Agent Allocation' : tab === 'preferences' ? 'Preference Report' : tab === 'violations' ? 'Constraint Violations' : 'PTO'}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', overflowX: 'auto' }}>
        {activeTab === 'staffing' && <StaffingTab data={filteredStaffing} />}
        {activeTab === 'agents' && <AgentScheduleTab data={filteredAgents} specs={specs} />}
        {activeTab === 'allocation' && <AgentAllocationTab schedule={schedule} dateFilter={dateFilter} specs={specs} specFilter={specFilter} onSpecFilterChange={setSpecFilter} />}
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
        {activeTab === 'pto' && <PtoTab data={ptoData} dateFilter={dateFilter} dates={dates} />}
      </div>
    </>
  )
}

function AgentAllocationTab({ schedule, dateFilter, specs, specFilter, onSpecFilterChange }: { schedule: ScheduleDetail; dateFilter: string; specs: Specialization[]; specFilter: string; onSpecFilterChange: (v: string) => void }) {
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

  // Filter agents by specialization: keep only entries where at least one assignment matches,
  // and narrow assignments within each entry to only the matching specialization
  const specFiltered = specFilter
    ? agentSchedule
        .map(e => {
          const matchedAssignments = e.assignments.filter(a => a.specializationName === specFilter)
          if (matchedAssignments.length === 0) return null
          const totalMinutes = matchedAssignments.reduce((sum, a) => sum + timeDiffMinutes(a.startTime, a.endTime), 0)
          return { ...e, assignments: matchedAssignments, totalHours: totalMinutes / 60 }
        })
        .filter((e): e is NonNullable<typeof e> => e !== null)
    : agentSchedule

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
  const filtered = dateFilter ? specFiltered.filter(e => e.date === dateFilter) : specFiltered

  // Get unique dates — include dates from unfilled slots even if no agent is scheduled
  const dateSet = new Set<string>()
  for (const e of filtered) dateSet.add(e.date)
  for (const key of unfilledSlots.keys()) {
    const d = key.split('|')[0]
    if (!dateFilter || d === dateFilter) dateSet.add(d)
  }
  const dates = [...dateSet].sort()

  if (dates.length === 0 && !specFilter) return <p style={{ color: '#6b7280' }}>No agent allocation data available.</p>

  return (
    <>
      {[...usedSpecNames].length > 0 && (
        <div style={{ marginBottom: '0.75rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <label style={{ fontSize: '0.85rem' }}>Filter by speciality:</label>
          <select value={specFilter} onChange={e => onSpecFilterChange(e.target.value)}>
            <option value="">All specialities</option>
            {[...usedSpecNames].sort().map(name => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </div>
      )}
      {dates.length === 0 && <p style={{ color: '#6b7280' }}>No agent allocation data for the selected speciality.</p>}
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

        // P-33: the mode branch is the very first statement of this block's rendering decision —
        // a slot-scheduled desk renders the exact table below, byte-for-byte, no restructuring
        // whatsoever; no Phase 15 grouping code executes on this branch (T-15-28).
        if (schedule.schedulingMode !== 'SHIFT') {
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
                              label = ''
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
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#e5e7eb', border: '1px solid #d1d5db', borderRadius: '2px' }} /> Break B</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#fecaca', border: '1px solid #fca5a5', borderRadius: '2px' }} /> Unfilled seat(s)</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ color: '#dc2626', fontWeight: 600 }}>Red name</span> = allocation violation</span>
              </div>
            </div>
          )
        }

        // --- Grouped variant (ENVL-10, shift-scheduled desks only) ---
        // Partition already-filtered, already-sorted sortedEntries by shift identity (P-31):
        // keyed on sourceTemplateId when present, falling back to templateName so the key
        // survives a null lineage id; entries carrying no shift land in the null bucket.
        type ShiftGroup = {
          key: string | null
          templateName: string
          startTime: string
          endTime: string
          entries: typeof sortedEntries
        }
        const groupsByKey = new Map<string | null, ShiftGroup>()
        for (const entry of sortedEntries) {
          const shift = entry.shift
          const key = shift ? (shift.sourceTemplateId || shift.templateName) : null
          let group = groupsByKey.get(key)
          if (!group) {
            group = {
              key,
              templateName: shift ? shift.templateName : '',
              startTime: shift ? shift.startTime : '',
              endTime: shift ? shift.endTime : '',
              entries: [],
            }
            groupsByKey.set(key, group)
          }
          group.entries.push(entry)
        }
        // Groups sort by shift start time ascending, tie-broken alphabetically by template name;
        // the null ("No shift assigned") bucket always sorts last, regardless of any agent's
        // actual times — an edge case belongs at the bottom, not hidden mid-list.
        const shiftGroups = [...groupsByKey.values()].sort((a, b) => {
          if (a.key === null) return 1
          if (b.key === null) return -1
          const startCompare = toHHMM(a.startTime).localeCompare(toHHMM(b.startTime))
          if (startCompare !== 0) return startCompare
          return a.templateName.localeCompare(b.templateName)
        })

        // Once 15-09 suppresses filler seats at hours no shift envelope reaches, such an hour
        // has no worked slot, no break and no unfilled seat, so it vanishes from `slots`
        // entirely and the desk appears to start later than it actually does. Regenerate the
        // full increment-aligned grid from the schedule's own operating window and union it
        // with the existing set so nothing already shown is lost. Scoped to this grouped
        // (shift-mode) branch only — `slots` itself, used by the slot-mode return above, is
        // untouched.
        const fullDaySlots = new Set(slots)
        if (schedule.incrementMinutes > 0) {
          const dayEnd = toHHMM(schedule.endTime)
          for (let t = toHHMM(schedule.startTime); t < dayEnd; t = addMinutes(t, schedule.incrementMinutes)) {
            fullDaySlots.add(t)
          }
        }
        const shiftSlots = [...fullDaySlots].sort()

        // Union of this day's shift-group envelope spans, from the group data already
        // assembled above. Envelope-only containment test — deliberately NOT band-aware: a
        // slot inside a shift's break window still counts as "reached" here. Break-window
        // rejection is instead surfaced per-cell via the divergence marks below.
        const envelopeSpans = shiftGroups
          .filter(g => g.key !== null)
          .map(g => [toHHMM(g.startTime), toHHMM(g.endTime)] as const)
        const isEnvelopeReached = (slot: string) => envelopeSpans.some(([s, e]) => slot >= s && slot < e)

        return (
          <div key={date} style={{ marginBottom: '2rem' }}>
            <h4 style={{ marginBottom: '0.5rem' }}>{date}</h4>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ borderCollapse: 'collapse', fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '4px 8px', position: 'sticky', left: 0, background: '#fff', zIndex: 1 }}>Agent</th>
                    <th style={{ textAlign: 'right', padding: '4px 6px', background: '#f3f4f6', fontWeight: 600 }}>Hours</th>
                    {shiftSlots.map(slot => {
                      const unfilled = (unfilledPerSlot[slot] || 0) > 0
                      const reached = isEnvelopeReached(slot)
                      return (
                        <th key={slot}
                          title={!unfilled && !reached ? "No shift in this desk's library covers this hour — unstaffed by design" : undefined}
                          style={{
                            padding: '4px 6px', textAlign: 'center', fontWeight: 500, borderLeft: '1px solid #e5e7eb',
                            background: unfilled ? '#fecaca' : !reached ? '#f3f4f6' : undefined,
                            color: unfilled ? '#991b1b' : !reached ? '#9ca3af' : undefined,
                            fontStyle: !unfilled && !reached ? 'italic' : undefined,
                          }}>
                          {slot.substring(0, 5)}
                        </th>
                      )
                    })}
                  </tr>
                </thead>
                <tbody>
                  {shiftGroups.map(group => (
                    <Fragment key={group.key ?? '__no_shift__'}>
                      <tr>
                        <td colSpan={2 + shiftSlots.length} style={{ padding: '12px', background: '#f3f4f6' }}>
                          {group.key === null ? (
                            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#9ca3af' }}>No shift assigned</span>
                          ) : (
                            <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>
                              {group.templateName} · {toHHMM(group.startTime)}–{toHHMM(group.endTime)} · {group.entries.length} agent(s)
                            </span>
                          )}
                        </td>
                      </tr>
                      {group.entries.map(entry => {
                        const isFailed = failedAgentIds.has(entry.agentId)
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

                        // Divergence marks (T-15-12-04): a seat outside the assigned envelope,
                        // and a legal slot inside it the agent left unworked.
                        const outOfEnvelopeSet = new Set((entry.divergence?.outOfEnvelopeSeats ?? []).map(toHHMM))
                        const unworkedLegalSet = new Set((entry.divergence?.unworkedLegalSlots ?? []).map(toHHMM))

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
                            {shiftSlots.map(slot => {
                              const isWork = workSlots.has(slot)
                              const isBreak = breakSlots.has(slot)
                              const isOutOfEnvelope = outOfEnvelopeSet.has(slot)
                              const isUnworkedLegal = unworkedLegalSet.has(slot)
                              let bg = '#fff'
                              let label = ''
                              if (isWork) {
                                const mt = matchTypes[slot]
                                const sn = specNames[slot]
                                bg = specColorMap[sn] || MATCH_COLORS[mt] || '#dcfce7'
                                label = ''
                              } else if (isBreak) {
                                bg = '#e5e7eb'
                                label = 'B'
                              }
                              let title: string | undefined
                              if (isOutOfEnvelope) {
                                label = 'E!'
                                title = 'Held seat outside the assigned envelope'
                              } else if (isUnworkedLegal) {
                                bg = '#fef3c7'
                                label = '×'
                                title = 'Legal slot inside the envelope left unworked'
                              }
                              return (
                                <td key={slot} title={title} style={{
                                  padding: '3px 6px', textAlign: 'center', borderLeft: '1px solid #e5e7eb',
                                  background: bg, fontSize: '0.7rem', color: isOutOfEnvelope ? '#92400e' : '#6b7280',
                                  fontWeight: isOutOfEnvelope ? 700 : undefined,
                                  boxShadow: isOutOfEnvelope ? 'inset 0 0 0 2px #d97706' : undefined,
                                }}>
                                  {label}
                                </td>
                              )
                            })}
                          </tr>
                        )
                      })}
                    </Fragment>
                  ))}
                  {/* Total row — once, after all groups, unchanged: the per-group headcount is a
                      finer-grained view of the same fact this row already shows, not a replacement. */}
                  <tr style={{ fontWeight: 700, background: '#f9fafb', borderTop: '2px solid #d1d5db' }}>
                    <td style={{ padding: '4px 8px', position: 'sticky', left: 0, background: '#f9fafb', zIndex: 1 }}>
                      Total: {sortedEntries.length} agents
                    </td>
                    <td style={{ textAlign: 'right', padding: '3px 6px', fontSize: '0.75rem', background: '#f9fafb' }}>
                      {sortedEntries.reduce((sum, e) => sum + Number(e.totalHours), 0).toFixed(1)}
                    </td>
                    {shiftSlots.map(slot => (
                      <td key={slot} style={{ padding: '3px 6px', textAlign: 'center', borderLeft: '1px solid #e5e7eb', fontSize: '0.75rem' }}>
                        {agentsPerSlot[slot] || ''}
                      </td>
                    ))}
                  </tr>
                  {/* Unfilled row — once, after all groups, unchanged — only shown when there are unassigned seats */}
                  {hasAnyUnfilled && (
                    <tr style={{ fontWeight: 700, background: '#fef2f2', borderTop: '1px solid #fca5a5' }}>
                      <td style={{ padding: '4px 8px', position: 'sticky', left: 0, background: '#fef2f2', zIndex: 1, color: '#991b1b' }}>
                        Unfilled
                      </td>
                      <td style={{ background: '#fef2f2' }} />
                      {shiftSlots.map(slot => (
                        <td key={slot} style={{
                          padding: '3px 6px', textAlign: 'center', borderLeft: '1px solid #fca5a5',
                          fontSize: '0.75rem', color: '#991b1b', background: (unfilledPerSlot[slot] || 0) > 0 ? '#fecaca' : '#fef2f2',
                        }}>
                          {(unfilledPerSlot[slot] || 0) > 0 ? unfilledPerSlot[slot] : ''}
                        </td>
                      ))}
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {/* Legend — base entries computed and applied exactly as today, plus two ENVL-10
                additions naming the deliberately-unstaffed header treatment and the two
                divergence marks so neither is left as an unexplained glyph. */}
            <div style={{ display: 'flex', gap: '1rem', fontSize: '0.8rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
              {[...usedSpecNames].sort().map(name => (
                <span key={name} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <span style={{ width: '12px', height: '12px', background: specColorMap[name] || '#dcfce7', border: '1px solid #d1d5db', borderRadius: '2px' }} />
                  {name}
                </span>
              ))}
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#e5e7eb', border: '1px solid #d1d5db', borderRadius: '2px' }} /> Break B</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#fecaca', border: '1px solid #fca5a5', borderRadius: '2px' }} /> Unfilled seat(s)</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#f3f4f6', border: '1px solid #d1d5db', borderRadius: '2px', fontStyle: 'italic' }} /> No shift covers this hour (unstaffed by design)</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#fff', boxShadow: 'inset 0 0 0 2px #d97706', border: '1px solid #d1d5db', borderRadius: '2px' }} /> E! = seat outside assigned envelope</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ width: '12px', height: '12px', background: '#fef3c7', border: '1px solid #d1d5db', borderRadius: '2px' }} /> × = legal slot left unworked</span>
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

function AgentScheduleTab({ data, specs }: { data: AgentScheduleEntry[]; specs: Specialization[] }) {
  if (!data || data.length === 0) return <p style={{ color: '#6b7280' }}>No agent schedule data available.</p>

  // Build specialization name -> color map
  const specColorMap: Record<string, string> = {}
  for (const s of specs) {
    if (s.color) specColorMap[s.name] = s.color
  }

  // Collect all specialization names used (for the legend)
  const usedSpecNames = new Set<string>()
  for (const entry of data) {
    for (const a of entry.assignments) {
      if (a.specializationName) usedSpecNames.add(a.specializationName)
    }
  }

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
                  <td style={{ padding: '4px 8px' }}>
                    {e.shift ? `${toHHMM(e.shift.startTime)} — ${toHHMM(e.shift.endTime)}` : `${e.shiftStart} — ${e.shiftEnd}`}
                    {/* The ⚠ is a DEFECT signal, so it is gated on out-of-envelope seats ALONE.
                        It used to fire on `outOfEnvelopeSeats > 0 || unworkedLegalSlots > 0`,
                        which predates bounded envelope slack (81117e3, V44, default 1 slot).
                        Under slack an agent whose envelope's net hours exceed their contracted
                        hours leaves a legal slot unworked BY CONSTRUCTION — e.g. Weekend Flex
                        10:00-20:00 is 9 legal slots against 8 contracted — so the warning fired
                        on every such agent-day, on every date, permanently. Measured on accepted
                        schedule 7cc71bf5: 16 of 19 markers were that false positive, burying the
                        3 real violations. An unworked slot is still shown, in neutral styling,
                        because this table has no per-cell × grid to carry it the way the Agent
                        Allocation view does. */}
                    {e.divergence && e.divergence.outOfEnvelopeSeats.length > 0 && (
                      <div
                        title={`Out-of-envelope seats: ${e.divergence.outOfEnvelopeSeats.map(toHHMM).join(', ')}\nUnworked legal slots: ${e.divergence.unworkedLegalSlots.length > 0 ? e.divergence.unworkedLegalSlots.map(toHHMM).join(', ') : 'none'}`}
                        style={{ marginTop: '2px', fontSize: '0.7rem', color: '#92400e', background: '#fffbeb', border: '1px solid #fbbf24', borderRadius: '3px', padding: '1px 4px', display: 'inline-block' }}
                      >
                        ⚠ {e.divergence.outOfEnvelopeSeats.length} outside envelope
                        {e.divergence.unworkedLegalSlots.length > 0 && `, ${e.divergence.unworkedLegalSlots.length} unworked`}
                      </div>
                    )}
                    {e.divergence && e.divergence.outOfEnvelopeSeats.length === 0
                      && e.divergence.unworkedLegalSlots.length > 0 && (
                      <div
                        title={`Legal slot(s) inside the envelope left unworked: ${e.divergence.unworkedLegalSlots.map(toHHMM).join(', ')}\nThis is normal where the shift's net hours exceed the agent's contracted hours.`}
                        style={{ marginTop: '2px', fontSize: '0.7rem', color: '#6b7280', background: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '3px', padding: '1px 4px', display: 'inline-block' }}
                      >
                        {e.divergence.unworkedLegalSlots.length} legal slot{e.divergence.unworkedLegalSlots.length === 1 ? '' : 's'} unworked
                      </div>
                    )}
                  </td>
                  <td style={{ textAlign: 'right', padding: '4px 8px' }}>{Number(e.totalHours).toFixed(2)}</td>
                  <td style={{ padding: '4px 8px' }}>
                    <div style={{ display: 'flex', gap: '2px', flexWrap: 'wrap' }}>
                      {e.assignments.map((a, j) => (
                        <span key={j} title={`${a.specializationName} (${a.matchType})`}
                          style={{ padding: '1px 4px', borderRadius: '2px', fontSize: '0.75rem',
                            background: specColorMap[a.specializationName] || MATCH_COLORS[a.matchType] || '#f3f4f6' }}>
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
      <div style={{ display: 'flex', gap: '1rem', fontSize: '0.8rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
        {[...usedSpecNames].sort().map(name => (
          <span key={name} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '12px', height: '12px', background: specColorMap[name] || '#dcfce7', border: '1px solid #d1d5db', borderRadius: '2px' }} />
            {name}
          </span>
        ))}
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

function PtoTab({ data, dateFilter, dates }: { data: DayOffWithAgent[]; dateFilter: string; dates: string[] }) {
  if (!data || data.length === 0) return <p style={{ color: '#6b7280' }}>No PTO / days-off data for this schedule period.</p>

  const filtered = dateFilter ? data.filter(d => d.date === dateFilter) : data

  // Build a matrix: rows = agents (sorted by name), columns = dates
  // Each cell shows the day-off type (PTO / MANDATORY) or is empty
  const agentMap = new Map<string, { id: string; name: string; daysByDate: Map<string, { type: string; status: string }> }>()
  for (const d of filtered) {
    if (!d.agent) continue
    let entry = agentMap.get(d.agent.id)
    if (!entry) {
      entry = { id: d.agent.id, name: d.agent.name, daysByDate: new Map() }
      agentMap.set(d.agent.id, entry)
    }
    entry.daysByDate.set(d.date, { type: d.type, status: d.status })
  }

  const agents = [...agentMap.values()].sort((a, b) => a.name.localeCompare(b.name))
  // Use schedule dates if available; otherwise derive from PTO data itself
  // so PTO is visible even while the solver is still running (dates may be empty).
  const ptoDates = dates.length > 0 ? dates : [...new Set(filtered.map(d => d.date))].sort()
  const displayDates = dateFilter ? [dateFilter] : ptoDates

  if (agents.length === 0) return <p style={{ color: '#6b7280' }}>No agents are on PTO / day off during this period.</p>

  return (
    <>
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
      <thead>
        <tr>
          <th style={{ textAlign: 'left', padding: '6px 8px', position: 'sticky', left: 0, background: '#fff', zIndex: 1 }}>Agent</th>
          {displayDates.map(d => (
            <th key={d} style={{ textAlign: 'center', padding: '6px 8px', borderLeft: '1px solid #e5e7eb' }}>{d}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {agents.map(agent => (
          <tr key={agent.id}>
            <td style={{ padding: '4px 8px', fontWeight: 500, position: 'sticky', left: 0, background: '#fff', zIndex: 1 }}>{agent.name}</td>
            {displayDates.map(d => {
              const info = agent.daysByDate.get(d)
              const type = info?.type
              const isRequested = info?.status === 'REQUESTED'
              const label = type ? (isRequested ? `${type} (Req)` : type) : null
              return (
                <td key={d} style={{
                  textAlign: 'center', padding: '4px 8px', borderLeft: '1px solid #e5e7eb',
                  background: isRequested ? '#fefce8' : type === 'PTO' ? '#eff6ff' : type === 'MANDATORY' ? '#fef2f2' : undefined,
                  color: isRequested ? '#a16207' : type === 'PTO' ? '#2563eb' : type === 'MANDATORY' ? '#dc2626' : '#d1d5db',
                  fontWeight: type ? 600 : 400, fontSize: '0.8rem',
                }}>
                  {label || '—'}
                </td>
              )
            })}
          </tr>
        ))}
        <tr style={{ fontWeight: 700, background: '#f9fafb', borderTop: '2px solid #d1d5db' }}>
          <td style={{ padding: '6px 8px', position: 'sticky', left: 0, background: '#f9fafb', zIndex: 1 }}>
            Total: {agents.length} agents
          </td>
          {displayDates.map(d => {
            const count = agents.filter(a => a.daysByDate.has(d)).length
            return (
              <td key={d} style={{ textAlign: 'center', padding: '4px 8px', borderLeft: '1px solid #e5e7eb', fontSize: '0.8rem' }}>
                {count > 0 ? count : ''}
              </td>
            )
          })}
        </tr>
      </tbody>
    </table>
    <div style={{ display: 'flex', gap: '1.5rem', marginTop: '0.75rem', fontSize: '0.8rem', color: '#6b7280', padding: '0.5rem 0.25rem' }}>
      <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
        <span style={{ display: 'inline-block', width: '14px', height: '14px', borderRadius: '3px', background: '#eff6ff', border: '1px solid #bfdbfe' }} />
        PTO (Approved)
      </span>
      <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
        <span style={{ display: 'inline-block', width: '14px', height: '14px', borderRadius: '3px', background: '#fefce8', border: '1px solid #fde68a' }} />
        PTO (Requested)
      </span>
      <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
        <span style={{ display: 'inline-block', width: '14px', height: '14px', borderRadius: '3px', background: '#fef2f2', border: '1px solid #fecaca' }} />
        Mandatory
      </span>
    </div>
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
