import { useState } from 'react'
import { CheckCircle2, Lock, PlayCircle, ShieldCheck, XCircle } from 'lucide-react'
import { api } from '../api'
import { Alert, Badge, Card, roleLabel } from './ui'

const MATRIX = [
  ['View batches, telemetry, ledger, audit trail', ['STOREKEEPER', 'TRANSPORTER', 'DISTRICT_OFFICER', 'AUDITOR']],
  ['Verify the custody hash chain', ['STOREKEEPER', 'TRANSPORTER', 'DISTRICT_OFFICER', 'AUDITOR']],
  ['Record custody transfer (own site only)', ['STOREKEEPER', 'TRANSPORTER']],
  ['Record custody transfer (any site)', ['DISTRICT_OFFICER']],
  ['Issue stock FEFO (own site only)', ['STOREKEEPER']],
  ['Issue stock anywhere / override FEFO', ['DISTRICT_OFFICER']],
  ['Release AT_RISK batch, change policy, run digest', ['DISTRICT_OFFICER']],
  ['Drive the logger simulator', ['STOREKEEPER', 'TRANSPORTER', 'DISTRICT_OFFICER']],
  ['Post logger readings', ['device token (role LOGGER) only']],
]
const ROLES = ['STOREKEEPER', 'TRANSPORTER', 'DISTRICT_OFFICER', 'AUDITOR']

/** Probes chosen to have no side effects: allowed roles reach the business rules and are stopped there. */
function probes(ctx) {
  const { mr1, dvs } = ctx
  return [
    { label: 'GET /api/batches', run: () => api('/api/batches', { raw: true }) },
    { label: 'GET /api/ledger/verify', run: () => api('/api/ledger/verify', { raw: true }) },
    {
      label: 'POST /api/custody/transfers (batch to the site it is already at)',
      run: () =>
        api('/api/custody/transfers', {
          method: 'POST',
          raw: true,
          body: { batchId: mr1.id, toSiteId: mr1.siteId, toCustodian: 'officer', note: 'access probe' },
        }),
    },
    {
      label: 'POST /api/stock/issue (FEFO, product that does not exist)',
      run: () => api('/api/stock/issue', { method: 'POST', raw: true, body: { siteId: dvs, product: 'probe-product', vials: 1 } }),
    },
    {
      label: 'PUT /api/policies/1 (stale @Version, so nothing changes)',
      run: () =>
        api('/api/policies/1', {
          method: 'PUT',
          raw: true,
          body: {
            heatAlarmC: 8,
            coldAlarmC: -0.5,
            atRiskAfterSeconds: 0,
            heatCondemnAfterSeconds: 90,
            coldCondemnAfterSeconds: 60,
            reason: 'access probe - no change',
            expectedVersion: -1,
          },
        }),
    },
    {
      label: 'POST /api/telemetry/readings with a staff JWT',
      run: () =>
        api('/api/telemetry/readings', {
          method: 'POST',
          raw: true,
          body: { deviceCode: 'LOG-VAN-01', readings: [{ recordedAt: new Date().toISOString(), temperatureC: 5 }] },
        }),
    },
  ]
}

function interpret(r) {
  if (r.status === 403) return ['denied', `403 ${r.data?.code || 'Forbidden'} - blocked by Spring Security / scope check`]
  if (r.status === 401) return ['denied', '401 - not authenticated']
  if (r.status >= 200 && r.status < 300) return ['allowed', `${r.status} OK`]
  return ['allowed', `${r.status} ${r.data?.code || ''} - role permitted; stopped by a business rule (no change made)`]
}

export default function Access({ session }) {
  const role = session.user.role
  const [results, setResults] = useState({})
  const [running, setRunning] = useState(false)

  async function runAll() {
    setRunning(true)
    const batches = await api('/api/batches')
    const sites = await api('/api/sites')
    const ctx = { mr1: batches.find((b) => b.batchCode === 'CVX-MR-001') || batches[0], dvs: sites.find((s) => s.code === 'DVS-UDP')?.id }
    const out = {}
    for (const p of probes(ctx)) {
      out[p.label] = interpret(await p.run())
      setResults({ ...out })
    }
    setRunning(false)
  }

  return (
    <div className="space-y-5">
      <Card title="Role-based access (enforced on the server with @PreAuthorize + data-level checks)" icon={ShieldCheck} bodyClass="p-0">
        <table className="w-full">
          <thead className="bg-slate-50">
            <tr>
              <th className="th">Action</th>
              {ROLES.map((r) => (
                <th key={r} className={`th text-center ${r === role ? 'bg-cyan-50 text-cyan-800' : ''}`}>
                  {roleLabel(r)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {MATRIX.map(([action, allowed]) => (
              <tr key={action}>
                <td className="td">{action}</td>
                {ROLES.map((r) => (
                  <td key={r} className={`td text-center ${r === role ? 'bg-cyan-50/60' : ''}`}>
                    {allowed.includes(r) ? (
                      <CheckCircle2 size={16} className="inline text-emerald-600" />
                    ) : (
                      <Lock size={14} className="inline text-slate-300" />
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card
        title={`Live check as ${session.user.username} (${roleLabel(role)})`}
        icon={PlayCircle}
        actions={
          <button className="btn-primary" onClick={runAll} disabled={running}>
            <PlayCircle size={14} /> {running ? 'Running…' : 'Run real API calls'}
          </button>
        }
      >
        <Alert tone="info" className="mb-3">
          These calls use your JWT against the real endpoints. Each request is built so that, if your role is allowed, a business rule
          stops it before anything changes - so you can see the difference between <b>403 (role denied)</b> and{' '}
          <b>permitted but rejected</b>. Switch users in the sidebar and run again.
        </Alert>
        <ul className="divide-y divide-slate-100">
          {probes({ mr1: {}, dvs: 0 }).map((p) => {
            const r = results[p.label]
            return (
              <li key={p.label} className="flex items-start justify-between gap-3 py-2 text-sm">
                <code className="text-xs">{p.label}</code>
                {r ? (
                  <span className={`flex items-center gap-1 text-right text-xs ${r[0] === 'denied' ? 'text-rose-700' : 'text-emerald-700'}`}>
                    {r[0] === 'denied' ? <XCircle size={14} /> : <CheckCircle2 size={14} />}
                    {r[1]}
                  </span>
                ) : (
                  <Badge>not run</Badge>
                )}
              </li>
            )
          })}
        </ul>
      </Card>
    </div>
  )
}
