import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { deskAgents, agents as agentsApi, specializations as specApi, type DeskAgent, type Agent, type Specialization, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

export default function DeskAgents() {
  const { deskId } = useParams<{ deskId: string }>()
  const [agentList, setAgentList] = useState<DeskAgent[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [showActiveOnly, setShowActiveOnly] = useState(true)

  // Assign modal
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [unassigned, setUnassigned] = useState<Agent[]>([])
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [searchTerm, setSearchTerm] = useState('')
  const [assigning, setAssigning] = useState(false)

  // Specialization edit
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [editSpecAgentId, setEditSpecAgentId] = useState<string | null>(null)
  const [editPrimary, setEditPrimary] = useState('')
  const [editSecondary, setEditSecondary] = useState<string[]>([])

  // Contracted hours edit
  const [editHoursAgentId, setEditHoursAgentId] = useState<string | null>(null)
  const [editHours, setEditHours] = useState(8)

  // Days off modal
  const [showDaysOff, setShowDaysOff] = useState<{ agentId: string; agentName: string; daysOff: Array<{ id: string; date: string; type: string }> } | null>(null)

  const loadAgents = async () => {
    if (!deskId) return
    try {
      const res = await deskAgents.list(deskId)
      setAgentList(res.data)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAgents() }, [deskId])

  useEffect(() => {
    if (deskId) specApi.list(deskId).then(setSpecs).catch(() => {})
  }, [deskId])

  const handleRefresh = async () => {
    if (!deskId) return
    setRefreshing(true)
    try {
      await deskAgents.refresh(deskId)
      await loadAgents()
      showToast('success', 'Agents refreshed from BambooHR')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setRefreshing(false)
    }
  }

  const openAssignModal = async () => {
    try {
      const res = await agentsApi.list({ unassigned: true, limit: 200 })
      setUnassigned(res.data)
      setSelectedIds(new Set())
      setSearchTerm('')
      setShowAssignModal(true)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const handleAssign = async () => {
    if (!deskId || selectedIds.size === 0) return
    setAssigning(true)
    try {
      await deskAgents.assign(deskId, Array.from(selectedIds))
      setShowAssignModal(false)
      await loadAgents()
      showToast('success', `${selectedIds.size} agent(s) assigned`)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setAssigning(false)
    }
  }

  const handleRemove = async (agentId: string) => {
    if (!deskId || !confirm('Remove this agent from the desk?')) return
    try {
      await deskAgents.remove(deskId, agentId)
      setAgentList(agentList.filter(da => da.agent.id !== agentId))
      showToast('success', 'Agent removed')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const startEditSpec = (da: DeskAgent) => {
    setEditSpecAgentId(da.agent.id)
    setEditPrimary(da.primarySpecialization?.id || '')
    setEditSecondary(da.secondarySpecializations.map(s => s.id))
  }

  const saveSpec = async () => {
    if (!deskId || !editSpecAgentId) return
    try {
      const updated = await deskAgents.setSpecializations(deskId, editSpecAgentId, {
        primarySpecializationId: editPrimary,
        secondarySpecializationIds: editSecondary,
      })
      setAgentList(agentList.map(da => da.agent.id === editSpecAgentId ? updated : da))
      setEditSpecAgentId(null)
      showToast('success', 'Specializations updated')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const startEditHours = (da: DeskAgent) => {
    setEditHoursAgentId(da.agent.id)
    setEditHours(da.effectiveContractedHoursPerDay)
  }

  const saveHours = async () => {
    if (!deskId || !editHoursAgentId) return
    try {
      const updated = await deskAgents.setContractedHours(deskId, editHoursAgentId, editHours)
      setAgentList(agentList.map(da => da.agent.id === editHoursAgentId ? updated : da))
      setEditHoursAgentId(null)
      showToast('success', 'Contracted hours updated')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const openDaysOff = async (agentId: string, agentName: string) => {
    try {
      const daysOff = await agentsApi.daysOff(agentId)
      setShowDaysOff({ agentId, agentName, daysOff })
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const filteredAgents = showActiveOnly ? agentList.filter(da => da.agent.active) : agentList
  const filteredUnassigned = searchTerm
    ? unassigned.filter(a => a.name.toLowerCase().includes(searchTerm.toLowerCase()))
    : unassigned

  if (loading) return <p>Loading agents...</p>

  return (
    <>
      <h1>Desk Agents</h1>
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <button className="primary" onClick={handleRefresh} disabled={refreshing}>
          {refreshing ? 'Refreshing...' : 'Refresh from BambooHR'}
        </button>
        <button className="primary" onClick={openAssignModal}>Assign Agents</button>
        <label style={{ marginLeft: 'auto', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
          <input type="checkbox" checked={showActiveOnly} onChange={e => setShowActiveOnly(e.target.checked)} />
          Active only
        </label>
      </div>

      <table>
        <thead>
          <tr>
            <th>Name</th><th>Email</th><th>Department</th><th>Job Title</th>
            <th>Primary Spec</th><th>Secondary Specs</th><th>Hours/Day</th>
            <th>Active</th><th>Last Refreshed</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {filteredAgents.map(da => (
            <tr key={da.id}>
              <td>{da.agent.name}</td>
              <td>{da.agent.email}</td>
              <td>{da.agent.department}</td>
              <td>{da.agent.jobTitle}</td>
              <td>
                {editSpecAgentId === da.agent.id ? (
                  <select value={editPrimary} onChange={e => setEditPrimary(e.target.value)}>
                    <option value="">—</option>
                    {specs.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                ) : (
                  <span onClick={() => startEditSpec(da)} style={{ cursor: 'pointer', textDecoration: 'underline dotted' }}>
                    {da.primarySpecialization?.name || '—'}
                  </span>
                )}
              </td>
              <td>
                {editSpecAgentId === da.agent.id ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                    {specs.map(s => (
                      <label key={s.id} style={{ fontSize: '0.8rem' }}>
                        <input type="checkbox" checked={editSecondary.includes(s.id)}
                          onChange={e => setEditSecondary(e.target.checked
                            ? [...editSecondary, s.id]
                            : editSecondary.filter(id => id !== s.id)
                          )} />
                        {s.name}
                      </label>
                    ))}
                    <div style={{ display: 'flex', gap: '0.25rem', marginTop: '2px' }}>
                      <button className="primary" onClick={saveSpec} style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}>Save</button>
                      <button onClick={() => setEditSpecAgentId(null)} style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}>Cancel</button>
                    </div>
                  </div>
                ) : (
                  <span onClick={() => startEditSpec(da)} style={{ cursor: 'pointer', textDecoration: 'underline dotted' }}>
                    {da.secondarySpecializations.map(s => s.name).join(', ') || '—'}
                  </span>
                )}
              </td>
              <td>
                {editHoursAgentId === da.agent.id ? (
                  <div style={{ display: 'flex', gap: '0.25rem' }}>
                    <input type="number" value={editHours} onChange={e => setEditHours(Number(e.target.value))}
                      step="0.25" style={{ width: '60px' }} />
                    <button className="primary" onClick={saveHours} style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}>OK</button>
                    <button onClick={() => setEditHoursAgentId(null)} style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}>X</button>
                  </div>
                ) : (
                  <span onClick={() => startEditHours(da)} style={{ cursor: 'pointer', textDecoration: 'underline dotted' }}>
                    {da.effectiveContractedHoursPerDay}
                  </span>
                )}
              </td>
              <td>{da.agent.active ? 'Yes' : 'No'}</td>
              <td style={{ fontSize: '0.8rem' }}>{da.agent.lastRefreshedAt ? new Date(da.agent.lastRefreshedAt).toLocaleDateString() : '—'}</td>
              <td>
                <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                  <Link to={`/desks/${deskId}/agents/${da.agent.id}/preferences`} style={{ fontSize: '0.8rem' }}>Prefs</Link>
                  <Link to={`/desks/${deskId}/agents/${da.agent.id}/exceptions`} style={{ fontSize: '0.8rem' }}>Exc</Link>
                  <button onClick={() => openDaysOff(da.agent.id, da.agent.name)} style={{ fontSize: '0.75rem', padding: '0.15rem 0.4rem' }}>Days Off</button>
                  <button className="danger" onClick={() => handleRemove(da.agent.id)} style={{ fontSize: '0.75rem', padding: '0.15rem 0.4rem' }}>Remove</button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Assign Modal */}
      {showAssignModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '500px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
            <h3>Assign Agents</h3>
            <input placeholder="Search agents..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)}
              style={{ margin: '0.5rem 0', padding: '0.4rem' }} />
            <div style={{ flex: 1, overflowY: 'auto', border: '1px solid #e5e7eb', borderRadius: '4px', maxHeight: '400px' }}>
              {filteredUnassigned.length === 0 ? (
                <p style={{ padding: '1rem', color: '#6b7280' }}>No unassigned agents found.</p>
              ) : filteredUnassigned.map(a => (
                <label key={a.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.4rem 0.5rem', borderBottom: '1px solid #f3f4f6', cursor: 'pointer' }}>
                  <input type="checkbox" checked={selectedIds.has(a.id)}
                    onChange={e => {
                      const next = new Set(selectedIds)
                      e.target.checked ? next.add(a.id) : next.delete(a.id)
                      setSelectedIds(next)
                    }} />
                  <span>{a.name}</span>
                  <span style={{ color: '#9ca3af', fontSize: '0.8rem' }}>{a.email}</span>
                </label>
              ))}
            </div>
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', justifyContent: 'flex-end' }}>
              <button onClick={() => setShowAssignModal(false)}>Cancel</button>
              <button className="primary" onClick={handleAssign} disabled={assigning || selectedIds.size === 0}>
                {assigning ? 'Assigning...' : `Assign (${selectedIds.size})`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Days Off Modal */}
      {showDaysOff && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '400px', maxHeight: '60vh', display: 'flex', flexDirection: 'column' }}>
            <h3>Days Off — {showDaysOff.agentName}</h3>
            <div style={{ flex: 1, overflowY: 'auto', marginTop: '0.5rem' }}>
              {showDaysOff.daysOff.length === 0 ? (
                <p style={{ color: '#6b7280' }}>No days off recorded.</p>
              ) : (
                <table style={{ width: '100%', fontSize: '0.85rem' }}>
                  <thead><tr><th>Date</th><th>Type</th></tr></thead>
                  <tbody>
                    {showDaysOff.daysOff.map(d => (
                      <tr key={d.id}><td>{d.date}</td><td>{d.type}</td></tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            <button onClick={() => setShowDaysOff(null)} style={{ marginTop: '0.75rem', alignSelf: 'flex-end' }}>Close</button>
          </div>
        </div>
      )}
    </>
  )
}
