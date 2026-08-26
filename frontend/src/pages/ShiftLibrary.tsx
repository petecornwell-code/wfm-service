import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { shiftTemplates as shiftTemplatesApi, type ShiftTemplate, type ShiftTemplateBody, ApiRequestError, getErrorMessage } from '../api/client'
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

const COLUMN_COUNT = 6 // Name, Start–End, Break, Weekdays, Effective range, Actions

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

  useEffect(() => {
    if (!deskId) return
    shiftTemplatesApi.list(deskId)
      .then(setTemplates)
      .catch(err => showToast('error', getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [deskId])

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
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSubmitting(false)
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
    </>
  )
}
