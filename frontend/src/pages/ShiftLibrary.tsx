import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { shiftTemplates as shiftTemplatesApi, type ShiftTemplate, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'
import { DAY_ORDER, DAY_LABELS } from './DeskAgents'

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

function emptyForm() {
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

export default function ShiftLibrary() {
  const { deskId } = useParams<{ deskId: string }>()
  const [templates, setTemplates] = useState<ShiftTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [adding, setAdding] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState(emptyForm())

  useEffect(() => {
    if (!deskId) return
    shiftTemplatesApi.list(deskId)
      .then(setTemplates)
      .catch(err => showToast('error', getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [deskId])

  const toggleWeekday = (day: string) => {
    setForm(f => {
      const next = new Set(f.validWeekdays)
      if (next.has(day)) next.delete(day)
      else next.add(day)
      return { ...f, validWeekdays: next }
    })
  }

  const startAdd = () => {
    setForm(emptyForm())
    setAdding(true)
  }

  const cancelAdd = () => {
    setAdding(false)
    setForm(emptyForm())
  }

  const handleCreate = async () => {
    if (!deskId || !form.name.trim() || !form.startTime || !form.endTime) return
    setSubmitting(true)
    try {
      // Break offset is computed client-side from the wall-clock break start (P-05) — the API
      // and database store the offset only (D-01), never a raw break-start wall-clock value.
      const breakOffsetMinutes = form.breakStartTime
        ? toMinutes(form.breakStartTime) - toMinutes(form.startTime)
        : 0
      const created = await shiftTemplatesApi.create(deskId, {
        name: form.name,
        startTime: form.startTime,
        endTime: form.endTime,
        breakOffsetMinutes,
        breakDurationMinutes: Number(form.breakDurationMinutes) || 0,
        validWeekdays: Array.from(form.validWeekdays),
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || null,
      })
      setTemplates([...templates, created])
      setAdding(false)
      setForm(emptyForm())
      showToast('success', 'Shift template created')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p>Loading...</p>

  return (
    <>
      <h3 style={{ fontSize: '18px', fontWeight: 600 }}>Shift Templates</h3>

      <div style={{ margin: '16px 0' }}>
        {!adding && (
          <button className="primary" onClick={startAdd}>Add Shift Template</button>
        )}
        {adding && (
          <div style={{ background: '#fff', padding: '0.75rem', borderRadius: '8px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
              <input placeholder="Name" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
              <label style={{ fontSize: '0.8rem' }}>
                Start time{' '}
                <input type="time" value={form.startTime} onChange={e => setForm({ ...form, startTime: e.target.value })} />
              </label>
              <label style={{ fontSize: '0.8rem' }}>
                End time{' '}
                <input type="time" value={form.endTime} onChange={e => setForm({ ...form, endTime: e.target.value })} />
              </label>
              <label style={{ fontSize: '0.8rem' }}>
                Break start time{' '}
                <input type="time" value={form.breakStartTime} onChange={e => setForm({ ...form, breakStartTime: e.target.value })} />
              </label>
              <label style={{ fontSize: '0.8rem' }}>
                Break duration (minutes){' '}
                <input type="number" min="0" value={form.breakDurationMinutes} onChange={e => setForm({ ...form, breakDurationMinutes: e.target.value })} style={{ width: '80px' }} />
              </label>
            </div>

            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              {DAY_ORDER.map(day => (
                <label key={day} style={{ fontSize: '0.8rem', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                  {DAY_LABELS[day]}
                  <input
                    type="checkbox"
                    checked={form.validWeekdays.has(day)}
                    onChange={() => toggleWeekday(day)}
                  />
                </label>
              ))}
            </div>

            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'flex-start' }}>
              <label style={{ fontSize: '0.8rem' }}>
                Effective from{' '}
                <input type="date" value={form.effectiveFrom} onChange={e => setForm({ ...form, effectiveFrom: e.target.value })} />
              </label>
              <div>
                <label style={{ fontSize: '0.8rem' }}>
                  Effective to{' '}
                  <input type="date" value={form.effectiveTo} onChange={e => setForm({ ...form, effectiveTo: e.target.value })} />
                </label>
                <div style={{ fontSize: '0.75rem', color: '#9ca3af' }}>Leave blank for open-ended</div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button className="primary" onClick={handleCreate} disabled={submitting}>
                {submitting ? 'Saving...' : 'Save'}
              </button>
              <button onClick={cancelAdd} disabled={submitting}>Cancel</button>
            </div>
          </div>
        )}
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
            </tr>
          </thead>
          <tbody>
            {templates.map(t => (
              <tr key={t.id}>
                <td>{t.name}</td>
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
                <td>{t.effectiveFrom} – {t.effectiveTo || 'Present'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
