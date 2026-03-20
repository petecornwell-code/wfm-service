import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { appConfiguration, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

export default function Configuration() {
  const [bambooServer, setBambooServer] = useState('')
  const [bambooApiKey, setBambooApiKey] = useState('')
  const [bambooCacheMaxSize, setBambooCacheMaxSize] = useState('5000')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

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
    </div>
  )
}
