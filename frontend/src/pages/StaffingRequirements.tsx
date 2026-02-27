import { useState, useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { timeslots as timeslotApi, specializations as specApi } from '../api/client'
import type { Timeslot, Specialization } from '../api/client'
import { saveTimeslotParams, loadTimeslotParams } from '../timeslotParams'

export default function StaffingRequirements() {
  const { deskId } = useParams<{ deskId: string }>()
  const saved = deskId ? loadTimeslotParams(deskId) : {}
  const [periodStart, setPeriodStart] = useState(saved.periodStart ?? '')
  const [periodEnd, setPeriodEnd] = useState(saved.periodEnd ?? '')
  const [startTime, setStartTime] = useState(saved.startTime ?? '08:00')
  const [endTime, setEndTime] = useState(saved.endTime ?? '18:00')
  const [increment, setIncrement] = useState(saved.increment ?? 15)
  const [slots, setSlots] = useState<Timeslot[]>([])
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  // Persist timeslot params per-desk so Schedule Setup can pre-populate
  useEffect(() => {
    if (deskId) {
      saveTimeslotParams(deskId, { periodStart, periodEnd, startTime, endTime, increment })
    }
  }, [deskId, periodStart, periodEnd, startTime, endTime, increment])

  // Load specializations once
  useEffect(() => {
    if (!deskId) return
    specApi.list(deskId).then(setSpecs).catch(console.error)
  }, [deskId])

  // Auto-generate timeslots when all parameters are set
  useEffect(() => {
    if (!deskId || !periodStart || !periodEnd) return

    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      setError('')
      try {
        const generated = await timeslotApi.generate(deskId, {
          periodStartDate: periodStart,
          periodEndDate: periodEnd,
          startTime,
          endTime,
          incrementMinutes: increment,
        })
        setSlots(generated)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to generate timeslots')
        setSlots([])
      } finally {
        setLoading(false)
      }
    }, 600)

    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [deskId, periodStart, periodEnd, startTime, endTime, increment])

  // Group slots by date for display
  const slotsByDate = slots.reduce<Record<string, Timeslot[]>>((acc, s) => {
    (acc[s.date] ??= []).push(s)
    return acc
  }, {})

  return (
    <>
      <h1>Staffing Requirements</h1>

      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
        <h3>Period &amp; Timeslot Configuration</h3>
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
        {loading && <p style={{ color: '#6b7280', marginTop: '0.5rem' }}>Generating timeslots...</p>}
        {error && <p style={{ color: '#dc2626', marginTop: '0.5rem' }}>{error}</p>}
      </div>

      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px' }}>
        <h3>Demand Entry</h3>
        {slots.length === 0 && !loading ? (
          <p style={{ color: '#6b7280' }}>
            Set the period and time range above to generate the timeslot grid.
          </p>
        ) : slots.length > 0 && (
          <div style={{ overflowX: 'auto' }}>
            {Object.entries(slotsByDate).map(([date, daySlots]) => (
              <div key={date} style={{ marginBottom: '1.5rem' }}>
                <h4>{date}</h4>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                  <thead>
                    <tr>
                      <th style={{ textAlign: 'left', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>Timeslot</th>
                      {specs.map(s => (
                        <th key={s.id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>{s.name}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {daySlots.map(slot => (
                      <tr key={slot.id}>
                        <td style={{ padding: '4px 8px', borderBottom: '1px solid #f3f4f6' }}>{slot.startTime}–{slot.endTime}</td>
                        {specs.map(s => (
                          <td key={s.id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '1px solid #f3f4f6' }}>
                            <input type="number" min={0} defaultValue={0}
                              style={{ width: '60px', textAlign: 'center' }} />
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
            {/* TODO: save button, Erlang X toggle, copy-day */}
          </div>
        )}
      </div>
    </>
  )
}
