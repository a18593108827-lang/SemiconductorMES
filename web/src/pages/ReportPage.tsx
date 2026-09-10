import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import gsap from 'gsap'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { RefreshCw } from 'lucide-react'
import {
  fetchReportHoldApi,
  fetchReportMoveApi,
  type ReportHold,
  type ReportMove,
} from '../api/report'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function toYmd(d: Date) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function defaultRange(daysInclusive: number): { from: string; to: string } {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - (daysInclusive - 1))
  return { from: toYmd(from), to: toYmd(to) }
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function fmtDayShort(day: string) {
  if (!day || day.length < 10) return day
  return day.slice(5)
}

function fmtMinutes(v: number | null | undefined) {
  if (v == null) return '—'
  if (v < 60) return `${v} 分`
  const h = Math.floor(v / 60)
  const m = v % 60
  return m === 0 ? `${h} 时` : `${h} 时 ${m} 分`
}

const MAX_SPAN_DAYS = 31
const RANGE_HINT = `当前最多查询 ${MAX_SPAN_DAYS} 天，请缩小范围或点「近7天 / 近30天」`

function spanDays(fromStr: string, toStr: string): number | null {
  if (!fromStr || !toStr) return null
  const a = new Date(`${fromStr}T00:00:00`)
  const b = new Date(`${toStr}T00:00:00`)
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime())) return null
  return Math.floor((b.getTime() - a.getTime()) / 86_400_000) + 1
}

function isRangeLimitError(msg: string) {
  return /最多查询|跨度|超过\s*\d+\s*天/.test(msg)
}

