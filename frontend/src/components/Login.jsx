import { useEffect, useState } from 'react'
import { Snowflake, LogIn, ShieldCheck, Truck, Warehouse, ClipboardCheck, Stethoscope } from 'lucide-react'
import { api, login } from '../api'
import { ErrorAlert, roleLabel } from './ui'

const ROLE_ICONS = {
  STOREKEEPER: Warehouse,
  TRANSPORTER: Truck,
  DISTRICT_OFFICER: ShieldCheck,
  AUDITOR: ClipboardCheck,
}

export default function Login({ onLogin }) {
  const [users, setUsers] = useState([])
  const [username, setUsername] = useState('officer')
  const [password, setPassword] = useState('demo1234')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

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
    <div className="flex min-h-full items-center justify-center bg-gradient-to-br from-slate-900 via-slate-800 to-cyan-950 p-6">
      <div className="grid w-full max-w-5xl gap-6 md:grid-cols-[1.1fr_1fr]">
        <div className="flex flex-col justify-center text-slate-100">
          <div className="flex items-center gap-3">
            <div className="rounded-xl bg-cyan-500/20 p-2.5 ring-1 ring-cyan-400/40">
              <Snowflake className="text-cyan-300" size={30} />
            </div>
            <div>
              <h1 className="text-3xl font-bold tracking-tight">ChainVax</h1>
              <p className="text-sm text-cyan-200/80">Cold-chain custody &amp; excursion ledger for vaccine supply</p>
            </div>
          </div>
          <ul className="mt-8 space-y-3 text-sm text-slate-300">
            <li>• Temperature readings arrive through the same API a real data logger would use (here: a built-in simulator).</li>
            <li>• Configurable excursion policies move batches VIABLE → AT RISK → CONDEMNED automatically; condemned stock leaves issuable stock.</li>
            <li>• Every custody hand-over is appended to a SHA-256 hash-chained ledger that anyone can verify.</li>
            <li>• Spring Security + JWT with four roles; officer overrides are recorded against the officer.</li>
          </ul>
          <p className="mt-8 rounded-lg border border-amber-400/30 bg-amber-400/10 px-3 py-2 text-xs text-amber-100">
            Academic demo (NMAMIT, Java &amp; Spring Framework Lab). All sites, batches and readings are simulated demo data; demo
            policy thresholds are illustrative and are not medical guidance.
          </p>
        </div>

        <div className="card p-5">
          <h2 className="flex items-center gap-2 text-base font-semibold">
            <Stethoscope size={18} className="text-cyan-700" /> Sign in as a demo user
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            One click signs in. Password for every demo account: <code className="rounded bg-slate-100 px-1">demo1234</code>
          </p>
          <div className="mt-4 space-y-2">
            {users.map((u) => {
              const Icon = ROLE_ICONS[u.role] || ShieldCheck
              return (
                <button
                  key={u.username}
                  disabled={busy}
                  onClick={() => doLogin(u.username, u.password)}
                  className="flex w-full cursor-pointer items-center gap-3 rounded-lg border border-slate-200 px-3 py-2 text-left hover:border-cyan-400 hover:bg-cyan-50"
                >
                  <Icon size={20} className="shrink-0 text-cyan-700" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-sm font-semibold">
                      {u.username}
                      <span className="rounded bg-slate-100 px-1.5 text-[11px] font-medium text-slate-600">{roleLabel(u.role)}</span>
                      {u.siteCode && <span className="text-[11px] text-slate-500">@ {u.siteCode}</span>}
                    </div>
                    <div className="truncate text-xs text-slate-500">{u.summary}</div>
                  </div>
                  <LogIn size={16} className="text-slate-400" />
                </button>
              )
            })}
            {users.length === 0 && <p className="text-sm text-slate-500">Demo users unavailable - is the backend running?</p>}
          </div>
          <form
            className="mt-5 grid grid-cols-[1fr_1fr_auto] items-end gap-2 border-t border-slate-100 pt-4"
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
        </div>
      </div>
    </div>
  )
}
