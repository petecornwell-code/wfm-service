import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { specializations as specApi, type Specialization, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

export default function Specializations() {
  const { deskId } = useParams<{ deskId: string }>()
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [newName, setNewName] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')

  useEffect(() => {
    if (deskId) specApi.list(deskId).then(setSpecs).catch(err => showToast('error', getErrorMessage(err)))
  }, [deskId])

  const handleCreate = async () => {
    if (!deskId || !newName.trim()) return
    try {
      const created = await specApi.create(deskId, newName)
      setSpecs([...specs, created])
      setNewName('')
      showToast('success', 'Specialization created')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const handleDelete = async (id: string) => {
    if (!deskId) return
    if (!confirm('Delete this specialization? This will fail if it is in use by agents or staffing requirements.')) return
    try {
      await specApi.delete(deskId, id)
      setSpecs(specs.filter(s => s.id !== id))
      showToast('success', 'Specialization deleted')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const startRename = (s: Specialization) => {
    setEditingId(s.id)
    setEditName(s.name)
  }

  const handleRename = async () => {
    if (!deskId || !editingId || !editName.trim()) return
    try {
      const updated = await specApi.update(deskId, editingId, editName)
      setSpecs(specs.map(s => s.id === editingId ? updated : s))
      setEditingId(null)
      showToast('success', 'Specialization renamed')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  return (
    <>
      <h1>Specializations</h1>
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem' }}>
        <input placeholder="Specialization name" value={newName} onChange={e => setNewName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleCreate()} />
        <button className="primary" onClick={handleCreate}>Add</button>
      </div>
      <table>
        <thead><tr><th>Name</th><th>Actions</th></tr></thead>
        <tbody>
          {specs.map(s => (
            <tr key={s.id}>
              <td>
                {editingId === s.id ? (
                  <div style={{ display: 'flex', gap: '0.25rem' }}>
                    <input value={editName} onChange={e => setEditName(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handleRename()} style={{ width: '200px' }} />
                    <button className="primary" onClick={handleRename} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Save</button>
                    <button onClick={() => setEditingId(null)} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Cancel</button>
                  </div>
                ) : s.name}
              </td>
              <td style={{ display: 'flex', gap: '0.25rem' }}>
                <button onClick={() => startRename(s)}>Rename</button>
                <button className="danger" onClick={() => handleDelete(s.id)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
