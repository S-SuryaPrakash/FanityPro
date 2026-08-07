import { useState, useRef, useCallback } from 'react'
import './App.css'

type Status = 'idle' | 'uploading' | 'processing' | 'done' | 'error'

interface UploadState {
  status: Status
  file: File | null
  progress: string
  error: string | null
  downloadUrl: string | null
  downloadFilename: string | null
}

const API_ENDPOINT = '/api/v1/files/classify'

export default function App() {
  const [state, setState] = useState<UploadState>({
    status: 'idle',
    file: null,
    progress: '',
    error: null,
    downloadUrl: null,
    downloadFilename: null,
  })
  const [dragOver, setDragOver] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const reset = useCallback(() => {
    if (state.downloadUrl) URL.revokeObjectURL(state.downloadUrl)
    setState({ status: 'idle', file: null, progress: '', error: null, downloadUrl: null, downloadFilename: null })
  }, [state.downloadUrl])

  const classify = useCallback(async (file: File) => {
    if (state.downloadUrl) URL.revokeObjectURL(state.downloadUrl)
    setState({ status: 'uploading', file, progress: 'Uploading…', error: null, downloadUrl: null, downloadFilename: null })

    const formData = new FormData()
    formData.append('file', file)

    try {
      const response = await fetch(API_ENDPOINT, { method: 'POST', body: formData })

      if (!response.ok) {
        let message = `Server returned ${response.status}`
        try {
          const body = await response.json()
          if (body.detail) message = body.detail
          else if (body.title) message = body.title
        } catch { /* use default message */ }
        throw new Error(message)
      }

      setState(prev => ({ ...prev, status: 'processing', progress: 'Classifying messages…' }))

      const blob = await response.blob()
      const disposition = response.headers.get('Content-Disposition') ?? ''
      const match = disposition.match(/filename\*?=(?:UTF-8'')?([^;\n]+)/i)
      const filename = match ? decodeURIComponent(match[1].replace(/"/g, '')) : 'classified.xlsx'

      const downloadUrl = URL.createObjectURL(blob)
      setState(prev => ({ ...prev, status: 'done', downloadUrl, downloadFilename: filename, progress: '' }))
    } catch (err) {
      setState(prev => ({
        ...prev,
        status: 'error',
        error: err instanceof Error ? err.message : 'Unknown error',
        progress: '',
      }))
    }
  }, [state.downloadUrl])

  const onFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) classify(file)
  }, [classify])

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (file) classify(file)
  }, [classify])

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragOver(true)
  }, [])

  const onDragLeave = useCallback(() => setDragOver(false), [])

  const busy = state.status === 'uploading' || state.status === 'processing'

  return (
    <div className="app">
      <header className="header">
        <h1>Content Filter</h1>
        <p className="subtitle">Upload a conversation workbook to classify messages by risk category</p>
      </header>

      <div
        className={`dropzone ${dragOver ? 'dropzone--active' : ''} ${busy ? 'dropzone--busy' : ''} ${state.status === 'error' ? 'dropzone--error' : ''}`}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onClick={() => !busy && inputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => e.key === 'Enter' && !busy && inputRef.current?.click()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".xlsx"
          onChange={onFileChange}
          disabled={busy}
          aria-label="Upload workbook"
        />

        {state.status === 'idle' && (
          <div className="dropzone__prompt">
            <svg className="dropzone__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
            <p>Drop your <code>.xlsx</code> file here or click to browse</p>
            <span className="dropzone__hint">Max 5 MB &middot; .xlsx only</span>
          </div>
        )}

        {busy && (
          <div className="dropzone__busy">
            <div className="spinner" />
            <p>{state.progress}</p>
            {state.file && <span className="dropzone__filename">{state.file.name}</span>}
          </div>
        )}

        {state.status === 'done' && (
          <div className="dropzone__done">
            <svg className="dropzone__check" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            <p>Classification complete</p>
            {state.file && <span className="dropzone__filename">{state.file.name}</span>}
          </div>
        )}

        {state.status === 'error' && (
          <div className="dropzone__error">
            <svg className="dropzone__error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="15" y1="9" x2="9" y2="15" />
              <line x1="9" y1="9" x2="15" y2="15" />
            </svg>
            <p>{state.error}</p>
            <button className="btn btn--secondary" onClick={reset}>Try again</button>
          </div>
        )}
      </div>

      {state.status === 'done' && state.downloadUrl && (
        <div className="actions">
          <a
            className="btn btn--primary"
            href={state.downloadUrl}
            download={state.downloadFilename}
          >
            Download {state.downloadFilename}
          </a>
          <button className="btn btn--secondary" onClick={reset}>Classify another</button>
        </div>
      )}

      <footer className="footer">
        <p>Content Filter v1 &middot; Messages are classified locally and never stored</p>
      </footer>
    </div>
  )
}
