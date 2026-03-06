import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { preferences, deskAgents, type AgentPreference, type DeskAgent, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

const DAYS_OF_WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

export default function AgentPreferences() {
  const { deskId, agentId } = useParams<{ deskId: string; agentId: string }>()
  const [prefs, setPrefs] = useState<AgentPreference[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [agentName, setAgentName] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  // New preference form
  const [newIsStanding, setNewIsStanding] = useState(true)
  const [newDayOfWeek, setNewDayOfWeek] = useState('MONDAY')
  const [newDate, setNewDate] = useState('')
  const [newStartTime, setNewStartTime] = useState('')
  const [newBreakTime, setNewBreakTime] = useState('')

  useEffect(() => {
    if (deskId && agentId) {
      deskAgents.list(deskId).then(res => {
        const da = res.data.find((d: DeskAgent) => d.agent.id === agentId)
        if (da) setAgentName(da.agent.name)
      }).catch(() => {})
    }
  }, [deskId, agentId])

  const loadPrefs = async () => {
    if (!deskId || !agentId) return
    setLoading(true)
    try {
      const data = await preferences.list(deskId, agentId, from || undefined, to || undefined)
      setPrefs(data)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadPrefs() }, [deskId, agentId, from, to])

  const handleAdd = () => {
    const pref: AgentPreference = {
      dayOfWeek: newIsStanding ? newDayOfWeek : '',
      isStanding: newIsStanding,
      date: newIsStanding ? undefined : newDate,
      preferredStartTime: newStartTime || undefined,
      preferredBreakTime: newBreakTime || undefined,
    }
    setPrefs([...prefs, pref])
    setNewStartTime('')
    setNewBreakTime('')
  }

  const handleSave = async () => {
    if (!deskId || !agentId) return
    setSaving(true)
    try {
      const saved = await preferences.save(deskId, agentId, prefs)
      setPrefs(saved)
      showToast('success', 'Preferences saved')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (index: number) => {
    const pref = prefs[index]
    if (pref.id && deskId && agentId) {
      try {
        await preferences.delete(deskId, agentId, pref.id)
        showToast('success', 'Preference deleted')
      } catch (err) {
        showToast('error', getErrorMessage(err))
        return
      }
    }
    setPrefs(prefs.filter((_, i) => i !== index))
  }

  return (
    <>
      <h1>Agent Preferences</h1>
      <p style={{ color: '#6b7280', marginBottom: '0.5rem' }}>
        Agent: {agentName || agentId}
        {deskId && <> | <Link to={`/desks/${deskId}/agents`}>Back to Agents</Link></>}
      </p>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <label style={{ fontSize: '0.85rem' }}>From: <input type="date" value={from} onChange={e => setFrom(e.target.value)} /></label>
        <label style={{ fontSize: '0.85rem' }}>To: <input type="date" value={to} onChange={e => setTo(e.target.value)} /></label>
      </div>

      {/* Add preference form */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1rem' }}>
        <h3 style={{ marginBottom: '0.5rem' }}>Add Preference</h3>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <label style={{ fontSize: '0.85rem' }}>
            <input type="checkbox" checked={newIsStanding} onChange={e => setNewIsStanding(e.target.checked)} />
            Standing
          </label>
          {newIsStanding ? (
            <select value={newDayOfWeek} onChange={e => setNewDayOfWeek(e.target.value)}>
              {DAYS_OF_WEEK.map(d => <option key={d} value={d}>{d}</option>)}
            </select>
          ) : (
            <input type="date" value={newDate} onChange={e => setNewDate(e.target.value)} />
          )}
          <label style={{ fontSize: '0.85rem' }}>Start: <input type="time" value={newStartTime} onChange={e => setNewStartTime(e.target.value)} /></label>
          <label style={{ fontSize: '0.85rem' }}>Break: <input type="time" value={newBreakTime} onChange={e => setNewBreakTime(e.target.value)} /></label>
          <button className="primary" onClick={handleAdd}>Add</button>
        </div>
      </div>

      {loading ? <p>Loading...</p> : (
        <>
          <table>
            <thead>
              <tr>
                <th>Day</th><th>Date</th><th>Standing</th>
                <th>Preferred Start</th><th>Preferred Break</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {prefs.map((p, i) => (
                <tr key={p.id || i}>
                  <td>{p.dayOfWeek || '—'}</td>
                  <td>{p.date || '(standing)'}</td>
                  <td>{p.isStanding ? 'Yes' : 'No'}</td>
                  <td>{p.preferredStartTime || '—'}</td>
                  <td>{p.preferredBreakTime || '—'}</td>
                  <td><button className="danger" onClick={() => handleDelete(i)} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Delete</button></td>
                </tr>
              ))}
              {prefs.length === 0 && <tr><td colSpan={6} style={{ color: '#6b7280', textAlign: 'center' }}>No preferences</td></tr>}
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
