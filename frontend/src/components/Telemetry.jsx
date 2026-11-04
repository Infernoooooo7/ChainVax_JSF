import { useState } from 'react'
import { Flame, Pause, Play, Radio, Snowflake, Thermometer, VolumeX, Zap } from 'lucide-react'
import { CartesianGrid, Line, LineChart, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api'
import { Alert, Badge, Card, Empty, ErrorAlert, ExcursionCountdown, SimulatedBadge, StatusChip, fmtTime, usePoll } from './ui'

const MODES = [
  { mode: 'NORMAL', label: 'Normal 4-6 °C', icon: Thermometer, cls: 'btn-secondary' },
  { mode: 'HEAT', label: 'Heat excursion', icon: Flame, cls: 'btn-danger' },
  { mode: 'COLD', label: 'Freeze excursion', icon: Snowflake, cls: 'btn bg-blue-600 text-white hover:bg-blue-700' },
  { mode: 'SILENT', label: 'Logger silent', icon: VolumeX, cls: 'btn-secondary' },
]

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
        <span className="text-sm font-semibold text-slate-700">Auto-simulation:</span>
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
            className={`card cursor-pointer p-4 ${selected === d.deviceCode ? 'ring-2 ring-cyan-500' : ''}`}
            onClick={() => setSelected(d.deviceCode)}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-sm font-semibold">
                <Radio size={15} className="text-cyan-700" /> {d.deviceCode}
              </div>
              <SimulatedBadge />
            </div>
            <div className="text-[11px] text-slate-500">
              {d.siteName} · {d.description}
            </div>
            <div className="mt-2 flex items-end justify-between">
              <div
                className={`text-3xl font-bold ${
                  Number(d.lastTemperatureC) >= 8 ? 'text-rose-600' : Number(d.lastTemperatureC) <= -0.5 ? 'text-blue-700' : 'text-emerald-700'
                }`}
              >
                {d.lastTemperatureC ?? '-'} °C
              </div>
              <div className="text-right text-[11px] text-slate-500">
                mode <b>{d.mode}</b>
                <div>{fmtTime(d.lastReadingAt)}</div>
              </div>
            </div>
            {canOperate && (
              <div className="mt-3 grid grid-cols-2 gap-1.5" onClick={(e) => e.stopPropagation()}>
                {MODES.map(({ mode, label, icon: Icon, cls }) => (
                  <button
                    key={mode}
                    className={`${cls} ${d.mode === mode ? 'ring-2 ring-offset-1 ring-cyan-500' : ''} px-2! text-xs`}
                    onClick={() => {
                      setSelected(d.deviceCode)
                      run(`${d.deviceCode} → ${mode}`, () =>
                        api(`/api/simulator/devices/${d.deviceCode}/mode`, { method: 'POST', body: { mode } }),
                      )
                    }}
                  >
                    <Icon size={13} /> {label}
                  </button>
                ))}
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
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis
                  dataKey="t"
                  type="number"
                  domain={['dataMin', 'dataMax']}
                  tickFormatter={(t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  fontSize={11}
                />
                <YAxis domain={[-4, 14]} fontSize={11} unit="°" />
                <ReferenceArea y1={2} y2={8} fill="#10b981" fillOpacity={0.08} />
                <ReferenceLine y={8} stroke="#e11d48" strokeDasharray="4 4" label={{ value: 'heat alarm ≥ 8 °C', fontSize: 10, fill: '#e11d48', position: 'insideTopRight' }} />
                <ReferenceLine y={-0.5} stroke="#2563eb" strokeDasharray="4 4" label={{ value: 'freeze alarm ≤ -0.5 °C', fontSize: 10, fill: '#2563eb', position: 'insideBottomRight' }} />
                <Tooltip labelFormatter={(t) => new Date(t).toLocaleTimeString()} formatter={(v) => [`${v} °C`, 'simulated reading']} />
                <Line type="monotone" dataKey="temp" stroke="#0e7490" strokeWidth={2} dot={false} isAnimationActive={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="mt-1 text-[11px] text-slate-500">
            Green band: WHO +2 to +8 °C storage range. Dashed lines: PQS E006 Type 1 alarm thresholds. All points are simulated.
          </div>
        </Card>

        <div className="space-y-4">
          <Card title={`Batches at ${device?.siteCode || '…'}`} icon={Thermometer}>
            <div className="space-y-2">
              {atSite.map((b) => (
                <div key={b.id} className="rounded-lg border border-slate-100 px-3 py-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold">{b.batchCode}</span>
                    <StatusChip status={b.status} />
                  </div>
                  <div className="text-[11px] text-slate-500">
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
                <div className="text-xs text-slate-500">
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
                      <div className="mt-1 text-xs text-slate-500">No status change from this reading.</div>
                    )}
                  </>
                ) : (
                  <div className="mt-1 text-xs text-slate-500">Simulator updated.</div>
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
