import { useState, useEffect, useCallback } from 'react'

export interface ToastMessage {
  id: number
  type: 'success' | 'error'
  text: string
}

let nextId = 0
let globalAdd: ((msg: Omit<ToastMessage, 'id'>) => void) | null = null

export function showToast(type: 'success' | 'error', text: string) {
  globalAdd?.({ type, text })
}

export function ToastContainer() {
  const [toasts, setToasts] = useState<ToastMessage[]>([])

  const add = useCallback((msg: Omit<ToastMessage, 'id'>) => {
    const id = ++nextId
    setToasts(prev => [...prev, { ...msg, id }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000)
  }, [])

  useEffect(() => {
    globalAdd = add
    return () => { globalAdd = null }
  }, [add])

  if (toasts.length === 0) return null

  return (
    <div style={{ position: 'fixed', top: '1rem', right: '1rem', zIndex: 9999, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      {toasts.map(t => (
        <div key={t.id} style={{
          padding: '0.75rem 1rem',
          borderRadius: '6px',
          color: '#fff',
          background: t.type === 'error' ? '#dc2626' : '#16a34a',
          boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
          maxWidth: '400px',
          fontSize: '0.9rem',
          animation: 'fadeIn 0.2s ease-out',
        }}>
          {t.text}
        </div>
      ))}
    </div>
  )
}
