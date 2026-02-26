import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { desks, type Desk } from '../api/client'

export default function DeskSelector() {
  const [deskList, setDeskList] = useState<Desk[]>([])
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    desks.list()
      .then(setDeskList)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <div className="main-content"><p>Loading desks...</p></div>

  return (
    <div className="main-content">
      <h1>Select a Desk</h1>
      <p style={{ marginBottom: '1rem' }}>
        <Link to="/desk-management">Manage Desks</Link>
      </p>
      {deskList.length === 0 ? (
        <p>No desks configured. <Link to="/desk-management">Create one</Link> to get started.</p>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1rem' }}>
          {deskList.map(desk => (
            <div
              key={desk.id}
              onClick={() => navigate(`/desks/${desk.id}`)}
              style={{
                background: '#fff', padding: '1.5rem', borderRadius: '8px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.1)', cursor: 'pointer',
                border: '2px solid transparent', transition: 'border-color 0.2s',
              }}
              onMouseOver={e => (e.currentTarget.style.borderColor = '#3b82f6')}
              onMouseOut={e => (e.currentTarget.style.borderColor = 'transparent')}
            >
              <h3>{desk.name}</h3>
              {desk.description && <p style={{ color: '#6b7280', marginTop: '0.5rem' }}>{desk.description}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
