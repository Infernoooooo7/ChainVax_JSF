import { useEffect, useState } from 'react'
import {
  ClipboardCheck,
  Link2,
  LogIn,
  MoonIcon,
  ShieldCheck,
  Snowflake,
  SunIcon,
  Thermometer,
  Truck,
  Warehouse,
  KeyRound,
} from 'lucide-react'
import { api, login } from '../api'
import { Button } from '@/components/ui/button'
import { ErrorAlert, roleLabel } from './ui'
import { useTheme } from './medesk/theme-provider'

const ROLE_ICONS = {
  STOREKEEPER: Warehouse,
  TRANSPORTER: Truck,
  DISTRICT_OFFICER: ShieldCheck,
  AUDITOR: ClipboardCheck,
}

const POINTS = [
  [Thermometer, 'Temperature readings arrive through the same API a real data logger would use (here: a built-in simulator).'],
  [ShieldCheck, 'Configurable excursion policies move batches VIABLE → AT RISK → CONDEMNED automatically; condemned stock leaves issuable stock.'],
  [Link2, 'Every custody hand-over is appended to a SHA-256 hash-chained ledger that anyone can verify.'],
  [KeyRound, 'Spring Security + JWT with four roles; officer overrides are recorded against the officer.'],
]

export default function Login({ onLogin }) {
  const [users, setUsers] = useState([])
  const [username, setUsername] = useState('officer')
  const [password, setPassword] = useState('demo1234')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const { resolvedTheme, setTheme } = useTheme()
  const isDark = resolvedTheme === 'dark'

  useEffect(() => {
    api('/api/auth/demo-users', { token: '' }).then(setUsers).catch(() => setUsers([]))
  }, [])

  async function doLogin(u, p) {
    setBusy(true)
    setError(null)
    try {
      onLogin(await login(u, p))
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-sidebar p-4 md:p-8">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-2xl bg-background shadow-xs md:grid-cols-[1.05fr_1fr]">
        <div className="flex flex-col justify-between gap-8 bg-card p-6 md:p-8">
          <div className="flex items-center gap-2.5">
            <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <Snowflake className="size-5" />
            </div>
            <div className="leading-tight">
              <div className="text-xl font-medium tracking-tight">ChainVax</div>
              <div className="text-xs text-muted-foreground">cold-chain ledger · demo</div>
            </div>
          </div>
          <div>
            <h1 className="font-serif text-4xl leading-tight tracking-tight md:text-[2.75rem]">
              Cold-chain custody &amp; excursion ledger for vaccine supply
            </h1>
            <ul className="mt-6 space-y-4">
              {POINTS.map(([Icon, text]) => (
                <li key={text} className="flex gap-3 text-sm text-foreground/80">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
                    <Icon className="size-4" />
                  </span>
                  <span className="pt-1.5">{text}</span>
                </li>
              ))}
            </ul>
          </div>
          <p className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-900 dark:text-amber-200">
            Academic demo (NMAMIT, Java &amp; Spring Framework Lab). All sites, batches and readings are simulated demo data; demo
            policy thresholds are illustrative and are not medical guidance.
          </p>
        </div>

        <div className="p-6 md:p-8">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-medium">Sign in as a demo user</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                One click signs in. Password for every demo account:{' '}
                <code className="rounded bg-muted px-1 font-mono">demo1234</code>
              </p>
            </div>
            <Button
              variant="ghost"
              size="icon"
              aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
              onClick={() => setTheme(isDark ? 'light' : 'dark')}
            >
              {isDark ? <SunIcon className="size-4" /> : <MoonIcon className="size-4" />}
            </Button>
          </div>
          <div className="mt-5 space-y-2">
            {users.map((u) => {
              const Icon = ROLE_ICONS[u.role] || ShieldCheck
              return (
                <button
                  key={u.username}
                  disabled={busy}
                  onClick={() => doLogin(u.username, u.password)}
                  className="flex w-full cursor-pointer items-center gap-3 rounded-lg border border-border/60 px-3 py-2.5 text-left transition-colors hover:border-foreground/20 hover:bg-foreground/[0.03] disabled:opacity-60"
                >
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/80">
                    <Icon className="size-4.5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-sm font-medium">
                      {u.username}
                      <span className="rounded-md bg-muted px-1.5 text-[11px] font-medium text-muted-foreground">{roleLabel(u.role)}</span>
                      {u.siteCode && <span className="text-[11px] text-muted-foreground">@ {u.siteCode}</span>}
                    </div>
                    <div className="truncate text-xs text-muted-foreground">{u.summary}</div>
                  </div>
                  <LogIn size={16} className="text-muted-foreground" />
                </button>
              )
            })}
            {users.length === 0 && <p className="text-sm text-muted-foreground">Demo users unavailable - is the backend running?</p>}
          </div>
          <form
            className="mt-5 grid grid-cols-[1fr_1fr_auto] items-end gap-2 border-t border-border/60 pt-4"
            onSubmit={(e) => {
              e.preventDefault()
              doLogin(username, password)
            }}
          >
            <div>
              <label className="label">Username</label>
              <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} />
            </div>
            <div>
              <label className="label">Password</label>
              <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            <button className="btn-primary" disabled={busy}>
              Sign in
            </button>
          </form>
          <div className="mt-3">
            <ErrorAlert error={error} title="Sign-in failed" />
          </div>
          <p className="mt-4 border-t border-border/60 pt-3 text-xs text-muted-foreground">
            Administrator? Accounts and master data are managed in the separate{' '}
            <a className="font-medium text-foreground underline-offset-4 hover:underline" href="/admin/">
              admin console
            </a>
            .
          </p>
        </div>
      </div>
    </div>
  )
}
