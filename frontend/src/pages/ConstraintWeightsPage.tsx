import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { constraintWeights as cwApi, type ConstraintWeightsData, type Score } from '../api/client'

const CONSTRAINT_LABELS: Record<string, string> = {
  agentDayOffWeight: 'Agent Day Off',
  specMatchWeight: 'Specialization Match',
  noOverlapWeight: 'One Assignment per Timeslot',
  exactlyOneBreakWeight: 'Exactly One Break',
  breakDurationWeight: 'Break Duration',
  breakBlockedWindowWeight: 'Break Blocked Window',
  breakAlignmentWeight: 'Break Start Alignment',
  preferPrimaryWeight: 'Prefer Primary Specialization',
  honourStartTimeWeight: 'Honour Preferred Start Time',
  honourBreakTimeWeight: 'Honour Preferred Break Time',
  breakClusteringWeight: 'Break Clustering',
  contractedHoursWeight: 'Contracted Hours',
  bulkOverallocationLimitWeight: 'Bulk Over-allocation Limit',
  bulkUnderallocationSoftWeight: 'Bulk Under-allocation (Soft)',
  bulkUnderallocationHardWeight: 'Bulk Under-allocation (Hard)',
}

export default function ConstraintWeightsPage() {
  const { deskId } = useParams<{ deskId: string }>()
  const [weights, setWeights] = useState<ConstraintWeightsData | null>(null)

  useEffect(() => {
    if (deskId) cwApi.get(deskId).then(setWeights).catch(console.error)
  }, [deskId])

  const handleSave = async () => {
    if (!deskId || !weights) return
    try {
      const updated = await cwApi.update(deskId, weights)
      setWeights(updated)
    } catch (err) {
      console.error(err)
    }
  }

  if (!weights) return <p>Loading...</p>

  return (
    <>
      <h1>Constraint Weights</h1>
      <table>
        <thead>
          <tr><th>Constraint</th><th>Hard Score</th><th>Soft Score</th></tr>
        </thead>
        <tbody>
          {Object.entries(CONSTRAINT_LABELS).map(([key, label]) => {
            const score = (weights as Record<string, Score>)[key] || { hardScore: 0, softScore: 0 }
            return (
              <tr key={key}>
                <td>{label}</td>
                <td><input type="number" value={score.hardScore} onChange={e => setWeights({ ...weights, [key]: { ...score, hardScore: Number(e.target.value) } })} /></td>
                <td><input type="number" value={score.softScore} onChange={e => setWeights({ ...weights, [key]: { ...score, softScore: Number(e.target.value) } })} /></td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <button className="primary" onClick={handleSave} style={{ marginTop: '1rem' }}>Save</button>
    </>
  )
}
