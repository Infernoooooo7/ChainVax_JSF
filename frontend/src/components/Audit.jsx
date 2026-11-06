import { useState } from 'react'
import { Activity, Inbox, ScrollText, SlidersHorizontal, UserCheck } from 'lucide-react'
import { api } from '../api'
import { Alert, Badge, Card, Empty, ErrorAlert, StatusChip, fmtDateTime, fmtDuration, usePoll } from './ui'
import { ContinuousTabs } from './watermelon/continuous-tabs'

const TABS = [
  { id: 'overrides', label: 'Officer overrides', icon: UserCheck },
  { id: 'events', label: 'Lifecycle events', icon: Activity },
  { id: 'digests', label: 'Expiry / alert digests', icon: Inbox },
  { id: 'policies', label: 'Excursion policies', icon: SlidersHorizontal },
]

export default function Audit({ session }) {
  const [tab, setTab] = useState('overrides')
  return (
    <div className="space-y-4">
      <ContinuousTabs tabs={TABS} value={tab} onChange={setTab} ariaLabel="Audit views" />
      {tab === 'overrides' && <Overrides />}
      {tab === 'events' && <Events />}
      {tab === 'digests' && <Digests session={session} />}
      {tab === 'policies' && <Policies session={session} />}
    </div>
  )
}

