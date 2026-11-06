import {
  Activity,
  Boxes,
  CalendarDays,
  Database,
  Hospital,
  Link2,
  PackageCheck,
  ShieldCheck,
  Thermometer,
  Truck,
  Warehouse,
} from 'lucide-react'
import { api } from '../api'
import { cn } from '@/lib/utils'
import { Badge, Card, Empty, ExcursionCountdown, Hash, StatusChip, fmtDateTime, fmtTime, roleLabel, usePoll } from './ui'
import { AnimatedNumber } from './watermelon/animated-number'

const SITE_ICONS = { DISTRICT_STORE: Warehouse, TRANSPORT_VAN: Truck, HEALTH_CENTRE: Hospital }

function tempTone(state) {
  return (
    {
      IN_RANGE: 'text-emerald-700 dark:text-emerald-400',
      HEAT: 'text-rose-600 dark:text-rose-400',
      FREEZE: 'text-blue-700 dark:text-blue-400',
      BELOW_RANGE: 'text-amber-600 dark:text-amber-400',
      STALE: 'text-muted-foreground',
    }[state] || 'text-muted-foreground'
  )
}

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

// Medesk metric card: label + icon on top, large figure and a mono footer line underneath.
function Metric({ label, icon: Icon, children, footer, valueClass = 'text-foreground' }) {
  return (
    <article className="flex h-34.5 min-w-0 flex-col justify-between rounded-xl bg-card p-4 text-card-foreground">
      <div className="flex items-start justify-between gap-3">
        <h2 className="text-sm leading-[1.4] font-medium text-muted-foreground">{label}</h2>
        <Icon className="size-5 shrink-0 text-foreground" />
      </div>
      <div className="min-w-0">
        <div className={cn('text-[1.75rem] leading-none font-medium', valueClass)}>{children}</div>
        {footer && <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-xs text-muted-foreground">{footer}</div>}
      </div>
    </article>
  )
}

function Dot({ className }) {
  return <span className={cn('inline-block size-1.5 rounded-full', className)} />
}

export default function Dashboard({ session, go }) {
  const { data: d } = usePoll(() => api('/api/dashboard'), 4000)
  const { data: devices } = usePoll(() => api('/api/telemetry/devices'), 4000)
  const { data: batches } = usePoll(() => api('/api/batches'), 4000)
  const { data: verify } = usePoll(() => api('/api/ledger/verify'), 8000)
  const { data: events } = usePoll(() => api('/api/audit/status-events'), 5000)
  const { data: digests } = usePoll(() => api('/api/digests'), 15000)
  const { data: info } = usePoll(() => api('/api/system/info'), 60000)
  const user = session.user
  const today = new Date().toLocaleDateString([], { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric' })

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div className="flex flex-col gap-1.5">
          <h1 className="text-2xl leading-[1.4] font-normal">
            {greeting()}, {user.fullName}
          </h1>
          <div className="flex flex-wrap items-center gap-1.5 text-sm text-muted-foreground">
            <CalendarDays className="size-4" />
            <span>{today}</span>
            <span>
              · {roleLabel(user.role)}
              {user.siteCode ? ` at ${user.siteCode}` : ', district-wide'}
            </span>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button className="btn-secondary" onClick={() => go('telemetry')}>
            <Thermometer size={15} /> Telemetry &amp; simulator
          </button>
          <button className="btn-primary" onClick={() => go('ledger')}>
            <Link2 size={15} /> Custody ledger
          </button>
        </div>
      </section>

      <section className="@container">
        <div className="grid grid-cols-1 gap-3 @min-[36rem]:grid-cols-2 @min-[72rem]:grid-cols-4">
          <Metric
            label="Batches tracked"
            icon={Boxes}
            footer={
              <>
                <span className="flex items-center gap-1">
                  <Dot className="bg-viable" /> VIABLE <AnimatedNumber value={d?.viable} />
                </span>
                <span className="flex items-center gap-1">
                  <Dot className="bg-at-risk" /> AT RISK <AnimatedNumber value={d?.atRisk} />
                </span>
                <span className="flex items-center gap-1">
                  <Dot className="bg-condemned" /> CONDEMNED <AnimatedNumber value={d?.condemned} />
                </span>
              </>
            }
          >
            <AnimatedNumber value={d?.totalBatches} />
          </Metric>
          <Metric label="Issuable vials" icon={PackageCheck} footer={d && <span>{d.withdrawnVials} vials withdrawn (condemned / expired)</span>}>
            <AnimatedNumber value={d?.issuableVials} />
          </Metric>
          <Metric
            label="Open excursions"
            icon={Activity}
            valueClass={d?.openExcursions ? 'text-rose-600 dark:text-rose-400' : 'text-foreground'}
            footer={<span>{devices ? `${devices.length} simulated loggers reporting` : '…'}</span>}
          >
            <AnimatedNumber value={d?.openExcursions} />
          </Metric>
          <Metric
            label="Custody ledger"
            icon={ShieldCheck}
            valueClass={verify ? (verify.valid ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400') : 'text-foreground'}
            footer={verify && <span>{verify.entriesChecked} entries re-hashed · {fmtTime(verify.checkedAt)}</span>}
          >
            {verify ? (verify.valid ? 'Valid' : 'BROKEN') : '…'}
          </Metric>
        </div>
      </section>

      <section className="grid gap-3 lg:grid-cols-3">
        {(d?.sites || []).map((site) => {
          const Icon = SITE_ICONS[site.siteType] || Warehouse
          const device = devices?.find((dv) => dv.siteCode === site.siteCode)
          const here = (batches || []).filter((b) => b.siteCode === site.siteCode)
          return (
            <Card key={site.siteCode} title={site.siteName} icon={Icon} actions={<Badge>{site.siteCode}</Badge>}>
              <div className="flex items-end justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    Logger {device?.deviceCode} <Badge tone="violet">SIMULATED</Badge>
                  </div>
                  <div className={cn('mt-1 flex items-baseline gap-1 text-[2rem] leading-none font-medium', tempTone(device?.state))}>
                    <AnimatedNumber value={device?.lastTemperatureC} minDecimals={1} />
                    <span className="text-lg">°C</span>
                  </div>
                  <div className="mt-1.5 text-[11px] text-muted-foreground">
                    {device?.state?.replace('_', ' ')} · {fmtTime(device?.lastReadingAt)}
                  </div>
                </div>
                <div className="text-right text-xs text-muted-foreground">
                  <div>
                    <span className="font-medium text-foreground/80">{site.issuableVials}</span> issuable vials
                  </div>
                  <div>{site.batches} batches</div>
                </div>
              </div>
              <div className="mt-4 space-y-2">
                {here.map((b) => (
                  <button
                    key={b.id}
                    onClick={() => go('batches', b.id)}
                    className="block w-full cursor-pointer rounded-lg border border-border/60 bg-background px-3 py-2 text-left transition-colors hover:border-foreground/20"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-medium">{b.batchCode}</span>
                      <StatusChip status={b.status} />
                    </div>
                    <div className="mt-0.5 text-[11px] text-muted-foreground">
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
      </section>

      <section className="grid gap-3 lg:grid-cols-[1.4fr_1fr]">
        <Card title="Recent lifecycle transitions" icon={Activity} bodyClass="p-0 pb-2">
          <div className="no-scrollbar overflow-x-auto">
            <table className="w-full min-w-140 text-left">
              <thead>
                <tr>
                  <th className="th">When</th>
                  <th className="th">Batch</th>
                  <th className="th">Transition</th>
                  <th className="th">Where / who</th>
                </tr>
              </thead>
              <tbody>
                {(events || []).slice(0, 7).map((e) => (
                  <tr key={e.id} className="border-t border-border/60 transition-colors hover:bg-foreground/[0.03]">
                    <td className="td text-xs whitespace-nowrap text-muted-foreground">{fmtDateTime(e.occurredAt)}</td>
                    <td className="td font-medium">{e.batchCode}</td>
                    <td className="td whitespace-nowrap">
                      <StatusChip status={e.fromStatus} /> <span className="text-muted-foreground">→</span> <StatusChip status={e.toStatus} />
                    </td>
                    <td className="td text-xs text-muted-foreground">
                      at {e.siteCode}, custodian <span className="font-medium text-foreground/80">{e.custodian}</span>
                      <div className="text-muted-foreground/80">{e.actor}</div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {events?.length === 0 && <Empty>No transitions yet - start an excursion on the Telemetry page.</Empty>}
        </Card>
        <Card title="Latest expiry / alert digest" icon={Database}>
          {digests?.[0] ? (
            <div>
              <div className="text-sm font-medium">{digests[0].headline}</div>
              <div className="text-[11px] text-muted-foreground">
                {digests[0].triggerSource} · {fmtDateTime(digests[0].generatedAt)}
              </div>
              <pre className="no-scrollbar mt-2 max-h-48 overflow-auto rounded-lg bg-background p-3 font-mono text-[11px] whitespace-pre-wrap text-foreground/80">
                {digests[0].body}
              </pre>
            </div>
          ) : (
            <Empty>The @Scheduled digest worker runs every 5 minutes (first run ~30 s after start).</Empty>
          )}
        </Card>
      </section>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
        <span>
          Dashboard aggregates computed {fmtTime(d?.generatedAt)} (served from the{' '}
          <span className="font-medium text-foreground/80">{info?.cacheProvider}</span> cache until data changes)
        </span>
        <span>
          DB: {info?.database?.split(',')[0]} {info?.embeddedDatabase ? '(embedded demo instance)' : ''}
        </span>
        {d && (
          <span className="flex items-center gap-1.5">
            Ledger head #{d.ledgerEntries} <Hash value={d.ledgerHeadHash} length={10} />
          </span>
        )}
      </div>
    </div>
  )
}
