import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { preferences, type AgentPreference } from '../api/client'

export default function AgentPreferences() {
  const { deskId, agentId } = useParams<{ deskId: string; agentId: string }>()
  const [prefs, setPrefs] = useState<AgentPreference[]>([])

  useEffect(() => {
    if (deskId && agentId) {
      preferences.list(deskId, agentId).then(setPrefs).catch(console.error)
    }
  }, [deskId, agentId])

  return (
    <>
      <h1>Agent Preferences</h1>
      <p style={{ color: '#6b7280', marginBottom: '1rem' }}>Agent: {agentId}</p>

      {/* TODO: date range picker, preferences grid, save/delete */}
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
              <td>{p.dayOfWeek}</td>
              <td>{p.date || '(standing)'}</td>
              <td>{p.isStanding ? 'Yes' : 'No'}</td>
              <td>{p.preferredStartTime || '—'}</td>
              <td>{p.preferredBreakTime || '—'}</td>
              <td>{p.id && <button className="danger" onClick={() => {
                if (deskId && agentId && p.id) preferences.delete(deskId, agentId, p.id)
              }}>Delete</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
