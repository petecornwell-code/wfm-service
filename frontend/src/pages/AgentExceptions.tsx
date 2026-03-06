import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { exceptions, deskAgents, agents as agentsApi, type AgentException, type DeskAgent, type DayOff, getErrorMessage } from '../api/client'
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

  useEffect(() => {
    if (deskId && agentId) {
      deskAgents.list(deskId).then(res => {
        const da = res.data.find((d: DeskAgent) => d.agent.id === agentId)
        if (da) {
          setAgentName(da.agent.name)
          setStandardHours(da.effectiveContractedHoursPerDay)
        }
      }).catch(() => {})
      agentsApi.daysOff(agentId).then(setDaysOff).catch(() => {})
    }
  }, [deskId, agentId])

  const loadExceptions = async () => {
    if (!deskId || !agentId) return
    setLoading(true)
    try {
      const data = await exceptions.list(deskId, agentId, from || undefined, to || undefined)
      setExcs(data)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadExceptions() }, [deskId, agentId, from, to])

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
        </>
      )}
    </>
  )
}