function Overrides() {
  const { data } = usePoll(() => api('/api/audit/overrides'), 5000)
  return (
    <Card title="Overrides - each attributed to the officer who made it" icon={UserCheck} bodyClass="p-0">
      <table className="w-full">
        <thead>
          <tr>
            <th className="th">#</th>
            <th className="th">When</th>
            <th className="th">Type</th>
            <th className="th">Officer</th>
            <th className="th">Reason</th>
            <th className="th">What happened</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/60 [&>tr]:transition-colors [&>tr:hover]:bg-foreground/[0.03]">
          {(data || []).map((o) => (
            <tr key={o.id}>
              <td className="td font-mono text-xs">{o.id}</td>
              <td className="td text-xs whitespace-nowrap">{fmtDateTime(o.createdAt)}</td>
              <td className="td">
                <Badge tone={o.type === 'FEFO_OVERRIDE' ? 'amber' : o.type === 'STATUS_RELEASE' ? 'emerald' : 'cyan'}>{o.type}</Badge>
                {o.batchCode && <div className="mt-0.5 text-xs font-semibold">{o.batchCode}</div>}
              </td>
              <td className="td text-xs">
                <b>{o.officerUsername}</b>
                <div className="text-muted-foreground">{o.officerName}</div>
              </td>
              <td className="td text-xs">{o.reason}</td>
              <td className="td text-xs text-muted-foreground">{o.details}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data?.length === 0 && <Empty>No overrides yet. Try an officer override on the Stock page or release an AT-RISK batch.</Empty>}
    </Card>
  )
}

function Events() {
  const { data } = usePoll(() => api('/api/audit/status-events'), 5000)
  return (
    <Card title="Lifecycle transitions (with location and custodian at the time)" icon={Activity} bodyClass="p-0">
      <table className="w-full">
        <tbody className="divide-y divide-border/60 [&>tr]:transition-colors [&>tr:hover]:bg-foreground/[0.03]">
          {(data || []).map((e) => (
            <tr key={e.id}>
              <td className="td text-xs whitespace-nowrap">{fmtDateTime(e.occurredAt)}</td>
              <td className="td font-semibold">{e.batchCode}</td>
              <td className="td whitespace-nowrap">
                <StatusChip status={e.fromStatus} /> → <StatusChip status={e.toStatus} />
              </td>
              <td className="td text-xs">
                {e.siteCode} · <b>{e.custodian}</b>
                <div className="text-muted-foreground/80">{e.actor}</div>
              </td>
              <td className="td text-xs text-muted-foreground">{e.reason}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data?.length === 0 && <Empty>No transitions yet.</Empty>}
    </Card>
  )
}

function Digests({ session }) {
  const { data, reload } = usePoll(() => api('/api/digests'), 10000)
  const [error, setError] = useState(null)
  return (
    <Card
      title="Digests dispatched by the @Scheduled worker (in-app inbox)"
      icon={Inbox}
      actions={
        session.user.role === 'DISTRICT_OFFICER' && (
          <button
            className="btn-primary"
            onClick={async () => {
              setError(null)
              try {
                await api('/api/digests/run', { method: 'POST' })
                reload()
              } catch (e) {
                setError(e)
              }
            }}
          >
            Run digest now
          </button>
        )
      }
    >
      <ErrorAlert error={error} />
      <div className="space-y-3">
        {(data || []).map((d) => (
          <div key={d.id} className="rounded-lg border border-border p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="text-sm font-semibold">{d.headline}</div>
              <div className="text-xs text-muted-foreground">
                <Badge>{d.triggerSource}</Badge> {fmtDateTime(d.generatedAt)}
              </div>
            </div>
            <pre className="mt-2 max-h-56 overflow-auto rounded bg-muted/60 p-2 text-[11px] whitespace-pre-wrap">{d.body}</pre>
          </div>
        ))}
        {data?.length === 0 && <Empty>The first scheduled digest appears ~30 s after start-up, then every 5 minutes.</Empty>}
      </div>
    </Card>
  )
}

function Policies({ session }) {
  const { data, reload } = usePoll(() => api('/api/policies'), 0)
  const isOfficer = session.user.role === 'DISTRICT_OFFICER'
  return (
    <div className="space-y-4">
      <Alert tone="warn" title="Thresholds are configurable and clearly labelled">
        DEMO-FAST compresses time for the presentation. PQS-E006-T1 mirrors the WHO PQS E006/TR06.4 Type 1 logger alarm settings
        (+8 °C or above for 10 h; -0.5 °C or below for 60 min). Automatically condemning at an alarm is a ChainVax design choice, not
        WHO discard guidance.
      </Alert>
      {(data || []).map((p) => (
        <PolicyCard key={p.id} policy={p} isOfficer={isOfficer} onSaved={reload} />
      ))}
    </div>
  )
}

function PolicyCard({ policy, isOfficer, onSaved }) {
  const [form, setForm] = useState({
    heatAlarmC: policy.heatAlarmC,
    coldAlarmC: policy.coldAlarmC,
    atRiskAfterSeconds: policy.atRiskAfterSeconds,
    heatCondemnAfterSeconds: policy.heatCondemnAfterSeconds,
    coldCondemnAfterSeconds: policy.coldCondemnAfterSeconds,
    reason: '',
  })
  const [error, setError] = useState(null)
  const [saved, setSaved] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function save(e) {
    e.preventDefault()
    setError(null)
    setSaved(false)
    try {
      await api(`/api/policies/${policy.id}`, {
        method: 'PUT',
        body: {
          heatAlarmC: Number(form.heatAlarmC),
          coldAlarmC: Number(form.coldAlarmC),
          atRiskAfterSeconds: Number(form.atRiskAfterSeconds),
          heatCondemnAfterSeconds: Number(form.heatCondemnAfterSeconds),
          coldCondemnAfterSeconds: Number(form.coldCondemnAfterSeconds),
          reason: form.reason,
          expectedVersion: policy.version,
        },
      })
      setSaved(true)
      onSaved()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <Card
      title={`${policy.code} - ${policy.name}`}
      icon={ScrollText}
      actions={policy.demoOnly ? <Badge tone="amber">DEMO ONLY</Badge> : <Badge tone="cyan">reference</Badge>}
    >
      <p className="text-xs text-muted-foreground">{policy.basis}</p>
      <div className="mt-2 flex flex-wrap gap-2 text-xs">
        <Badge tone="rose">heat ≥ {policy.heatAlarmC} °C → condemn after {fmtDuration(policy.heatCondemnAfterSeconds)}</Badge>
        <Badge tone="cyan">cold ≤ {policy.coldAlarmC} °C → condemn after {fmtDuration(policy.coldCondemnAfterSeconds)}</Badge>
        <Badge tone="amber">AT RISK after {fmtDuration(policy.atRiskAfterSeconds)}</Badge>
        <Badge>@Version {policy.version}</Badge>
        {policy.updatedBy && (
          <Badge>
            last changed by {policy.updatedBy} {fmtDateTime(policy.updatedAt)}
          </Badge>
        )}
      </div>
      {isOfficer && (
        <form className="mt-3 grid grid-cols-2 gap-2 border-t border-border/60 pt-3 md:grid-cols-5" onSubmit={save}>
          <div>
            <label className="label">Heat alarm °C</label>
            <input className="input" value={form.heatAlarmC} onChange={set('heatAlarmC')} />
          </div>
          <div>
            <label className="label">Cold alarm °C</label>
            <input className="input" value={form.coldAlarmC} onChange={set('coldAlarmC')} />
          </div>
          <div>
            <label className="label">At-risk after s</label>
            <input className="input" value={form.atRiskAfterSeconds} onChange={set('atRiskAfterSeconds')} />
          </div>
          <div>
            <label className="label">Heat condemn s</label>
            <input className="input" value={form.heatCondemnAfterSeconds} onChange={set('heatCondemnAfterSeconds')} />
          </div>
          <div>
            <label className="label">Cold condemn s</label>
            <input className="input" value={form.coldCondemnAfterSeconds} onChange={set('coldCondemnAfterSeconds')} />
          </div>
          <div className="col-span-2 md:col-span-4">
            <label className="label">Reason for change (recorded against you)</label>
            <input className="input" value={form.reason} onChange={set('reason')} placeholder="min. 10 characters" />
          </div>
          <div className="flex items-end">
            <button className="btn-primary w-full">Save policy</button>
          </div>
          <div className="col-span-2 md:col-span-5">
            <ErrorAlert error={error} />
            {saved && <Alert tone="success">Saved and recorded as a POLICY_CHANGE override.</Alert>}
          </div>
        </form>
      )}
    </Card>
  )
}
