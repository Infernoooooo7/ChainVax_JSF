import { useEffect, useState } from 'react'
import { Boxes, History, Link2, ShieldCheck, Thermometer, UserCheck } from 'lucide-react'
import { api } from '../api'
import {
  Alert,
  Badge,
  Card,
  Empty,
  ErrorAlert,
  ExcursionCountdown,
  Hash,
  StatusChip,
  fmtDateTime,
  fmtDuration,
  usePoll,
} from './ui'

export default function Batches({ session, focusBatchId }) {
  const { data: batches } = usePoll(() => api('/api/batches'), 4000)
  const [selected, setSelected] = useState(focusBatchId || null)

  useEffect(() => {
    if (!selected && batches?.length) setSelected(batches[0].id)
  }, [batches, selected])

  return (
    <div className="grid gap-5 xl:grid-cols-[1.1fr_1fr]">
      <Card title="Vaccine batches (demo data)" icon={Boxes} bodyClass="p-0">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr>
                <th className="th">Batch</th>
                <th className="th">Product / lot</th>
                <th className="th">Expiry</th>
                <th className="th">Location · custodian</th>
                <th className="th">Vials</th>
                <th className="th">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60 [&>tr]:transition-colors [&>tr:hover]:bg-foreground/[0.03]">
              {(batches || []).map((b) => (
                <tr
                  key={b.id}
                  onClick={() => setSelected(b.id)}
                  className={`cursor-pointer ${selected === b.id ? 'bg-accent' : 'hover:bg-foreground/[0.03]'}`}
                >
                  <td className="td font-semibold whitespace-nowrap">{b.batchCode}</td>
                  <td className="td">
                    {b.product}
                    <div className="text-[11px] text-muted-foreground">
                      lot {b.lotNumber} · policy {b.policyCode}
                    </div>
                  </td>
                  <td className="td whitespace-nowrap">{b.expiryDate}</td>
                  <td className="td whitespace-nowrap">
                    {b.siteCode}
                    <div className="text-[11px] text-muted-foreground">{b.custodian}</div>
                  </td>
                  <td className="td">{b.vialsOnHand}</td>
                  <td className="td">
                    <StatusChip status={b.status} />
                    {b.issuable ? (
                      <div className="mt-0.5 text-[10px] text-emerald-700 dark:text-emerald-400">issuable</div>
                    ) : (
                      <div className="mt-0.5 text-[10px] text-rose-600 dark:text-rose-400">not issuable</div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      {selected && <BatchDetail id={selected} session={session} />}
    </div>
  )
}

function BatchDetail({ id, session }) {
  const { data, error, reload } = usePoll(() => api(`/api/batches/${id}`), 4000, [id])
  const { data: revisions } = usePoll(() => api(`/api/audit/batches/${id}/revisions`), 8000, [id])
  const [reason, setReason] = useState('VVM checked: not at discard point; shake test not required (demo)')
  const [releaseError, setReleaseError] = useState(null)
  const [released, setReleased] = useState(null)
  if (error) return <ErrorAlert error={error} />
  if (!data) return null
  const b = data.batch
  const isOfficer = session.user.role === 'DISTRICT_OFFICER'

  async function release() {
    setReleaseError(null)
    setReleased(null)
    try {
      setReleased(await api(`/api/batches/${id}/release`, { method: 'POST', body: { reason } }))
      reload()
    } catch (e) {
      setReleaseError(e)
    }
  }

  return (
    <div className="space-y-4">
      <Card
        title={`${b.batchCode} - ${b.product}`}
        icon={Boxes}
        actions={<StatusChip status={b.status} large />}
      >
        <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm md:grid-cols-3">
          <Field label="Lot">{b.lotNumber}</Field>
          <Field label="Expiry">{b.expiryDate}</Field>
          <Field label="Vials on hand">
            {b.vialsOnHand} / {b.initialVials}
          </Field>
          <Field label="Location">{b.siteName}</Field>
          <Field label="Custodian">{b.custodian}</Field>
          <Field label="Policy">
            {b.policyCode} {b.policyDemoOnly && <Badge tone="amber">demo-only</Badge>}
          </Field>
          <Field label="Issuable">{b.issuable ? 'Yes' : 'No - withdrawn from issuable stock'}</Field>
          <Field label="@Version">{b.version}</Field>
          <Field label="Status changed">{fmtDateTime(b.statusChangedAt)}</Field>
        </dl>
        {b.statusReason && <p className="mt-2 rounded-lg bg-muted/60 px-2.5 py-1.5 text-xs text-muted-foreground">{b.statusReason}</p>}
        <ExcursionCountdown batch={b} />
        {isOfficer && b.status === 'AT_RISK' && (
          <div className="mt-3 rounded-lg border border-amber-200 dark:border-amber-500/30 bg-amber-50 dark:bg-amber-500/10 p-3">
            <div className="flex items-center gap-1.5 text-sm font-semibold text-amber-900 dark:text-amber-200">
              <UserCheck size={16} /> Officer release (AT_RISK → VIABLE, recorded against you)
            </div>
            <input className="input mt-2" value={reason} onChange={(e) => setReason(e.target.value)} />
            <button className="btn-primary mt-2" onClick={release}>
              Release batch
            </button>
            <div className="mt-2">
              <ErrorAlert error={releaseError} />
              {released && <Alert tone="success" title="Released">{released.reason}</Alert>}
            </div>
          </div>
        )}
      </Card>

      <Card title="Lifecycle timeline" icon={ShieldCheck} bodyClass="p-0">
        <ol className="divide-y divide-border/60">
          {data.events.map((e) => (
            <li key={e.id} className="px-4 py-2">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <StatusChip status={e.fromStatus} /> → <StatusChip status={e.toStatus} />
                <span className="text-xs text-muted-foreground">{fmtDateTime(e.occurredAt)}</span>
                <Badge>{e.actor}</Badge>
              </div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                at <b>{e.siteCode}</b>, custodian <b>{e.custodian}</b> - {e.reason}
              </div>
            </li>
          ))}
        </ol>
        {data.events.length === 0 && <Empty>Still VIABLE since registration.</Empty>}
      </Card>

      <Card title="Excursion windows" icon={Thermometer} bodyClass="p-0">
        <table className="w-full">
          <tbody className="divide-y divide-border/60 [&>tr]:transition-colors [&>tr:hover]:bg-foreground/[0.03]">
            {data.excursions.map((w) => (
              <tr key={w.id}>
                <td className="td">
                  <Badge tone={w.type === 'HEAT' ? 'rose' : 'cyan'}>{w.type}</Badge>
                </td>
                <td className="td text-xs">
                  {fmtDateTime(w.startedAt)} → {w.open ? <b className="text-rose-600 dark:text-rose-400">open</b> : fmtDateTime(w.endedAt)}
                  <div className="text-muted-foreground">
                    {w.siteCode} via {w.deviceCode}, custodian {w.custodian}
                  </div>
                </td>
                <td className="td text-xs">
                  {fmtDuration(w.durationSeconds)}
                  <div className="text-muted-foreground">peak {w.peakTemperatureC} °C</div>
                </td>
                <td className="td">{w.outcome && <StatusChip status={w.outcome} />}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data.excursions.length === 0 && <Empty>No excursions recorded.</Empty>}
      </Card>

      <Card title="Custody chain for this batch" icon={Link2} bodyClass="p-0">
        <table className="w-full">
          <tbody className="divide-y divide-border/60 [&>tr]:transition-colors [&>tr:hover]:bg-foreground/[0.03]">
            {data.custody.map((e) => (
              <tr key={e.seq}>
                <td className="td font-mono text-xs">#{e.seq}</td>
                <td className="td text-xs">
                  <b>{e.eventType}</b> {e.fromSiteCode ? `${e.fromSiteCode} → ` : ''}
                  {e.toSiteCode}
                  <div className="text-muted-foreground">
                    {e.fromCustodian ? `${e.fromCustodian} → ` : ''}
                    {e.toCustodian} · {fmtDateTime(e.occurredAt)}
                  </div>
                </td>
                <td className="td">
                  <StatusChip status={e.batchStatus} />
                </td>
                <td className="td">
                  <Hash value={e.hash} length={10} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card title="Envers revision history (who changed this batch)" icon={History} bodyClass="p-0">
        <table className="w-full">
          <tbody className="divide-y divide-border/60 [&>tr]:transition-colors [&>tr:hover]:bg-foreground/[0.03]">
            {(revisions || []).map((r) => (
              <tr key={r.revision}>
                <td className="td font-mono text-xs">rev {r.revision}</td>
                <td className="td text-xs whitespace-nowrap">{fmtDateTime(r.at)}</td>
                <td className="td text-xs">
                  <Badge tone="cyan">{r.username}</Badge>
                </td>
                <td className="td text-xs">
                  {r.revisionType} · <StatusChip status={r.status} /> · {r.vialsOnHand} vials @ {r.siteCode}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  )
}

function Field({ label, children }) {
  return (
    <div>
      <dt className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}
