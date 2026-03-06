import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { desks, type Desk, type CreateDeskRequest, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

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
  }

  const handleUpdate = async () => {
    if (!editingId || !editName.trim()) return
    try {
      const updated = await desks.update(editingId, {
        name: editName,
        description: editDescription || undefined,
        defaultContractedHoursPerDay: editHours,
      })
      setDeskList(deskList.map(d => d.id === editingId ? updated : d))
      setEditingId(null)
      showToast('success', 'Desk updated')
    } catch (err) {
      showToast('error', getErrorMessage(err))
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
          <tr><th>Name</th><th>Description</th><th>Default Hours/Day</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {deskList.map(desk => (
            <tr key={desk.id}>
              {editingId === desk.id ? (
                <>
                  <td><input value={editName} onChange={e => setEditName(e.target.value)} style={{ width: '100%' }} /></td>
                  <td><input value={editDescription} onChange={e => setEditDescription(e.target.value)} style={{ width: '100%' }} /></td>
                  <td><input type="number" value={editHours} onChange={e => setEditHours(Number(e.target.value))} step="0.25" style={{ width: '80px' }} /></td>
                  <td style={{ display: 'flex', gap: '0.25rem' }}>
                    <button className="primary" onClick={handleUpdate}>Save</button>
                    <button onClick={() => setEditingId(null)}>Cancel</button>
                  </td>
                </>
              ) : (
                <>
                  <td>{desk.name}</td>
                  <td>{desk.description || '—'}</td>
                  <td>{desk.defaultContractedHoursPerDay}</td>
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
