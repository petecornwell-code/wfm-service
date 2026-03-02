import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { timeslots as timeslotApi, specializations as specApi, staffingRequirements as srApi } from '../api/client'
import type { Timeslot, Specialization, StaffingRequirementItem } from '../api/client'
import { saveTimeslotParams, loadTimeslotParams } from '../timeslotParams'

// Key for demand state: "timeslotId:specializationId" → requiredHours
type DemandMap = Record<string, number>

function demandKey(timeslotId: string, specId: string) {
  return `${timeslotId}:${specId}`
}

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
  const [demand, setDemand] = useState<DemandMap>({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saveMsg, setSaveMsg] = useState('')
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

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

  // Load existing staffing requirements when slots are available
  const loadExisting = useCallback(async (generatedSlots: Timeslot[]) => {
    if (!deskId || !periodStart || !periodEnd || generatedSlots.length === 0) return
    try {
      const resp = await srApi.list(deskId, { from: periodStart, to: periodEnd })
      const loaded: DemandMap = {}
      for (const item of resp.data) {
        loaded[demandKey(item.timeslotId, item.specializationId)] = item.requiredHours
      }
      setDemand(loaded)
    } catch {
      // No existing requirements — start with empty grid
      setDemand({})
    }
  }, [deskId, periodStart, periodEnd])

  // Auto-generate timeslots when all parameters are set
  useEffect(() => {
    if (!deskId || !periodStart || !periodEnd) return

    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      setError('')
      setSaveMsg('')
      try {
        const generated = await timeslotApi.generate(deskId, {
          periodStartDate: periodStart,
          periodEndDate: periodEnd,
          startTime,
          endTime,
          incrementMinutes: increment,
        })
        setSlots(generated)
        await loadExisting(generated)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to generate timeslots')
        setSlots([])
        setDemand({})
      } finally {
        setLoading(false)
      }
    }, 600)

    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [deskId, periodStart, periodEnd, startTime, endTime, increment, loadExisting])

  // Group slots by date for display
  const slotsByDate = slots.reduce<Record<string, Timeslot[]>>((acc, s) => {
    (acc[s.date] ??= []).push(s)
    return acc
  }, {})

  const handleDemandChange = (timeslotId: string, specId: string, value: number) => {
    setDemand(prev => ({ ...prev, [demandKey(timeslotId, specId)]: value }))
    setSaveMsg('')
  }

  const handleSave = async () => {
    if (!deskId) return
    setSaving(true)
    setError('')
    setSaveMsg('')
    try {
      // Build requirements list from demand state, only include non-zero entries
      const requirements: StaffingRequirementItem[] = []
      for (const slot of slots) {
        for (const spec of specs) {
          const val = demand[demandKey(slot.id, spec.id)] ?? 0
          if (val > 0) {
            requirements.push({ timeslotId: slot.id, specializationId: spec.id, requiredHours: val })
          }
        }
      }
      await srApi.save(deskId, requirements)
      setSaveMsg('Saved successfully')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save requirements')
    } finally {
      setSaving(false)
    }
  }

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
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Demand Entry</h3>
          {slots.length > 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              {saveMsg && <span style={{ color: '#16a34a', fontSize: '0.85rem' }}>{saveMsg}</span>}
              <button onClick={handleSave} disabled={saving}
                style={{ padding: '0.4rem 1.2rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.6 : 1 }}>
                {saving ? 'Saving...' : 'Save'}
              </button>
            </div>
          )}
        </div>
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
                        <th key={s.id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>{s.name} (hrs)</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {daySlots.map(slot => (
                      <tr key={slot.id}>
                        <td style={{ padding: '4px 8px', borderBottom: '1px solid #f3f4f6' }}>{slot.startTime}–{slot.endTime}</td>
                        {specs.map(s => (
                          <td key={s.id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '1px solid #f3f4f6' }}>
                            <input type="number" min={0} step={0.25}
                              value={demand[demandKey(slot.id, s.id)] ?? 0}
                              onChange={e => handleDemandChange(slot.id, s.id, Math.max(0, parseFloat(e.target.value) || 0))}
                              style={{ width: '70px', textAlign: 'center' }} />
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  )
}
