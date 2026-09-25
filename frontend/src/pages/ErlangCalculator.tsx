import { useState } from 'react'
import type { CSSProperties } from 'react'
import { erlangCalculator, getErrorMessage } from '../api/client'
import type {
  ErlangAdjustments,
  ErlangCalculationResponse,
} from '../api/client'
import { showToast } from '../components/Toast'

/**
 * A scratchpad over the Erlang C and Erlang X maths. It calls /calc/erlang-c and /calc/erlang-x,
 * neither of which reads or writes anything: no desk, no timeslot, no staffing requirement row.
 *
 * Deliberately separate from the Erlang X mode on Staffing Requirements, which is the persisting
 * path -- that one replaces the live requirements for its whole date range, and it still calls the
 * older ErlangXService with the hardcoded hourly divisor. This page is where the corrected maths can
 * be compared against real numbers without moving anything the solver reads.
 */

// Percentages on screen, fractions on the wire. The API rejects 80 where 0.8 belongs -- correctly,
// since a fraction and a percentage cannot be told apart by magnitude alone -- so the conversion
// happens here rather than surfacing that 400 to an operator who typed what the label asked for.
const asFraction = (percent: number) => percent / 100

interface Inputs {
  volume: number
  intervalMinutes: number
  ahtSeconds: number
  serviceLevelTargetPercent: number
  serviceLevelThresholdSeconds: number
  patienceSeconds: number
  retryPercent: number
  countAbandonsAsAnswered: boolean
  shrinkagePercent: number
  maxOccupancyPercent: number
  concurrency: number
}

const DEFAULTS: Inputs = {
  volume: 100,
  intervalMinutes: 30,
  ahtSeconds: 180,
  serviceLevelTargetPercent: 80,
  serviceLevelThresholdSeconds: 20,
  patienceSeconds: 90,
  retryPercent: 25,
  countAbandonsAsAnswered: false,
  // All three adjustments start OFF. This system has no agreed shrinkage or occupancy ceiling, and
  // a plausible-looking default here would quietly become one.
  shrinkagePercent: 0,
  maxOccupancyPercent: 0,
  concurrency: 1,
}

const DASH = '—'

function pct(value: number | null, digits = 1) {
  return value == null ? DASH : `${(value * 100).toFixed(digits)}%`
}

function num(value: number | null, digits = 2) {
  return value == null ? DASH : value.toFixed(digits)
}

function int(value: number | null) {
  return value == null ? DASH : String(value)
}

function signed(value: number, digits = 0) {
  const shown = value.toFixed(digits)
  return value > 0 ? `+${shown}` : shown
}

/** Δ only where both models report the quantity; anything else is not a difference but an absence. */
function delta(
  c: ErlangCalculationResponse | null,
  x: ErlangCalculationResponse | null,
  pick: (r: ErlangCalculationResponse) => number | null,
  format: 'int' | 'pct' | 'num',
) {
  if (!c || !x) return DASH
  const a = pick(c)
  const b = pick(x)
  if (a == null || b == null) return DASH
  const diff = b - a
  if (Math.abs(diff) < 1e-9) return '0'
  if (format === 'pct') return `${signed(diff * 100, 1)} pp`
  if (format === 'num') return signed(diff, 2)
  return signed(diff)
}

