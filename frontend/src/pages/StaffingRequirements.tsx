import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { timeslots as timeslotApi, specializations as specApi, staffingRequirements as srApi, getErrorMessage } from '../api/client'
import type { Timeslot, Specialization, StaffingRequirementItem, ErlangXParam, ErlangCParam, FteUploadResult } from '../api/client'
import { saveTimeslotParams, loadScheduleSetup, saveScheduleSetup } from '../timeslotParams'
import type { ScheduleSetupParams } from '../timeslotParams'
import { showToast } from '../components/Toast'

type DemandMap = Record<string, number>

function demandKey(timeslotId: string, specId: string) {
  return `${timeslotId}:${specId}`
}

export default function StaffingRequirements() {
  const { deskId } = useParams<{ deskId: string }>()
  // Schedule Setup is the single source of truth for period and timeslot geometry.
  // This page reads those values and never edits them: two independently editable
  // copies of the same fields is what let the two pages drift apart in the first place.
  // setupVersion re-reads localStorage after this page writes it (FTE upload).
  const [setupVersion, setSetupVersion] = useState(0)
  const setup = useMemo(
    () => (deskId ? loadScheduleSetup(deskId) : {}),
    [deskId, setupVersion],
  )
  const periodStart = setup.periodStart ?? ''
  const periodEnd = setup.periodEnd ?? ''
  const startTime = setup.startTime ?? '08:00'
  const endTime = setup.endTime ?? '18:00'
  const increment = setup.increment ?? 15
  const setupConfigured = Boolean(setup.periodStart && setup.periodEnd)
  // Set when the stored timeslots did not match Schedule Setup and were realigned,
  // which discards the staffing requirements attached to the removed slots.
  const [realigned, setRealigned] = useState(false)
  const [slots, setSlots] = useState<Timeslot[]>([])
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [demand, setDemand] = useState<DemandMap>({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saveMsg, setSaveMsg] = useState('')
  const [mode, setMode] = useState<'direct' | 'erlangC' | 'erlangX'>('direct')
  // Visible and editable, deliberately. These were hardcoded fallbacks inside the calculate
  // handler, where nobody could see that serviceLevelTarget was being sent as 0.8 to an API that
  // reads it as a PERCENTAGE -- a 0.8% target, met by almost any headcount. Shown on screen in the
  // units the API actually uses, the mistake is not expressible.
  const [erlangSettings, setErlangSettings] = useState({
    aht: 300,
    serviceLevelTarget: 80,
    serviceLevelThreshold: 20,
    patience: 60,
    retryRate: 10,
  })
  const isErlang = mode === 'erlangC' || mode === 'erlangX'
  const modelLabel = mode === 'erlangC' ? 'Erlang C' : 'Erlang X'

  const settingField = (label: string, key: keyof typeof erlangSettings, hint?: string) => (
    <label style={{ display: 'flex', flexDirection: 'column', gap: '0.15rem', fontSize: '0.8rem' }}>
      <span style={{ fontWeight: 500 }}>{label}</span>
      <input type="number" min={0} value={erlangSettings[key]}
        onChange={e => setErlangSettings(prev => ({ ...prev, [key]: Number(e.target.value) || 0 }))}
        style={{ width: '90px', padding: '0.25rem 0.35rem', border: '1px solid #d1d5db', borderRadius: '4px' }} />
      {hint && <span style={{ color: '#6b7280', fontSize: '0.7rem' }}>{hint}</span>}
    </label>
  )
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Erlang X default params per timeslot+spec
  const [erlangParams, setErlangParams] = useState<Record<string, Partial<ErlangXParam>>>({})

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
        // Compare what is actually stored against Schedule Setup BEFORE regenerating.
        // Any difference means the stored timeslots belong to a different geometry, so
        // the staffing requirements hanging off them cannot be carried across — the
        // generate call below deletes those slots and cascades their requirements away.
        const bounds = await timeslotApi.bounds(deskId).catch(() => null)
        const mismatch = Boolean(bounds) && (
          bounds!.periodStart !== periodStart ||
          bounds!.periodEnd !== periodEnd ||
          bounds!.startTime.slice(0, 5) !== startTime.slice(0, 5) ||
          bounds!.endTime.slice(0, 5) !== endTime.slice(0, 5) ||
          bounds!.incrementMinutes !== increment
        )

        const generated = await timeslotApi.generate(deskId, {
          periodStartDate: periodStart,
          periodEndDate: periodEnd,
          startTime,
          endTime,
          incrementMinutes: increment,
        })
        setSlots(generated)
        // Persist params to localStorage only after generate succeeds, so that
        // navigating away mid-debounce doesn't store params that don't match
        // the timeslots in the database (which would cause timeslotsMatch to
        // fail on return, deleting all saved staffing requirements).
        saveTimeslotParams(deskId, { periodStart, periodEnd, startTime, endTime, increment })

        if (mismatch) {
          // Align to Schedule Setup: the old requirements are gone server-side, so
          // show the grid zeroed rather than briefly rendering stale values.
          setDemand({})
          setRealigned(true)
          showToast('warning', 'Timeslots did not match Schedule Setup. They have been regenerated and staffing requirements reset to 0.')
        } else {
          setRealigned(false)
          await loadExisting(generated)
        }
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

  /** Every timeslot+specialization the operator entered a volume against. */
  const enteredVolumes = () => {
    const entries: Array<{ slotId: string; specId: string; callVolume: number }> = []
    for (const slot of slots) {
      for (const spec of specs) {
        const p = erlangParams[demandKey(slot.id, spec.id)]
        if (p?.callVolume && p.callVolume > 0) {
          entries.push({ slotId: slot.id, specId: spec.id, callVolume: p.callVolume })
        }
      }
    }
    return entries
  }

  const handleErlangCalculate = async (model: 'erlangC' | 'erlangX') => {
    if (!deskId) return
    const entries = enteredVolumes()
    if (entries.length === 0) {
      showToast('error', 'Enter call volume for at least one timeslot')
      return
    }
    setSaving(true)
    setError('')
    try {
      // Both endpoints REPLACE the live requirements for the whole period, so both take the same
      // grid. The interval each volume is converted on comes from its own timeslot, server-side.
      const result = model === 'erlangX'
        ? await srApi.calculateErlangX(deskId, {
            from: periodStart,
            to: periodEnd,
            parameters: entries.map<ErlangXParam>(e => ({
              timeslotId: e.slotId,
              specializationId: e.specId,
              callVolume: e.callVolume,
              aht: erlangSettings.aht,
              patience: erlangSettings.patience,
              retryRate: erlangSettings.retryRate,
              serviceLevelTarget: erlangSettings.serviceLevelTarget,
              serviceLevelThreshold: erlangSettings.serviceLevelThreshold,
            })),
          })
        : await srApi.calculateErlangC(deskId, {
            from: periodStart,
            to: periodEnd,
            parameters: entries.map<ErlangCParam>(e => ({
              timeslotId: e.slotId,
              specializationId: e.specId,
              callVolume: e.callVolume,
              aht: erlangSettings.aht,
              serviceLevelTarget: erlangSettings.serviceLevelTarget,
              serviceLevelThreshold: erlangSettings.serviceLevelThreshold,
            })),
          })
      const loaded: DemandMap = {}
      for (const item of result.requirements) {
        loaded[demandKey(item.timeslotId, item.specializationId)] = item.requiredFTEs
      }
      setDemand(loaded)
      showToast('success', model === 'erlangX' ? 'Erlang X calculation complete' : 'Erlang C calculation complete')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleFteUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !deskId) return
    setUploading(true)
    setError('')
    setSaveMsg('')
    try {
      const result: FteUploadResult = await srApi.uploadFtes(deskId, file)
      showToast('success', `Uploaded: ${result.savedCount} requirements saved, ${result.skippedCount} skipped`)
      // The spreadsheet dictates its own period and granularity, so push those back
      // into Schedule Setup rather than holding a second copy here. Schedule Setup
      // stays the single source of truth; its break and solver fields are preserved.
      saveScheduleSetup(deskId, {
        ...(loadScheduleSetup(deskId) as ScheduleSetupParams),
        periodStart: result.periodStart,
        periodEnd: result.periodEnd,
        startTime: result.startTime,
        endTime: result.endTime,
        increment: result.incrementMinutes,
      })
      setSetupVersion(v => v + 1)
      // Regenerate timeslots to match the uploaded data, then load FTEs
      const generated = await timeslotApi.generate(deskId, {
        periodStartDate: result.periodStart,
        periodEndDate: result.periodEnd,
        startTime: result.startTime,
        endTime: result.endTime,
        incrementMinutes: result.incrementMinutes,
      })
      setSlots(generated)
      saveTimeslotParams(deskId, {
        periodStart: result.periodStart,
        periodEnd: result.periodEnd,
        startTime: result.startTime,
        endTime: result.endTime,
        increment: result.incrementMinutes,
      })
      // Load the newly saved FTE values into the demand map
      const loaded: Record<string, number> = {}
      let cursor: string | undefined
      do {
        const resp = await srApi.list(deskId, { from: result.periodStart, to: result.periodEnd, cursor })
        for (const item of resp.data) {
          loaded[demandKey(item.timeslotId, item.specializationId)] = item.requiredFTEs
        }
        cursor = resp.hasMore ? resp.nextCursor : undefined
      } while (cursor)
      setDemand(loaded)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  return (
    <>
      <h1>Staffing Requirements</h1>

      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.75rem', flexWrap: 'wrap' }}>
          <h3 style={{ margin: 0 }}>Period &amp; Timeslot Configuration</h3>
          <span style={{ color: '#6b7280', fontSize: '0.8rem' }}>
            From Schedule Setup — <Link to={`/desks/${deskId}/schedule-setup`}>edit there</Link>
          </span>
        </div>

        {!setupConfigured ? (
          <p style={{ color: '#6b7280', marginTop: '0.75rem' }}>
            No period configured yet. Set the period and time range in{' '}
            <Link to={`/desks/${deskId}/schedule-setup`}>Schedule Setup</Link> to generate the timeslot grid.
          </p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.5rem', marginTop: '0.5rem' }}>
            <label>Period Start<input type="date" value={periodStart} readOnly disabled /></label>
            <label>Period End<input type="date" value={periodEnd} readOnly disabled /></label>
            <label>Start Time<input type="time" value={startTime} readOnly disabled /></label>
            <label>End Time<input type="time" value={endTime} readOnly disabled /></label>
            <label>Increment<input type="text" value={`${increment} min`} readOnly disabled /></label>
          </div>
        )}

        {realigned && (
          <p style={{ color: '#b45309', background: '#fffbeb', border: '1px solid #fcd34d', borderRadius: '4px', padding: '0.5rem 0.75rem', marginTop: '0.75rem', fontSize: '0.85rem' }}>
            Stored timeslots did not match Schedule Setup. The grid has been regenerated to align with it,
            and the staffing requirements attached to the removed timeslots were discarded — all values below start at 0.
          </p>
        )}
        {loading && <p style={{ color: '#6b7280', marginTop: '0.5rem' }}>Generating timeslots...</p>}
        {error && <p style={{ color: '#dc2626', marginTop: '0.5rem' }}>{error}</p>}
      </div>

      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <h3>Demand Entry</h3>
            {(['direct', 'erlangC', 'erlangX'] as const).map(m => (
              <button key={m} onClick={() => setMode(m)}
                style={{ background: mode === m ? '#3b82f6' : '#e5e7eb', color: mode === m ? '#fff' : '#374151', padding: '0.3rem 0.8rem', borderRadius: '4px', fontSize: '0.8rem' }}>
                {m === 'direct' ? 'Direct' : m === 'erlangC' ? 'Erlang C' : 'Erlang X'}
              </button>
            ))}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            {saveMsg && <span style={{ color: '#15803d', fontSize: '0.85rem' }}>{saveMsg}</span>}
            <input type="file" accept=".xlsx" ref={fileInputRef} onChange={handleFteUpload} style={{ display: 'none' }} />
            <button onClick={() => fileInputRef.current?.click()} disabled={uploading}
              style={{ padding: '0.4rem 1.2rem', background: '#059669', color: '#fff', border: 'none', borderRadius: '4px', cursor: uploading ? 'not-allowed' : 'pointer', opacity: uploading ? 0.6 : 1 }}>
              {uploading ? 'Uploading...' : 'Upload XLSX'}
            </button>
            {slots.length > 0 && mode === 'direct' && (
              <button onClick={handleSave} disabled={saving}
                style={{ padding: '0.4rem 1.2rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.6 : 1 }}>
                {saving ? 'Saving...' : 'Save'}
              </button>
            )}
            {slots.length > 0 && isErlang && (
              <button onClick={() => handleErlangCalculate(mode as 'erlangC' | 'erlangX')} disabled={saving}
                style={{ padding: '0.4rem 1.2rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.6 : 1 }}>
                {saving ? 'Calculating...' : `Calculate ${modelLabel}`}
              </button>
            )}
          </div>
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

        {slots.length > 0 && isErlang && (
          <div style={{ overflowX: 'auto', marginTop: '0.5rem' }}>
            <p style={{ fontSize: '0.85rem', color: '#6b7280', marginBottom: '0.5rem' }}>
              Enter call volume for each timeslot+specialization — contacts IN the slot, not a
              per-hour rate. The parameters below apply to every row.{' '}
              {mode === 'erlangC'
                ? 'Erlang C is the conservative baseline: every caller waits, nobody abandons, so it asks for more agents than Erlang X on the same numbers.'
                : 'Erlang X adds impatience and retrials, so it asks for fewer agents than Erlang C.'}
            </p>
            <p style={{ fontSize: '0.8rem', color: '#b45309', background: '#fffbeb', border: '1px solid #fcd34d', borderRadius: '4px', padding: '0.4rem 0.6rem', marginBottom: '0.75rem' }}>
              Calculating REPLACES every staffing requirement from {periodStart || 'the period start'} to {periodEnd || 'the period end'},
              including rows for timeslots not listed below. To try numbers without changing anything, use the{' '}
              <Link to={`/desks/${deskId}/erlang-calculator`}>Erlang Calculator</Link>.
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '0.75rem', padding: '0.5rem 0.75rem', background: '#f9fafb', borderRadius: '6px' }}>
              {settingField('AHT (s)', 'aht', 'incl. wrap-up')}
              {settingField('Target (%)', 'serviceLevelTarget', '80 = 80%')}
              {settingField('Within (s)', 'serviceLevelThreshold')}
              {mode === 'erlangX' && settingField('Patience (s)', 'patience', 'mean before hang-up')}
              {mode === 'erlangX' && settingField('Retry (%)', 'retryRate', 'of abandoned callers')}
            </div>
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
                {Object.entries(slotsByDate).slice(0, 1).map(([date, daySlots]) => (
                  <div key={date}>
                    <h4 style={{ marginBottom: '0.15rem' }}>Required FTEs per timeslot — {date} ({modelLabel})</h4>
                    <p style={{ fontSize: '0.78rem', color: '#6b7280', marginTop: 0, marginBottom: '0.4rem' }}>
                      One row per {increment}-minute timeslot. The same parameters were applied to every
                      day from {periodStart} to {periodEnd}; switch to <strong>Direct</strong> to see and edit
                      the other days.
                    </p>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                      <thead>
                        <tr>
                          <th style={{ textAlign: 'left', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>Timeslot</th>
                          {specs.map(s => <th key={s.id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>{s.name}</th>)}
                          {specs.length > 1 && <th style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '2px solid #e5e7eb' }}>Total</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {daySlots.map(slot => {
                          const perSpec = specs.map(s => demand[demandKey(slot.id, s.id)] ?? 0)
                          return (
                            <tr key={slot.id}>
                              <td style={{ padding: '4px 8px', borderBottom: '1px solid #f3f4f6' }}>{slot.startTime}–{slot.endTime}</td>
                              {perSpec.map((v, i) => (
                                <td key={specs[i].id} style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '1px solid #f3f4f6', fontVariantNumeric: 'tabular-nums' }}>{v}</td>
                              ))}
                              {specs.length > 1 && (
                                <td style={{ textAlign: 'center', padding: '4px 8px', borderBottom: '1px solid #f3f4f6', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                                  {perSpec.reduce((a, b) => a + b, 0)}
                                </td>
                              )}
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  )
}
