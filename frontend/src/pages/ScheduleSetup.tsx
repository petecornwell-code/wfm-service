import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { schedules, deskAgents, specializations as specApi, staffingRequirements as srApi, timeslots as tsApi, type ScheduleSummary, getErrorMessage } from '../api/client'
import { saveTimeslotParams, loadScheduleSetup, saveScheduleSetup, clearScheduleSetup } from '../timeslotParams'
import { showToast } from '../components/Toast'

const DEFAULTS = {
  periodStart: '',
  periodEnd: '',
  startTime: '08:00',
  endTime: '18:00',
  increment: 15,
  breakBlocked: 1.0,
  breakDuration: 60,
  breakMinShift: 4.0,
  breakAlignment: 'ON_HOUR',
  breakCluster: 20,
  defaultHours: 8.0,
  overallocationLimit: 130,
  underallocationLimit: 70,
  solveTimeMinutes: 10,
}

export default function ScheduleSetup() {
  const { deskId } = useParams<{ deskId: string }>()
  const navigate = useNavigate()
  const saved = deskId ? loadScheduleSetup(deskId) : {}

  const [periodStart, setPeriodStart] = useState(saved.periodStart ?? DEFAULTS.periodStart)
  const [periodEnd, setPeriodEnd] = useState(saved.periodEnd ?? DEFAULTS.periodEnd)
  const [startTime, setStartTime] = useState(saved.startTime ?? DEFAULTS.startTime)
  const [endTime, setEndTime] = useState(saved.endTime ?? DEFAULTS.endTime)
  const [increment, setIncrement] = useState(saved.increment ?? DEFAULTS.increment)
  const [breakBlocked, setBreakBlocked] = useState(saved.breakBlocked ?? DEFAULTS.breakBlocked)
  const [breakDuration, setBreakDuration] = useState(saved.breakDuration ?? DEFAULTS.breakDuration)
  const [breakMinShift, setBreakMinShift] = useState(saved.breakMinShift ?? DEFAULTS.breakMinShift)
  const [breakAlignment, setBreakAlignment] = useState(saved.breakAlignment ?? DEFAULTS.breakAlignment)
  const [breakCluster, setBreakCluster] = useState(saved.breakCluster ?? DEFAULTS.breakCluster)
  const [defaultHours, setDefaultHours] = useState(saved.defaultHours ?? DEFAULTS.defaultHours)
  const [overallocationLimit, setOverallocationLimit] = useState(saved.overallocationLimit ?? DEFAULTS.overallocationLimit)
  const [underallocationLimit, setUnderallocationLimit] = useState(saved.underallocationLimit ?? DEFAULTS.underallocationLimit)
  const [solveTimeMinutes, setSolveTimeMinutes] = useState(saved.solveTimeMinutes ?? DEFAULTS.solveTimeMinutes)
  const [solving, setSolving] = useState(false)
  const [error, setError] = useState('')

  // Validation summary
  const [agentCount, setAgentCount] = useState<number | null>(null)
  const [specCount, setSpecCount] = useState<number | null>(null)
  const [srCount, setSrCount] = useState<number | null>(null)

  // Past schedules
  const [pastSchedules, setPastSchedules] = useState<ScheduleSummary[]>([])
  const [actionLoading, setActionLoading] = useState<string | null>(null)

  const loadSchedules = useCallback(() => {
    if (!deskId) return
    schedules.list(deskId).then(res => setPastSchedules(res.data)).catch(() => {})
  }, [deskId])

  useEffect(() => {
    if (!deskId) return
    deskAgents.list(deskId).then(res => setAgentCount(res.data.filter(da => da.active).length)).catch(() => {})
    specApi.list(deskId).then(s => setSpecCount(s.length)).catch(() => {})
    loadSchedules()

    // Pre-populate from existing timeslot bounds when no saved period exists
    if (!saved.periodStart) {
      tsApi.bounds(deskId).then(bounds => {
        if (!bounds) return
        setPeriodStart(bounds.periodStart)
        setPeriodEnd(bounds.periodEnd)
        setStartTime(bounds.startTime)
        setEndTime(bounds.endTime)
        setIncrement(bounds.incrementMinutes)
      }).catch(() => {})
    }
  }, [deskId, loadSchedules])

  const handleAcceptSchedule = async (id: string, version: number) => {
    if (!deskId || actionLoading) return
    setActionLoading(id)
    try {
      await schedules.accept(deskId, id, version)
      showToast('success', 'Schedule accepted')
      loadSchedules()
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setActionLoading(null)
    }
  }

  const handleRejectSchedule = async (id: string) => {
    if (!deskId || actionLoading) return
    if (!confirm('Are you sure you want to reject this schedule?')) return
    setActionLoading(id)
    try {
      await schedules.reject(deskId, id)
      showToast('success', 'Schedule rejected')
      loadSchedules()
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setActionLoading(null)
    }
  }

  const handleDeleteSchedule = async (id: string) => {
    if (!deskId || actionLoading) return
    if (!confirm('Are you sure you want to delete this schedule? This cannot be undone.')) return
    setActionLoading(id)
    try {
      await schedules.delete(deskId, id)
      showToast('success', 'Schedule deleted')
      loadSchedules()
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setActionLoading(null)
    }
  }

  useEffect(() => {
    if (!deskId || !periodStart || !periodEnd) { setSrCount(null); return }
    srApi.list(deskId, { from: periodStart, to: periodEnd }).then(res => setSrCount(res.data.length)).catch(() => setSrCount(0))
  }, [deskId, periodStart, periodEnd])

  const handleReset = () => {
    setPeriodStart(DEFAULTS.periodStart)
    setPeriodEnd(DEFAULTS.periodEnd)
    setStartTime(DEFAULTS.startTime)
    setEndTime(DEFAULTS.endTime)
    setIncrement(DEFAULTS.increment)
    setBreakBlocked(DEFAULTS.breakBlocked)
    setBreakDuration(DEFAULTS.breakDuration)
    setBreakMinShift(DEFAULTS.breakMinShift)
    setBreakAlignment(DEFAULTS.breakAlignment)
    setBreakCluster(DEFAULTS.breakCluster)
    setDefaultHours(DEFAULTS.defaultHours)
    setOverallocationLimit(DEFAULTS.overallocationLimit)
    setUnderallocationLimit(DEFAULTS.underallocationLimit)
    setSolveTimeMinutes(DEFAULTS.solveTimeMinutes)
    if (deskId) clearScheduleSetup(deskId)
  }

  // Filter break duration options to multiples of increment
  const breakDurationOptions = [15, 30, 45, 60, 90, 120].filter(d => d % increment === 0)

  const handleSolve = async () => {
    if (!deskId) return
    if (!periodStart || !periodEnd) {
      const msg = 'Please select both a period start and end date'
      setError(msg)
      showToast('error', msg)
      return
    }
    saveTimeslotParams(deskId, { periodStart, periodEnd, startTime, endTime, increment })
    saveScheduleSetup(deskId, {
      periodStart, periodEnd, startTime, endTime, increment,
      breakBlocked, breakDuration, breakMinShift, breakAlignment, breakCluster,
      defaultHours, overallocationLimit, underallocationLimit, solveTimeMinutes,
    })
    setSolving(true)
    setError('')
    try {
      const schedule = await schedules.solve(deskId, {
        periodStartDate: periodStart,
        periodEndDate: periodEnd,
        startTime,
        endTime,
        incrementMinutes: increment,
        breakBlockedHours: breakBlocked,
        breakDurationMinutes: breakDuration,
        breakMinShiftHours: breakMinShift,
        breakStartAlignment: breakAlignment,
        breakClusterThresholdPct: breakCluster,
        defaultContractedHoursPerDay: defaultHours,
        overallocationHardLimitPct: overallocationLimit,
        underallocationHardLimitPct: underallocationLimit,
        solveTimeSeconds: solveTimeMinutes * 60,
      })
      navigate(`/desks/${deskId}/schedules/${schedule.id}`)
    } catch (err) {
      const msg = getErrorMessage(err)
      setError(msg)
      showToast('error', msg)
      setSolving(false)
    }
  }

  return (
    <>
      <h1>Schedule Setup</h1>

      {/* Validation summary */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1rem', display: 'flex', gap: '1.5rem', flexWrap: 'wrap', fontSize: '0.85rem' }}>
        <div>Active agents: <strong>{agentCount ?? '...'}</strong></div>
        <div>Specializations: <strong>{specCount ?? '...'}</strong></div>
        <div>Staffing requirements: <strong>{srCount !== null ? srCount : '...'}</strong></div>
        {agentCount === 0 && <span style={{ color: '#dc2626' }}>No active agents assigned to this desk</span>}
        {specCount === 0 && <span style={{ color: '#dc2626' }}>No specializations defined</span>}
        {srCount === 0 && periodStart && <span style={{ color: '#dc2626' }}>No staffing requirements for selected period</span>}
      </div>

      <div style={{ background: '#fff', padding: '1.5rem', borderRadius: '8px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
          <label>Period Start<input type="date" value={periodStart} onChange={e => setPeriodStart(e.target.value)} required /></label>
          <label>Period End<input type="date" value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} required /></label>
          <label>Start Time<input type="time" value={startTime} onChange={e => setStartTime(e.target.value)} /></label>
          <label>End Time<input type="time" value={endTime} onChange={e => setEndTime(e.target.value)} /></label>
          <label>Increment
            <select value={increment} onChange={e => setIncrement(Number(e.target.value))}>
              <option value={15}>15 min</option>
              <option value={30}>30 min</option>
              <option value={60}>60 min</option>
            </select>
          </label>
          <label>Break Blocked Hours<input type="number" step="0.5" value={breakBlocked} onChange={e => setBreakBlocked(Number(e.target.value))} /></label>
          <label>Break Duration (min)
            <select value={breakDuration} onChange={e => setBreakDuration(Number(e.target.value))}>
              {breakDurationOptions.map(d => <option key={d} value={d}>{d} min</option>)}
            </select>
          </label>
          <label>Min Shift for Break (hrs)<input type="number" step="0.5" value={breakMinShift} onChange={e => setBreakMinShift(Number(e.target.value))} /></label>
          <label>Break Alignment
            <select value={breakAlignment} onChange={e => setBreakAlignment(e.target.value)}>
              <option value="ON_HOUR">On the hour</option>
              <option value="ON_HALF_HOUR">On the half hour</option>
              <option value="ON_QUARTER_HOUR">On the quarter hour</option>
            </select>
          </label>
          <label>Break Cluster Threshold (%)<input type="number" value={breakCluster} onChange={e => setBreakCluster(Number(e.target.value))} /></label>
          <label>Default Contracted Hrs/Day<input type="number" step="0.25" value={defaultHours} onChange={e => setDefaultHours(Number(e.target.value))} /></label>
          <label>Over-allocation Limit (%)<input type="number" value={overallocationLimit} onChange={e => setOverallocationLimit(Number(e.target.value))} /></label>
          <label>Under-allocation Limit (%)<input type="number" value={underallocationLimit} onChange={e => setUnderallocationLimit(Number(e.target.value))} /></label>
          <label>Solve Time (min)<input type="number" min={1} max={60} value={solveTimeMinutes} onChange={e => setSolveTimeMinutes(Number(e.target.value))} /></label>
        </div>

        {error && <p style={{ color: '#dc2626', marginTop: '0.75rem' }}>{error}</p>}

        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem' }}>
          <button className="primary" onClick={handleSolve} disabled={solving}>
            {solving ? 'Starting solve...' : 'Solve'}
          </button>
          <button onClick={handleReset} disabled={solving}>
            Reset to Defaults
          </button>
        </div>
      </div>

      {/* Past schedules */}
      {pastSchedules.length > 0 && (
        <div style={{ marginTop: '2rem' }}>
          <h3>Past Schedules</h3>
          <table>
            <thead>
              <tr><th>Created</th><th>Status</th><th>Period</th><th>Score</th><th>Feasible</th><th></th></tr>
            </thead>
            <tbody>
              {pastSchedules.map(s => {
                const canAccept = s.status === 'COMPLETED' || s.status === 'STOPPED'
                const canReject = canAccept || s.status === 'FAILED'
                return (
                  <tr key={s.id}>
                    <td style={{ fontSize: '0.85rem' }}>{new Date(s.createdAt).toLocaleString()}</td>
                    <td>
                      <span style={{ padding: '0.15rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                        background: s.status === 'ACCEPTED' ? '#f0fdf4' : s.status === 'FAILED' ? '#fef2f2' : s.status === 'RUNNING' ? '#eff6ff' : '#f3f4f6',
                        color: s.status === 'ACCEPTED' ? '#16a34a' : s.status === 'FAILED' ? '#dc2626' : s.status === 'RUNNING' ? '#2563eb' : '#6b7280' }}>
                        {s.status}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.85rem' }}>{s.periodStartDate} — {s.periodEndDate}</td>
                    <td style={{ fontSize: '0.85rem' }}>{s.score ? `H:${s.score.hardScore} S:${s.score.softScore}` : '—'}</td>
                    <td>{s.feasible === true ? 'Yes' : s.feasible === false ? 'No' : '—'}</td>
                    <td style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                      <Link to={`/desks/${deskId}/schedules/${s.id}`}>View</Link>
                      {canAccept && (
                        <button className="primary" style={{ fontSize: '0.75rem', padding: '0.15rem 0.5rem' }}
                          disabled={actionLoading === s.id} onClick={() => handleAcceptSchedule(s.id, s.version)}>
                          Accept
                        </button>
                      )}
                      {canReject && (
                        <button className="danger" style={{ fontSize: '0.75rem', padding: '0.15rem 0.5rem' }}
                          disabled={actionLoading === s.id} onClick={() => handleRejectSchedule(s.id)}>
                          Reject
                        </button>
                      )}
                      {s.status === 'ACCEPTED' && (
                        <button className="danger" style={{ fontSize: '0.75rem', padding: '0.15rem 0.5rem' }}
                          disabled={actionLoading === s.id} onClick={() => handleDeleteSchedule(s.id)}>
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
