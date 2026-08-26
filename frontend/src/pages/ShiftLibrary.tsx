import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  shiftTemplates as shiftTemplatesApi,
  shiftLibrary as shiftLibraryApi,
  desks,
  type ShiftTemplate,
  type ShiftTemplateBody,
  type ShiftLibraryValidation,
  type Desk,
  ApiRequestError,
  getErrorMessage,
} from '../api/client'
import { showToast } from '../components/Toast'
import { DAY_ORDER, DAY_LABELS } from './DeskAgents'

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

function minutesToTime(mins: number): string {
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

function formatHours(hours: number): string {
  return String(Math.round(hours * 100) / 100)
}

// The live preview line as the operator fills the add/edit form (Copywriting Contract, Component
// Specifications §2) — computed client-side, purely for display; the offset submitted to the API
// is derived separately, immediately before submit (D-01).
function breakPreviewText(startTime: string, endTime: string, breakStartTime: string, breakDurationMinutes: string): string | null {
  if (!startTime || !endTime) return null
  const duration = Number(breakDurationMinutes) || 0
  const breakStartMin = toMinutes(breakStartTime || startTime)
  const breakEndMin = breakStartMin + duration
  const netMinutes = toMinutes(endTime) - toMinutes(startTime) - duration
  return `Break ${minutesToTime(breakStartMin)}–${minutesToTime(breakEndMin)} (${duration}m), net ${formatHours(netMinutes / 60)}h worked.`
}

interface TemplateFormState {
  name: string
  startTime: string
  endTime: string
  breakStartTime: string
  breakDurationMinutes: string
  validWeekdays: Set<string>
  effectiveFrom: string
  effectiveTo: string
}

function emptyForm(): TemplateFormState {
  return {
    name: '',
    startTime: '',
    endTime: '',
    breakStartTime: '',
    breakDurationMinutes: '0',
    validWeekdays: new Set<string>(),
    effectiveFrom: todayIso(),
    effectiveTo: '',
  }
}

function formToBody(f: TemplateFormState): ShiftTemplateBody {
  // The operator enters a wall-clock break start; the offset the API stores is computed
  // client-side immediately before submit (D-01), never persisted as a wall-clock value.
  const breakOffsetMinutes = f.breakStartTime ? toMinutes(f.breakStartTime) - toMinutes(f.startTime) : 0
  return {
    name: f.name,
    startTime: f.startTime,
    endTime: f.endTime,
    breakOffsetMinutes,
    breakDurationMinutes: Number(f.breakDurationMinutes) || 0,
    validWeekdays: Array.from(f.validWeekdays),
    effectiveFrom: f.effectiveFrom,
    effectiveTo: f.effectiveTo || null,
  }
}

const fieldErrorStyle = { fontSize: '0.75rem', color: '#92400e', marginTop: '2px' }
const amberPanelStyle = { background: '#fffbeb', border: '1px solid #fde68a', color: '#92400e', borderRadius: '8px', padding: '0.75rem' }

const COLUMN_COUNT = 7 // Name, Start–End, Break, Weekdays, Effective range, Hours match, Actions

// The coverage panel (D-04/D-05/D-08 — one implementation, two callers). Every verdict is read
// from the response and rendered; none is recomputed from templates/timeslots in the browser.
// `extraLine` carries the mode-switch's own 400 refusal's contractedHours detail message (Task 3
// §5) — the single fatal-weekday sentence, rendered as its own line rather than a duplicate
// error surface elsewhere on the page.
function CoveragePanel({ validation, extraLine }: { validation: ShiftLibraryValidation | null; extraLine?: string | null }) {
  if (!validation) return null

  if (!validation.hasLiveDemand) {
    return (
      <div style={amberPanelStyle}>
        This desk has no staffing demand loaded. Upload staffing requirements before switching to shift-scheduled mode.
      </div>
    )
  }

  const hasProblems = validation.uncoveredWindows.length > 0 || validation.misalignedTemplates.length > 0 || !!extraLine
  if (hasProblems) {
    return (
      <div style={amberPanelStyle}>
        {validation.uncoveredWindows.length > 0 && (
          <>
            <div style={{ fontWeight: 600 }}>{validation.uncoveredWindows.length} demand window(s) have no covering shift template:</div>
            <ul style={{ maxHeight: '220px', overflowY: 'auto', margin: '4px 0 0', paddingLeft: '1.25rem' }}>
              {validation.uncoveredWindows.map((w, i) => <li key={i}>{w}</li>)}
            </ul>
          </>
        )}
        {validation.misalignedTemplates.length > 0 && (
          <div style={{ marginTop: validation.uncoveredWindows.length > 0 ? '8px' : 0 }}>
            <div style={{ fontWeight: 600 }}>Misaligned templates:</div>
            <ul style={{ margin: '4px 0 0', paddingLeft: '1.25rem' }}>
              {validation.misalignedTemplates.map((m, i) => <li key={i}>{m}</li>)}
            </ul>
          </div>
        )}
        {extraLine && (
          <div style={{ marginTop: (validation.uncoveredWindows.length > 0 || validation.misalignedTemplates.length > 0) ? '8px' : 0 }}>
            {extraLine}
          </div>
        )}
      </div>
    )
  }

  return <p>✓ All staffing-demand windows are covered by the current shift library.</p>
}

export default function ShiftLibrary() {
  const { deskId } = useParams<{ deskId: string }>()
  const [templates, setTemplates] = useState<ShiftTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [adding, setAdding] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState<TemplateFormState>(emptyForm())
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editForm, setEditForm] = useState<TemplateFormState>(emptyForm())

  const [retiringId, setRetiringId] = useState<string | null>(null)
  const [retireDate, setRetireDate] = useState(todayIso())

  const [validation, setValidation] = useState<ShiftLibraryValidation | null>(null)
  const [modeSwitchHoursError, setModeSwitchHoursError] = useState<string | null>(null)

  const [desk, setDesk] = useState<Desk | null>(null)
  const [switchingMode, setSwitchingMode] = useState(false)

  useEffect(() => {
    if (!deskId) return
    shiftTemplatesApi.list(deskId)
      .then(setTemplates)
      .catch(err => showToast('error', getErrorMessage(err)))
      .finally(() => setLoading(false))
    fetchValidation()
    desks.get(deskId).then(setDesk).catch(err => showToast('error', getErrorMessage(err)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deskId])

  // Re-fetched after every successful create/update/retire and after every mode-switch attempt
  // (SHLB-05 "reported at definition time," D-08's one-validator-two-callers). No independent
  // loading state — the panel re-renders synchronously off the response the caller already awaits.
  // On failure the panel keeps its last-known state rather than clearing to blank.
  const fetchValidation = async (): Promise<ShiftLibraryValidation | null> => {
    if (!deskId) return null
    try {
      const result = await shiftLibraryApi.validation(deskId)
      setValidation(result)
      setModeSwitchHoursError(null)
      return result
    } catch (err) {
      showToast('error', getErrorMessage(err))
      return null
    }
  }

  const clearFieldError = (field: string) => {
    setFieldErrors(fe => {
      if (!(field in fe)) return fe
      const next = { ...fe }
      delete next[field]
      return next
    })
  }

  // Blocking (D-02 grid misalignment, name-uniqueness) 400s carry a `details` array keyed by
  // field name — rendered inline below the offending input. A 409 (identity/era collision) has
  // no `field`-keyed details and falls through to the existing toast path.
  const applyErrorResponse = (err: unknown) => {
    if (err instanceof ApiRequestError && err.status === 400 && err.details.length > 0) {
      const next: Record<string, string> = {}
      for (const d of err.details) {
        if (d.field) next[d.field] = d.message
      }
      setFieldErrors(next)
    } else {
      showToast('error', getErrorMessage(err))
    }
  }

  const toggleWeekday = (target: 'add' | 'edit', day: string) => {
    const setter = target === 'add' ? setForm : setEditForm
    setter(f => {
      const next = new Set(f.validWeekdays)
      if (next.has(day)) next.delete(day)
      else next.add(day)
      return { ...f, validWeekdays: next }
    })
  }

  const startAdd = () => {
    setForm(emptyForm())
    setFieldErrors({})
    setAdding(true)
  }

  const cancelAdd = () => {
    setAdding(false)
    setFieldErrors({})
    setForm(emptyForm())
  }

  // The save's own success toast fires unconditionally; a second amber toast (P-28) fires only
  // if the refetched report additionally flags the just-saved template with an hours advisory —
  // two separate facts with two separate lifetimes, not one qualified success message.
  const toastAdvisoryIfAny = (report: ShiftLibraryValidation | null, templateId: string) => {
    const advisory = report?.hoursAdvisories.find(a => a.templateId === templateId)
    if (advisory) showToast('warning', advisory.message)
  }

  const handleCreate = async () => {
    if (!deskId || !form.name.trim() || !form.startTime || !form.endTime) return
    setSubmitting(true)
    try {
      const created = await shiftTemplatesApi.create(deskId, formToBody(form))
      setTemplates([...templates, created])
      setAdding(false)
      setFieldErrors({})
      setForm(emptyForm())
      showToast('success', 'Shift template created')
      const report = await fetchValidation()
      toastAdvisoryIfAny(report, created.id)
    } catch (err) {
      applyErrorResponse(err)
    } finally {
      setSubmitting(false)
    }
  }

  // Seeds every field from the template's current values (Specializations.tsx:58-62 pattern) —
  // never opens blank for an edit.
  const startEdit = (t: ShiftTemplate) => {
    setRetiringId(null)
    setEditingId(t.id)
    setFieldErrors({})
    setEditForm({
      name: t.name,
      startTime: t.startTime.slice(0, 5),
      endTime: t.endTime.slice(0, 5),
      breakStartTime: t.breakStartTime.slice(0, 5),
      breakDurationMinutes: String(t.breakDurationMinutes),
      validWeekdays: new Set(t.validWeekdays),
      effectiveFrom: t.effectiveFrom,
      effectiveTo: t.effectiveTo || '',
    })
  }

  const cancelEdit = () => {
    setEditingId(null)
    setFieldErrors({})
  }

  const handleUpdate = async () => {
    if (!deskId || !editingId || !editForm.name.trim() || !editForm.startTime || !editForm.endTime) return
    setSubmitting(true)
    try {
      const updated = await shiftTemplatesApi.update(deskId, editingId, formToBody(editForm))
      setTemplates(templates.map(t => (t.id === editingId ? updated : t)))
      setEditingId(null)
      setFieldErrors({})
      showToast('success', 'Shift template updated')
      const report = await fetchValidation()
      toastAdvisoryIfAny(report, updated.id)
    } catch (err) {
      applyErrorResponse(err)
    } finally {
      setSubmitting(false)
    }
  }

  // Retire (D-10): not a delete — an inline reveal of an `Effective to` date, defaulting to
  // today, plus Confirm/Cancel. Plain Save-weight styling; no destructive control anywhere.
  const startRetire = (t: ShiftTemplate) => {
    setEditingId(null)
    setRetiringId(t.id)
    setRetireDate(todayIso())
  }

  const cancelRetire = () => {
    setRetiringId(null)
  }

  const confirmRetire = async (t: ShiftTemplate) => {
    if (!deskId) return
    setSubmitting(true)
    try {
      const updated = await shiftTemplatesApi.update(deskId, t.id, {
        name: t.name,
        startTime: t.startTime.slice(0, 5),
        endTime: t.endTime.slice(0, 5),
        breakOffsetMinutes: t.breakOffsetMinutes,
        breakDurationMinutes: t.breakDurationMinutes,
        validWeekdays: t.validWeekdays,
        effectiveFrom: t.effectiveFrom,
        effectiveTo: retireDate,
      })
      setTemplates(templates.map(x => (x.id === t.id ? updated : x)))
      setRetiringId(null)
      showToast('success', 'Shift template retired')
      await fetchValidation()
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  // Scheduling Mode toggle (D-12/D-13). Optimistic-with-revert (P-30): the selection updates
  // immediately and both options disable for the duration of the request; on any error the
  // selection snaps back to the server's last known value. No native confirmation dialog in
  // either direction — Shift-to-Slot is unconditional and takes the identical code path, no
  // extra step.
  const handleModeSwitch = async (target: 'SLOT' | 'SHIFT') => {
    if (!deskId || !desk || switchingMode || desk.schedulingMode === target) return
    const previous = desk.schedulingMode
    setSwitchingMode(true)
    setDesk({ ...desk, schedulingMode: target })
    try {
      const updated = await desks.setSchedulingMode(deskId, target)
      setDesk(updated)
      await fetchValidation()
    } catch (err) {
      setDesk(d => (d ? { ...d, schedulingMode: previous } : d))
      if (err instanceof ApiRequestError && err.status === 400) {
        // A coverage/hours/demand/grid refusal — update the Coverage panel in place from the
        // error's own details array (the named windows/templates that caused the refusal),
        // never a duplicate error surface elsewhere on the page. `hasLiveDemand` is derived
        // from whether a `demand`-field detail is present, rather than forced true, so a
        // "no demand loaded" refusal doesn't render a false "all covered" success state.
        const demandMessage = err.details.find(d => d.field === 'demand')?.message ?? null
        const refusalWindowMessages = err.details.filter(d => d.field === 'coverage').map(d => d.message)
        const gridMessages = err.details.filter(d => d.field === 'grid').map(d => d.message)
        const hoursMessage = err.details.find(d => d.field === 'contractedHours')?.message ?? null
        setValidation(prev => (prev
          ? {
              ...prev,
              hasLiveDemand: demandMessage === null,
              uncoveredWindows: refusalWindowMessages.length > 0 ? refusalWindowMessages : prev.uncoveredWindows,
              misalignedTemplates: gridMessages.length > 0 ? gridMessages : prev.misalignedTemplates,
            }
          : prev))
        setModeSwitchHoursError(hoursMessage)
        if (demandMessage) showToast('error', demandMessage)
      } else if (err instanceof ApiRequestError && err.status === 409) {
        // D-13: an in-flight solve — a one-line fact, the right size for a toast.
        showToast('error', getErrorMessage(err))
      } else {
        showToast('error', getErrorMessage(err))
      }
    } finally {
      setSwitchingMode(false)
    }
  }

  if (loading) return <p>Loading...</p>

  const renderForm = (
    f: TemplateFormState,
    setF: (updater: (f: TemplateFormState) => TemplateFormState) => void,
    target: 'add' | 'edit',
  ) => (
    <div style={{ background: '#fff', padding: '0.75rem', borderRadius: '8px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <input placeholder="Name" value={f.name} onChange={e => setF(prev => ({ ...prev, name: e.target.value }))} />
        <div>
          <label style={{ fontSize: '0.8rem' }}>
            Start time{' '}
            <input type="time" value={f.startTime} onChange={e => { setF(prev => ({ ...prev, startTime: e.target.value })); clearFieldError('startTime') }} />
          </label>
          {fieldErrors.startTime && <div style={fieldErrorStyle}>{fieldErrors.startTime}</div>}
        </div>
        <div>
          <label style={{ fontSize: '0.8rem' }}>
            End time{' '}
            <input type="time" value={f.endTime} onChange={e => { setF(prev => ({ ...prev, endTime: e.target.value })); clearFieldError('endTime') }} />
          </label>
          {fieldErrors.endTime && <div style={fieldErrorStyle}>{fieldErrors.endTime}</div>}
        </div>
        <div>
          <label style={{ fontSize: '0.8rem' }}>
            Break start time{' '}
            <input type="time" value={f.breakStartTime} onChange={e => { setF(prev => ({ ...prev, breakStartTime: e.target.value })); clearFieldError('breakStartTime') }} />
          </label>
          {fieldErrors.breakStartTime && <div style={fieldErrorStyle}>{fieldErrors.breakStartTime}</div>}
        </div>
        <div>
          <label style={{ fontSize: '0.8rem' }}>
            Break duration (minutes){' '}
            {/* Clearing 'breakEndTime' (not a duration-keyed field) is deliberate: changing the
                duration moves where breakEndTime lands, so any stale breakEndTime error is now
                invalid. There is no server-side 'breakDurationMinutes' field key today (negative
                and envelope checks throw IllegalArgumentException, which carries no details), but
                if one is ever added, clear it here too so it isn't silently left stale. */}
            <input type="number" min="0" value={f.breakDurationMinutes} onChange={e => { setF(prev => ({ ...prev, breakDurationMinutes: e.target.value })); clearFieldError('breakEndTime') }} style={{ width: '80px' }} />
          </label>
          {fieldErrors.breakEndTime && <div style={fieldErrorStyle}>{fieldErrors.breakEndTime}</div>}
        </div>
      </div>

      {breakPreviewText(f.startTime, f.endTime, f.breakStartTime, f.breakDurationMinutes) && (
        <div style={{ fontSize: '0.8rem', color: '#9ca3af' }}>
          {breakPreviewText(f.startTime, f.endTime, f.breakStartTime, f.breakDurationMinutes)}
        </div>
      )}

      <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
        {DAY_ORDER.map(day => (
          <label key={day} style={{ fontSize: '0.8rem', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            {DAY_LABELS[day]}
            <input
              type="checkbox"
              checked={f.validWeekdays.has(day)}
              onChange={() => toggleWeekday(target, day)}
            />
          </label>
        ))}
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <label style={{ fontSize: '0.8rem' }}>
          Effective from{' '}
          <input type="date" value={f.effectiveFrom} onChange={e => setF(prev => ({ ...prev, effectiveFrom: e.target.value }))} />
        </label>
        <div>
          <label style={{ fontSize: '0.8rem' }}>
            Effective to{' '}
            <input type="date" value={f.effectiveTo} onChange={e => setF(prev => ({ ...prev, effectiveTo: e.target.value }))} />
          </label>
          <div style={{ fontSize: '0.75rem', color: '#9ca3af' }}>Leave blank for open-ended</div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <button
          className="primary"
          onClick={target === 'add' ? handleCreate : handleUpdate}
          disabled={submitting}
          style={target === 'edit' ? { fontSize: '0.8rem', padding: '0.2rem 0.5rem' } : undefined}
        >
          {submitting ? 'Saving...' : 'Save'}
        </button>
        <button
          onClick={target === 'add' ? cancelAdd : cancelEdit}
          disabled={submitting}
          style={target === 'edit' ? { fontSize: '0.8rem', padding: '0.2rem 0.5rem' } : undefined}
        >
          Cancel
        </button>
      </div>
    </div>
  )

  return (
    <>
      <h3 style={{ fontSize: '18px', fontWeight: 600 }}>Shift Templates</h3>

      <div style={{ margin: '16px 0' }}>
        {!adding && (
          <button className="primary" onClick={startAdd}>Add Shift Template</button>
        )}
        {adding && renderForm(form, setForm, 'add')}
      </div>

      {templates.length === 0 ? (
        <p>No shift templates yet — add one to start building this desk's shift library.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Start–End</th>
              <th>Break</th>
              <th>Weekdays</th>
              <th>Effective range</th>
              <th>Hours match</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {templates.map(t => {
              if (editingId === t.id) {
                return (
                  <tr key={t.id}>
                    <td colSpan={COLUMN_COUNT}>{renderForm(editForm, setEditForm, 'edit')}</td>
                  </tr>
                )
              }
              // Era legibility (D-11): rows arrive already grouped by name and sorted
              // effective_from-descending from the server — rendered in that order, never
              // re-sorted client-side. eraStatus comes from the response, never recomputed here.
              return (
                <tr key={t.id}>
                  <td>
                    {t.name}
                    {t.eraStatus === 'CURRENT' && (
                      <div>
                        <span style={{ fontSize: '13px', fontWeight: 600, background: '#e5e7eb', color: '#374151', borderRadius: '4px', padding: '1px 6px' }}>Current</span>
                      </div>
                    )}
                  </td>
                  <td>{t.startTime.slice(0, 5)}–{t.endTime.slice(0, 5)}</td>
                  <td>{t.breakStartTime.slice(0, 5)}–{t.breakEndTime.slice(0, 5)} ({t.breakDurationMinutes}m)</td>
                  <td>
                    <div style={{ display: 'flex', gap: '4px' }}>
                      {DAY_ORDER.map(day => (
                        <span
                          key={day}
                          style={{
                            fontSize: '0.8rem',
                            fontWeight: t.validWeekdays.includes(day) ? 600 : 400,
                            opacity: t.validWeekdays.includes(day) ? 1 : 0.4,
                          }}
                        >
                          {DAY_LABELS[day]}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td>
                    {t.effectiveFrom} – {t.effectiveTo || 'Present'}
                    {t.eraStatus !== 'CURRENT' && (
                      <span style={{ marginLeft: '6px', fontSize: '0.75rem', color: '#9ca3af' }}>
                        {t.eraStatus === 'UPCOMING' ? 'Upcoming' : 'Past'}
                      </span>
                    )}
                  </td>
                  <td>
                    {(() => {
                      // A clean row adds no colour role (no checkmark, no green) — the glyph
                      // renders only when the report flags this template. Verdict read from the
                      // response, never recomputed from the row's own duration in the browser.
                      const advisory = validation?.hoursAdvisories.find(a => a.templateId === t.id)
                      return advisory ? <span title={advisory.message}>⚠</span> : null
                    })()}
                  </td>
                  <td>
                    {retiringId === t.id ? (
                      <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                        <label style={{ fontSize: '0.8rem' }}>
                          Effective to{' '}
                          <input type="date" value={retireDate} onChange={e => setRetireDate(e.target.value)} />
                        </label>
                        <button className="primary" onClick={() => confirmRetire(t)} disabled={submitting} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Confirm</button>
                        <button onClick={cancelRetire} disabled={submitting} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Cancel</button>
                      </div>
                    ) : (
                      <div style={{ display: 'flex', gap: '0.25rem' }}>
                        <button onClick={() => startEdit(t)}>Edit</button>
                        <button onClick={() => startRetire(t)}>Retire</button>
                      </div>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      <div style={{ margin: '16px 0' }}>
        <h3 style={{ fontSize: '18px', fontWeight: 600 }}>Coverage</h3>
        <CoveragePanel validation={validation} extraLine={modeSwitchHoursError} />
      </div>

      <div style={{ margin: '16px 0' }}>
        <h3 style={{ fontSize: '18px', fontWeight: 600 }}>Scheduling Mode</h3>
        {desk && (
          <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
            <button
              onClick={() => handleModeSwitch('SLOT')}
              disabled={switchingMode}
              style={desk.schedulingMode === 'SLOT'
                ? { background: '#3b82f6', color: '#fff', border: '1px solid #3b82f6' }
                : { background: '#fff', color: '#111827', border: '1px solid #d1d5db' }}
            >
              Slot-scheduled
            </button>
            <button
              onClick={() => handleModeSwitch('SHIFT')}
              disabled={switchingMode}
              style={desk.schedulingMode === 'SHIFT'
                ? { background: '#3b82f6', color: '#fff', border: '1px solid #3b82f6' }
                : { background: '#fff', color: '#111827', border: '1px solid #d1d5db' }}
            >
              Shift-scheduled
            </button>
          </div>
        )}
      </div>
    </>
  )
}
