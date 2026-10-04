import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { desks, type Desk, type CreateDeskRequest, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

// Minutes -> display string. Exact division, no rounding mode: a decimal place is shown only
// when the quotient is not a whole number, so 660 reads `11` and 630 reads `10.5`. No
// unconditional `toFixed` — that would turn `11` into `11.0`, a format the UI-SPEC's populated
// row does not specify.
const minutesToHoursDisplay = (minutes: number): string => {
  const hours = minutes / 60
  if (Number.isInteger(hours)) return String(hours)
  // Exact for any 15-minute-aligned value (the input's 0.25-hour step produces only these);
  // trailing zeros trimmed so 10.50 reads 10.5 while 10.25 and 10.75 keep both digits.
  return hours.toFixed(2).replace(/0+$/, '')
}

// Edited hours string -> minutes, or null to clear. Exact multiplication, no rounding mode: the
// input's 0.25 step makes every legal entry a whole number of minutes, so there is no rounding
// decision to make here.
const hoursStringToMinutes = (value: string): number | null => {
  const trimmed = value.trim()
  if (trimmed === '') return null
  return Number(trimmed) * 60
}

export default function DeskManagement() {
  const [deskList, setDeskList] = useState<Desk[]>([])
  const [loading, setLoading] = useState(true)
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [newHours, setNewHours] = useState(8)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editHours, setEditHours] = useState(8)
  const [editDayStart, setEditDayStart] = useState('')
  const [savingDayStart, setSavingDayStart] = useState(false)
  // Held as a string so an empty input is representable and distinguishable from 0 (REST-04).
  const [editMinimumRestHours, setEditMinimumRestHours] = useState('')

  useEffect(() => {
    desks.list()
      .then(setDeskList)
      .catch(err => showToast('error', getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  const handleCreate = async () => {
    if (!newName.trim()) return
    try {
      const data: CreateDeskRequest = { name: newName, description: newDescription || undefined, defaultContractedHoursPerDay: newHours }
      const created = await desks.create(data)
      setDeskList([...deskList, created])
      setNewName('')
      setNewDescription('')
      setNewHours(8)
      showToast('success', 'Desk created')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm('Delete this desk and all its data?')) return
    try {
      await desks.delete(id)
      setDeskList(deskList.filter(d => d.id !== id))
      showToast('success', 'Desk deleted')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const startEdit = (desk: Desk) => {
    setEditingId(desk.id)
    setEditName(desk.name)
    setEditDescription(desk.description || '')
    setEditHours(desk.defaultContractedHoursPerDay)
    setEditDayStart(desk.dayStart)
    setEditMinimumRestHours(desk.minimumRestMinutes == null ? '' : minutesToHoursDisplay(desk.minimumRestMinutes))
  }

  // The locked disclosure (OVNT-01/D-04) treats the schedule id and the period as independent
  // halves: a complete period (start and end both present) renders as its own parenthetical, an
  // id renders on its own when present, and neither is ever shown as a dangling fragment (a lone
  // date or an empty bracket pair). Absent both, the bare sentence stands alone.
  const dayStartLockExplanation = (desk: Desk): string => {
    const base = 'Locked — accepted schedule blocks day start.'
    const id = desk.dayStartLockedByScheduleId
    const hasPeriod = Boolean(desk.dayStartLockedPeriodStart && desk.dayStartLockedPeriodEnd)
    const period = hasPeriod ? `(${desk.dayStartLockedPeriodStart}–${desk.dayStartLockedPeriodEnd})` : ''
    if (id && hasPeriod) return `${base} ${id} ${period}.`
    if (id) return `${base} ${id}.`
    if (hasPeriod) return `${base} ${period}.`
    return base
  }

  const handleUpdate = async () => {
    if (!editingId || !editName.trim()) return
    const original = deskList.find(d => d.id === editingId)
    setSavingDayStart(true)
    try {
      const updated = await desks.update(editingId, {
        name: editName,
        description: editDescription || undefined,
        defaultContractedHoursPerDay: editHours,
      })
      setDeskList(deskList.map(d => d.id === editingId ? updated : d))

      // The backend's equal-value early return makes a redundant call harmless, but only
      // submitting when the value actually changed keeps the no-change path a single request.
      let latest = updated
      if (original && editDayStart !== original.dayStart) {
        latest = await desks.setDayStart(editingId, editDayStart)
        setDeskList(prev => prev.map(d => d.id === editingId ? latest : d))
      }

      // Null-safe comparison so the null-to-null and unset-to-unset cases both short-circuit.
      // Fires after the day-start PUT, per the UI-SPEC's ordering: a failed main save never
      // leaves a rest value applied against stale desk fields.
      const editedMinimumRestMinutes = hoursStringToMinutes(editMinimumRestHours)
      const originalMinimumRestMinutes = original?.minimumRestMinutes ?? null
      if (original && editedMinimumRestMinutes !== originalMinimumRestMinutes) {
        latest = await desks.setMinimumRest(editingId, editedMinimumRestMinutes)
        setDeskList(prev => prev.map(d => d.id === editingId ? latest : d))
      }

      setEditingId(null)
      // One submission produces exactly one message: the tiling advisory (when present) carries
      // its own "the desk still saved" confirmation, so it replaces the success message rather
      // than following it.
      if (latest.dayStartTilingWarning) {
        showToast('warning', latest.dayStartTilingWarning)
      } else {
        showToast('success', 'Desk updated')
      }
    } catch (err) {
      // Stay in edit mode on failure (do not clear editingId here) so the operator's entered
      // value is not discarded, and show exactly the message the server produced.
      showToast('error', getErrorMessage(err))
    } finally {
      setSavingDayStart(false)
    }
  }

  if (loading) return <div className="main-content"><p>Loading...</p></div>

  return (
    <div className="main-content">
      <h1>Desk Management</h1>
      <p style={{ marginBottom: '1rem' }}><Link to="/">Back to Desk Selector</Link></p>

      <div style={{ marginBottom: '2rem', background: '#fff', padding: '1rem', borderRadius: '8px' }}>
        <h3>Add Desk</h3>
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
          <input placeholder="Name" value={newName} onChange={e => setNewName(e.target.value)} />
          <input placeholder="Description (optional)" value={newDescription} onChange={e => setNewDescription(e.target.value)} />
          <input type="number" placeholder="Hours/Day" value={newHours} onChange={e => setNewHours(Number(e.target.value))} step="0.25" style={{ width: '100px' }} />
          <button className="primary" onClick={handleCreate}>Create</button>
        </div>
      </div>

      <table>
        <thead>
          <tr><th>Name</th><th>Description</th><th>Default Hours/Day</th><th>Scheduling Mode</th><th>Day Start</th><th>Min Rest (hrs)</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {deskList.map(desk => (
            <tr key={desk.id}>
              {editingId === desk.id ? (
                <>
                  <td><input value={editName} onChange={e => setEditName(e.target.value)} style={{ width: '100%' }} /></td>
                  <td><input value={editDescription} onChange={e => setEditDescription(e.target.value)} style={{ width: '100%' }} /></td>
                  <td><input type="number" value={editHours} onChange={e => setEditHours(Number(e.target.value))} step="0.25" style={{ width: '80px' }} /></td>
                  {/* Read-only in both branches — the mode cannot be changed from this page. Switching
                      modes is validated against the shift library and desk state (a running solve,
                      uncovered demand), so an inline edit here would bypass that gate; a plain-text
                      cell keeps the row's column count equal across edit/display so the table does not
                      shift while a row is being edited. */}
                  <td>{desk.schedulingMode === 'SHIFT' ? 'Shift' : 'Slot'}</td>
                  {/* The time input steps in 15-minute increments (OVNT-01) because that is the
                      only boundary the backend's day-start save accepts — stepping the picker
                      keeps an operator from producing a value it will refuse. A desk permanently
                      locked by an ACCEPTED schedule (OVNT-01/D-04) renders disabled instead, with
                      the blocking schedule named beneath it, because the constraint cannot be
                      worked around from this page. */}
                  <td>
                    {desk.dayStartLockedByScheduleId ? (
                      <>
                        <input type="time" step="900" value={desk.dayStart} readOnly disabled style={{ width: '100%' }} />
                        <div style={{ color: '#6b7280', fontSize: '13px', fontWeight: 400, marginTop: '2px' }}>
                          {dayStartLockExplanation(desk)}
                        </div>
                      </>
                    ) : (
                      <input
                        type="time"
                        step="900"
                        value={editDayStart}
                        onChange={e => setEditDayStart(e.target.value)}
                        style={{ width: '100%' }}
                      />
                    )}
                  </td>
                  {/* No lock or disabled state here, unlike Day Start two columns over — D-14
                      explicitly declined mirroring the day-start accepted-schedule refusal for
                      rest, reasoning that rest is ordinary desk policy an operator may change
                      mid-quarter. This is deliberate, not an oversight. */}
                  <td>
                    <input
                      type="number"
                      step="0.25"
                      min="0"
                      value={editMinimumRestHours}
                      onChange={e => setEditMinimumRestHours(e.target.value)}
                      style={{ width: '90px' }}
                    />
                    {(() => {
                      const parsed = editMinimumRestHours.trim() === '' ? null : Number(editMinimumRestHours)
                      return parsed !== null && (parsed < 0 || parsed >= 24)
                    })() && (
                      <div style={{ color: '#92400e', fontSize: '13px', fontWeight: 400, marginTop: '2px' }}>
                        Minimum rest must be less than 24 hours.
                      </div>
                    )}
                  </td>
                  <td style={{ display: 'flex', gap: '0.25rem' }}>
                    <button className="primary" onClick={handleUpdate} disabled={savingDayStart}>Save</button>
                    <button onClick={() => setEditingId(null)}>Cancel</button>
                  </td>
                </>
              ) : (
                <>
                  <td>{desk.name}</td>
                  <td>{desk.description || '—'}</td>
                  <td>{desk.defaultContractedHoursPerDay}</td>
                  <td>{desk.schedulingMode === 'SHIFT' ? 'Shift' : 'Slot'}</td>
                  {/* Plain-text read mode; the editable 15-minute-stepped picker (OVNT-01) only
                      appears in edit mode below. When a schedule locks the day start (OVNT-01/D-04)
                      the same disclosure renders here too, so an operator learns the constraint
                      without entering edit mode. */}
                  <td>
                    {desk.dayStart}
                    {desk.dayStartLockedByScheduleId && (
                      <div style={{ color: '#6b7280', fontSize: '13px', fontWeight: 400, marginTop: '2px' }}>
                        {dayStartLockExplanation(desk)}
                      </div>
                    )}
                  </td>
                  {/* Plain table cell, exactly like the five before it — no new color, weight or
                      size, which is what keeps Actions the row's rightmost high-contrast element.
                      Strict null-or-undefined check: 0 is a legal, meaningfully different value
                      from unset and must render `0`, never the dash. */}
                  <td>{desk.minimumRestMinutes == null ? '—' : minutesToHoursDisplay(desk.minimumRestMinutes)}</td>
                  <td style={{ display: 'flex', gap: '0.25rem' }}>
                    <button onClick={() => startEdit(desk)}>Edit</button>
                    <button className="danger" onClick={() => handleDelete(desk.id)}>Delete</button>
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
