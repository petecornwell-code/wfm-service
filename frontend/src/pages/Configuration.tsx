import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { appConfiguration, jobTitleConfig, jobTitleIncludePattern, bambooSyncStatus, getErrorMessage, type JobTitleConfigResponse, type JobTitleIncludePatternResponse, type BambooSyncEventResponse } from '../api/client'
import { showToast } from '../components/Toast'

export default function Configuration() {
  const [bambooServer, setBambooServer] = useState('')
  const [bambooApiKey, setBambooApiKey] = useState('')
  const [bambooCacheMaxSize, setBambooCacheMaxSize] = useState('5000')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  // Non-Schedulable Job Titles
  const [jobTitles, setJobTitles] = useState<JobTitleConfigResponse[] | null>(null)

  // Job Title Allowlist
  const [patterns, setPatterns] = useState<JobTitleIncludePatternResponse[] | null>(null)
  const [newPattern, setNewPattern] = useState('')
  const [addingPattern, setAddingPattern] = useState(false)

  // BambooHR Sync Status
  const [syncStatus, setSyncStatus] = useState<BambooSyncEventResponse | null>(null)
  const [loadingSyncStatus, setLoadingSyncStatus] = useState(true)

  useEffect(() => {
    jobTitleConfig.list()
      .then(setJobTitles)
      .catch(err => showToast('error', getErrorMessage(err)))
  }, [])

  useEffect(() => {
    jobTitleIncludePattern.list()
      .then(setPatterns)
      .catch(err => showToast('error', getErrorMessage(err)))
  }, [])

  const handleAddPattern = async () => {
    const trimmed = newPattern.trim()
    if (!trimmed) return
    setAddingPattern(true)
    try {
      const created = await jobTitleIncludePattern.add(trimmed)
      // Re-adding an existing pattern returns the existing row, so guard against duplicates.
      setPatterns(prev => {
        if (!prev) return [created]
        return prev.some(p => p.id === created.id) ? prev : [...prev, created]
      })
      setNewPattern('')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setAddingPattern(false)
    }
  }

  const handleRemovePattern = async (id: string) => {
    const previous = patterns
    setPatterns(prev => prev ? prev.filter(p => p.id !== id) : prev)
    try {
      await jobTitleIncludePattern.remove(id)
    } catch (err) {
      setPatterns(previous)
      showToast('error', 'Failed to remove pattern — please try again')
    }
  }

  useEffect(() => {
    bambooSyncStatus.get()
      .then(setSyncStatus)
      .catch(err => showToast('error', getErrorMessage(err)))
      .finally(() => setLoadingSyncStatus(false))
  }, [])

  const handleToggle = async (id: string, value: boolean) => {
    if (!jobTitles) return
    // Optimistic update
    setJobTitles(jobTitles.map(row => row.id === id ? { ...row, nonSchedulable: value } : row))
    try {
      const updated = await jobTitleConfig.setNonSchedulable(id, value)
      setJobTitles(prev => prev ? prev.map(row => row.id === id ? updated : row) : prev)
    } catch (err) {
      // Revert on error
      setJobTitles(prev => prev ? prev.map(row => row.id === id ? { ...row, nonSchedulable: !value } : row) : prev)
      // Surface the actual failure rather than a generic string — the generic message made a
      // real failure indistinguishable from a mis-click on another section.
      showToast('error', `Failed to update job title: ${getErrorMessage(err)}`)
    }
  }

  useEffect(() => {
    appConfiguration.get()
      .then(config => {
        setBambooServer(config['bamboohr.server'] || '')
        setBambooApiKey(config['bamboohr.apiKey'] || '')
        setBambooCacheMaxSize(config['bamboohr.cache.maxSize'] || '5000')
      })
      .catch(err => showToast('error', getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  const handleSave = async () => {
    setSaving(true)
    try {
      const updated = await appConfiguration.update({
        'bamboohr.server': bambooServer,
        'bamboohr.apiKey': bambooApiKey,
        'bamboohr.cache.maxSize': bambooCacheMaxSize,
      })
      setBambooServer(updated['bamboohr.server'] || '')
      setBambooApiKey(updated['bamboohr.apiKey'] || '')
      setBambooCacheMaxSize(updated['bamboohr.cache.maxSize'] || '5000')
      showToast('success', 'Configuration saved')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="main-content"><p>Loading configuration...</p></div>

  return (
    <div className="main-content">
      <p style={{ marginBottom: '1rem' }}><Link to="/">Back to Desk Selector</Link></p>
      <h1>Configuration</h1>
      <div style={{ maxWidth: '500px' }}>
        <div style={{ marginBottom: '1rem' }}>
          <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem' }}>BambooHR Server</label>
          <input
            value={bambooServer}
            onChange={e => setBambooServer(e.target.value)}
            placeholder="e.g. helpware.bamboohr.com"
            style={{ width: '100%', padding: '0.5rem', border: '1px solid #d1d5db', borderRadius: '4px' }}
          />
          <span style={{ fontSize: '0.8rem', color: '#6b7280' }}>The BambooHR subdomain or full server hostname</span>
        </div>
        <div style={{ marginBottom: '1rem' }}>
          <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem' }}>API Key</label>
          <input
            value={bambooApiKey}
            onChange={e => setBambooApiKey(e.target.value)}
            placeholder="Enter BambooHR API key"
            style={{ width: '100%', padding: '0.5rem', border: '1px solid #d1d5db', borderRadius: '4px' }}
          />
          <span style={{ fontSize: '0.8rem', color: '#6b7280' }}>Your BambooHR API key (will be hidden in a future release)</span>
        </div>
        <div style={{ marginBottom: '1.5rem' }}>
          <label style={{ display: 'block', fontWeight: 500, marginBottom: '0.25rem' }}>Cache Max Size</label>
          <input
            type="number"
            value={bambooCacheMaxSize}
            onChange={e => setBambooCacheMaxSize(e.target.value)}
            placeholder="5000"
            style={{ width: '100%', padding: '0.5rem', border: '1px solid #d1d5db', borderRadius: '4px' }}
          />
          <span style={{ fontSize: '0.8rem', color: '#6b7280' }}>Maximum number of employee records to cache. Results exceeding this will not be cached (default: 5000)</span>
        </div>
        <button className="primary" onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save Configuration'}
        </button>
      </div>

      {/* Non-Schedulable Job Titles */}
      <section>
        <div style={{ marginTop: '2rem', maxWidth: '500px' }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600 }}>Schedulable Job Titles</h2>
          <p style={{ fontSize: '0.85rem', color: '#6b7280', marginBottom: '0.75rem' }}>
            Checked titles can be scheduled. <strong>Unchecked titles are excluded</strong> from schedule
            solving and cannot be assigned to a desk.
          </p>
          {jobTitles === null ? (
            <p>Loading job titles...</p>
          ) : jobTitles.length === 0 ? (
            <p style={{ color: '#6b7280' }}>No job titles synced yet. Run a BambooHR refresh to populate this list.</p>
          ) : (
            <div>
              {jobTitles.map(row => (
                <label
                  key={row.id}
                  style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.25rem 0.5rem', borderBottom: '1px solid #f3f4f6', cursor: 'pointer', fontWeight: 400 }}
                >
                  {/* Checkbox represents SCHEDULABLE, the inverse of the stored nonSchedulable
                      flag. Toggling flips the stored flag either way, so the handler call is
                      unchanged — only the checked binding and labelling are inverted. */}
                  <input
                    type="checkbox"
                    checked={!row.nonSchedulable}
                    onChange={() => handleToggle(row.id, !row.nonSchedulable)}
                  />
                  <span>{row.jobTitle}</span>
                  {row.nonSchedulable && (
                    <span style={{ fontSize: '0.85rem', color: '#ef4444', marginLeft: 'auto', fontWeight: 400 }}>Not schedulable</span>
                  )}
                </label>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Job Title Allowlist */}
      <section>
        <div style={{ marginTop: '2rem', maxWidth: '500px' }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600 }}>Job Title Allowlist</h2>
          <p style={{ fontSize: '0.85rem', color: '#6b7280', marginBottom: '0.75rem' }}>
            Restricts which agents are pre-seeded into the desk-assignment template and accepted on
            upload. A job title matches when it <em>contains</em> one of these entries, so
            "Customer Support Representative" also matches "Senior Customer Support Representative II".
            Matching ignores case.
          </p>

          {patterns !== null && patterns.length === 0 && (
            <p style={{ fontSize: '0.85rem', color: '#92400e', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '6px', padding: '0.5rem 0.75rem', marginBottom: '0.75rem' }}>
              No entries — the allowlist is <strong>inactive</strong> and every job title is included.
              Add an entry to restrict it.
            </p>
          )}

          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
            <input
              value={newPattern}
              onChange={e => setNewPattern(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleAddPattern() } }}
              placeholder="e.g. Customer Support Representative"
              style={{ flex: 1 }}
              disabled={addingPattern}
            />
            <button className="primary" onClick={handleAddPattern} disabled={addingPattern || !newPattern.trim()}>
              {addingPattern ? 'Adding...' : 'Add'}
            </button>
          </div>

          {patterns === null ? (
            <p>Loading allowlist...</p>
          ) : (
            <div>
              {patterns.map(row => (
                <div
                  key={row.id}
                  style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.25rem 0.5rem', borderBottom: '1px solid #f3f4f6' }}
                >
                  <span>{row.pattern}</span>
                  <button
                    onClick={() => handleRemovePattern(row.id)}
                    style={{ marginLeft: 'auto', fontSize: '0.85rem' }}
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* BambooHR Sync Status */}
      <section>
        <div style={{ marginTop: '2rem', maxWidth: '600px' }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600 }}>BambooHR Sync Status</h2>
          <div style={{ marginTop: '0.5rem', padding: '1rem', background: '#f9fafb', borderRadius: '6px', border: '1px solid #e5e7eb', maxWidth: '600px' }}>
            {loadingSyncStatus ? (
              <p style={{ color: '#6b7280' }}>Loading sync status...</p>
            ) : (
              <dl style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem', margin: 0 }}>
                <div>
                  <dt style={{ fontWeight: 600 }}>Last sync</dt>
                  <dd style={{ margin: 0 }}>{syncStatus?.startedAt ? new Date(syncStatus.startedAt).toLocaleString() : 'Never'}</dd>
                </div>
                <div>
                  <dt style={{ fontWeight: 600 }}>Status</dt>
                  <dd style={{ margin: 0 }}>
                    {syncStatus?.startedAt == null
                      ? <span style={{ color: '#6b7280' }}>—</span>
                      : syncStatus.success
                        ? <span style={{ color: '#16a34a' }}>Success</span>
                        : <span style={{ color: '#dc2626' }}>Failed</span>
                    }
                  </dd>
                </div>
                {syncStatus && !syncStatus.success && syncStatus.errorMessage && (
                  <div>
                    <dt style={{ fontWeight: 600 }}>Error</dt>
                    <dd style={{ margin: 0 }}>{syncStatus.errorMessage.length > 200 ? syncStatus.errorMessage.slice(0, 200) : syncStatus.errorMessage}</dd>
                  </div>
                )}
                {syncStatus?.retryAfterSeconds != null && (
                  <div>
                    <dt style={{ fontWeight: 600 }}>Retry in</dt>
                    <dd style={{ margin: 0 }}>{syncStatus.retryAfterSeconds} seconds</dd>
                  </div>
                )}
                {syncStatus?.success && syncStatus.agentsSynced != null && (
                  <div>
                    <dt style={{ fontWeight: 600 }}>Agents synced</dt>
                    <dd style={{ margin: 0 }}>{syncStatus.agentsSynced}</dd>
                  </div>
                )}
                {syncStatus?.success && syncStatus.timeOffPulled != null && (
                  <div>
                    <dt style={{ fontWeight: 600 }}>PTO records pulled</dt>
                    <dd style={{ margin: 0 }}>{syncStatus.timeOffPulled}</dd>
                  </div>
                )}
              </dl>
            )}
          </div>
        </div>
      </section>
    </div>
  )
}
