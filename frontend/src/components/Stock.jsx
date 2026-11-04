import { useEffect, useMemo, useState } from 'react'
import { Ban, ListOrdered, PackageCheck, PackageMinus, ShieldAlert } from 'lucide-react'
import { api } from '../api'
import { Alert, Badge, Card, Empty, ErrorAlert, StatusChip, fmtDateTime, usePoll } from './ui'

export default function Stock({ session }) {
  const user = session.user
  const { data: sites } = usePoll(() => api('/api/sites'), 0)
  const { data: batches, reload: reloadBatches } = usePoll(() => api('/api/batches'), 5000)
  const [siteId, setSiteId] = useState(null)
  const [product, setProduct] = useState('')

  useEffect(() => {
    if (sites && siteId === null) {
      const mine = sites.find((s) => s.code === user.siteCode) || sites[0]
      setSiteId(mine.id)
    }
  }, [sites, siteId, user.siteCode])

  const products = useMemo(
    () => [...new Set((batches || []).filter((b) => b.siteId === siteId).map((b) => b.product))],
    [batches, siteId],
  )

  useEffect(() => {
    if (products.length && !products.includes(product)) setProduct(products[0])
  }, [products, product])

  const { data: view, reload } = usePoll(
    () => (siteId && product ? api(`/api/stock/issuable?siteId=${siteId}&product=${encodeURIComponent(product)}`) : Promise.resolve(null)),
    5000,
    [siteId, product],
  )
  const { data: issues, reload: reloadIssues } = usePoll(() => api('/api/stock/issues'), 8000)
  const refresh = () => {
    reload()
    reloadIssues()
    reloadBatches()
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className="label">Site</label>
          <select className="input w-72!" value={siteId ?? ''} onChange={(e) => setSiteId(Number(e.target.value))}>
            {(sites || []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.code} - {s.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="label">Product</label>
          <select className="input w-72!" value={product} onChange={(e) => setProduct(e.target.value)}>
            {products.map((p) => (
              <option key={p}>{p}</option>
            ))}
          </select>
        </div>
        {view && (
          <div className="pb-1.5 text-sm">
            <b>{view.issuableVials}</b> issuable vials of {product} at {view.siteCode}
          </div>
        )}
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="Issuable stock - first-expiry-first-out order" icon={ListOrdered} bodyClass="p-0">
          <table className="w-full">
            <tbody className="divide-y divide-slate-100">
              {(view?.issuable || []).map((b, i) => (
                <tr key={b.id}>
                  <td className="td font-mono text-xs text-slate-400">{i + 1}</td>
                  <td className="td">
                    <b>{b.batchCode}</b>
                    <div className="text-[11px] text-slate-500">lot {b.lotNumber}</div>
                  </td>
                  <td className="td text-xs">exp {b.expiryDate}</td>
                  <td className="td text-xs">{b.vialsOnHand} vials</td>
                  <td className="td">
                    <StatusChip status={b.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {view && view.issuable.length === 0 && <Empty>No issuable stock of this product here.</Empty>}
        </Card>

        <Card title="Withdrawn - never issuable" icon={Ban} bodyClass="p-0">
          <table className="w-full">
            <tbody className="divide-y divide-slate-100">
              {(view?.withdrawn || []).map((w) => (
                <tr key={w.batchId} className="bg-rose-50/40">
                  <td className="td">
                    <b>{w.batchCode}</b>
                    <div className="text-[11px] text-slate-500">exp {w.expiryDate}</div>
                  </td>
                  <td className="td">
                    <StatusChip status={w.status} />
                  </td>
                  <td className="td text-xs text-rose-800">{w.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {view && view.withdrawn.length === 0 && <Empty>Nothing withdrawn for this product here.</Empty>}
        </Card>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <IssueForm siteId={siteId} product={product} user={user} onDone={refresh} />
        <OverrideForm siteId={siteId} product={product} user={user} batches={batches} onDone={refresh} />
      </div>

      <Card title="Recent stock issues" icon={PackageMinus} bodyClass="p-0">
        <table className="w-full">
          <thead className="bg-slate-50">
            <tr>
              <th className="th">When</th>
              <th className="th">Batch</th>
              <th className="th">Site</th>
              <th className="th">Vials</th>
              <th className="th">By</th>
              <th className="th">Order</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {(issues || []).slice(0, 10).map((i) => (
              <tr key={i.id}>
                <td className="td text-xs">{fmtDateTime(i.issuedAt)}</td>
                <td className="td text-xs">
                  <b>{i.batchCode}</b>
                  <div className="text-slate-500">{i.purpose}</div>
                </td>
                <td className="td text-xs">{i.siteCode}</td>
                <td className="td text-xs">{i.vials}</td>
                <td className="td text-xs">{i.issuedBy}</td>
                <td className="td text-xs">
                  {i.fefoOrder ? <Badge tone="emerald">FEFO</Badge> : <Badge tone="amber">OVERRIDE #{i.overrideAuditId}</Badge>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {issues?.length === 0 && <Empty>No stock issued yet.</Empty>}
      </Card>
    </div>
  )
}

function IssueForm({ siteId, product, user, onDone }) {
  const [vials, setVials] = useState(10)
  const [purpose, setPurpose] = useState('Routine immunization session')
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)
  async function submit(e) {
    e.preventDefault()
    setError(null)
    setResult(null)
    try {
      setResult(await api('/api/stock/issue', { method: 'POST', body: { siteId, product, vials: Number(vials), purpose } }))
      onDone()
    } catch (err) {
      setError(err)
    }
  }
  return (
    <Card title="Issue vials (FEFO allocator)" icon={PackageCheck}>
      <p className="mb-3 text-xs text-slate-500">
        Allowed for storekeepers at their own site and district officers. Uses <code>@Transactional</code> + <code>@Version</code>{' '}
        so two clerks issuing at the same moment cannot over-allocate.
      </p>
      <form className="flex flex-wrap items-end gap-2" onSubmit={submit}>
        <div>
          <label className="label">Vials</label>
          <input className="input w-24!" type="number" min="1" value={vials} onChange={(e) => setVials(e.target.value)} />
        </div>
        <div className="min-w-48 flex-1">
          <label className="label">Purpose</label>
          <input className="input" value={purpose} onChange={(e) => setPurpose(e.target.value)} />
        </div>
        <button className="btn-primary">Issue as {user.username}</button>
      </form>
      <div className="mt-3">
        <ErrorAlert error={error} title={error ? `Refused - HTTP ${error.status} ${error.code || ''}` : undefined} />
        {result && (
          <Alert tone="success" title={`Issued ${result.vialsIssued} vials of ${result.product}`}>
            <ul>
              {result.allocations.map((a) => (
                <li key={a.batchId}>
                  {a.vials} from <b>{a.batchCode}</b> (exp {a.expiryDate}) - {a.remainingVials} left
                </li>
              ))}
            </ul>
            {result.skipped.length > 0 && (
              <div className="mt-1 text-xs">FEFO skipped: {result.skipped.map((s) => `${s.batchCode} (${s.status})`).join(', ')}</div>
            )}
          </Alert>
        )}
      </div>
    </Card>
  )
}

function OverrideForm({ siteId, product, user, batches, onDone }) {
  const candidates = (batches || []).filter((b) => b.siteId === siteId && b.product === product)
  const [batchId, setBatchId] = useState('')
  const [vials, setVials] = useState(5)
  const [reason, setReason] = useState('Earlier-expiry lot reserved for Friday outreach session')
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)

  useEffect(() => {
    if (candidates.length && !candidates.some((c) => String(c.id) === String(batchId))) {
      setBatchId(String(candidates[candidates.length - 1].id))
    }
  }, [candidates, batchId])

  async function submit(e) {
    e.preventDefault()
    setError(null)
    setResult(null)
    try {
      setResult(
        await api('/api/stock/issue', {
          method: 'POST',
          body: { siteId, product, vials: Number(vials), purpose: 'Officer override', overrideBatchId: Number(batchId), overrideReason: reason },
        }),
      )
      onDone()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <Card title="Officer override (bypass FEFO, audited)" icon={ShieldAlert}>
      <p className="mb-3 text-xs text-slate-500">
        Only a district officer may choose a specific batch out of FEFO order; the reason is stored against their name. Condemned or
        at-risk stock is refused even with an override.
      </p>
      {user.role !== 'DISTRICT_OFFICER' && (
        <Alert tone="warn" className="mb-3">
          You are signed in as {user.role}. Submitting will show the server refusing it.
        </Alert>
      )}
      <form className="grid gap-2 md:grid-cols-[1fr_6rem]" onSubmit={submit}>
        <div>
          <label className="label">Batch</label>
          <select className="input" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
            {candidates.map((b) => (
              <option key={b.id} value={b.id}>
                {b.batchCode} - exp {b.expiryDate} - {b.status}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="label">Vials</label>
          <input className="input" type="number" min="1" value={vials} onChange={(e) => setVials(e.target.value)} />
        </div>
        <div className="md:col-span-2">
          <label className="label">Reason (recorded)</label>
          <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
        <div className="md:col-span-2">
          <button className="btn-secondary" disabled={!batchId}>
            <ShieldAlert size={14} /> Issue with override
          </button>
        </div>
      </form>
      <div className="mt-3">
        <ErrorAlert error={error} title={error ? `Refused - HTTP ${error.status} ${error.code || ''}` : undefined} />
        {result && (
          <Alert tone="success" title={`Override recorded (#${result.overrideAuditId})`}>
            {result.allocations.map((a) => `${a.vials} vials from ${a.batchCode}`).join(', ')} - see the Audit trail.
          </Alert>
        )}
      </div>
    </Card>
  )
}
