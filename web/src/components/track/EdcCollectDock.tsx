import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import gsap from 'gsap'
import { AlertTriangle, CheckCircle2, CircleDot, Ruler } from 'lucide-react'
import {
  getEdcPlanApi,
  getEdcSpecApi,
  getLatestEdcCollectionApi,
  listEdcPlansApi,
  listEdcSpecsApi,
  submitEdcCollectionApi,
  type MesEdcCollection,
  type MesEdcPlan,
  type MesEdcPlanItem,
  type MesEdcSpecItem,
} from '../../api/edc'
import type { MesTxLogItem } from '../../api/track'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../ui/Button'
import { Drawer } from '../ui/Drawer'
import { ApiError } from '../../lib/http'
import { cn } from '../../lib/cn'
import { motionMs } from '../../lib/motion'

type Props = {
  lotId: number | string
  lotNo: string
  stepId: number | string
  stepLabel: string
  productCode: string | null
  history: MesTxLogItem[]
}

function toNum(v: number | string | null | undefined): number | null {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function isOos(value: number, usl: number | null, lsl: number | null) {
  if (lsl != null && value < lsl) return true
  return usl != null && value > usl
}

function latestTrackInId(history: MesTxLogItem[], stepId: number | string) {
  const hits = history.filter(
    (h) => h.txType === 'TRACK_IN' && (h.stepId == null || String(h.stepId) === String(stepId)),
  )
  const pool = hits.length > 0 ? hits : history.filter((h) => h.txType === 'TRACK_IN')
  if (pool.length === 0) return null
  return String(pool[pool.length - 1].id)
}

function ResultMark({ result }: { result: string }) {
  const pass = result === 'PASS'
  const fail = result === 'FAIL' || result === 'OOS'
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center gap-1.5 rounded-sm px-2 text-xs font-medium',
        pass && 'bg-success/15 text-success',
        fail && 'bg-danger/15 text-danger',
        !pass && !fail && 'bg-field-surface text-field-muted',
      )}
    >
      {pass ? (
        <CheckCircle2 className="size-3.5" aria-hidden />
      ) : fail ? (
        <AlertTriangle className="size-3.5" aria-hidden />
      ) : (
        <CircleDot className="size-3.5" aria-hidden />
      )}
      {pass ? 'PASS' : fail ? (result === 'OOS' ? 'OOS' : 'FAIL') : result}
    </span>
  )
}

function SpecRail({
  lsl,
  usl,
  value,
}: {
  lsl: number | null
  usl: number | null
  value: number | null
}) {
  if (lsl == null && usl == null) return null
  const lo = lsl ?? (usl != null && value != null ? Math.min(value, usl) : usl ?? 0)
  const hi = usl ?? (lsl != null && value != null ? Math.max(value, lsl) : lsl ?? 1)
  const span = hi - lo
  const pct =
    value == null || span === 0 ? null : Math.min(1.08, Math.max(-0.08, (value - lo) / span))
  const oos = value != null && isOos(value, usl, lsl)

  return (
    <div className="mt-2">
      <div className="relative h-1.5 overflow-visible rounded-full bg-field-border/80">
        {pct != null ? (
          <span
            className={cn(
              'absolute top-1/2 size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full',
              oos ? 'bg-danger' : 'bg-accent',
            )}
            style={{ left: `${((pct + 0.08) / 1.16) * 100}%` }}
            aria-hidden
          />
        ) : null}
      </div>
      <div className="mt-1 flex justify-between font-mono text-[11px] tabular-nums text-field-muted">
        <span>{lsl != null ? `LSL ${lsl}` : '无下限'}</span>
        <span>{usl != null ? `USL ${usl}` : '无上限'}</span>
      </div>
    </div>
  )
}

async function resolveSpec(
  item: MesEdcPlanItem,
  productCode: string | null,
): Promise<MesEdcSpecItem | null> {
  if (item.specId != null && item.specId !== '') {
    try {
      return await getEdcSpecApi(item.specId)
    } catch {
      return null
    }
  }
  const product = productCode?.trim() ?? ''
  if (product) {
    const byProduct = await listEdcSpecsApi({
      paramId: item.paramId,
      productCode: product,
      status: 'active',
      page: 1,
      size: 5,
    })
    if (byProduct.records?.[0]) return byProduct.records[0]
  }
  const fallback = await listEdcSpecsApi({
    paramId: item.paramId,
    productCode: '',
    status: 'active',
    page: 1,
    size: 5,
  })
  return fallback.records?.[0] ?? null
}

