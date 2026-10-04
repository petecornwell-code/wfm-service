import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { exceptions, restWaivers, deskAgents, agents as agentsApi, type AgentException, type RestWaiver, type DeskAgent, type DayOff, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

export default function AgentExceptions() {
  const { deskId, agentId } = useParams<{ deskId: string; agentId: string }>()
  const [excs, setExcs] = useState<AgentException[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [agentName, setAgentName] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [daysOff, setDaysOff] = useState<DayOff[]>([])
  const [standardHours, setStandardHours] = useState(8)

  // New exception form
  const [newDate, setNewDate] = useState('')
  const [newHours, setNewHours] = useState(0)
  const [newReason, setNewReason] = useState('')

  // Rest Waivers (REST-06). Deliberately NOT the local-optimistic-then-batch-Save shape above:
  // a rest waiver is rare, single-row and mandatory-reason -- the opposite shape to editing
  // several exception dates for one agent at once -- so Add and Delete each call the server
  // immediately, with no batch step and no local-only pending row. This difference is load-bearing
  // and must not be "corrected" into consistency with the Exceptions table above it.
  const [waivers, setWaivers] = useState<RestWaiver[]>([])
  const [newWaiverDate, setNewWaiverDate] = useState('')
  const [newWaiverReason, setNewWaiverReason] = useState('')
  const [addingWaiver, setAddingWaiver] = useState(false)

  useEffect(() => {
    if (deskId && agentId) {
      deskAgents.list(deskId).then(res => {
        const da = res.data.find((d: DeskAgent) => d.id === agentId)
        if (da) {
          setAgentName(da.name)
          setStandardHours(da.effectiveContractedHoursPerDay)
        }
      }).catch(() => {})
      agentsApi.daysOff(agentId).then(setDaysOff).catch(() => {})
    }
  }, [deskId, agentId])

  // Loads the exceptions AND the rest waivers together, under the one shared `loading` boolean --
  // the Rest Waivers section below inherits this same loading surface rather than introducing a
  // second, independent fetch-loading state (UI E3 loading).
  const loadExceptions = async () => {
    if (!deskId || !agentId) return
    setLoading(true)
    try {
      const [excData, waiverData] = await Promise.all([
        exceptions.list(deskId, agentId, from || undefined, to || undefined),
        restWaivers.list(deskId, agentId, from || undefined, to || undefined),
      ])
      setExcs(excData)
      setWaivers(waiverData)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadExceptions() }, [deskId, agentId, from, to])

  // Quiet refresh used after an immediate Add/Delete -- refetches only the waiver list, with no
  // page-wide loading flicker. This is intentionally separate from loadExceptions above: the
  // waiver section's own server round trips must not re-trigger the shared Loading... text or
  // re-fetch the unrelated exceptions table.
  const loadWaivers = async () => {
    if (!deskId || !agentId) return
    try {
      const data = await restWaivers.list(deskId, agentId, from || undefined, to || undefined)
      setWaivers(data)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const daysOffSet = new Set(daysOff.map(d => d.date))

  const handleAdd = () => {
    if (!newDate || !newReason.trim()) {
      showToast('error', 'Date and reason are required')
      return
    }
    setExcs([...excs, { date: newDate, contractedHoursOverride: newHours, reason: newReason }])
    setNewDate('')
    setNewHours(0)
    setNewReason('')
  }

  const handleSave = async () => {
    if (!deskId || !agentId) return
    setSaving(true)
    try {
      const saved = await exceptions.save(deskId, agentId, excs)
      setExcs(saved)
      showToast('success', 'Exceptions saved')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (index: number) => {
    const exc = excs[index]
    if (exc.id && deskId && agentId) {
      try {
        await exceptions.delete(deskId, agentId, exc.date)
        showToast('success', 'Exception deleted')
      } catch (err) {
        showToast('error', getErrorMessage(err))
        return
      }
    }
    setExcs(excs.filter((_, i) => i !== index))
  }

  // Immediate Add: reaches the server on click, with no local-only pending row. The button is
  // disabled from click until the request settles and re-enabled in `finally` on both success and
  // failure, so two fast clicks cannot create two waivers for the same date (T-22-24).
  const handleAddWaiver = async () => {
    if (!deskId || !agentId) return
    setAddingWaiver(true)
    try {
      await restWaivers.save(deskId, agentId, [{ date: newWaiverDate, reason: newWaiverReason }])
      await loadWaivers()
      setNewWaiverDate('')
      setNewWaiverReason('')
      showToast('success', 'Waiver added')
    } catch (err) {
      // No client-side validation message is added here: the reason requirement is signalled by
      // the input's placeholder, and the authoritative refusal (e.g. a blank reason) is the
      // server's, surfaced verbatim through this same error-toast path.
      showToast('error', getErrorMessage(err))
    } finally {
      setAddingWaiver(false)
    }
  }

  // Immediate Delete: no confirmation dialog, matching this page's existing exception delete.
  // A confirmation prompt on a reversible action trains operators to click through the ones that
  // matter (Phase 14 D-12), and a waiver can simply be re-added -- it is not data loss.
  const handleDeleteWaiver = async (date: string) => {
    if (!deskId || !agentId) return
    try {
      await restWaivers.delete(deskId, agentId, date)
      await loadWaivers()
      showToast('success', 'Waiver removed')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  return (
    <>
      <h1>Agent Exceptions</h1>
      <p style={{ color: '#6b7280', marginBottom: '0.5rem' }}>
        Agent: {agentName || agentId} | Standard hours: {standardHours}
        {deskId && <> | <Link to={`/desks/${deskId}/agents`}>Back to Agents</Link></>}
      </p>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <label style={{ fontSize: '0.85rem' }}>From: <input type="date" value={from} onChange={e => setFrom(e.target.value)} /></label>
        <label style={{ fontSize: '0.85rem' }}>To: <input type="date" value={to} onChange={e => setTo(e.target.value)} /></label>
      </div>

      {/* Add exception form */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1rem' }}>
        <h3 style={{ marginBottom: '0.5rem' }}>Add Exception</h3>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <input type="date" value={newDate} onChange={e => setNewDate(e.target.value)} />
          <label style={{ fontSize: '0.85rem' }}>Override hours:
            <input type="number" value={newHours} onChange={e => setNewHours(Number(e.target.value))} step="0.25" style={{ width: '80px', marginLeft: '0.25rem' }} />
          </label>
          <input placeholder="Reason (required)" value={newReason} onChange={e => setNewReason(e.target.value)} style={{ width: '200px' }} />
          <button className="primary" onClick={handleAdd}>Add</button>
        </div>
      </div>

      {loading ? <p>Loading...</p> : (
        <>
          <table>
            <thead>
              <tr><th>Date</th><th>Standard Hours</th><th>Override Hours</th><th>Reason</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {excs.map((ex, i) => {
                const isDayOff = daysOffSet.has(ex.date)
                return (
                  <tr key={ex.id || i} style={isDayOff ? { background: '#f3f4f6', color: '#9ca3af' } : {}}>
                    <td>{ex.date}{isDayOff && ' (day off)'}</td>
                    <td>{standardHours}</td>
                    <td>{ex.contractedHoursOverride}</td>
                    <td>{ex.reason}</td>
                    <td><button className="danger" onClick={() => handleDelete(i)} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Delete</button></td>
                  </tr>
                )
              })}
              {excs.length === 0 && <tr><td colSpan={5} style={{ color: '#6b7280', textAlign: 'center' }}>No exceptions</td></tr>}
            </tbody>
          </table>
          <button className="primary" onClick={handleSave} disabled={saving} style={{ marginTop: '1rem' }}>
            {saving ? 'Saving...' : 'Save All'}
          </button>

          {/* Rest Waivers (REST-06) -- see the state block above for why Add/Delete are immediate
              rather than batched. */}
          <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginTop: '1rem' }}>
            <h3 style={{ marginBottom: '0.5rem' }}>Rest Waivers</h3>
            <p style={{ color: '#6b7280', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
              Waives the desk's minimum rest requirement for this agent on one date — the agent may start early on the date you pick.
            </p>
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
              <input type="date" value={newWaiverDate} onChange={e => setNewWaiverDate(e.target.value)} />
              <input placeholder="Reason (required)" value={newWaiverReason} onChange={e => setNewWaiverReason(e.target.value)} style={{ width: '200px' }} />
              <button className="primary" onClick={handleAddWaiver} disabled={addingWaiver}>
                {addingWaiver ? 'Adding...' : 'Add Waiver'}
              </button>
            </div>

            <table style={{ marginTop: '1rem' }}>
              <thead>
                <tr><th>Date</th><th>Reason</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {waivers.map((w, i) => {
                  // Advisory only (D-09) -- reusing the exceptions table's exact day-off dimming.
                  // This must never gate the Add action: a waiver that waives nothing is reported
                  // as unused rather than refused.
                  const isDayOff = daysOffSet.has(w.date)
                  return (
                    <tr key={w.id || i} style={isDayOff ? { background: '#f3f4f6', color: '#9ca3af' } : {}}>
                      <td>{w.date}{isDayOff && ' (day off)'}</td>
                      <td>{w.reason}</td>
                      <td><button className="danger" onClick={() => handleDeleteWaiver(w.date)} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Delete</button></td>
                    </tr>
                  )
                })}
                {waivers.length === 0 && <tr><td colSpan={3} style={{ color: '#6b7280', textAlign: 'center' }}>No rest waivers</td></tr>}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  )
}
