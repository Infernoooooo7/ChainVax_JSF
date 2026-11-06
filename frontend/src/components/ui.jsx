import { useCallback, useEffect, useRef, useState } from 'react'
import { motion } from 'motion/react'
import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { CopyToast } from './watermelon/copy-toast'
import { LabeledProgress } from './watermelon/labeled-progress'

// Status pills in the style of Medesk's appointment-status chips (dot + label), colours from its palette.
export const STATUS_STYLES = {
  VIABLE: 'border-emerald-500/25 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 [--dot:var(--color-viable)]',
  AT_RISK: 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300 [--dot:var(--color-at-risk)]',
  CONDEMNED: 'border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-300 [--dot:var(--color-condemned)]',
}

export function StatusChip({ status, large = false }) {
  if (!status) return null
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border font-medium whitespace-nowrap',
        large ? 'px-3 py-1 text-sm' : 'px-2 py-0.5 text-xs',
        STATUS_STYLES[status] || 'border-border bg-muted text-muted-foreground [--dot:var(--color-muted-foreground)]',
      )}
    >
      <span className="size-1.5 shrink-0 rounded-full bg-(--dot)" />
      {status.replace('_', ' ')}
    </span>
  )
}

const TONES = {
  slate: 'bg-muted text-muted-foreground',
  cyan: 'bg-sky-500/10 text-sky-700 dark:text-sky-300',
  violet: 'bg-violet-500/10 text-violet-700 dark:text-violet-300',
  amber: 'bg-amber-500/10 text-amber-700 dark:text-amber-300',
  rose: 'bg-rose-500/10 text-rose-700 dark:text-rose-300',
  emerald: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
}

export function Badge({ children, tone = 'slate', className = '' }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap',
        TONES[tone] ?? TONES.slate,
        className,
      )}
    >
      {children}
    </span>
  )
}

export function SimulatedBadge() {
  return <Badge tone="violet">SIMULATED</Badge>
}

// Medesk "DashboardCard" + "CardHeader": borderless soft card, medium-weight title, actions on the right.
export function Card({ title, icon: Icon, actions, children, className = '', bodyClass }) {
  const hasHeader = Boolean(title || actions)
  return (
    <section className={cn('card min-w-0', className)}>
      {hasHeader && (
        <header className="flex flex-wrap items-center justify-between gap-3 px-4 pt-4 pb-3">
          <h2 className="flex min-w-0 items-center gap-2 text-base leading-6 font-medium text-foreground">
            {Icon && <Icon size={17} className="shrink-0 text-muted-foreground" />}
            <span className="min-w-0">{title}</span>
          </h2>
          {actions && <div className="flex items-center gap-2 text-sm font-medium text-foreground/80">{actions}</div>}
        </header>
      )}
      <div className={bodyClass ?? (hasHeader ? 'px-4 pb-4' : 'p-4')}>{children}</div>
    </section>
  )
}

export function Hash({ value, length = 12, className = '' }) {
  if (!value) return <span className="text-muted-foreground">-</span>
  return <CopyToast value={value} display={`${value.slice(0, length)}…`} className={className} />
}

const ALERT_TONES = {
  info: ['border-border bg-muted/50 text-foreground', Info, 'text-sky-600 dark:text-sky-400'],
  success: ['border-emerald-500/25 bg-emerald-500/10 text-emerald-900 dark:text-emerald-200', CheckCircle2, ''],
  warn: ['border-amber-500/30 bg-amber-500/10 text-amber-900 dark:text-amber-200', AlertTriangle, ''],
  error: ['border-rose-500/30 bg-rose-500/10 text-rose-900 dark:text-rose-200', XCircle, ''],
}

// Entrance uses the blur/scale swap from Watermelon UI's Inline Toast.
export function Alert({ tone = 'info', title, children, className = '' }) {
  const [style, Icon, iconClass] = ALERT_TONES[tone]
  return (
    <motion.div
      initial={{ opacity: 0, filter: 'blur(4px)', scale: 0.98 }}
      animate={{ opacity: 1, filter: 'blur(0px)', scale: 1 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.4 }}
      className={cn('flex gap-2.5 rounded-lg border px-3 py-2.5 text-sm', style, className)}
    >
      <Icon size={17} className={cn('mt-0.5 shrink-0', iconClass)} />
      <div className="min-w-0">
        {title && <div className="font-medium">{title}</div>}
        {children && <div className="break-words opacity-90">{children}</div>}
      </div>
    </motion.div>
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

/** Live countdown to condemnation for a batch with an open excursion window (Watermelon Labeled Progress). */
export function ExcursionCountdown({ batch }) {
  useNow(1000) // re-render every second; read the clock directly because hidden tabs throttle timers
  const now = Date.now()
  const ex = batch?.openExcursion
  if (!ex) return null
  const elapsed = (now - new Date(ex.startedAt).getTime()) / 1000
  const remaining = ex.condemnAfterSeconds - elapsed
  const pct = Math.min(100, Math.max(0, (elapsed / ex.condemnAfterSeconds) * 100))
  const condemned = batch.status === 'CONDEMNED'
  const end = condemned ? 'condemned' : remaining > 0 ? `condemns in ${fmtDuration(remaining)}` : 'threshold reached - awaiting worker'
  const labels = [
    `${ex.type} excursion for ${fmtDuration(elapsed)}`,
    `peak ${ex.peakTemperatureC} °C`,
    `threshold ${fmtDuration(ex.condemnAfterSeconds)} under ${batch.policyCode}${batch.policyDemoOnly ? ' (demo-only, compressed time)' : ''}`,
  ]
  return <LabeledProgress labels={labels} value={pct} tone={condemned || pct >= 100 ? 'danger' : 'warning'} end={end} />
}

export function Empty({ children }) {
  return <div className="px-4 py-6 text-center text-sm text-muted-foreground">{children}</div>
}

export function roleLabel(role) {
  return (
    {
      STOREKEEPER: 'Storekeeper',
      TRANSPORTER: 'Transporter',
      DISTRICT_OFFICER: 'District officer',
      AUDITOR: 'Auditor',
      ADMIN: 'Administrator',
    }[role] || role
  )
}

export function siteTone(type) {
  return { DISTRICT_STORE: 'cyan', TRANSPORT_VAN: 'violet', HEALTH_CENTRE: 'emerald' }[type] || 'slate'
}
