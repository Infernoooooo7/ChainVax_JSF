import { Activity, Boxes, Database, Hospital, Link2, ShieldAlert, ShieldCheck, Skull, Thermometer, Truck, Warehouse } from 'lucide-react'
import { api } from '../api'
import { Badge, Card, Empty, ExcursionCountdown, Hash, StatusChip, fmtDateTime, fmtTime, usePoll } from './ui'

const SITE_ICONS = { DISTRICT_STORE: Warehouse, TRANSPORT_VAN: Truck, HEALTH_CENTRE: Hospital }

function tempTone(state) {
  return (
    {
      IN_RANGE: 'text-emerald-700',
      HEAT: 'text-rose-600',
      FREEZE: 'text-blue-700',
      BELOW_RANGE: 'text-amber-600',
      STALE: 'text-slate-400',
    }[state] || 'text-slate-500'
  )
}

function Kpi({ icon: Icon, label, value, tone = 'text-slate-800', sub }) {
  return (
    <div className="card px-4 py-3">
      <div className="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-slate-500 uppercase">
        <Icon size={14} /> {label}
      </div>
      <div className={`mt-1 text-2xl font-bold ${tone}`}>{value ?? '-'}</div>
      {sub && <div className="text-[11px] text-slate-500">{sub}</div>}
    </div>
  )
}

export default function Dashboard({ go }) {
  const { data: d } = usePoll(() => api('/api/dashboard'), 4000)
  const { data: devices } = usePoll(() => api('/api/telemetry/devices'), 4000)
  const { data: batches } = usePoll(() => api('/api/batches'), 4000)
  const { data: verify } = usePoll(() => api('/api/ledger/verify'), 8000)
  const { data: events } = usePoll(() => api('/api/audit/status-events'), 5000)
  const { data: digests } = usePoll(() => api('/api/digests'), 15000)
  const { data: info } = usePoll(() => api('/api/system/info'), 60000)

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
        <Kpi icon={Boxes} label="Batches" value={d?.totalBatches} />
        <Kpi icon={ShieldCheck} label="Viable" value={d?.viable} tone="text-emerald-700" />
        <Kpi icon={ShieldAlert} label="At risk" value={d?.atRisk} tone="text-amber-600" />
        <Kpi icon={Skull} label="Condemned" value={d?.condemned} tone="text-rose-600" />
        <Kpi icon={Boxes} label="Issuable vials" value={d?.issuableVials} sub={d ? `${d.withdrawnVials} vials withdrawn` : ''} />
        <Kpi icon={Activity} label="Open excursions" value={d?.openExcursions} tone={d?.openExcursions ? 'text-rose-600' : 'text-slate-800'} />
        <Kpi
          icon={Link2}
          label="Custody ledger"
          value={verify ? (verify.valid ? 'Valid' : 'BROKEN') : '…'}
          tone={verify?.valid ? 'text-emerald-700' : 'text-rose-600'}
          sub={verify ? `${verify.entriesChecked} entries verified ${fmtTime(verify.checkedAt)}` : ''}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {(d?.sites || []).map((site) => {
          const Icon = SITE_ICONS[site.siteType] || Warehouse
          const device = devices?.find((dv) => dv.siteCode === site.siteCode)
          const here = (batches || []).filter((b) => b.siteCode === site.siteCode)
          return (
            <Card
              key={site.siteCode}
              title={site.siteName}
              icon={Icon}
              actions={<Badge tone="slate">{site.siteCode}</Badge>}
            >
              <div className="flex items-end justify-between">
                <div>
                  <div className="text-xs text-slate-500">
                    Logger {device?.deviceCode} <Badge tone="violet">SIMULATED</Badge>
                  </div>
                  <div className={`flex items-center gap-1 text-3xl font-bold ${tempTone(device?.state)}`}>
                    <Thermometer size={22} />
                    {device?.lastTemperatureC ?? '-'} °C
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {device?.state?.replace('_', ' ')} · {fmtTime(device?.lastReadingAt)}
                  </div>
                </div>
                <div className="text-right text-xs text-slate-500">
                  <div>
                    <b className="text-slate-800">{site.issuableVials}</b> issuable vials
                  </div>
                  <div>{site.batches} batches</div>
                </div>
              </div>
              <div className="mt-3 space-y-1.5">
                {here.map((b) => (
                  <button
                    key={b.id}
                    onClick={() => go('batches', b.id)}
                    className="block w-full cursor-pointer rounded-lg border border-slate-100 px-2.5 py-1.5 text-left hover:border-cyan-300 hover:bg-cyan-50/40"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-semibold">{b.batchCode}</span>
                      <StatusChip status={b.status} />
                    </div>
                    <div className="text-[11px] text-slate-500">
                      {b.product} · {b.vialsOnHand} vials · exp {b.expiryDate} · custodian {b.custodian}
                    </div>
                    <ExcursionCountdown batch={b} />
                  </button>
                ))}
                {here.length === 0 && <Empty>No stock at this site</Empty>}
              </div>
            </Card>
          )
        })}
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
        <Card title="Recent lifecycle transitions" icon={Activity} bodyClass="p-0">
          <table className="w-full">
            <tbody className="divide-y divide-slate-100">
              {(events || []).slice(0, 7).map((e) => (
                <tr key={e.id}>
                  <td className="td whitespace-nowrap text-xs text-slate-500">{fmtDateTime(e.occurredAt)}</td>
                  <td className="td font-semibold">{e.batchCode}</td>
                  <td className="td whitespace-nowrap">
                    <StatusChip status={e.fromStatus} /> → <StatusChip status={e.toStatus} />
                  </td>
                  <td className="td text-xs text-slate-600">
                    at {e.siteCode}, custodian <b>{e.custodian}</b>
                    <div className="text-slate-400">{e.actor}</div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {events?.length === 0 && <Empty>No transitions yet - start an excursion on the Telemetry page.</Empty>}
        </Card>
        <Card title="Latest expiry / alert digest" icon={Database}>
          {digests?.[0] ? (
            <div>
              <div className="text-sm font-semibold">{digests[0].headline}</div>
              <div className="text-[11px] text-slate-500">
                {digests[0].triggerSource} · {fmtDateTime(digests[0].generatedAt)}
              </div>
              <pre className="mt-2 max-h-48 overflow-auto rounded-lg bg-slate-50 p-2 text-[11px] whitespace-pre-wrap text-slate-700">
                {digests[0].body}
              </pre>
            </div>
          ) : (
            <Empty>The @Scheduled digest worker runs every 5 minutes (first run ~30 s after start).</Empty>
          )}
        </Card>
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-slate-500">
        <span>
          Dashboard aggregates computed {fmtTime(d?.generatedAt)} (served from the <b>{info?.cacheProvider}</b> cache until data changes)
        </span>
        <span>DB: {info?.database?.split(',')[0]} {info?.embeddedDatabase ? '(embedded demo instance)' : ''}</span>
        {d && (
          <span>
            Ledger head #{d.ledgerEntries} <Hash value={d.ledgerHeadHash} length={10} />
          </span>
        )}
      </div>
    </div>
  )
}
