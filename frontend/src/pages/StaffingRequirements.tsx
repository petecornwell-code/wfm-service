import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { timeslots as timeslotApi, specializations as specApi, staffingRequirements as srApi, getErrorMessage } from '../api/client'
import type { Timeslot, Specialization, StaffingRequirementItem, ErlangXParam } from '../api/client'
import { saveTimeslotParams, loadTimeslotParams } from '../timeslotParams'
import { showToast } from '../components/Toast'

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
  const [mode, setMode] = useState<'direct' | 'erlang'>('direct')
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  // Erlang X default params per timeslot+spec
  const [erlangParams, setErlangParams] = useState<Record<string, Partial<ErlangXParam>>>({})

  useEffect(() => {
    if (deskId) {
      saveTimeslotParams(deskId, { periodStart, periodEnd, startTime, endTime, increment })
    }
  }, [deskId, periodStart, periodEnd, startTime, endTime, increment])

  useEffect(() => {
    if (!deskId) return
    specApi.list(deskId).then(setSpecs).catch(err => showToast('error', getErrorMessage(err)))
  }, [deskId])

  const loadExisting = useCallback(async (generatedSlots: Timeslot[]) => {
    if (!deskId || !periodStart || !periodEnd || generatedSlots.length === 0) return
    try {
      const loaded: DemandMap = {}
      let cursor: string | undefined
      do {
        const resp = await srApi.list(deskId, { from: periodStart, to: periodEnd, cursor })
        for (const item of resp.data) {
          loaded[demandKey(item.timeslotId, item.specializationId)] = item.requiredFTEs
        }
        cursor = resp.hasMore ? resp.nextCursor : undefined
      } while (cursor)
      setDemand(loaded)
    } catch {
      setDemand({})
    }
  }, [deskId, periodStart, periodEnd])

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
        setError(getErrorMessage(err))
        setSlots([])
        setDemand({})
      } finally {
        setLoading(false)
      }
    }, 600)

    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [deskId, periodStart, periodEnd, startTime, endTime, increment, loadExisting])

  const slotsByDate = slots.reduce<Record<string, Timeslot[]>>((acc, s) => {
    (acc[s.date] ??= []).push(s)
    return acc
  }, {})

  const handleDemandChange = (timeslotId: string, specId: string, value: number) => {
    setDemand(prev => ({ ...prev, [demandKey(timeslotId, specId)]: value }))
    setSaveMsg('')
  }

  const handleCopyDay = (sourceDate: string) => {
    const dates = Object.keys(slotsByDate).sort()
    const sourceSlots = slotsByDate[sourceDate] || []
    const newDemand = { ...demand }
    for (const targetDate of dates) {
      if (targetDate === sourceDate) continue
      const targetSlots = slotsByDate[targetDate] || []
      for (let i = 0; i < sourceSlots.length && i < targetSlots.length; i++) {
        for (const spec of specs) {
          const srcVal = demand[demandKey(sourceSlots[i].id, spec.id)] ?? 0
          newDemand[demandKey(targetSlots[i].id, spec.id)] = srcVal
        }
      }
    }
    setDemand(newDemand)
    setSaveMsg('')
    showToast('success', `Copied ${sourceDate} to all other days`)
  }

  const handleSave = async () => {
    if (!deskId) return
    setSaving(true)
    setError('')
    setSaveMsg('')
    try {
      const requirements: StaffingRequirementItem[] = []
      for (const slot of slots) {
        for (const spec of specs) {
          const val = demand[demandKey(slot.id, spec.id)] ?? 0
          if (val > 0) {
            requirements.push({ timeslotId: slot.id, specializationId: spec.id, requiredFTEs: val })
          }
        }
      }
      await srApi.save(deskId, requirements)
      setSaveMsg('Saved successfully')
      showToast('success', 'Staffing requirements saved')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleErlangCalculate = async () => {
    if (!deskId) return
    setSaving(true)
    setError('')
    try {
      const parameters: ErlangXParam[] = []
      for (const slot of slots) {
        for (const spec of specs) {
          const key = demandKey(slot.id, spec.id)
          const p = erlangParams[key]
          if (p?.callVolume && p.callVolume > 0) {
            parameters.push({
              timeslotId: slot.id,
              specializationId: spec.id,
              callVolume: p.callVolume || 0,
              aht: p.aht || 300,
              patience: p.patience || 60,
              retryRate: p.retryRate || 0.1,
              serviceLevelTarget: p.serviceLevelTarget || 0.8,
              serviceLevelThreshold: p.serviceLevelThreshold || 20,
            })
          }
        }
      }
      if (parameters.length === 0) {
        showToast('error', 'Enter call volume for at least one timeslot')
        setSaving(false)
        return
      }
      const result = await srApi.calculateErlangX(deskId, { from: periodStart, to: periodEnd, parameters })
      const loaded: DemandMap = {}
      for (const item of result.requirements) {
        loaded[demandKey(item.timeslotId, item.specializationId)] = item.requiredFTEs
      }
      setDemand(loaded)
      showToast('success', 'Erlang X calculation complete')
    } catch (err) {
      setError(getErrorMessage(err))
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
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <h3>Demand Entry</h3>
            <button onClick={() => setMode('direct')} style={{ background: mode === 'direct' ? '#3b82f6' : '#e5e7eb', color: mode === 'direct' ? '#fff' : '#374151', padding: '0.3rem 0.8rem', borderRadius: '4px', fontSize: '0.8rem' }}>Direct</button>
            <button onClick={() => setMode('erlang')} style={{ background: mode === 'erlang' ? '#3b82f6' : '#e5e7eb', color: mode === 'erlang' ? '#fff' : '#374151', padding: '0.3rem 0.8rem', borderRadius: '4px', fontSize: '0.8rem' }}>Erlang X</button>
          </div>
          {slots.length > 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              {saveMsg && <span style={{ color: '#16a34a', fontSize: '0.85rem' }}>{saveMsg}</span>}
              {mode === 'direct' ? (
                <button onClick={handleSave} disabled={saving}
                  style={{ padding: '0.4rem 1.2rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.6 : 1 }}>
                  {saving ? 'Saving...' : 'Save'}
                </button>
              ) : (
                <button onClick={handleErlangCalculate} disabled={saving}
                  style={{ padding: '0.4rem 1.2rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.6 : 1 }}>
                  {saving ? 'Calculating...' : 'Calculate Erlang X'}
                </button>
              )}
            </div>
          )}
        </div>

        {slots.length === 0 && !loading ? (
          <p style={{ color: '#6b7280' }}>Set the period and time range above to generate the timeslot grid.</p>
        ) : slots.length > 0 && mode === 'direct' && (
          <div style={{ overflowX: 'auto' }}>
            {Object.entries(slotsByDate).map(([date, daySlots]) => (
              <div key={date} style={{ marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <h4>{date}</h4>
                  <button onClick={() => handleCopyDay(date)} style={{ fontSize: '0.75rem', padding: '0.15rem 0.5rem' }}>Copy to all days</button>
                </div>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                  <thead>
                    <tr>
                      <th style={{ textAlign: 'left', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>Timeslot</th>
                      {specs.map(s => (
                        <th key={s.id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>{s.name} (FTEs)</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {daySlots.map(slot => (
                      <tr key={slot.id}>
                        <td style={{ padding: '4px 8px', borderBottom: '1px solid #f3f4f6' }}>{slot.startTime}–{slot.endTime}</td>
                        {specs.map(s => (
                          <td key={s.id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '1px solid #f3f4f6' }}>
                            <input type="number" min={0} step={1}
                              value={demand[demandKey(slot.id, s.id)] ?? 0}
                              onChange={e => handleDemandChange(slot.id, s.id, Math.max(0, Math.round(parseFloat(e.target.value) || 0)))}
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

        {slots.length > 0 && mode === 'erlang' && (
          <div style={{ overflowX: 'auto', marginTop: '0.5rem' }}>
            <p style={{ fontSize: '0.85rem', color: '#6b7280', marginBottom: '0.5rem' }}>Enter call volume for each timeslot+specialization. Other fields use defaults.</p>
            {Object.entries(slotsByDate).slice(0, 1).map(([date, daySlots]) => (
              <div key={date}>
                <h4>{date} (parameters apply to all days)</h4>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                  <thead>
                    <tr>
                      <th style={{ textAlign: 'left', padding: '4px 8px' }}>Timeslot</th>
                      {specs.map(s => (
                        <th key={s.id} style={{ textAlign: 'center', padding: '4px 8px' }}>{s.name} — Call Vol</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {daySlots.map(slot => (
                      <tr key={slot.id}>
                        <td style={{ padding: '4px 8px' }}>{slot.startTime}–{slot.endTime}</td>
                        {specs.map(s => {
                          const key = demandKey(slot.id, s.id)
                          return (
                            <td key={s.id} style={{ textAlign: 'center', padding: '4px 8px' }}>
                              <input type="number" min={0}
                                value={erlangParams[key]?.callVolume ?? 0}
                                onChange={e => setErlangParams(prev => ({ ...prev, [key]: { ...prev[key], timeslotId: slot.id, specializationId: s.id, callVolume: Number(e.target.value) } }))}
                                style={{ width: '70px', textAlign: 'center' }} />
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
            {/* Show calculated results */}
            {Object.keys(demand).length > 0 && (
              <div style={{ marginTop: '1rem' }}>
                <h4>Calculated Results (required FTEs)</h4>
                {Object.entries(slotsByDate).slice(0, 1).map(([date, daySlots]) => (
                  <table key={date} style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                    <thead>
                      <tr>
                        <th style={{ textAlign: 'left', padding: '4px 8px' }}>Timeslot</th>
                        {specs.map(s => <th key={s.id} style={{ textAlign: 'center', padding: '4px 8px' }}>{s.name}</th>)}
                      </tr>
                    </thead>
                    <tbody>
                      {daySlots.map(slot => (
                        <tr key={slot.id}>
                          <td style={{ padding: '4px 8px' }}>{slot.startTime}–{slot.endTime}</td>
                          {specs.map(s => (
                            <td key={s.id} style={{ textAlign: 'center', padding: '4px 8px' }}>{demand[demandKey(slot.id, s.id)] ?? 0}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  )
}
