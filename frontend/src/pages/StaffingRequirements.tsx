import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { timeslots as timeslotApi } from '../api/client'

export default function StaffingRequirements() {
  const { deskId } = useParams<{ deskId: string }>()
  const [periodStart, setPeriodStart] = useState('')
  const [periodEnd, setPeriodEnd] = useState('')
  const [startTime, setStartTime] = useState('08:00')
  const [endTime, setEndTime] = useState('18:00')
  const [increment, setIncrement] = useState(15)

  const handleGenerate = async () => {
    if (!deskId || !periodStart || !periodEnd) return
    try {
      await timeslotApi.generate(deskId, {
        periodStartDate: periodStart,
        periodEndDate: periodEnd,
        startTime,
        endTime,
        incrementMinutes: increment,
      })
      // TODO: reload timeslots and demand grid
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <>
      <h1>Staffing Requirements</h1>

      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
        <h3>Timeslot Configuration</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.5rem', marginTop: '0.5rem' }}>
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
        </div>
        <button className="primary" onClick={handleGenerate} style={{ marginTop: '0.5rem' }}>Generate Timeslots</button>
      </div>

      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px' }}>
        <h3>Demand Entry</h3>
        <p style={{ color: '#6b7280' }}>
          Generate timeslots above, then enter staffing requirements per timeslot per specialization.
        </p>
        {/* TODO: demand grid with direct input / Erlang X toggle */}
      </div>
    </>
  )
}
