import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { exceptions, type AgentException } from '../api/client'

export default function AgentExceptions() {
  const { deskId, agentId } = useParams<{ deskId: string; agentId: string }>()
  const [excs, setExcs] = useState<AgentException[]>([])

  useEffect(() => {
    if (deskId && agentId) {
      exceptions.list(deskId, agentId).then(setExcs).catch(console.error)
    }
  }, [deskId, agentId])

  return (
    <>
      <h1>Agent Exceptions</h1>
      <p style={{ color: '#6b7280', marginBottom: '1rem' }}>Agent: {agentId}</p>

      {/* TODO: date range picker, exceptions grid, save/delete */}
      <table>
        <thead>
          <tr><th>Date</th><th>Override Hours</th><th>Reason</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {excs.map((ex, i) => (
            <tr key={ex.id || i}>
              <td>{ex.date}</td>
              <td>{ex.contractedHoursOverride}</td>
              <td>{ex.reason}</td>
              <td><button className="danger" onClick={() => {
                if (deskId && agentId) exceptions.delete(deskId, agentId, ex.date)
              }}>Delete</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
