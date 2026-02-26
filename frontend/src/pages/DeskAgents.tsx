import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { deskAgents, type DeskAgent } from '../api/client'

export default function DeskAgents() {
  const { deskId } = useParams<{ deskId: string }>()
  const [agents, setAgents] = useState<DeskAgent[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!deskId) return
    deskAgents.list(deskId)
      .then(res => setAgents(res.data))
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [deskId])

  const handleRefresh = async () => {
    if (!deskId) return
    setLoading(true)
    try {
      const refreshed = await deskAgents.refresh(deskId)
      setAgents(refreshed)
    } catch (err) {
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  if (loading) return <p>Loading agents...</p>

  return (
    <>
      <h1>Desk Agents</h1>
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem' }}>
        <button className="primary" onClick={handleRefresh}>Refresh from BambooHR</button>
        <button className="primary">Assign Agents</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th><th>Email</th><th>Department</th><th>Primary Spec</th>
            <th>Secondary Specs</th><th>Hours/Day</th><th>Active</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {agents.map(da => (
            <tr key={da.id}>
              <td>{da.agent.name}</td>
              <td>{da.agent.email}</td>
              <td>{da.agent.department}</td>
              <td>{da.primarySpecialization?.name || '—'}</td>
              <td>{da.secondarySpecializations.map(s => s.name).join(', ') || '—'}</td>
              <td>{da.effectiveContractedHoursPerDay}</td>
              <td>{da.agent.active ? 'Yes' : 'No'}</td>
              <td>
                <Link to={`/desks/${deskId}/agents/${da.agent.id}/preferences`}>Prefs</Link>
                {' | '}
                <Link to={`/desks/${deskId}/agents/${da.agent.id}/exceptions`}>Exceptions</Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
