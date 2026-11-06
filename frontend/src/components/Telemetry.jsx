import { useState } from 'react'
import { Flame, Pause, Play, Radio, Snowflake, Thermometer, VolumeX, Zap } from 'lucide-react'
import { CartesianGrid, Line, LineChart, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api'
import { Alert, Badge, Card, Empty, ErrorAlert, ExcursionCountdown, SimulatedBadge, StatusChip, fmtTime, usePoll } from './ui'
import { ContinuousTabs } from './watermelon/continuous-tabs'
import { AnimatedNumber } from './watermelon/animated-number'

// Simulator modes as Watermelon "Continuous Tabs"; the heat/freeze pills take the alarm colours.
const MODES = [
  { id: 'NORMAL', label: 'Normal', icon: Thermometer },
  { id: 'HEAT', label: 'Heat', icon: Flame, pillClassName: 'bg-rose-600 dark:bg-rose-500', activeTextClassName: 'text-white dark:text-white' },
  { id: 'COLD', label: 'Freeze', icon: Snowflake, pillClassName: 'bg-blue-600 dark:bg-blue-500', activeTextClassName: 'text-white dark:text-white' },
  { id: 'SILENT', label: 'Silent', icon: VolumeX },
]
const MODE_HELP = {
  NORMAL: 'Normal: readings stay at 4-6 °C.',
  HEAT: 'Heat excursion: readings climb above the +8 °C alarm.',
  COLD: 'Freeze excursion: readings drop below the -0.5 °C alarm.',
  SILENT: 'Logger silent: no readings are sent (the worker still evaluates open windows).',
}

// Tooltip in the style of Medesk's ChartTooltip.
function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-lg border border-border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-sm">
      <p className="mb-1.5 font-medium">{new Date(label).toLocaleTimeString()}</p>
      <div className="flex items-center justify-between gap-6">
        <span className="flex items-center gap-2 text-muted-foreground">
          <span className="size-2 rounded-sm bg-temperature" />
          simulated reading
        </span>
        <span className="font-medium text-foreground">{payload[0].value} °C</span>
      </div>
    </div>
  )
}

