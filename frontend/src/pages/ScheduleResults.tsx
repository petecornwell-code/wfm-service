import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { schedules, type ScheduleDetail } from '../api/client'

export default function ScheduleResults() {
  const { deskId, scheduleId } = useParams<{ deskId: string; scheduleId: string }>()
  const navigate = useNavigate()
  const [schedule, setSchedule] = useState<ScheduleDetail | null>(null)
  const [activeTab, setActiveTab] = useState<'staffing' | 'agents' | 'preferences' | 'violations'>('staffing')

  useEffect(() => {
    if (!deskId || !scheduleId) return
    const poll = () => {
      schedules.get(deskId, scheduleId).then(data => {
        setSchedule(data)
        if (data.status === 'RUNNING') {
          setTimeout(poll, 2000)
        }
      }).catch(console.error)
    }
    poll()
  }, [deskId, scheduleId])

  if (!schedule) return <p>Loading schedule...</p>

  const isRunning = schedule.status === 'RUNNING'
  const canAccept = schedule.status === 'COMPLETED' || schedule.status === 'STOPPED'
  const canReject = canAccept || schedule.status === 'FAILED'

  const handleStop = async () => {
    if (deskId && scheduleId) {
      await schedules.stop(deskId, scheduleId)
    }
  }

  const handleAccept = async () => {
    if (!deskId || !scheduleId) return
    if (schedule.feasible === false) {
      if (!confirm('This schedule has hard constraint violations and is not optimal. Accept anyway?')) return
    }
    await schedules.accept(deskId, scheduleId)
    setSchedule({ ...schedule, status: 'ACCEPTED' })
  }

  const handleReject = async () => {
    if (!deskId || !scheduleId) return
    if (!confirm('Are you sure you want to reject this schedule? It will be permanently deleted.')) return
    await schedules.reject(deskId, scheduleId)
    navigate(`/desks/${deskId}/schedule-setup`)
  }

  const handleExport = async () => {
    if (!deskId || !scheduleId) return
    const res = await schedules.export(deskId, scheduleId)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `schedule-${scheduleId}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <>
      <h1>Schedule Results</h1>

      {/* Header */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <span><strong>Status:</strong> {schedule.status}</span>
          <span><strong>Period:</strong> {schedule.periodStartDate} — {schedule.periodEndDate}</span>
          {schedule.score && (
            <span><strong>Score:</strong> Hard: {schedule.score.hardScore}, Soft: {schedule.score.softScore}</span>
          )}
          {schedule.feasible !== null && (
            <span style={{ color: schedule.feasible ? '#16a34a' : '#dc2626' }}>
              {schedule.feasible ? 'Feasible' : 'NOT FEASIBLE'}
            </span>
          )}
        </div>

        {schedule.feasible === false && schedule.violatedHardConstraints?.length > 0 && (
          <div style={{ background: '#fef2f2', border: '1px solid #fca5a5', padding: '0.75rem', borderRadius: '6px', marginTop: '0.75rem' }}>
            <strong>NON-OPTIMAL SOLUTION</strong>
            <ul style={{ marginTop: '0.25rem', paddingLeft: '1.25rem' }}>
              {schedule.violatedHardConstraints.map(c => <li key={c}>{c}</li>)}
            </ul>
          </div>
        )}

        {schedule.errorMessage && (
          <div style={{ background: '#fef2f2', border: '1px solid #fca5a5', padding: '0.75rem', borderRadius: '6px', marginTop: '0.75rem' }}>
            <strong>Error:</strong> {schedule.errorMessage}
          </div>
        )}

        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
          {isRunning && <button className="danger" onClick={handleStop}>Stop Solver</button>}
          {canAccept && <button className="primary" onClick={handleAccept}>Accept</button>}
          {canReject && <button className="danger" onClick={handleReject}>Reject</button>}
          <button onClick={handleExport}>Export to Excel</button>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '0', marginBottom: '1rem' }}>
        {(['staffing', 'agents', 'preferences', 'violations'] as const).map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            style={{ background: activeTab === tab ? '#3b82f6' : '#e5e7eb', color: activeTab === tab ? '#fff' : '#374151', borderRadius: 0, padding: '0.5rem 1.25rem' }}>
            {tab === 'staffing' ? 'Staffing Summary' : tab === 'agents' ? 'Agent Schedule' : tab === 'preferences' ? 'Preference Report' : 'Constraint Violations'}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px' }}>
        {activeTab === 'staffing' && (
          <div>
            <h3>Staffing Summary</h3>
            {/* TODO: render staffingSummary table */}
            <p style={{ color: '#6b7280' }}>Staffing summary data will be displayed here.</p>
          </div>
        )}
        {activeTab === 'agents' && (
          <div>
            <h3>Agent Schedule</h3>
            {/* TODO: render agent schedule grid */}
            <p style={{ color: '#6b7280' }}>Agent schedule grid will be displayed here.</p>
          </div>
        )}
        {activeTab === 'preferences' && (
          <div>
            <h3>Preference Report</h3>
            {/* TODO: render preference report table */}
            <p style={{ color: '#6b7280' }}>Preference report will be displayed here.</p>
          </div>
        )}
        {activeTab === 'violations' && (
          <div>
            <h3>Constraint Violations</h3>
            {/* TODO: render constraint violations table */}
            <p style={{ color: '#6b7280' }}>Constraint violations will be displayed here.</p>
          </div>
        )}
      </div>
    </>
  )
}
