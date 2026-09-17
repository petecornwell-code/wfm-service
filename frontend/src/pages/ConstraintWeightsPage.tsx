import { Fragment, useEffect, useState } from 'react'
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
  { key: 'shiftEnvelopeComplianceWeight', label: 'Shift Envelope Compliance', description: 'Shift mode: agents must only work hours inside their assigned shift, outside its break' },
  { key: 'shiftWorkContiguityWeight', label: 'Shift Work Contiguity', description: 'Shift mode: an agent\u2019s hours must be contiguous apart from their break \u2014 no split shifts' },
  { key: 'bandCapacityWeight', label: 'Break Band Capacity', description: 'Shift mode: no more agents on a break band than its capacity allows' },
  { key: 'contractedHoursOverWeight', label: 'Contracted Hours (Over)', description: 'Prevent agents working more than contracted hours' },
  { key: 'contractedHoursUnderWeight', label: 'Contracted Hours (Under)', description: 'Penalise agents working fewer than contracted hours' },
  { key: 'contractedHoursUnderZeroWeight', label: 'Contracted Hours (Under, Zero)', description: 'Penalise an agent-day left completely unassigned — distinct from working merely too few hours' },
  { key: 'bulkOverallocationLimitWeight', label: 'Bulk Over-allocation Limit', description: 'Prevent excessive over-staffing' },
  { key: 'bulkUnderallocationHardWeight', label: 'Bulk Under-allocation (Hard)', description: 'Hard limit on under-staffing' },
  { key: 'preferPrimaryWeight', label: 'Prefer Primary Specialization', description: 'Prefer assigning agents to their primary specialization' },
  { key: 'honourStartTimeWeight', label: 'Honour Preferred Start Time', description: 'Try to honour agent preferred start time' },
  { key: 'honourBreakTimeWeight', label: 'Honour Preferred Break Time', description: 'Try to honour agent preferred break time' },
  { key: 'breakClusteringWeight', label: 'Break Clustering', description: 'Avoid too many agents on break at the same time' },
  { key: 'bulkUnderallocationSoftWeight', label: 'Bulk Under-allocation (Soft)', description: 'Soft penalty for under-staffing' },
  { key: 'consistentStartWeight', label: 'Usual Shift Consistency', description: 'Shift mode: nudge agents toward their stored usual shift start time, past the tolerance band below. Hard score must stay 0 — a hard score here would shorten shifts instead of keeping agents consistent, so it is rejected on save' },
  { key: 'consistencyToleranceMinutes', label: 'Usual Shift Consistency Tolerance', description: 'Shift mode: deviation from the usual shift start within this many minutes carries zero penalty' },
  { key: 'preferredStartShiftModeWeight', label: 'Preferred Start (Shift Mode)', description: 'Shift mode: tie-break only, used when Usual Shift Consistency scores two shifts equally. Weight must stay below Usual Shift Consistency’s — enforced on save' },
  { key: 'minStaffingWeight', label: 'Minimum Staffing', description: 'Keep at least one agent on every hour, even where forecast demand is zero' },
]

const DEFAULTS: Record<string, Score | number> = {
  unassignedAssignmentWeight: { hardScore: 0, softScore: 1000 },
  agentDayOffWeight: { hardScore: 1, softScore: 0 },
  specMatchWeight: { hardScore: 1, softScore: 0 },
  noOverlapWeight: { hardScore: 1, softScore: 0 },
  exactlyOneBreakWeight: { hardScore: 1, softScore: 0 },
  breakDurationWeight: { hardScore: 1, softScore: 0 },
  breakBlockedWindowWeight: { hardScore: 1, softScore: 0 },
  breakAlignmentWeight: { hardScore: 1, softScore: 0 },
  shiftEnvelopeComplianceWeight: { hardScore: 1, softScore: 0 },
  shiftWorkContiguityWeight: { hardScore: 10, softScore: 0 },
  bandCapacityWeight: { hardScore: 1, softScore: 0 },
  contractedHoursOverWeight: { hardScore: 1001, softScore: 0 },
  contractedHoursUnderWeight: { hardScore: 1, softScore: 0 },
  contractedHoursUnderZeroWeight: { hardScore: 100, softScore: 0 },
  bulkOverallocationLimitWeight: { hardScore: 1, softScore: 0 },
  bulkUnderallocationHardWeight: { hardScore: 1, softScore: 0 },
  preferPrimaryWeight: { hardScore: 0, softScore: 1 },
  honourStartTimeWeight: { hardScore: 0, softScore: 5 },
  honourBreakTimeWeight: { hardScore: 0, softScore: 5 },
  breakClusteringWeight: { hardScore: 0, softScore: 2 },
  bulkUnderallocationSoftWeight: { hardScore: 0, softScore: 1 },
  consistentStartWeight: { hardScore: 0, softScore: 2 },
  consistencyToleranceMinutes: 60,
  preferredStartShiftModeWeight: { hardScore: 0, softScore: 1 },
  minStaffingWeight: { hardScore: 0, softScore: 1000 },
}

