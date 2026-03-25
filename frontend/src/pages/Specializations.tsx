import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { specializations as specApi, type Specialization, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

const DEFAULT_COLORS = [
  '#a7f3d0', '#bfdbfe', '#fde68a', '#fbcfe8', '#c7d2fe',
  '#fed7aa', '#6ee7b7', '#d8b4fe', '#99f6e4', '#fca5a5',
  '#fcd34d', '#a5f3fc',
]

export default function Specializations() {
  const { deskId } = useParams<{ deskId: string }>()
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [newName, setNewName] = useState('')
  const [newColor, setNewColor] = useState(DEFAULT_COLORS[0])
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editColor, setEditColor] = useState('')

  useEffect(() => {
    if (deskId) specApi.list(deskId).then(setSpecs).catch(err => showToast('error', getErrorMessage(err)))
  }, [deskId])

  const nextDefaultColor = (currentSpecs: Specialization[]) => {
    const usedColors = new Set(currentSpecs.map(s => s.color))
    return DEFAULT_COLORS.find(c => !usedColors.has(c)) || DEFAULT_COLORS[currentSpecs.length % DEFAULT_COLORS.length]
  }

  useEffect(() => {
    setNewColor(nextDefaultColor(specs))
  }, [specs.length])

  const handleCreate = async () => {
    if (!deskId || !newName.trim()) return
    try {
      const created = await specApi.create(deskId, newName, newColor)
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

  const startEdit = (s: Specialization) => {
    setEditingId(s.id)
    setEditName(s.name)
    setEditColor(s.color || DEFAULT_COLORS[0])
  }

  const handleUpdate = async () => {
    if (!deskId || !editingId || !editName.trim()) return
    try {
      const updated = await specApi.update(deskId, editingId, editName, editColor)
      setSpecs(specs.map(s => s.id === editingId ? updated : s))
      setEditingId(null)
      showToast('success', 'Specialization updated')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  return (
    <>
      <h1>Specializations</h1>
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <input
          type="color"
          value={newColor}
          onChange={e => setNewColor(e.target.value)}
          title="Pick a color"
          style={{ width: '36px', height: '36px', padding: '2px', cursor: 'pointer', border: '1px solid #d1d5db', borderRadius: '4px' }}
        />
        <input placeholder="Specialization name" value={newName} onChange={e => setNewName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleCreate()} />
        <button className="primary" onClick={handleCreate}>Add</button>
      </div>
      <table>
        <thead><tr><th>Color</th><th>Name</th><th>Actions</th></tr></thead>
        <tbody>
          {specs.map(s => (
            <tr key={s.id}>
              <td style={{ width: '60px' }}>
                {editingId === s.id ? (
                  <input
                    type="color"
                    value={editColor}
                    onChange={e => setEditColor(e.target.value)}
                    style={{ width: '36px', height: '28px', padding: '2px', cursor: 'pointer', border: '1px solid #d1d5db', borderRadius: '4px' }}
                  />
                ) : (
                  <span style={{
                    display: 'inline-block', width: '24px', height: '24px',
                    background: s.color || '#e5e7eb', border: '1px solid #d1d5db', borderRadius: '4px',
                  }} />
                )}
              </td>
              <td>
                {editingId === s.id ? (
                  <div style={{ display: 'flex', gap: '0.25rem' }}>
                    <input value={editName} onChange={e => setEditName(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handleUpdate()} style={{ width: '200px' }} />
                    <button className="primary" onClick={handleUpdate} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Save</button>
                    <button onClick={() => setEditingId(null)} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Cancel</button>
                  </div>
                ) : s.name}
              </td>
              <td style={{ display: 'flex', gap: '0.25rem' }}>
                <button onClick={() => startEdit(s)}>Edit</button>
                <button className="danger" onClick={() => handleDelete(s.id)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
