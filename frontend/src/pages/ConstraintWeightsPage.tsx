import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { constraintWeights as cwApi, type ConstraintWeightsData, type Score, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

const CONSTRAINTS: Array<{ key: string; label: string; description: string }> = [
  { key: 'unassignedAssignmentWeight', label: 'Unassigned Assignment', description: 'Prefer filling every staffing slot with an agent' },
  { key: 'agentDayOffWeight', label: 'Agent Day Off', description: 'Agents must not be assigned on their days off' },
  { key: 'specMatchWeight', label: 'Specialization Match', description: 'Assignments must match agent specializations' },
  { key: 'noOverlapWeight', label: 'One Assignment per Timeslot', description: 'Agent can only be assigned once per timeslot' },
  { key: 'exactlyOneBreakWeight', label: 'Exactly One Break', description: 'Each agent must have exactly one break per day' },
  { key: 'breakDurationWeight', label: 'Break Duration', description: 'Break must match the configured duration' },
  { key: 'breakBlockedWindowWeight', label: 'Break Blocked Window', description: 'No breaks in the first/last hours of a shift' },
  { key: 'breakAlignmentWeight', label: 'Break Start Alignment', description: 'Break must start on hour/half-hour/quarter' },
  { key: 'contractedHoursOverWeight', label: 'Contracted Hours (Over)', description: 'Prevent agents working more than contracted hours' },
  { key: 'contractedHoursUnderWeight', label: 'Contracted Hours (Under)', description: 'Penalise agents working fewer than contracted hours' },
  { key: 'bulkOverallocationLimitWeight', label: 'Bulk Over-allocation Limit', description: 'Prevent excessive over-staffing' },
  { key: 'bulkUnderallocationHardWeight', label: 'Bulk Under-allocation (Hard)', description: 'Hard limit on under-staffing' },
  { key: 'preferPrimaryWeight', label: 'Prefer Primary Specialization', description: 'Prefer assigning agents to their primary specialization' },
  { key: 'honourStartTimeWeight', label: 'Honour Preferred Start Time', description: 'Try to honour agent preferred start time' },
  { key: 'honourBreakTimeWeight', label: 'Honour Preferred Break Time', description: 'Try to honour agent preferred break time' },
  { key: 'breakClusteringWeight', label: 'Break Clustering', description: 'Avoid too many agents on break at the same time' },
  { key: 'bulkUnderallocationSoftWeight', label: 'Bulk Under-allocation (Soft)', description: 'Soft penalty for under-staffing' },
  { key: 'minStaffingWeight', label: 'Minimum Staffing', description: 'Keep at least one agent on every hour, even where forecast demand is zero' },
  { key: 'consistentStartWeight', label: 'Consistent Daily Start', description: 'Prefer agents starting at the same time every day they work' },
]

const DEFAULTS: Record<string, Score> = {
  unassignedAssignmentWeight: { hardScore: 0, softScore: 1000 },
  agentDayOffWeight: { hardScore: 1, softScore: 0 },
  specMatchWeight: { hardScore: 1, softScore: 0 },
  noOverlapWeight: { hardScore: 1, softScore: 0 },
  exactlyOneBreakWeight: { hardScore: 1, softScore: 0 },
  breakDurationWeight: { hardScore: 1, softScore: 0 },
  breakBlockedWindowWeight: { hardScore: 1, softScore: 0 },
  breakAlignmentWeight: { hardScore: 1, softScore: 0 },
  contractedHoursOverWeight: { hardScore: 1001, softScore: 0 },
  contractedHoursUnderWeight: { hardScore: 1, softScore: 0 },
  bulkOverallocationLimitWeight: { hardScore: 1, softScore: 0 },
  bulkUnderallocationHardWeight: { hardScore: 1, softScore: 0 },
  preferPrimaryWeight: { hardScore: 0, softScore: 1 },
  honourStartTimeWeight: { hardScore: 0, softScore: 5 },
  honourBreakTimeWeight: { hardScore: 0, softScore: 5 },
  breakClusteringWeight: { hardScore: 0, softScore: 2 },
  bulkUnderallocationSoftWeight: { hardScore: 0, softScore: 1 },
  minStaffingWeight: { hardScore: 0, softScore: 1000 },
  consistentStartWeight: { hardScore: 0, softScore: 2 },
}

export default function ConstraintWeightsPage() {
  const { deskId } = useParams<{ deskId: string }>()
  const [weights, setWeights] = useState<ConstraintWeightsData | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (deskId) cwApi.get(deskId).then(setWeights).catch(err => showToast('error', getErrorMessage(err)))
  }, [deskId])

  const handleSave = async () => {
    if (!deskId || !weights) return
    setSaving(true)
    try {
      const updated = await cwApi.update(deskId, weights)
      setWeights(updated)
      showToast('success', 'Constraint weights saved')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleReset = () => {
    setWeights({ ...DEFAULTS })
  }

  if (!weights) return <p>Loading...</p>

  return (
    <>
      <h1>Constraint Weights</h1>
      <table>
        <thead>
          <tr><th>Constraint</th><th>Description</th><th>Level</th><th>Hard Score</th><th>Soft Score</th></tr>
        </thead>
        <tbody>
          {CONSTRAINTS.map(({ key, label, description }) => {
            const score = (weights as Record<string, Score>)[key] || DEFAULTS[key]
            const level = score.hardScore > 0 ? 'Hard' : 'Soft'
            return (
              <tr key={key}>
                <td style={{ fontWeight: 500 }}>{label}</td>
                <td style={{ fontSize: '0.8rem', color: '#6b7280' }}>{description}</td>
                <td>
                  <span style={{ padding: '0.15rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                    background: level === 'Hard' ? '#fef2f2' : '#f0fdf4',
                    color: level === 'Hard' ? '#dc2626' : '#16a34a' }}>
                    {level}
                  </span>
                </td>
                <td><input type="number" value={score.hardScore} onChange={e => setWeights({ ...weights, [key]: { ...score, hardScore: Number(e.target.value) } })} style={{ width: '70px' }} /></td>
                <td><input type="number" value={score.softScore} onChange={e => setWeights({ ...weights, [key]: { ...score, softScore: Number(e.target.value) } })} style={{ width: '70px' }} /></td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
        <button className="primary" onClick={handleSave} disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
        <button onClick={handleReset}>Reset to Defaults</button>
      </div>
    </>
  )
}