export function EdcCollectDock({
  lotId,
  lotNo,
  stepId,
  stepLabel,
  productCode,
  history,
}: Props) {
  const { hasPermission } = useAuth()
  const canView = hasPermission('edc:view') || hasPermission('edc:collect')
  const canCollect = hasPermission('edc:collect')

  const [plan, setPlan] = useState<MesEdcPlan | null>(null)
  const [latest, setLatest] = useState<MesEdcCollection | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [values, setValues] = useState<Record<string, string>>({})
  const [limits, setLimits] = useState<Record<string, { usl: number | null; lsl: number | null }>>(
    {},
  )
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const resultRef = useRef<HTMLDivElement>(null)

  const trackInTxId = useMemo(() => latestTrackInId(history, stepId), [history, stepId])

  const load = useCallback(async () => {
    if (!canView) {
      setPlan(null)
      setLatest(null)
      setLoading(false)
      return
    }
    setLoading(true)
    setLoadFailed(false)
    try {
      const page = await listEdcPlansApi({ stepId, enabled: 1, page: 1, size: 1 })
      const head = page.records?.[0] ?? null
      if (!head) {
        setPlan(null)
        setLatest(null)
        return
      }
      const full = await getEdcPlanApi(head.id)
      setPlan(full)
      if (!trackInTxId) {
        setLatest(null)
        return
      }
      const col = await getLatestEdcCollectionApi(lotId, trackInTxId)
      setLatest(col)
    } catch {
      setPlan(null)
      setLatest(null)
      setLoadFailed(true)
    } finally {
      setLoading(false)
    }
  }, [canView, stepId, lotId, trackInTxId])

  useEffect(() => {
    void load()
  }, [load])

  const items = plan?.items ?? []

  async function openDrawer() {
    setError('')
    setOpen(true)
    const next: Record<string, string> = {}
    const lim: Record<string, { usl: number | null; lsl: number | null }> = {}
    const latestMap = new Map(
      (latest?.items ?? []).map((it) => [String(it.paramId), it] as const),
    )
    await Promise.all(
      items.map(async (it) => {
        const key = String(it.paramId)
        const prev = latestMap.get(key)
        next[key] = prev?.valueNum != null ? String(prev.valueNum) : ''
        if (prev?.uslSnap != null || prev?.lslSnap != null) {
          lim[key] = { usl: toNum(prev.uslSnap), lsl: toNum(prev.lslSnap) }
          return
        }
        const spec = await resolveSpec(it, productCode)
        lim[key] = { usl: toNum(spec?.usl), lsl: toNum(spec?.lsl) }
      }),
    )
    setValues(next)
    setLimits(lim)
  }

  async function submit() {
    if (!plan || !trackInTxId) {
      setError('找不到本趟开工履历，请重新载入批次')
      return
    }
    const payload: { paramId: string; value: string }[] = []
    for (const it of items) {
      const key = String(it.paramId)
      const raw = (values[key] ?? '').trim()
      if (!raw) {
        if (it.mandatory !== 0) {
          setError(`必采项未填：${it.paramCode ?? key}`)
          return
        }
        continue
      }
      if (toNum(raw) == null) {
        setError(`数值无效：${it.paramCode ?? key}`)
        return
      }
      payload.push({ paramId: key, value: raw })
    }
    setSaving(true)
    setError('')
    try {
      const created = await submitEdcCollectionApi({
        lotId,
        trackInTxId,
        items: payload,
      })
      setLatest(created)
      const el = resultRef.current
      if (el && motionMs() > 0) {
        gsap.fromTo(
          el,
          { backgroundColor: 'oklch(0.58 0.12 230 / 0.22)' },
          { backgroundColor: 'transparent', duration: 0.55, ease: 'power2.out' },
        )
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '提交失败')
    } finally {
      setSaving(false)
    }
  }

  if (!canView) return null
  if (loading) {
    return (
      <div className="h-[52px] animate-pulse rounded-md border border-field-border bg-field-bg" />
    )
  }
  if (loadFailed) {
    return (
      <div className="rounded-md border border-field-border bg-field-bg px-3 py-2">
        <p className="text-xs text-field-muted">量测计划加载失败</p>
        <button
          type="button"
          className="mt-1 cursor-pointer text-xs text-accent hover:underline"
          onClick={() => void load()}
        >
          重试
        </button>
      </div>
    )
  }
  if (!plan || items.length === 0) return null

  const pending = latest == null
  const fail = latest?.result === 'FAIL'
  const pass = latest?.result === 'PASS'
  const label = pending ? '待采' : fail ? 'FAIL' : 'PASS'
  const hint = pending
    ? plan.required === 1
      ? '出门禁 · 尚未采集'
      : '本站可采集'
    : fail
      ? '最新判定超限或缺必采'
      : '本趟最新判定合格'

  return (
    <>
      <button
        type="button"
        onClick={() => void openDrawer()}
        className={cn(
          'flex w-full cursor-pointer items-center justify-between gap-3 rounded-md border px-3 py-2 text-left transition-colors duration-150',
          fail
            ? 'border-danger/40 bg-danger/10 hover:bg-danger/15'
            : pass
              ? 'border-success/40 bg-success/10 hover:bg-success/15'
              : 'border-accent/30 bg-accent/10 hover:bg-accent/15',
        )}
      >
        <span className="flex items-center gap-1.5 text-xs font-medium text-field-ink">
          <Ruler
            className={cn(
              'size-3.5',
              fail ? 'text-danger' : pass ? 'text-success' : 'text-accent',
            )}
            aria-hidden
          />
          量测 EDC · {plan.required === 1 ? '出门禁' : '不拦'}
        </span>
        <span className="flex items-center gap-2">
          <span className="text-[11px] text-field-muted">{hint}</span>
          <ResultMark result={pending ? '待采' : latest.result} />
        </span>
      </button>

      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        title="量测采集"
        width={560}
        tone="field"
        footer={
          canCollect ? (
            <>
              <Button
                variant="ghost"
                size="field"
                className="text-field-ink hover:bg-field-surface"
                onClick={() => setOpen(false)}
              >
                关闭
              </Button>
              <Button size="field" loading={saving} onClick={() => void submit()}>
                提交采集
              </Button>
            </>
          ) : undefined
        }
      >
        <div className="space-y-4">
          <div ref={resultRef} className="rounded-md px-1 py-1">
            <div className="font-mono text-lg font-semibold tracking-tight">{lotNo}</div>
            <p className="mt-0.5 text-sm text-field-muted">{stepLabel}</p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {latest ? <ResultMark result={latest.result} /> : <ResultMark result="待采" />}
              <span className="text-xs text-field-muted">{label} · 同 visit 可重采</span>
            </div>
          </div>

          {!trackInTxId ? (
            <p className="text-sm text-danger">找不到本趟开工履历，请重新载入批次后再采</p>
          ) : null}

          {items.length === 0 ? (
            <p className="text-sm text-field-muted">计划未配置采集项</p>
          ) : (
            <div className="space-y-2">
              {items.map((it) => {
                const key = String(it.paramId)
                const raw = values[key] ?? ''
                const num = toNum(raw)
                const lim = limits[key] ?? { usl: null, lsl: null }
                const oos = num != null && isOos(num, lim.usl, lim.lsl)
                return (
                  <div
                    key={key}
                    className={cn(
                      'rounded-md border bg-field-surface p-3',
                      oos ? 'border-danger/50' : 'border-field-border',
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <div className="font-mono text-sm text-accent">{it.paramCode ?? '—'}</div>
                        <div className="text-xs text-field-muted">
                          {it.paramName ?? ''}
                          {it.unit ? ` · ${it.unit}` : ''}
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        {it.mandatory !== 0 ? (
                          <span className="text-[11px] text-warning">必采</span>
                        ) : (
                          <span className="text-[11px] text-field-muted">选采</span>
                        )}
                        {oos ? <ResultMark result="OOS" /> : null}
                      </div>
                    </div>
                    <label className="mt-2 flex flex-col gap-1">
                      <span className="text-xs font-medium text-field-muted">量测值</span>
                      <input
                        inputMode="decimal"
                        className="h-11 rounded-md border border-field-border bg-field-bg px-3 font-mono text-base tabular-nums text-field-ink outline-none focus:border-accent"
                        value={raw}
                        disabled={!canCollect}
                        onChange={(e) => setValues((m) => ({ ...m, [key]: e.target.value }))}
                        placeholder={it.mandatory !== 0 ? '必填' : '可空'}
                      />
                    </label>
                    <SpecRail lsl={lim.lsl} usl={lim.usl} value={num} />
                  </div>
                )
              })}
            </div>
          )}

          {error ? <p className="text-sm text-danger">{error}</p> : null}
          {!canCollect ? (
            <p className="text-xs text-field-muted">无采集权限（需要 edc:collect）</p>
          ) : null}
        </div>
      </Drawer>
    </>
  )
}