export function ReportPage() {
  const { hasPermission } = useAuth()
  const canView = hasPermission('report:view')
  const rootRef = useRef<HTMLDivElement>(null)

  const init = useMemo(() => defaultRange(7), [])
  const [from, setFrom] = useState(init.from)
  const [to, setTo] = useState(init.to)
  const [applied, setApplied] = useState(init)
  const [rangeHint, setRangeHint] = useState('')

  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [errMsg, setErrMsg] = useState('')
  const [move, setMove] = useState<ReportMove | null>(null)
  const [hold, setHold] = useState<ReportHold | null>(null)

  const load = useCallback(async () => {
    if (!canView) return
    setLoading(true)
    setFailed(false)
    setErrMsg('')
    setRangeHint('')
    try {
      const [m, h] = await Promise.all([
        fetchReportMoveApi(applied.from, applied.to),
        fetchReportHoldApi(applied.from, applied.to),
      ])
      setMove(m)
      setHold(h)
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '加载失败'
      if (isRangeLimitError(msg)) {
        setRangeHint(msg.includes('最多查询') ? `${msg}，请缩小范围或点「近7天 / 近30天」` : RANGE_HINT)
        setFailed(false)
      } else {
        setFailed(true)
        setErrMsg(msg)
        setMove(null)
        setHold(null)
      }
    } finally {
      setLoading(false)
    }
  }, [canView, applied.from, applied.to])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (loading || failed || !rootRef.current) return
    const ms = motionMs()
    if (ms <= 0) return
    const ctx = gsap.context(() => {
      gsap.from('.report-block', {
        y: 10,
        autoAlpha: 0,
        duration: ms / 1000,
        stagger: 0.05,
        ease: 'power2.out',
        clearProps: 'all',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [loading, failed, move, hold])

  function applyPreset(days: number) {
    const r = defaultRange(days)
    setRangeHint('')
    setFrom(r.from)
    setTo(r.to)
    setApplied(r)
  }

  function onQuery() {
    const span = spanDays(from, to)
    if (span != null && span < 1) {
      setRangeHint('结束日期不能早于开始日期')
      return
    }
    if (span != null && span > MAX_SPAN_DAYS) {
      setRangeHint(RANGE_HINT)
      return
    }
    setRangeHint('')
    setApplied({ from, to })
  }

  if (!canView) {
    return (
      <div className="rounded-md border border-border bg-surface px-4 py-8 text-center text-sm text-muted">
        无报表查看权限（report:view）
      </div>
    )
  }

  const chartData = (move?.byDay ?? []).map((p) => ({
    t: fmtDayShort(p.day),
    day: p.day,
    qty: p.trackOutCount,
  }))

  const holdBars = (hold?.byReason ?? []).map((r) => ({
    name: r.reasonName || r.reasonCode || '未归属',
    code: r.reasonCode ?? '',
    count: r.holdCount,
  }))

  const partial =
    (move?.partial && (move.errors?.length ?? 0) > 0) ||
    (hold?.partial && (hold.errors?.length ?? 0) > 0)
  const partialErrors = [
    ...(move?.partial ? move.errors ?? [] : []),
    ...(hold?.partial ? hold.errors ?? [] : []),
  ]
  const generatedAt = move?.generatedAt ?? hold?.generatedAt
  const reasonCount = hold?.byReason?.length ?? 0

  return (
    <div ref={rootRef} className="space-y-3">
      <header className="report-block flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight text-ink">报表</h1>
          <p className="mt-1 text-sm text-muted">过站与锁批复盘 · 只读对账</p>
          <p className="mt-0.5 font-mono text-[11px] text-muted">
            生成 {fmtTime(generatedAt)}
            {partial ? ' · 部分延迟' : ''}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Link to="/app/history" className="text-xs text-accent hover:underline">
            履历
          </Link>
          <span className="text-muted">/</span>
          <Link to="/app/hold" className="text-xs text-accent hover:underline">
            锁批
          </Link>
        </div>
      </header>

      <div className="report-block space-y-2 rounded-md border border-border bg-surface p-3">
        <div className="flex flex-wrap items-end gap-2">
          <label className="flex flex-col gap-1 text-xs text-muted">
            起
            <input
              type="date"
              className="h-9 rounded-md border border-border bg-bg px-2 font-mono text-sm text-ink"
              value={from}
              onChange={(e) => {
                setRangeHint('')
                setFrom(e.target.value)
              }}
            />
          </label>
          <label className="flex flex-col gap-1 text-xs text-muted">
            止
            <input
              type="date"
              className="h-9 rounded-md border border-border bg-bg px-2 font-mono text-sm text-ink"
              value={to}
              onChange={(e) => {
                setRangeHint('')
                setTo(e.target.value)
              }}
            />
          </label>
          <div className="flex gap-1 pb-0.5">
            <Button type="button" variant="secondary" className="h-9 min-w-0 px-2.5 text-xs" onClick={() => applyPreset(7)}>
              近7天
            </Button>
            <Button type="button" variant="secondary" className="h-9 min-w-0 px-2.5 text-xs" onClick={() => applyPreset(30)}>
              近30天
            </Button>
          </div>
          <Button type="button" className="h-9" loading={loading} onClick={onQuery}>
            查询
          </Button>
          <Button
            type="button"
            variant="secondary"
            className="h-9 min-w-0 px-2.5"
            disabled={loading}
            onClick={() => void load()}
            aria-label="刷新"
          >
            <RefreshCw className={cn('size-3.5', loading && 'animate-spin')} />
          </Button>
        </div>
        {rangeHint ? (
          <p className="rounded-md border border-warning/40 bg-warning/10 px-3 py-1.5 text-xs text-ink" role="status">
            {rangeHint}
          </p>
        ) : null}
      </div>

      {partial && partialErrors.length > 0 && (
        <div className="report-block rounded-md border border-warning/40 bg-warning/10 px-3 py-1.5 text-xs text-ink">
          部分数据未就绪：{partialErrors.join('；')}
        </div>
      )}

      {failed && (
        <div className="report-block rounded-md border border-danger/40 bg-danger/10 px-4 py-6 text-center">
          <p className="text-sm text-ink">{errMsg || '加载失败'}</p>
          <Button type="button" className="mt-3" onClick={() => void load()}>
            重试
          </Button>
        </div>
      )}

      {!failed && (
        <>
          <div className="report-block grid grid-cols-2 gap-2 sm:grid-cols-3">
            {[
              { label: '出站合计', value: move?.totalTrackOut },
              { label: '锁批发生', value: hold?.totalHold },
              { label: '原因种类', value: reasonCount },
            ].map((item) => (
              <div key={item.label} className="rounded-md border border-border bg-surface px-3 py-2">
                <div className="text-[11px] font-medium text-muted">{item.label}</div>
                {loading && !move && !hold ? (
                  <div className="mt-1.5 h-7 w-14 animate-pulse rounded bg-border/60" />
                ) : (
                  <div className="mt-0.5 font-mono text-xl font-semibold tabular-nums text-ink">
                    {item.value ?? '—'}
                  </div>
                )}
              </div>
            ))}
          </div>

          <section className="report-block rounded-md border border-border bg-surface">
            <header className="flex items-center justify-between border-b border-border px-3 py-2">
              <span className="text-sm font-medium text-ink">过站 · 按日</span>
              <span className="font-mono text-xs text-muted">
                {applied.from} ~ {applied.to} · TRACK_OUT
              </span>
            </header>
            <div className="h-[220px] p-2">
              {loading && !move ? (
                <div className="h-full animate-pulse rounded bg-border/30" />
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData}>
                    <CartesianGrid stroke="oklch(0.9 0 0)" strokeDasharray="3 3" />
                    <XAxis
                      dataKey="t"
                      tick={{ fontSize: 12, fill: 'oklch(0.48 0.01 28)' }}
                      stroke="oklch(0.9 0 0)"
                    />
                    <YAxis
                      allowDecimals={false}
                      tick={{ fontSize: 12, fill: 'oklch(0.48 0.01 28)' }}
                      stroke="oklch(0.9 0 0)"
                    />
                    <Tooltip
                      contentStyle={{
                        background: 'oklch(1 0 0)',
                        border: '1px solid oklch(0.9 0 0)',
                        borderRadius: 6,
                        color: 'oklch(0.22 0.015 28)',
                        fontFamily: 'IBM Plex Mono, ui-monospace, monospace',
                        fontSize: 12,
                      }}
                      labelFormatter={(_, payload) => {
                        const p = payload?.[0]?.payload as { day?: string } | undefined
                        return p?.day ?? ''
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="qty"
                      name="出站"
                      stroke="oklch(0.58 0.12 230)"
                      fill="oklch(0.58 0.12 230 / 0.2)"
                      strokeWidth={2}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              )}
            </div>
          </section>

          <section className="report-block rounded-md border border-border bg-surface">
            <header className="border-b border-border px-3 py-2 text-sm font-medium text-ink">过站 · 按站</header>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[480px] text-left text-sm">
                <thead className="border-b border-border bg-bg/60 text-xs text-muted">
                  <tr>
                    <th className="px-3 py-2 font-medium">工序</th>
                    <th className="px-3 py-2 font-medium">代码</th>
                    <th className="px-3 py-2 text-right font-medium">出站数</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {loading && !move
                    ? Array.from({ length: 3 }).map((_, i) => (
                        <tr key={i}>
                          <td colSpan={3} className="px-3 py-3">
                            <div className="h-4 animate-pulse rounded bg-border/40" />
                          </td>
                        </tr>
                      ))
                    : null}
                  {(move?.byStep ?? []).map((row, i) => (
                    <tr key={`${row.stepId ?? 'x'}-${i}`} className="hover:bg-bg/80">
                      <td className="px-3 py-2 text-ink">{row.stepName || '—'}</td>
                      <td className="px-3 py-2 font-mono text-xs text-muted">{row.stepCode || '—'}</td>
                      <td className="px-3 py-2 text-right font-mono tabular-nums text-ink">
                        {row.trackOutCount}
                      </td>
                    </tr>
                  ))}
                  {!loading && move && move.byStep.length === 0 && (
                    <tr>
                      <td colSpan={3} className="px-3 py-8 text-center text-muted">
                        窗内无出站记录
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <section className="report-block grid gap-2 lg:grid-cols-5">
            <div className="rounded-md border border-border bg-surface lg:col-span-3">
              <header className="border-b border-border px-3 py-2 text-sm font-medium text-ink">
                锁批 · 按原因
              </header>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] text-left text-sm">
                  <thead className="border-b border-border bg-bg/60 text-xs text-muted">
                    <tr>
                      <th className="px-3 py-2 font-medium">原因</th>
                      <th className="px-3 py-2 font-medium">代码</th>
                      <th className="px-3 py-2 text-right font-medium">发生</th>
                      <th className="px-3 py-2 text-right font-medium">仍锁</th>
                      <th className="px-3 py-2 text-right font-medium">平均时长</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {loading && !hold
                      ? Array.from({ length: 3 }).map((_, i) => (
                          <tr key={i}>
                            <td colSpan={5} className="px-3 py-3">
                              <div className="h-4 animate-pulse rounded bg-border/40" />
                            </td>
                          </tr>
                        ))
                      : null}
                    {(hold?.byReason ?? []).map((row, i) => (
                      <tr key={`${row.reasonCode ?? 'x'}-${i}`} className="hover:bg-bg/80">
                        <td className="px-3 py-2 text-ink">{row.reasonName || '未归属'}</td>
                        <td className="px-3 py-2 font-mono text-xs text-muted">{row.reasonCode || '—'}</td>
                        <td className="px-3 py-2 text-right font-mono tabular-nums text-ink">
                          {row.holdCount}
                        </td>
                        <td className="px-3 py-2 text-right font-mono tabular-nums text-warning">
                          {row.activeCount}
                        </td>
                        <td className="px-3 py-2 text-right font-mono text-xs tabular-nums text-muted">
                          {fmtMinutes(row.avgDurationMinutes)}
                        </td>
                      </tr>
                    ))}
                    {!loading && hold && hold.byReason.length === 0 && (
                      <tr>
                        <td colSpan={5} className="px-3 py-8 text-center text-muted">
                          窗内无锁批发生
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="rounded-md border border-border bg-surface lg:col-span-2">
              <header className="border-b border-border px-3 py-2 text-sm font-medium text-ink">
                原因分布
              </header>
              <div className="h-[240px] p-2">
                {loading && !hold ? (
                  <div className="h-full animate-pulse rounded bg-border/30" />
                ) : holdBars.length === 0 ? (
                  <p className="flex h-full items-center justify-center text-sm text-muted">无数据</p>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={holdBars} layout="vertical" margin={{ left: 8, right: 12 }}>
                      <CartesianGrid stroke="oklch(0.9 0 0)" strokeDasharray="3 3" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11, fill: 'oklch(0.48 0.01 28)' }} />
                      <YAxis
                        type="category"
                        dataKey="name"
                        width={72}
                        tick={{ fontSize: 11, fill: 'oklch(0.48 0.01 28)' }}
                      />
                      <Tooltip
                        contentStyle={{
                          background: 'oklch(1 0 0)',
                          border: '1px solid oklch(0.9 0 0)',
                          borderRadius: 6,
                          fontSize: 12,
                        }}
                      />
                      <Bar dataKey="count" name="发生" fill="oklch(0.62 0.11 55)" radius={[0, 2, 2, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
