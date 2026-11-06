import { useEffect, useState } from 'react'
import {
  Boxes,
  FileCode2,
  LayoutDashboard,
  Link2,
  PackageCheck,
  ScrollText,
  ShieldCheck,
  Thermometer,
} from 'lucide-react'
import { api, clearSession, loadSession, login, setUnauthorizedHandler } from './api'
import Login from './components/Login'
import Dashboard from './components/Dashboard'
import Batches from './components/Batches'
import Telemetry from './components/Telemetry'
import Ledger from './components/Ledger'
import Stock from './components/Stock'
import Audit from './components/Audit'
import Access from './components/Access'
import { AppLayout } from './components/medesk/layout'

// Sidebar groups (Medesk layout). Page ids double as #/hash routes.
const GROUPS = [
  {
    label: 'Operations',
    items: [
      { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
      { id: 'batches', label: 'Batches', icon: Boxes },
      { id: 'telemetry', label: 'Telemetry & simulator', icon: Thermometer },
      { id: 'stock', label: 'Stock & FEFO issue', icon: PackageCheck },
    ],
  },
  {
    label: 'Evidence',
    items: [
      { id: 'ledger', label: 'Custody ledger', icon: Link2 },
      { id: 'audit', label: 'Audit trail', icon: ScrollText },
      { id: 'access', label: 'Role access check', icon: ShieldCheck },
    ],
  },
  {
    label: 'Developer',
    items: [{ id: 'swagger', label: 'API docs (Swagger)', icon: FileCode2, href: '/swagger-ui.html' }],
  },
]
const PAGE_IDS = GROUPS.flatMap((g) => g.items).filter((i) => !i.href).map((i) => i.id)

function initialPage() {
  const fromHash = window.location.hash.replace('#/', '')
  return PAGE_IDS.includes(fromHash) ? fromHash : 'dashboard'
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
    if (!u || u.username === user.username) return
    setSession(await login(u.username, u.password))
  }

  function signOut() {
    clearSession()
    setSession(null)
  }

  const props = { session, go }
  return (
    <AppLayout
      groups={GROUPS}
      page={page}
      go={go}
      user={user}
      demoUsers={demoUsers}
      onSwitchUser={switchUser}
      onSignOut={signOut}
    >
      <div className="p-4 md:p-6" key={user.username /* remount pages when switching demo user */}>
        {page === 'dashboard' && <Dashboard {...props} />}
        {page === 'batches' && <Batches {...props} focusBatchId={focusBatchId} />}
        {page === 'telemetry' && <Telemetry {...props} />}
        {page === 'ledger' && <Ledger {...props} />}
        {page === 'stock' && <Stock {...props} />}
        {page === 'audit' && <Audit {...props} />}
        {page === 'access' && <Access {...props} />}
      </div>
    </AppLayout>
  )
}
