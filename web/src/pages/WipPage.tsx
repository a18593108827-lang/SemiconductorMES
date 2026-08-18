import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import gsap from 'gsap'
import { ExternalLink, RefreshCw, Search } from 'lucide-react'
import {
  listWipApi,
  wipSummaryByStepApi,
  type MesWipItem,
  type MesWipStepSummary,
  type WipStatus,
} from '../api/wip'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { HotLotPill, MesLotStatusPill, mesLotStatusLabel } from '../components/ui/StatusPill'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

const STATUS_FILTERS: Array<{ key: WipStatus | 'All'; label: string }> = [
  { key: 'All', label: '全部在制' },
  { key: 'wait', label: mesLotStatusLabel.wait.label },
  { key: 'processing', label: mesLotStatusLabel.processing.label },
  { key: 'held', label: mesLotStatusLabel.held.label },
]

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function stepLabel(s: MesWipStepSummary) {
  const name = s.stepName || s.stepCode || (s.stepId != null ? `工序 ${s.stepId}` : '未定站')
  return s.sortNo != null ? `S${s.sortNo} · ${name}` : name
}

export function WipPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const summaryRef = useRef<HTMLDivElement>(null)

  const [rows, setRows] = useState<MesWipItem[]>([])
  const [summary, setSummary] = useState<MesWipStepSummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<WipStatus | 'All'>('All')
  const [sortFilter, setSortFilter] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [summaryLoading, setSummaryLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)

  const size = 20
  const canView = hasPermission('wip:list')
  const totalPages = Math.max(1, Math.ceil(total / size))

  const flashSummary = useCallback(() => {
    const el = summaryRef.current
    if (!el) return
    const dur = motionMs() / 1000
    if (dur <= 0) return
    gsap.fromTo(
      el,
      { boxShadow: '0 0 0 0 oklch(0.58 0.12 230 / 0)' },
      {
        boxShadow: '0 0 0 2px oklch(0.58 0.12 230 / 0.45)',
        duration: dur,
        yoyo: true,
        repeat: 1,
        ease: 'power2.out',
      },
    )
  }, [])

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true)
    try {
      const data = await wipSummaryByStepApi()
      setSummary(data)
      flashSummary()
    } catch (err) {
      setSummary([])
      toast.error(err instanceof ApiError ? err.message : '汇总加载失败')
    } finally {
      setSummaryLoading(false)
    }
  }, [flashSummary, toast])

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listWipApi({
        keyword,
        status: statusFilter === 'All' ? '' : statusFilter,
        currentSortNo: sortFilter,
        page,
        size,
      })
      setRows(data.records)
      setTotal(data.total)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '在制加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, page, sortFilter, statusFilter, toast])

  const refreshAll = useCallback(async () => {
    await Promise.all([loadSummary(), loadList()])
  }, [loadList, loadSummary])

  useEffect(() => {
    if (!canView) return
    void loadSummary()
  }, [canView, loadSummary])

  useEffect(() => {
    if (!canView) return
    void loadList()
  }, [canView, loadList])

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">在制查询</h1>
        <p className="text-sm text-muted">无权限查看在制（需要 wip:list）</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">在制查询</h1>
          <p className="mt-1 text-sm text-muted">只读视图 · 状态来自 Track · 回答「货在哪、能不能动」</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => void refreshAll()} disabled={loading || summaryLoading}>
            <RefreshCw className={cn('size-4', (loading || summaryLoading) && 'animate-spin')} aria-hidden />
            刷新
          </Button>
          <Link
            to="/track"
            className="inline-flex h-9 cursor-pointer items-center gap-2 rounded-md border border-border bg-surface px-3.5 text-sm font-medium text-ink hover:bg-border/40"
          >
            现场台
            <ExternalLink className="size-3.5 text-muted" aria-hidden />
          </Link>
        </div>
      </header>

      <section
        ref={summaryRef}
        className="rounded-md border border-border bg-surface p-3"
        aria-label="按站汇总"
      >
        <div className="mb-2 flex items-center justify-between gap-2">
          <h2 className="text-xs font-medium text-muted">按站排队</h2>
          {sortFilter != null ? (
            <button
              type="button"
              className="cursor-pointer text-xs text-accent hover:underline"
              onClick={() => {
                setSortFilter(null)
                setPage(1)
              }}
            >
              清除站筛选
            </button>
          ) : null}
        </div>
        {summaryLoading && summary.length === 0 ? (
          <p className="py-2 text-sm text-muted">汇总加载中…</p>
        ) : summary.length === 0 ? (
          <p className="py-2 text-sm text-muted">当前无在制分布</p>
        ) : (
          <ul className="flex gap-2 overflow-x-auto pb-1">
            {summary.map((s) => {
              const active = sortFilter != null && s.sortNo === sortFilter
              const key = `${s.sortNo ?? 'x'}-${s.stepId ?? 'n'}`
              return (
                <li key={key} className="shrink-0">
                  <button
                    type="button"
                    onClick={() => {
                      setSortFilter(s.sortNo)
                      setPage(1)
                    }}
                    className={cn(
                      'min-w-[140px] cursor-pointer rounded-md border px-3 py-2 text-left transition-colors duration-150',
                      active
                        ? 'border-primary bg-primary/10'
                        : 'border-border bg-bg hover:bg-border/30',
                    )}
                  >
                    <div className="truncate text-xs font-medium text-ink">{stepLabel(s)}</div>
                    <div className="mt-1 flex gap-2 font-mono text-[11px] text-muted">
                      <span title="待加工">等 {s.waitCount}</span>
                      <span title="加工中">做 {s.processingCount}</span>
                      {s.heldCount > 0 ? <span title="锁批">锁 {s.heldCount}</span> : null}
                      <span className="text-ink">合 {s.total}</span>
                    </div>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <div className="flex flex-wrap items-center gap-2">
        <input
          className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
          placeholder="批次号 / 产品 / 客户Lot"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setPage(1)
              setKeyword(q.trim())
            }
          }}
          aria-label="搜索在制"
        />
        <Button
          variant="secondary"
          onClick={() => {
            setPage(1)
            setKeyword(q.trim())
          }}
        >
          <Search className="size-4" aria-hidden />
          搜索
        </Button>
        <div className="flex flex-wrap gap-1.5 sm:ml-2">
          {STATUS_FILTERS.map((s) => (
            <button
              key={s.key}
              type="button"
              onClick={() => {
                setStatusFilter(s.key)
                setPage(1)
              }}
              className={cn(
                'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors',
                statusFilter === s.key
                  ? 'border-primary bg-primary text-white'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[960px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">批次号</th>
              <th className="px-3">状态</th>
              <th className="px-3">当前站</th>
              <th className="px-3">产品</th>
              <th className="px-3">数量</th>
              <th className="px-3">优先级</th>
              <th className="px-3">急度</th>
              <th className="px-3">设备</th>
              <th className="px-3">路线</th>
              <th className="px-3">更新</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={11} className="px-3 py-8 text-center text-muted">
                  加载中…
                </td>
              </tr>
            ) : loadFailed ? (
              <tr>
                <td colSpan={11} className="px-3 py-8 text-center text-muted">
                  加载失败，请刷新重试
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={11} className="px-3 py-10 text-center">
                  <p className="text-sm text-muted">当前无在制批次</p>
                  <Link to="/app/lots" className="mt-2 inline-block text-sm text-accent hover:underline">
                    去批次管理放行
                  </Link>
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr
                  key={String(row.lotId)}
                  className={cn(
                    'h-10 border-b border-border hover:bg-surface/80',
                    row.hotFlag === 1 && 'border-l-2 border-l-warning bg-warning/[0.04]',
                  )}
                >
                  <td className="px-3 font-mono text-[13px]">{row.lotNo}</td>
                  <td className="px-3">
                    <MesLotStatusPill status={row.status} />
                  </td>
                  <td className="px-3 font-mono text-[13px]">
                    {row.currentSortNo != null ? `S${row.currentSortNo}` : '—'}
                    {row.currentStepName ? (
                      <span className="ml-1.5 font-sans text-muted">{row.currentStepName}</span>
                    ) : null}
                  </td>
                  <td className="px-3 font-mono text-[13px]">{row.productCode ?? '—'}</td>
                  <td className="px-3 font-mono tabular-nums">{row.qty}</td>
                  <td className="px-3 font-mono tabular-nums">{row.priority}</td>
                  <td className="px-3">
                    <HotLotPill hot={row.hotFlag} />
                    {row.hotFlag !== 1 ? <span className="text-xs text-muted">—</span> : null}
                  </td>
                  <td className="px-3 font-mono text-[13px] text-muted">
                    {row.currentEqpId ?? '—'}
                  </td>
                  <td className="px-3">
                    <span className="font-mono text-[13px]">{row.routeCode ?? '—'}</span>
                    {row.routeVersionNo != null ? (
                      <span className="ml-1 font-mono text-xs text-muted">v{row.routeVersionNo}</span>
                    ) : null}
                  </td>
                  <td className="px-3 font-mono text-[13px] text-muted">{fmtTime(row.updateTime)}</td>
                  <td className="px-3">
                    <Link
                      to="/track"
                      className="text-xs font-medium text-accent hover:underline"
                      title={`到现场台载入 ${row.lotNo}`}
                    >
                      过账
                    </Link>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between gap-3 text-sm text-muted">
        <span>
          共 {total} 批
          {sortFilter != null ? ` · 站序 S${sortFilter}` : ''}
        </span>
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            disabled={page <= 1 || loading}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </Button>
          <span className="font-mono text-xs">
            {page} / {totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={page >= totalPages || loading}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </div>
      </div>
    </div>
  )
}
