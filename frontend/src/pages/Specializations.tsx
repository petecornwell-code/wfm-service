import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { specializations as specApi, type Specialization } from '../api/client'

export default function Specializations() {
  const { deskId } = useParams<{ deskId: string }>()
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [newName, setNewName] = useState('')

  useEffect(() => {
    if (deskId) specApi.list(deskId).then(setSpecs).catch(console.error)
  }, [deskId])

  const handleCreate = async () => {
    if (!deskId || !newName.trim()) return
    try {
      const created = await specApi.create(deskId, newName)
      setSpecs([...specs, created])
      setNewName('')
    } catch (err) {
      console.error(err)
    }
  }

  const handleDelete = async (id: string) => {
    if (!deskId) return
    try {
      await specApi.delete(deskId, id)
      setSpecs(specs.filter(s => s.id !== id))
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <>
      <h1>Specializations</h1>
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem' }}>
        <input placeholder="Specialization name" value={newName} onChange={e => setNewName(e.target.value)} />
        <button className="primary" onClick={handleCreate}>Add</button>
      </div>
      <table>
        <thead><tr><th>Name</th><th>Actions</th></tr></thead>
        <tbody>
          {specs.map(s => (
            <tr key={s.id}>
              <td>{s.name}</td>
              <td><button className="danger" onClick={() => handleDelete(s.id)}>Delete</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
