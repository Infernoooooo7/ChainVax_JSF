import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, Bomb, CheckCircle2, Fingerprint, Link2, RotateCcw, Send, ShieldCheck, XCircle } from 'lucide-react'
import { api, sha256Hex } from '../api'
import { Alert, Badge, Card, ErrorAlert, Hash, StatusChip, fmtDateTime, fmtTime, usePoll } from './ui'

export default function Ledger({ session }) {
  const role = session.user.role
  const { data: entries, reload } = usePoll(() => api('/api/ledger'), 6000)
  const { data: batches, reload: reloadBatches } = usePoll(() => api('/api/batches'), 6000)
  const { data: sites } = usePoll(() => api('/api/sites'), 0)
  const { data: staff } = usePoll(() => api('/api/users'), 0)
  const [verify, setVerify] = useState(null)
  const [verifyError, setVerifyError] = useState(null)
  const [selectedSeq, setSelectedSeq] = useState(null)
  const [newSeq, setNewSeq] = useState(null)

  async function runVerify() {
    setVerifyError(null)
    try {
      setVerify(await api('/api/ledger/verify'))
    } catch (e) {
      setVerifyError(e)
    }
  }

  useEffect(() => {
    runVerify()
  }, [])

  const selected = entries?.find((e) => e.seq === selectedSeq) || (entries && entries[entries.length - 1])
  const brokenSeqs = new Set((verify?.problems || []).map((p) => p.seq))

  return (
    <div className="space-y-5">
      <div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
        <TransferForm
          role={role}
          user={session.user}
          batches={batches}
          sites={sites}
          staff={staff}
          onDone={(r) => {
            setNewSeq(r.entry.seq)
            setSelectedSeq(r.entry.seq)
            reload()
            reloadBatches()
            runVerify()
          }}
        />
        <Card
          title="Chain verification"
          icon={ShieldCheck}
          actions={
            <button className="btn-primary" onClick={runVerify}>
              <ShieldCheck size={14} /> Verify whole chain
            </button>
          }
        >
          <p className="text-xs text-muted-foreground">
            <code>GET /api/ledger/verify</code> recomputes <code>SHA-256(previous_hash + payload)</code> for every entry from the
            stored columns and checks each link, the sequence and the head pointer.
          </p>
          <div className="mt-3">
            <ErrorAlert error={verifyError} />
            {verify &&
              (verify.valid ? (
                <Alert tone="success" title={`Chain valid - ${verify.entriesChecked} entries`}>
                  Head #{verify.headSeq} <Hash value={verify.headHash} length={16} /> · checked {fmtTime(verify.checkedAt)}
                </Alert>
              ) : (
                <Alert tone="error" title={`Chain BROKEN - ${verify.problems.length} problem(s)`}>
                  <ul className="mt-1 space-y-0.5">
                    {verify.problems.map((p, i) => (
                      <li key={i}>
                        <b>
                          #{p.seq} {p.code}
                        </b>
                        : {p.detail}
                      </li>
                    ))}
                  </ul>
                </Alert>
              ))}
          </div>
          {role === 'DISTRICT_OFFICER' && <TamperControls entries={entries} onChange={runVerify} />}
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.6fr_1fr]">
        <Card title="Custody ledger (append-only, hash-chained)" icon={Link2} bodyClass="p-0">
          <div className="max-h-[560px] overflow-auto">
            <table className="w-full">
              <thead className="sticky top-0 bg-muted/60">
                <tr>
                  <th className="th">#</th>
                  <th className="th">When</th>
                  <th className="th">Batch · event</th>
                  <th className="th">Custody</th>
                  <th className="th">Status</th>
                  <th className="th">previous_hash → hash</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60 [&>tr]:transition-colors [&>tr:hover]:bg-foreground/[0.03]">
                {(entries || [])
                  .slice()
                  .reverse()
                  .map((e) => (
                    <tr
                      key={e.seq}
                      onClick={() => setSelectedSeq(e.seq)}
                      className={`cursor-pointer ${
                        brokenSeqs.has(e.seq)
                          ? 'bg-rose-50 dark:bg-rose-500/10'
                          : e.seq === newSeq
                            ? 'bg-emerald-50 dark:bg-emerald-500/10'
                            : selected?.seq === e.seq
                              ? 'bg-accent'
                              : 'hover:bg-foreground/[0.03]'
                      }`}
                    >
                      <td className="td font-mono text-xs">{e.seq}</td>
                      <td className="td text-xs whitespace-nowrap">{fmtDateTime(e.occurredAt)}</td>
                      <td className="td text-xs">
                        <b>{e.batchCode}</b>
                        <div className="text-muted-foreground">{e.eventType}</div>
                      </td>
                      <td className="td text-xs">
                        {e.fromSiteCode ? `${e.fromSiteCode} → ` : ''}
                        <b>{e.toSiteCode}</b>
                        <div className="text-muted-foreground">
                          {e.fromCustodian ? `${e.fromCustodian} → ` : ''}
                          {e.toCustodian}
                        </div>
                      </td>
                      <td className="td">
                        <StatusChip status={e.batchStatus} />
                      </td>
                      <td className="td">
                        <div className="flex items-center gap-1">
                          <Hash value={e.previousHash} length={8} />
                          <ArrowRight size={12} className="text-muted-foreground/80" />
                          <Hash value={e.hash} length={8} />
                        </div>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </Card>
        <EntryInspector entry={selected} entries={entries} />
      </div>
    </div>
  )
}

function TransferForm({ role, user, batches, sites, staff, onDone }) {
  const [batchId, setBatchId] = useState('')
  const [toSiteId, setToSiteId] = useState('')
  const [toCustodian, setToCustodian] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)
  const batch = batches?.find((b) => String(b.id) === String(batchId))
  const toSite = sites?.find((s) => String(s.id) === String(toSiteId))
  const receivers = useMemo(
    () => (staff || []).filter((u) => u.role !== 'AUDITOR' && (!u.siteCode || u.siteCode === toSite?.code)),
    [staff, toSite],
  )

  useEffect(() => {
    if (!batchId && batches?.length) {
      const mine = batches.find((b) => b.siteCode === user.siteCode) || batches[0]
      setBatchId(String(mine.id))
    }
  }, [batches, batchId, user.siteCode])

  useEffect(() => {
    if (receivers.length && !receivers.some((r) => r.username === toCustodian)) {
      setToCustodian(receivers.find((r) => r.siteCode)?.username || receivers[0].username)
    }
  }, [receivers, toCustodian])

  async function submit(e) {
    e.preventDefault()
    setError(null)
    setResult(null)
    try {
      const r = await api('/api/custody/transfers', {
        method: 'POST',
        body: { batchId: Number(batchId), toSiteId: Number(toSiteId), toCustodian, note },
      })
      setResult(r)
      onDone(r)
    } catch (err) {
      setError(err)
    }
  }

  return (
    <Card title="Record a custody transfer" icon={Send}>
      {role === 'AUDITOR' && (
        <Alert tone="warn" className="mb-3">
          Auditors are read-only. The form stays enabled so you can see the server refuse it (HTTP 403).
        </Alert>
      )}
      <form className="grid gap-3 md:grid-cols-2" onSubmit={submit}>
        <div className="md:col-span-2">
          <label className="label">Batch</label>
          <select className="input" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
            {(batches || []).map((b) => (
              <option key={b.id} value={b.id}>
                {b.batchCode} - {b.product} @ {b.siteCode} ({b.status})
              </option>
            ))}
          </select>
          {batch && (
            <div className="mt-1 text-[11px] text-muted-foreground">
              Currently at <b>{batch.siteCode}</b> with <b>{batch.custodian}</b> · <StatusChip status={batch.status} />
            </div>
          )}
        </div>
        <div>
          <label className="label">To site</label>
          <select className="input" value={toSiteId} onChange={(e) => setToSiteId(e.target.value)} required>
            <option value="">Select…</option>
            {(sites || [])
              .filter((s) => s.code !== batch?.siteCode)
              .map((s) => (
                <option key={s.id} value={s.id}>
                  {s.code} - {s.name}
                </option>
              ))}
          </select>
        </div>
        <div>
          <label className="label">Receiving custodian</label>
          <select className="input" value={toCustodian} onChange={(e) => setToCustodian(e.target.value)}>
            {receivers.map((u) => (
              <option key={u.username} value={u.username}>
                {u.username} ({u.role})
              </option>
            ))}
          </select>
        </div>
        <div className="md:col-span-2">
          <label className="label">Note (optional, part of the hashed payload)</label>
          <input className="input" value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Delivered, ILR checked" />
        </div>
        <div className="md:col-span-2">
          <button className="btn-primary" disabled={!toSiteId}>
            <Send size={14} /> Record transfer &amp; append to ledger
          </button>
        </div>
      </form>
      <div className="mt-3">
        <ErrorAlert error={error} title={error ? `Refused - HTTP ${error.status} ${error.code || ''}` : undefined} />
        {result && (
          <Alert tone="success" title={`Ledger entry #${result.entry.seq} appended`}>
            {result.entry.batchCode}: {result.entry.fromSiteCode} → {result.entry.toSiteCode} (
            {result.entry.fromCustodian} → {result.entry.toCustodian})
            <div className="mt-1 flex flex-wrap items-center gap-1 text-xs">
              previous_hash <Hash value={result.entry.previousHash} length={10} /> → hash <Hash value={result.entry.hash} length={10} />
            </div>
          </Alert>
        )}
      </div>
    </Card>
  )
}

function TamperControls({ entries, onChange }) {
  const [seq, setSeq] = useState(3)
  const [msg, setMsg] = useState(null)
  async function act(kind) {
    const r = await api(`/api/demo/ledger/${seq}/${kind}`, { method: 'POST' })
    setMsg(kind === 'tamper' ? r.description : `Entry #${seq} restored.`)
    onChange()
  }
  return (
    <div className="mt-4 rounded-lg border border-dashed border-rose-300 dark:border-rose-500/30 bg-rose-50/50 dark:bg-rose-500/10 p-3">
      <div className="flex items-center gap-1.5 text-sm font-semibold text-rose-800 dark:text-rose-300">
        <Bomb size={15} /> Demo only: simulate a rogue database edit
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        Changes the <code>vials</code> column of one ledger row directly with SQL, bypassing the application. Verification should
        then flag that entry.
      </p>
      <div className="mt-2 flex items-center gap-2">
        <select className="input w-28!" value={seq} onChange={(e) => setSeq(Number(e.target.value))}>
          {(entries || []).map((e) => (
            <option key={e.seq} value={e.seq}>
              entry #{e.seq}
            </option>
          ))}
        </select>
        <button className="btn-danger" onClick={() => act('tamper')}>
          <Bomb size={14} /> Tamper
        </button>
        <button className="btn-secondary" onClick={() => act('restore')}>
          <RotateCcw size={14} /> Restore
        </button>
      </div>
      {msg && <div className="mt-2 text-xs text-rose-800 dark:text-rose-300">{msg}</div>}
    </div>
  )
}

function EntryInspector({ entry, entries }) {
  const [browserHash, setBrowserHash] = useState(null)
  useEffect(() => {
    setBrowserHash(null)
    if (entry) sha256Hex(entry.previousHash + entry.payload).then(setBrowserHash)
  }, [entry])
  if (!entry) return null
  const previous = entries?.find((e) => e.seq === entry.seq - 1)
  const linkOk = entry.seq === 1 ? /^0{64}$/.test(entry.previousHash) : previous?.hash === entry.previousHash
  const hashOk = browserHash === entry.hash
  return (
    <Card title={`Inspect entry #${entry.seq}`} icon={Fingerprint}>
      <div className="space-y-3 text-xs">
        <div>
          <div className="label">previous_hash {entry.seq === 1 ? '(genesis = 64 zeros)' : `(= hash of #${entry.seq - 1})`}</div>
          <code className="block rounded bg-muted/60 p-2 font-mono break-all">{entry.previousHash}</code>
        </div>
        <div>
          <div className="label">payload (canonical JSON stored with the entry)</div>
          <code className="block rounded bg-muted/60 p-2 font-mono break-all">{entry.payload}</code>
        </div>
        <div>
          <div className="label">stored hash</div>
          <code className="block rounded bg-muted/60 p-2 font-mono break-all">{entry.hash}</code>
        </div>
        <div className="space-y-1.5">
          <div className={`flex items-center gap-1.5 ${hashOk ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-700 dark:text-rose-300'}`}>
            {hashOk ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
            Recomputed in this browser (Web Crypto): SHA-256(previous_hash + payload) {hashOk ? 'matches' : 'does NOT match'} the stored hash
          </div>
          <div className={`flex items-center gap-1.5 ${linkOk ? 'text-emerald-700 dark:text-emerald-400' : 'text-rose-700 dark:text-rose-300'}`}>
            {linkOk ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
            {entry.seq === 1 ? 'First entry links to the genesis value' : `Links to the hash of entry #${entry.seq - 1}`}
          </div>
          <div className="text-muted-foreground">
            The server check also rebuilds the payload from the stored columns, so an edited column (e.g. <code>vials</code>) is
            caught even if the payload text is untouched. <Badge>stored vials: {entry.vials}</Badge>
          </div>
        </div>
      </div>
    </Card>
  )
}
