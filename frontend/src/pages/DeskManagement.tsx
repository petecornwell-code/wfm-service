import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { desks, type Desk } from '../api/client'

export default function DeskManagement() {
  const [deskList, setDeskList] = useState<Desk[]>([])
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')

  useEffect(() => {
    desks.list().then(setDeskList).catch(console.error)
  }, [])

  const handleCreate = async () => {
    if (!newName.trim()) return
    try {
      const created = await desks.create({ name: newName, description: newDescription || undefined })
      setDeskList([...deskList, created])
      setNewName('')
      setNewDescription('')
    } catch (err) {
      console.error(err)
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm('Delete this desk and all its data?')) return
    try {
      await desks.delete(id)
      setDeskList(deskList.filter(d => d.id !== id))
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <div className="main-content">
      <h1>Desk Management</h1>
      <p style={{ marginBottom: '1rem' }}><Link to="/">Back to Desk Selector</Link></p>

      <div style={{ marginBottom: '2rem', background: '#fff', padding: '1rem', borderRadius: '8px' }}>
        <h3>Add Desk</h3>
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
          <input placeholder="Name" value={newName} onChange={e => setNewName(e.target.value)} />
          <input placeholder="Description (optional)" value={newDescription} onChange={e => setNewDescription(e.target.value)} />
          <button className="primary" onClick={handleCreate}>Create</button>
        </div>
      </div>

      <table>
        <thead>
          <tr><th>Name</th><th>Description</th><th>Default Hours/Day</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {deskList.map(desk => (
            <tr key={desk.id}>
              <td>{desk.name}</td>
              <td>{desk.description || '—'}</td>
              <td>{desk.defaultContractedHoursPerDay}</td>
              <td><button className="danger" onClick={() => handleDelete(desk.id)}>Delete</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
