import { useEffect, useState } from 'react'
import {
  Boxes,
  ExternalLink,
  LayoutDashboard,
  Link2,
  LogOut,
  PackageCheck,
  ScrollText,
  ShieldCheck,
  Snowflake,
  Thermometer,
  UserRoundCog,
} from 'lucide-react'
import { api, clearSession, loadSession, login, setUnauthorizedHandler } from './api'
import { Badge, roleLabel } from './components/ui'
import Login from './components/Login'
import Dashboard from './components/Dashboard'
import Batches from './components/Batches'
import Telemetry from './components/Telemetry'
import Ledger from './components/Ledger'
import Stock from './components/Stock'
import Audit from './components/Audit'
import Access from './components/Access'

const PAGES = [
  { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { id: 'batches', label: 'Batches', icon: Boxes },
  { id: 'telemetry', label: 'Telemetry & simulator', icon: Thermometer },
  { id: 'ledger', label: 'Custody ledger', icon: Link2 },
  { id: 'stock', label: 'Stock & FEFO issue', icon: PackageCheck },
  { id: 'audit', label: 'Audit trail', icon: ScrollText },
  { id: 'access', label: 'Role access check', icon: ShieldCheck },
]

function initialPage() {
  const fromHash = window.location.hash.replace('#/', '')
  return PAGES.some((p) => p.id === fromHash) ? fromHash : 'dashboard'
}

export default function App() {
  const [session, setSession] = useState(loadSession())
  const [page, setPage] = useState(initialPage)
  const [focusBatchId, setFocusBatchId] = useState(null)
  const [demoUsers, setDemoUsers] = useState([])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession()
      setSession(null)
    })
    api('/api/auth/demo-users', { token: '' }).then(setDemoUsers).catch(() => {})
  }, [])

  useEffect(() => {
    window.location.hash = '/' + page
  }, [page])

  if (!session) return <Login onLogin={setSession} />

  const user = session.user
  const go = (target, batchId) => {
    if (batchId) setFocusBatchId(batchId)
    setPage(target)
  }

  async function switchUser(username) {
    const u = demoUsers.find((d) => d.username === username)
    if (!u) return
    setSession(await login(u.username, u.password))
  }

  const props = { session, go }
  return (
    <div className="flex min-h-full">
      <aside className="sticky top-0 flex h-screen w-60 shrink-0 flex-col bg-slate-900 text-slate-300">
        <div className="flex items-center gap-2 px-4 py-4">
          <div className="rounded-lg bg-cyan-500/20 p-1.5 ring-1 ring-cyan-400/40">
            <Snowflake size={20} className="text-cyan-300" />
          </div>
          <div>
            <div className="text-base font-bold text-white">ChainVax</div>
            <div className="text-[10px] tracking-wide text-cyan-300/80 uppercase">cold-chain ledger · demo</div>
          </div>
        </div>
        <nav className="flex-1 space-y-0.5 px-2">
          {PAGES.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              onClick={() => go(id)}
              className={`flex w-full cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-sm ${
                page === id ? 'bg-cyan-600/20 font-semibold text-white ring-1 ring-cyan-500/40' : 'hover:bg-slate-800 hover:text-white'
              }`}
            >
              <Icon size={16} /> {label}
            </button>
          ))}
          <a
            href="/swagger-ui.html"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm hover:bg-slate-800 hover:text-white"
          >
            <ExternalLink size={16} /> API docs (Swagger)
          </a>
        </nav>
        <div className="border-t border-slate-800 p-3">
          <div className="text-sm font-semibold text-white">{user.username}</div>
          <div className="text-xs text-slate-400">{user.fullName}</div>
          <div className="mt-1.5 flex flex-wrap gap-1">
            <Badge tone="cyan">{roleLabel(user.role)}</Badge>
            {user.siteCode && <Badge>{user.siteCode}</Badge>}
          </div>
          <label className="mt-3 mb-1 flex items-center gap-1 text-[10px] font-semibold tracking-wide text-slate-500 uppercase">
            <UserRoundCog size={12} /> Switch demo user
          </label>
          <select
            className="w-full rounded-md border border-slate-700 bg-slate-800 px-2 py-1 text-xs text-slate-200"
            value={user.username}
            onChange={(e) => switchUser(e.target.value)}
          >
            {demoUsers.map((d) => (
              <option key={d.username} value={d.username}>
                {d.username} ({roleLabel(d.role)})
              </option>
            ))}
          </select>
          <button
            className="mt-2 flex w-full cursor-pointer items-center justify-center gap-1.5 rounded-md px-2 py-1 text-xs text-slate-400 hover:bg-slate-800 hover:text-white"
            onClick={() => {
              clearSession()
              setSession(null)
            }}
          >
            <LogOut size={13} /> Sign out
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1">
        <div className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
          <h1 className="text-lg font-semibold text-slate-800">{PAGES.find((p) => p.id === page)?.label}</h1>
          <div className="flex items-center gap-2 text-[11px]">
            <Badge tone="violet">Simulated logger data</Badge>
            <Badge tone="amber">Demo thresholds - not medical guidance</Badge>
          </div>
        </div>
        <div className="p-6" key={user.username /* remount pages when switching demo user */}>
          {page === 'dashboard' && <Dashboard {...props} />}
          {page === 'batches' && <Batches {...props} focusBatchId={focusBatchId} />}
          {page === 'telemetry' && <Telemetry {...props} />}
          {page === 'ledger' && <Ledger {...props} />}
          {page === 'stock' && <Stock {...props} />}
          {page === 'audit' && <Audit {...props} />}
          {page === 'access' && <Access {...props} />}
        </div>
      </main>
    </div>
  )
}