// WR-01: every row except the dedicated consistencyToleranceMinutes one is rendered as a Score.
// ConstraintWeightsData's index signature is `Score | number`, so a runtime guard is required
// here instead of an unchecked `as Record<string, Score>` cast -- otherwise a future plain-number
// field added without its own branch (mirroring the tolerance-band one above) would silently
// reinterpret the number as `{ hardScore: undefined, softScore: undefined }` and misrender as
// "Soft" with NaN/blank inputs rather than failing visibly.
function isScore(value: Score | number | undefined): value is Score {
  return typeof value === 'object' && value !== null && 'hardScore' in value && 'softScore' in value
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
            // Component Specifications §3: the tolerance band is the page's first non-score
            // field -- it has no hard/soft dimension, so it must never be read through the
            // Record<string, Score> cast the other rows share. Its own render branch reads and
            // writes the plain number through its own state path.
            if (key === 'consistencyToleranceMinutes') {
              const rawValue = weights[key]
              const minutes = typeof rawValue === 'number' ? rawValue : (DEFAULTS[key] as number)
              return (
                <tr key={key}>
                  <td style={{ fontWeight: 500 }}>{label}</td>
                  <td style={{ fontSize: '0.8rem', color: '#6b7280' }}>{description}</td>
                  <td>
                    <span style={{ padding: '0.15rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600,
                      background: '#e5e7eb', color: '#374151' }}>
                      Minutes
                    </span>
                  </td>
                  <td colSpan={2}>
                    <input type="number" min={0} value={minutes} onChange={e => setWeights({ ...weights, [key]: Number(e.target.value) })} style={{ width: '70px' }} />
                    <span style={{ fontSize: '0.75rem', color: '#6b7280', marginLeft: '4px' }}> min</span>
                  </td>
                </tr>
              )
            }

            const rawValue = weights[key]
            const fallback = DEFAULTS[key]
            if (!isScore(rawValue) && !isScore(fallback)) {
              // Neither the live value nor the default is a Score, and this key has no
              // dedicated branch (like consistencyToleranceMinutes) to render it as a plain
              // number either -- skip the row rather than silently misrendering it.
              console.error(`ConstraintWeightsPage: "${key}" is not a Score and has no dedicated render branch — skipping row.`)
              return null
            }
            const score = isScore(rawValue) ? rawValue : (fallback as Score)
            const level = score.hardScore > 0 ? 'Hard' : 'Soft'
            const row = (
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

            // D-10 artefact 3: the precedence note sits directly beneath the third new row
            // (immediately above Minimum Staffing), stated in plain words rather than left for
            // an operator to reverse-engineer from the two weights' relative size.
            if (key === 'preferredStartShiftModeWeight') {
              return (
                <Fragment key={key}>
                  {row}
                  <tr>
                    <td colSpan={5}>
                      <div style={{ background: '#f9fafb', padding: '0.75rem', borderRadius: '6px', fontSize: '0.85rem', marginTop: '0.25rem' }}>
                        Usual Shift Consistency decides first. Preferred Start (Shift Mode) only breaks ties where Usual Shift Consistency scores two shifts equally — its weight must be lower than Usual Shift Consistency's, checked when you save.
                      </div>
                    </td>
                  </tr>
                </Fragment>
              )
            }

            return row
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
