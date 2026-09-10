import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import {
  fetchDashboardOverviewApi,
  type DashboardOverview,
} from '../api/dashboard'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { LiveDot, mesEqpStatusLabel } from '../components/ui/StatusPill'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import type { MesEqpStatus } from '../api/eqp'

const POLL_MS = 20_000

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function fmtDayShort(day: string) {
  if (!day || day.length < 10) return day
  return day.slice(5)
}

function eqpTone(status: string) {
  const m = mesEqpStatusLabel[status as MesEqpStatus]
  return m?.dot ?? 'bg-muted'
}

function eqpLetter(status: string) {
  const m = mesEqpStatusLabel[status as MesEqpStatus]
  return m?.letter ?? '?'
}

export function DashboardPage() {
  const { hasPermission } = useAuth()
  const navigate = useNavigate()
  const canView = hasPermission('dashboard:view')

  const [paused, setPaused] = useState(false)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [errMsg, setErrMsg] = useState('')
  const [data, setData] = useState<DashboardOverview | null>(null)
  const pausedRef = useRef(paused)
  pausedRef.current = paused

  const load = useCallback(async (silent = false) => {
    if (!canView) return
    if (!silent) setLoading(true)
    try {
      const vo = await fetchDashboardOverviewApi({ trendDays: 7, alarmLimit: 20 })
      setData(vo)
      setFailed(false)
      setErrMsg('')
    } catch (e) {
      setFailed(true)
      setErrMsg(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [canView])

  useEffect(() => {
    void load(false)
  }, [load])

  useEffect(() => {
    if (!canView || paused) return
    const id = window.setInterval(() => {
      if (!pausedRef.current) void load(true)
    }, POLL_MS)
    return () => window.clearInterval(id)
  }, [canView, paused, load])

  if (!canView) {
    return (
      <div className="rounded-md border border-border bg-surface px-4 py-8 text-center text-sm text-muted">
        无看板查看权限（dashboard:view）
      </div>
    )
  }

  const kpi = data?.kpi
  const chartData = (data?.outputTrend ?? []).map((p) => ({
    t: fmtDayShort(p.day),
    day: p.day,
    qty: p.trackOutCount,
  }))

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-2">
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <h1 className="text-lg font-semibold text-ink">生产看板</h1>
          <p className="font-mono text-[11px] text-muted">
            更新 {fmtTime(data?.generatedAt)}
            {data?.partial ? ' · 部分延迟' : ''}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <LiveDot paused={paused} />
          <Button
            type="button"
            variant="secondary"
            className="h-8 min-w-0 px-2.5 text-xs"
            onClick={() => setPaused((v) => !v)}
          >
            {paused ? '恢复' : '暂停'}
          </Button>
          <Button
            type="button"
            variant="secondary"
            className="h-8 min-w-0 px-2.5 text-xs"
            disabled={loading}
            onClick={() => void load(false)}
          >
            <RefreshCw className={cn('size-3.5', loading && 'animate-spin')} />
            刷新
          </Button>
        </div>
      </div>

      {data?.partial && data.errors?.length > 0 && (
        <div className="shrink-0 rounded-md border border-warning/40 bg-warning/10 px-3 py-1.5 text-xs text-ink">
          部分数据未就绪：{data.errors.join('；')}
        </div>
      )}

      {failed && !data && (
        <div className="shrink-0 rounded-md border border-danger/40 bg-danger/10 px-4 py-6 text-center">
          <p className="text-sm text-ink">{errMsg || '加载失败'}</p>
          <Button type="button" className="mt-3" onClick={() => void load(false)}>
            重试
          </Button>
        </div>
      )}

      <div className="grid shrink-0 grid-cols-2 gap-2 lg:grid-cols-4">
        {[
          { label: '在制', value: kpi?.wipCount, tone: 'text-ink' },
          { label: '锁批', value: kpi?.holdActiveCount, tone: 'text-warning' },
          { label: '报警', value: kpi?.alarmOpenCount, tone: 'text-danger' },
          { label: '故障', value: kpi?.eqpDownCount, tone: 'text-danger' },
        ].map((item) => (
          <div key={item.label} className="rounded-md border border-border bg-surface px-3 py-2">
            <div className="text-[11px] font-medium text-muted">{item.label}</div>
            {loading && !data ? (
              <div className="mt-1.5 h-7 w-14 animate-pulse rounded bg-border/60" />
            ) : (
              <div className={cn('mt-0.5 font-mono text-xl font-semibold tabular-nums', item.tone)}>
                {item.value ?? '—'}
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="grid min-h-0 flex-1 gap-2 lg:grid-cols-3">
        <section className="flex min-h-0 flex-col rounded-md border border-border bg-surface lg:col-span-2">
          <header className="flex shrink-0 items-center justify-between border-b border-border px-3 py-2">
            <span className="text-sm font-medium text-ink">设备状态矩阵</span>
            <span className="font-mono text-xs text-muted">
              运 {kpi?.eqpRunningCount ?? '—'} / 共 {kpi?.eqpTotal ?? '—'}
            </span>
          </header>
          <div className="min-h-0 flex-1 overflow-auto p-2">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6">
              {loading && !data
                ? Array.from({ length: 10 }).map((_, i) => (
                    <div key={i} className="h-16 animate-pulse rounded-md border border-border bg-bg" />
                  ))
                : null}
              {(data?.equipment ?? []).map((eq) => {
                const down = eq.status === 'down'
                return (
                  <div
                    key={String(eq.id)}
                    className={cn(
                      'rounded-md border bg-bg p-2.5',
                      down ? 'border-danger/50' : 'border-border',
                    )}
                  >
                    <div className="flex items-center gap-1.5">
                      <span className={cn('size-2 shrink-0 rounded-sm', eqpTone(eq.status))} />
                      <span className="font-mono text-[11px] text-muted">{eqpLetter(eq.status)}</span>
                      <span className="truncate text-sm font-medium text-ink">{eq.name}</span>
                    </div>
                    <div className="mt-1.5 font-mono text-[11px] text-muted">{eq.eqpCode}</div>
                    <div className="mt-0.5 truncate font-mono text-[11px] text-ink">
                      {eq.currentLotNo ?? '—'}
                    </div>
                  </div>
                )
              })}
              {!loading && data && data.equipment.length === 0 && (
                <p className="col-span-full py-8 text-center text-sm text-muted">暂无启用设备</p>
              )}
            </div>
          </div>
        </section>

        <section className="flex min-h-[200px] flex-col rounded-md border border-border bg-surface lg:min-h-0">
          <header className="flex shrink-0 items-center justify-between border-b border-border px-3 py-2">
            <span className="text-sm font-medium text-ink">报警流</span>
            <button
              type="button"
              className="text-xs text-accent hover:underline"
              onClick={() => navigate('/app/alarm')}
            >
              告警台
            </button>
          </header>
          <ul className="min-h-0 flex-1 divide-y divide-border overflow-y-auto">
            {loading && !data
              ? Array.from({ length: 4 }).map((_, i) => (
                  <li key={i} className="px-3 py-2.5">
                    <div className="h-10 animate-pulse rounded bg-border/40" />
                  </li>
                ))
              : null}
            {(data?.alarms ?? []).map((a) => (
              <li key={String(a.id)} className="px-3 py-2.5 text-sm">
                <div className="flex items-center gap-2">
                  <span
                    className={cn(
                      'size-1.5 shrink-0 rounded-full',
                      a.level === 'CRITICAL' && 'bg-danger',
                      a.level === 'WARNING' && 'bg-warning',
                      a.level === 'INFO' && 'bg-accent',
                      !['CRITICAL', 'WARNING', 'INFO'].includes(a.level) && 'bg-muted',
                    )}
                  />
                  {a.level === 'CRITICAL' && (
                    <AlertTriangle className="size-3 shrink-0 text-danger" aria-hidden />
                  )}
                  <span className="truncate font-mono text-xs text-muted">{a.source}</span>
                  <span className="ml-auto shrink-0 font-mono text-[10px] text-muted">{a.status}</span>
                </div>
                <p className="mt-1 line-clamp-2 text-ink">{a.message}</p>
                <p className="mt-0.5 font-mono text-[11px] text-muted">{fmtTime(a.raisedAt)}</p>
              </li>
            ))}
            {!loading && data && data.alarms.length === 0 && (
              <li className="px-3 py-8 text-center text-sm text-muted">暂无未关闭报警</li>
            )}
          </ul>
        </section>
      </div>

      <section className="flex h-[24vh] min-h-[160px] max-h-[260px] shrink-0 flex-col rounded-md border border-border bg-surface">
        <header className="flex shrink-0 items-center justify-between border-b border-border px-3 py-2">
          <span className="text-sm font-medium text-ink">产出趋势（TrackOut · 近 7 日）</span>
          <button
            type="button"
            className="text-xs text-accent hover:underline"
            onClick={() => navigate('/app/report')}
          >
            打开报表
          </button>
        </header>
        <div className="min-h-0 flex-1 p-1.5">
          {loading && !data ? (
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
                  name="TrackOut"
                  stroke="oklch(0.58 0.12 230)"
                  fill="oklch(0.58 0.12 230 / 0.2)"
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>
    </div>
  )
}