export default function Telemetry({ session }) {
  const canOperate = session.user.role !== 'AUDITOR'
  const { data: sim, reload: reloadSim } = usePoll(() => api('/api/simulator'), 3000)
  const [selected, setSelected] = useState('LOG-VAN-01')
  const { data: readings } = usePoll(() => api(`/api/telemetry/devices/${selected}/readings?minutes=20`), 3000, [selected])
  const { data: batches } = usePoll(() => api('/api/batches'), 3000)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [custom, setCustom] = useState('9.5')

  const device = sim?.devices.find((d) => d.deviceCode === selected)
  const atSite = (batches || []).filter((b) => device && b.siteCode === device.siteCode)

  async function run(label, fn) {
    setError(null)
    try {
      const r = await fn()
      setResult({ label, r, at: new Date().toISOString() })
      reloadSim()
    } catch (e) {
      setError(e)
    }
  }

  const chartData = (readings || []).map((r) => ({ t: new Date(r.recordedAt).getTime(), temp: Number(r.temperatureC) }))

  return (
    <div className="space-y-5">
      <Alert tone="info" title="How readings arrive">
        Each simulated logger signs in with its own device secret (<code>POST /api/auth/device-token</code>) and posts to{' '}
        <code>POST /api/telemetry/readings</code> over HTTP - the same API a real 30-day logger or gateway would use. Every
        reading is stored with <code>source = SIMULATOR</code>. Policy <b>DEMO-FAST</b> condemns after 90 s of heat (60 s of
        freezing) so the lifecycle fits a live demo; <b>PQS-E006-T1</b> uses the WHO PQS E006 alarm durations (10 h / 60 min).
      </Alert>

      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-semibold text-foreground/90">Auto-simulation:</span>
        {sim && (
          <button
            className={sim.autoEnabled ? 'btn-secondary' : 'btn-primary'}
            disabled={!canOperate}
            onClick={() => run('auto', () => api('/api/simulator/auto', { method: 'POST', body: { enabled: !sim.autoEnabled } }))}
          >
            {sim.autoEnabled ? <Pause size={14} /> : <Play size={14} />}
            {sim.autoEnabled ? `On - every ${sim.intervalSeconds}s (pause)` : 'Off (resume)'}
          </button>
        )}
        {!canOperate && <Badge tone="amber">Auditors can watch but not drive the simulator</Badge>}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {(sim?.devices || []).map((d) => (
          <div
            key={d.deviceCode}
            className={`card cursor-pointer p-4 transition-shadow ${selected === d.deviceCode ? 'ring-2 ring-foreground/25' : 'hover:ring-1 hover:ring-foreground/10'}`}
            onClick={() => setSelected(d.deviceCode)}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-sm font-semibold">
                <Radio size={15} className="text-foreground/70" /> {d.deviceCode}
              </div>
              <SimulatedBadge />
            </div>
            <div className="text-[11px] text-muted-foreground">
              {d.siteName} · {d.description}
            </div>
            <div className="mt-2 flex items-end justify-between">
              <div
                className={`flex items-baseline gap-1 text-[2rem] leading-none font-medium ${
                  Number(d.lastTemperatureC) >= 8
                    ? 'text-rose-600 dark:text-rose-400 dark:text-rose-400'
                    : Number(d.lastTemperatureC) <= -0.5
                      ? 'text-blue-700 dark:text-blue-400 dark:text-blue-400'
                      : 'text-emerald-700 dark:text-emerald-400 dark:text-emerald-400'
                }`}
              >
                <AnimatedNumber value={d.lastTemperatureC} minDecimals={1} />
                <span className="text-lg">°C</span>
              </div>
              <div className="text-right text-[11px] text-muted-foreground">
                mode <b>{d.mode}</b>
                <div>{fmtTime(d.lastReadingAt)}</div>
              </div>
            </div>
            {canOperate && (
              <div className="mt-3" onClick={(e) => e.stopPropagation()}>
                <ContinuousTabs
                  size="sm"
                  ariaLabel={`Simulator mode for ${d.deviceCode}`}
                  tabs={MODES}
                  value={d.mode}
                  onChange={(mode) => {
                    setSelected(d.deviceCode)
                    run(`${d.deviceCode} → ${mode}`, () =>
                      api(`/api/simulator/devices/${d.deviceCode}/mode`, { method: 'POST', body: { mode } }),
                    )
                  }}
                />
                <div className="mt-1.5 text-[11px] text-muted-foreground">{MODE_HELP[d.mode]}</div>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.5fr_1fr]">
        <Card
          title={`${selected} - last 20 minutes`}
          icon={Thermometer}
          actions={
            canOperate && (
              <form
                className="flex items-center gap-1.5"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(`${selected} custom ${custom} °C`, () =>
                    api(`/api/simulator/devices/${selected}/readings`, { method: 'POST', body: { temperatureC: Number(custom) } }),
                  )
                }}
              >
                <input className="input w-20! py-1!" value={custom} onChange={(e) => setCustom(e.target.value)} />
                <button className="btn-secondary py-1! text-xs">
                  <Zap size={13} /> Send one reading
                </button>
              </form>
            )
          }
        >
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: -12 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis
                  dataKey="t"
                  type="number"
                  domain={['dataMin', 'dataMax']}
                  tickFormatter={(t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}
                />
                <YAxis domain={[-4, 14]} unit="°" axisLine={false} tickLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }} />
                <ReferenceArea y1={2} y2={8} fill="#10b981" fillOpacity={0.08} />
                <ReferenceLine y={8} stroke="#e11d48" strokeDasharray="4 4" label={{ value: 'heat alarm ≥ 8 °C', fontSize: 10, fill: '#e11d48', position: 'insideTopRight' }} />
                <ReferenceLine y={-0.5} stroke="#2563eb" strokeDasharray="4 4" label={{ value: 'freeze alarm ≤ -0.5 °C', fontSize: 10, fill: '#2563eb', position: 'insideBottomRight' }} />
                <Tooltip content={<ChartTooltip />} cursor={{ stroke: 'var(--border)' }} />
                <Line type="monotone" dataKey="temp" stroke="var(--dashboard-scheduled)" strokeWidth={2} dot={false} isAnimationActive={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="mt-1 text-[11px] text-muted-foreground">
            Green band: WHO +2 to +8 °C storage range. Dashed lines: PQS E006 Type 1 alarm thresholds. All points are simulated.
          </div>
        </Card>

        <div className="space-y-4">
          <Card title={`Batches at ${device?.siteCode || '…'}`} icon={Thermometer}>
            <div className="space-y-2">
              {atSite.map((b) => (
                <div key={b.id} className="rounded-lg border border-border/60 px-3 py-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold">{b.batchCode}</span>
                    <StatusChip status={b.status} />
                  </div>
                  <div className="text-[11px] text-muted-foreground">
                    {b.product} · policy {b.policyCode} · custodian {b.custodian}
                  </div>
                  <ExcursionCountdown batch={b} />
                </div>
              ))}
              {atSite.length === 0 && <Empty>No batches currently at this site.</Empty>}
            </div>
          </Card>

          <Card title="Last ingestion response" icon={Zap}>
            <ErrorAlert error={error} />
            {result ? (
              <div className="text-sm">
                <div className="text-xs text-muted-foreground">
                  {result.label} · {fmtTime(result.at)}
                </div>
                {result.r?.accepted !== undefined ? (
                  <>
                    <div className="mt-1">
                      Accepted <b>{result.r.accepted}</b> reading(s) for {result.r.deviceCode} at {result.r.siteCode}
                    </div>
                    {result.r.statusChanges.length > 0 ? (
                      <ul className="mt-2 space-y-1">
                        {result.r.statusChanges.map((c, i) => (
                          <li key={i} className="text-xs">
                            <b>{c.batchCode}</b>: <StatusChip status={c.from} /> → <StatusChip status={c.to} />
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <div className="mt-1 text-xs text-muted-foreground">No status change from this reading.</div>
                    )}
                  </>
                ) : (
                  <div className="mt-1 text-xs text-muted-foreground">Simulator updated.</div>
                )}
              </div>
            ) : (
              <Empty>Pick a scenario on a logger card.</Empty>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}
