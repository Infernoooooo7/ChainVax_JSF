import { useCallback, useEffect, useRef, useState } from 'react'
import { AlertTriangle, CheckCircle2, Copy, Info, XCircle } from 'lucide-react'

export const STATUS_STYLES = {
  VIABLE: 'bg-emerald-100 text-emerald-800 ring-emerald-300',
  AT_RISK: 'bg-amber-100 text-amber-800 ring-amber-300',
  CONDEMNED: 'bg-rose-100 text-rose-800 ring-rose-300',
}

export function StatusChip({ status, large = false }) {
  if (!status) return null
  return (
    <span
      className={`inline-flex items-center rounded-full font-semibold ring-1 ${large ? 'px-3 py-1 text-sm' : 'px-2 py-0.5 text-xs'} ${
        STATUS_STYLES[status] || 'bg-slate-100 text-slate-700 ring-slate-300'
      }`}
    >
      {status.replace('_', ' ')}
    </span>
  )
}

const TONES = {
  slate: 'bg-slate-100 text-slate-700',
  cyan: 'bg-cyan-50 text-cyan-800 ring-1 ring-cyan-200',
  violet: 'bg-violet-50 text-violet-800 ring-1 ring-violet-200',
  amber: 'bg-amber-50 text-amber-800 ring-1 ring-amber-200',
  rose: 'bg-rose-50 text-rose-800 ring-1 ring-rose-200',
  emerald: 'bg-emerald-50 text-emerald-800 ring-1 ring-emerald-200',
}

export function Badge({ children, tone = 'slate', className = '' }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-semibold ${TONES[tone]} ${className}`}>
      {children}
    </span>
  )
}

export function SimulatedBadge() {
  return <Badge tone="violet">SIMULATED</Badge>
}

export function Card({ title, icon: Icon, actions, children, className = '', bodyClass = 'p-4' }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-2.5">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-700">
            {Icon && <Icon size={16} className="text-cyan-700" />}
            {title}
          </h2>
          <div className="flex items-center gap-2">{actions}</div>
        </header>
      )}
      <div className={bodyClass}>{children}</div>
    </section>
  )
}

export function Hash({ value, length = 12, className = '' }) {
  const [copied, setCopied] = useState(false)
  if (!value) return <span className="text-slate-400">-</span>
  return (
    <button
      type="button"
      title={`${value}\n(click to copy)`}
      onClick={() => {
        navigator.clipboard?.writeText(value)
        setCopied(true)
        setTimeout(() => setCopied(false), 1200)
      }}
      className={`inline-flex items-center gap-1 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] text-slate-700 hover:bg-slate-200 ${className}`}
    >
      {value.slice(0, length)}…{copied ? <CheckCircle2 size={11} className="text-emerald-600" /> : <Copy size={11} className="text-slate-400" />}
    </button>
  )
}

const ALERT_TONES = {
  info: ['border-cyan-200 bg-cyan-50 text-cyan-900', Info],
  success: ['border-emerald-200 bg-emerald-50 text-emerald-900', CheckCircle2],
  warn: ['border-amber-200 bg-amber-50 text-amber-900', AlertTriangle],
  error: ['border-rose-200 bg-rose-50 text-rose-900', XCircle],
}

export function Alert({ tone = 'info', title, children, className = '' }) {
  const [style, Icon] = ALERT_TONES[tone]
  return (
    <div className={`flex gap-2 rounded-lg border px-3 py-2 text-sm ${style} ${className}`}>
      <Icon size={18} className="mt-0.5 shrink-0" />
      <div className="min-w-0">
        {title && <div className="font-semibold">{title}</div>}
        {children && <div className="break-words">{children}</div>}
      </div>
    </div>
  )
}

export function ErrorAlert({ error, title }) {
  if (!error) return null
  return (
    <Alert tone="error" title={title || `HTTP ${error.status ?? ''} ${error.code ? '- ' + error.code : ''}`}>
      {error.message}
      {error.data?.errors && (
        <ul className="mt-1 list-disc pl-4">
          {error.data.errors.map((e) => (
            <li key={e.field}>
              {e.field}: {e.message}
            </li>
          ))}
        </ul>
      )}
    </Alert>
  )
}

/** Calls fn now and every `ms` milliseconds; re-subscribes when deps change. */
export function usePoll(fn, ms, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const fnRef = useRef(fn)
  fnRef.current = fn
  const reload = useCallback(async () => {
    try {
      const result = await fnRef.current()
      setData(result)
      setError(null)
      return result
    } catch (e) {
      setError(e)
      return null
    }
  }, [])
  useEffect(() => {
    reload()
    if (!ms) return undefined
    const id = setInterval(reload, ms)
    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ms, reload, ...deps])
  return { data, error, reload }
}

export function useNow(ms = 1000) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms)
    return () => clearInterval(id)
  }, [ms])
  return now
}

export function fmtTime(iso) {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function fmtDateTime(iso) {
  if (!iso) return '-'
  return new Date(iso).toLocaleString([], { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function fmtDuration(totalSeconds) {
  const s = Math.max(0, Math.round(totalSeconds))
  if (s < 60) return `${s}s`
  if (s < 3600) return `${Math.floor(s / 60)}m ${String(s % 60).padStart(2, '0')}s`
  return `${Math.floor(s / 3600)}h ${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}m`
}

/** Live countdown to condemnation for a batch with an open excursion window. */
export function ExcursionCountdown({ batch }) {
  useNow(1000) // re-render every second; read the clock directly because hidden tabs throttle timers
  const now = Date.now()
  const ex = batch?.openExcursion
  if (!ex) return null
  const elapsed = (now - new Date(ex.startedAt).getTime()) / 1000
  const remaining = ex.condemnAfterSeconds - elapsed
  const pct = Math.min(100, Math.max(0, (elapsed / ex.condemnAfterSeconds) * 100))
  const condemned = batch.status === 'CONDEMNED'
  return (
    <div className="mt-1.5">
      <div className="flex justify-between text-[11px] text-slate-600">
        <span>
          {ex.type} excursion for <b>{fmtDuration(elapsed)}</b> (peak {ex.peakTemperatureC} °C)
        </span>
        <span>
          {condemned ? 'condemned' : remaining > 0 ? <>condemns in <b>{fmtDuration(remaining)}</b></> : 'condemn threshold reached - awaiting worker'}
        </span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-200">
        <div className={`h-full ${condemned || pct >= 100 ? 'bg-rose-500' : 'bg-amber-500'}`} style={{ width: `${pct}%` }} />
      </div>
      <div className="mt-0.5 text-[10px] text-slate-500">
        threshold {fmtDuration(ex.condemnAfterSeconds)} under {batch.policyCode}
        {batch.policyDemoOnly ? ' (demo-only, compressed time)' : ''}
      </div>
    </div>
  )
}

export function Empty({ children }) {
  return <div className="px-4 py-6 text-center text-sm text-slate-400">{children}</div>
}

export function roleLabel(role) {
  return (
    {
      STOREKEEPER: 'Storekeeper',
      TRANSPORTER: 'Transporter',
      DISTRICT_OFFICER: 'District officer',
      AUDITOR: 'Auditor',
    }[role] || role
  )
}

export function siteTone(type) {
  return { DISTRICT_STORE: 'cyan', TRANSPORT_VAN: 'violet', HEALTH_CENTRE: 'emerald' }[type] || 'slate'
}
