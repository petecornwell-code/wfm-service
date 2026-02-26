import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { schedules } from '../api/client'

export default function ScheduleSetup() {
  const { deskId } = useParams<{ deskId: string }>()
  const navigate = useNavigate()

  const [periodStart, setPeriodStart] = useState('')
  const [periodEnd, setPeriodEnd] = useState('')
  const [startTime, setStartTime] = useState('08:00')
  const [endTime, setEndTime] = useState('18:00')
  const [increment, setIncrement] = useState(15)
  const [breakBlocked, setBreakBlocked] = useState(1.0)
  const [breakDuration, setBreakDuration] = useState(60)
  const [breakMinShift, setBreakMinShift] = useState(4.0)
  const [breakAlignment, setBreakAlignment] = useState('ON_HOUR')
  const [breakCluster, setBreakCluster] = useState(20)
  const [defaultHours, setDefaultHours] = useState(8.0)
  const [overallocationLimit, setOverallocationLimit] = useState(130)
  const [underallocationLimit, setUnderallocationLimit] = useState(70)
  const [solving, setSolving] = useState(false)

  const handleSolve = async () => {
    if (!deskId || !periodStart || !periodEnd) return
    setSolving(true)
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
      console.error(err)
      setSolving(false)
    }
  }

  return (
    <>
      <h1>Schedule Setup</h1>
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
          <label>Break Duration (min)<input type="number" value={breakDuration} onChange={e => setBreakDuration(Number(e.target.value))} /></label>
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
        <button className="primary" onClick={handleSolve} disabled={solving} style={{ marginTop: '1.5rem' }}>
          {solving ? 'Starting solve...' : 'Solve'}
        </button>
      </div>

      {/* TODO: validation summary panel */}
      {/* TODO: past schedules list */}
    </>
  )
}
