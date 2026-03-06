import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { schedules, deskAgents, specializations as specApi, staffingRequirements as srApi, type ScheduleSummary, getErrorMessage } from '../api/client'
import { loadTimeslotParams } from '../timeslotParams'
import { showToast } from '../components/Toast'

export default function ScheduleSetup() {
  const { deskId } = useParams<{ deskId: string }>()
  const navigate = useNavigate()
  const saved = deskId ? loadTimeslotParams(deskId) : {}

  const [periodStart, setPeriodStart] = useState(saved.periodStart ?? '')
  const [periodEnd, setPeriodEnd] = useState(saved.periodEnd ?? '')
  const [startTime, setStartTime] = useState(saved.startTime ?? '08:00')
  const [endTime, setEndTime] = useState(saved.endTime ?? '18:00')
  const [increment, setIncrement] = useState(saved.increment ?? 15)
  const [breakBlocked, setBreakBlocked] = useState(1.0)
  const [breakDuration, setBreakDuration] = useState(60)
  const [breakMinShift, setBreakMinShift] = useState(4.0)
  const [breakAlignment, setBreakAlignment] = useState('ON_HOUR')
  const [breakCluster, setBreakCluster] = useState(20)
  const [defaultHours, setDefaultHours] = useState(8.0)
  const [overallocationLimit, setOverallocationLimit] = useState(130)
  const [underallocationLimit, setUnderallocationLimit] = useState(70)
  const [solving, setSolving] = useState(false)
  const [error, setError] = useState('')

  // Validation summary
  const [agentCount, setAgentCount] = useState<number | null>(null)
  const [specCount, setSpecCount] = useState<number | null>(null)
  const [srCount, setSrCount] = useState<number | null>(null)

  // Past schedules
  const [pastSchedules, setPastSchedules] = useState<ScheduleSummary[]>([])

  useEffect(() => {
    if (!deskId) return
    deskAgents.list(deskId).then(res => setAgentCount(res.data.filter(da => da.agent.active).length)).catch(() => {})
    specApi.list(deskId).then(s => setSpecCount(s.length)).catch(() => {})
    schedules.list(deskId).then(res => setPastSchedules(res.data)).catch(() => {})
  }, [deskId])

  useEffect(() => {
    if (!deskId || !periodStart || !periodEnd) { setSrCount(null); return }
    srApi.list(deskId, { from: periodStart, to: periodEnd }).then(res => setSrCount(res.data.length)).catch(() => setSrCount(0))
  }, [deskId, periodStart, periodEnd])

  // Filter break duration options to multiples of increment
  const breakDurationOptions = [15, 30, 45, 60, 90, 120].filter(d => d % increment === 0)

  const handleSolve = async () => {
    if (!deskId || !periodStart || !periodEnd) return
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
          <label>Period Start<input type="date" value={periodStart} onChange={e => setPeriodStart(e.target.value)} /></label>
          <label>Period End<input type="date" value={periodEnd} onChange={e => setPeriodEnd(e.target.value)} /></label>
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
        </div>

        {error && <p style={{ color: '#dc2626', marginTop: '0.75rem' }}>{error}</p>}

        <button className="primary" onClick={handleSolve} disabled={solving} style={{ marginTop: '1.5rem' }}>
          {solving ? 'Starting solve...' : 'Solve'}
        </button>
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
              {pastSchedules.map(s => (
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
                  <td><Link to={`/desks/${deskId}/schedules/${s.id}`}>View</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