export default function ErlangCalculator() {
  const [inputs, setInputs] = useState<Inputs>(DEFAULTS)
  const [erlangC, setErlangC] = useState<ErlangCalculationResponse | null>(null)
  const [erlangX, setErlangX] = useState<ErlangCalculationResponse | null>(null)
  const [calculating, setCalculating] = useState(false)
  const [error, setError] = useState('')

  const set = <K extends keyof Inputs>(key: K, value: Inputs[K]) =>
    setInputs(prev => ({ ...prev, [key]: value }))

  const adjustments = (): ErlangAdjustments => ({
    // Zero means "no adjustment" for all three, which is also what null means to the API. An
    // occupancy ceiling of 0% has no sensible reading, so 0 is the off switch rather than a value.
    shrinkage: inputs.shrinkagePercent > 0 ? asFraction(inputs.shrinkagePercent) : null,
    maxOccupancy: inputs.maxOccupancyPercent > 0 ? asFraction(inputs.maxOccupancyPercent) : null,
    concurrency: inputs.concurrency > 1 ? inputs.concurrency : null,
  })

  const handleCalculate = async () => {
    setCalculating(true)
    setError('')
    const shared = {
      volume: inputs.volume,
      intervalMinutes: inputs.intervalMinutes,
      ahtSeconds: inputs.ahtSeconds,
      serviceLevelTarget: asFraction(inputs.serviceLevelTargetPercent),
      serviceLevelThresholdSeconds: inputs.serviceLevelThresholdSeconds,
      adjustments: adjustments(),
    }
    try {
      // Both models on the same inputs, every time. The difference between them IS the answer to
      // "what do impatience and retrials do to headcount", and it is not visible one at a time.
      const [c, x] = await Promise.all([
        erlangCalculator.erlangC(shared),
        erlangCalculator.erlangX({
          ...shared,
          patienceSeconds: inputs.patienceSeconds,
          retryFraction: asFraction(inputs.retryPercent),
          countAbandonsAsAnswered: inputs.countAbandonsAsAnswered,
        }),
      ])
      setErlangC(c)
      setErlangX(x)
    } catch (err) {
      // Leave the previous answers on screen: clearing them loses the comparison the operator was
      // reading, and the message says plainly that these are the older numbers.
      setError(getErrorMessage(err))
      showToast('error', getErrorMessage(err))
    } finally {
      setCalculating(false)
    }
  }

  const numberField = (
    label: string,
    key: keyof Inputs,
    opts: { min?: number; max?: number; step?: number; hint?: string } = {},
  ) => (
    <label style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem', fontSize: '0.85rem' }}>
      <span style={{ fontWeight: 500 }}>{label}</span>
      <input
        type="number"
        min={opts.min ?? 0}
        max={opts.max}
        step={opts.step ?? 1}
        value={inputs[key] as number}
        onChange={e => set(key, (Number(e.target.value) || 0) as Inputs[typeof key])}
        style={{ padding: '0.3rem 0.4rem', border: '1px solid #d1d5db', borderRadius: '4px' }}
      />
      {opts.hint && <span style={{ color: '#6b7280', fontSize: '0.75rem' }}>{opts.hint}</span>}
    </label>
  )

  const rows: Array<{
    label: string
    pick: (r: ErlangCalculationResponse) => number | null
    format: 'int' | 'pct' | 'num'
    emphasis?: boolean
    note?: string
  }> = [
    { label: 'Agents required', pick: r => r.agentsRequired, format: 'int', note: 'smallest headcount meeting the target' },
    { label: '+ Occupancy relief', pick: r => r.occupancyRelief, format: 'int', note: 'added to respect the ceiling' },
    { label: 'Handling agents', pick: r => r.handlingAgents, format: 'int', note: 'must be on contacts' },
    { label: '+ Shrinkage uplift', pick: r => r.shrinkageUplift, format: 'int', note: 'added to cover absence' },
    { label: 'Scheduled agents', pick: r => r.scheduledAgents, format: 'int', emphasis: true, note: 'the number to roster' },
    { label: 'Offered load (Erlangs)', pick: r => r.offeredLoad, format: 'num', note: 'including converged retrials' },
    { label: 'Base offered load', pick: r => r.baseOfferedLoad, format: 'num', note: 'first attempts only' },
    { label: 'Service level', pick: r => r.serviceLevel, format: 'pct', note: 'at agents required' },
    { label: 'P(wait)', pick: r => r.probabilityOfWait, format: 'pct' },
    { label: 'P(abandon)', pick: r => r.probabilityOfAbandon, format: 'pct', note: 'Erlang C models none' },
    { label: 'Occupancy', pick: r => r.occupancy, format: 'pct', note: 'at agents required' },
    { label: 'Occupancy at handling', pick: r => r.occupancyAtHandlingAgents, format: 'pct', note: 'what the ceiling bought' },
    { label: 'Average speed of answer (s)', pick: r => r.averageSpeedOfAnswerSeconds, format: 'num', note: 'no closed form under Erlang X' },
    { label: 'Retrial iterations', pick: r => r.retrialIterations, format: 'int', note: 'high means a fragile input' },
  ]

  const cell = (r: ErlangCalculationResponse | null, row: typeof rows[number]) => {
    if (!r) return DASH
    const value = row.pick(r)
    if (row.format === 'pct') return pct(value)
    if (row.format === 'num') return num(value)
    return int(value)
  }

  const th: CSSProperties = { textAlign: 'right', padding: '6px 10px', borderBottom: '2px solid #e5e7eb' }
  const td: CSSProperties = { textAlign: 'right', padding: '6px 10px', borderBottom: '1px solid #f3f4f6', fontVariantNumeric: 'tabular-nums' }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div>
        <h2 style={{ marginBottom: '0.25rem' }}>Erlang Calculator</h2>
        <p style={{ color: '#15803d', background: '#f0fdf4', border: '1px solid #86efac', borderRadius: '4px', padding: '0.5rem 0.75rem', fontSize: '0.85rem', margin: 0 }}>
          Nothing here is saved. This page does not touch staffing requirements, timeslots or any
          schedule — it posts numbers and shows the answer. The <strong>Erlang X</strong> mode on{' '}
          <strong>Staffing Requirements</strong> is the one that writes: it replaces the live
          requirements for the whole date range it is given.
        </p>
      </div>

      <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px' }}>
        <h3 style={{ marginTop: 0 }}>Demand</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '0.75rem' }}>
          {numberField('Contacts in interval', 'volume', { hint: 'not a per-hour rate' })}
          {numberField('Interval (minutes)', 'intervalMinutes', { min: 1, max: 1440, hint: '15, 30 and 60 are all normal' })}
          {numberField('AHT (seconds)', 'ahtSeconds', { hint: 'including wrap-up' })}
          {numberField('Service level target (%)', 'serviceLevelTargetPercent', { max: 100 })}
          {numberField('Within (seconds)', 'serviceLevelThresholdSeconds')}
        </div>

        <h3>Impatience <span style={{ fontWeight: 400, fontSize: '0.8rem', color: '#6b7280' }}>— Erlang X only; Erlang C ignores these by definition</span></h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '0.75rem' }}>
          {numberField('Mean patience (seconds)', 'patienceSeconds', { hint: '0 = infinitely patient' })}
          {numberField('Retry rate (%)', 'retryPercent', { max: 100, hint: 'of abandoned callers' })}
          <label style={{ display: 'flex', alignItems: 'flex-start', gap: '0.4rem', fontSize: '0.85rem', marginTop: '1.1rem' }}>
            <input
              type="checkbox"
              checked={inputs.countAbandonsAsAnswered}
              onChange={e => set('countAbandonsAsAnswered', e.target.checked)}
            />
            <span>
              Count abandons as answered
              <span style={{ display: 'block', color: '#6b7280', fontSize: '0.75rem' }}>
                raises service level, lowers headcount
              </span>
            </span>
          </label>
        </div>

        <h3>Rostering adjustments <span style={{ fontWeight: 400, fontSize: '0.8rem', color: '#6b7280' }}>— 0 means off; these are not system defaults</span></h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '0.75rem' }}>
          {numberField('Shrinkage (%)', 'shrinkagePercent', { max: 99, hint: 'absence, training, meetings' })}
          {numberField('Max occupancy (%)', 'maxOccupancyPercent', { max: 100, hint: '0 = no ceiling' })}
          {numberField('Concurrency', 'concurrency', { min: 1, step: 0.5, hint: 'contacts per agent; 1 for voice' })}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginTop: '1rem' }}>
          <button
            onClick={handleCalculate}
            disabled={calculating}
            style={{ padding: '0.4rem 1.2rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: calculating ? 'not-allowed' : 'pointer', opacity: calculating ? 0.6 : 1 }}
          >
            {calculating ? 'Calculating...' : 'Calculate'}
          </button>
          <button
            onClick={() => { setInputs(DEFAULTS); setErlangC(null); setErlangX(null); setError('') }}
            style={{ padding: '0.4rem 1rem', background: '#e5e7eb', color: '#374151', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
          >
            Reset
          </button>
          {error && <span style={{ color: '#dc2626', fontSize: '0.85rem' }}>{error}{erlangC && ' — the numbers below are from the previous calculation'}</span>}
        </div>
      </div>

      {(erlangC || erlangX) && (
        <div style={{ background: '#fff', padding: '1rem', borderRadius: '8px' }}>
          <h3 style={{ marginTop: 0 }}>Result</h3>
          <p style={{ fontSize: '0.8rem', color: '#6b7280', marginTop: 0 }}>
            Both models on the same inputs. Erlang C is the conservative baseline — it assumes every
            caller waits forever, so it never staffs below what impatience would allow. A dash means
            the model does not report that quantity, not zero.
          </p>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
            <thead>
              <tr>
                <th style={{ ...th, textAlign: 'left' }}>Quantity</th>
                <th style={th}>Erlang C</th>
                <th style={th}>Erlang X</th>
                <th style={th}>Δ</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => (
                <tr key={row.label}>
                  <td style={{ ...td, textAlign: 'left', fontWeight: row.emphasis ? 600 : 400 }}>
                    {row.label}
                    {row.note && <span style={{ display: 'block', color: '#6b7280', fontSize: '0.72rem' }}>{row.note}</span>}
                  </td>
                  <td style={{ ...td, fontWeight: row.emphasis ? 600 : 400 }}>{cell(erlangC, row)}</td>
                  <td style={{ ...td, fontWeight: row.emphasis ? 600 : 400 }}>{cell(erlangX, row)}</td>
                  <td style={{ ...td, color: '#6b7280' }}>{delta(erlangC, erlangX, row.pick, row.format)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
