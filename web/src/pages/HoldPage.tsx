import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import gsap from 'gsap'
import { CalendarClock, Lock, Pause, Search, Unlock, X } from 'lucide-react'
import {
  cancelFutureHoldApi,
  createFutureHoldApi,
  createHoldApi,
  listFutureHoldsApi,
  listHoldReasonsApi,
  listHoldsApi,
  releaseHoldApi,
  type FutureHoldStatus,
  type FutureHoldTiming,
  type HoldStatus,
  type MesFutureHoldItem,
  type MesHoldItem,
  type MesHoldReason,
} from '../api/hold'
import { listLotsApi, type MesLotItem } from '../api/lot'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

type MainTab = 'hold' | 'future'

const HOLD_STATUS_FILTERS: Array<{ key: HoldStatus | 'all'; label: string }> = [
  { key: 'active', label: '生效中' },
  { key: 'released', label: '已解锁' },
  { key: 'all', label: '全部' },
]

const FUTURE_STATUS_FILTERS: Array<{ key: FutureHoldStatus | 'all'; label: string }> = [
  { key: 'pending', label: '未生效' },
  { key: 'activated', label: '已激活' },
  { key: 'cancelled', label: '已取消' },
  { key: 'all', label: '全部' },
]

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function HoldStatusPill({ status }: { status: HoldStatus }) {
  const active = status === 'active'
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', active ? 'bg-warning' : 'bg-muted')} aria-hidden />
      <Pause className={cn('size-3', active ? 'text-warning' : 'text-muted')} aria-hidden />
      {active ? '生效中' : '已解锁'}
    </span>
  )
}

function FutureStatusPill({ status }: { status: FutureHoldStatus }) {
  const map = {
    pending: { label: '未生效', dot: 'bg-accent', icon: 'text-accent' },
    activated: { label: '已激活', dot: 'bg-warning', icon: 'text-warning' },
    cancelled: { label: '已取消', dot: 'bg-muted', icon: 'text-muted' },
  } as const
  const m = map[status]
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      <CalendarClock className={cn('size-3', m.icon)} aria-hidden />
      {m.label}
    </span>
  )
}

export function HoldPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const rootRef = useRef<HTMLDivElement>(null)

  const [mainTab, setMainTab] = useState<MainTab>('hold')

  const [rows, setRows] = useState<MesHoldItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<HoldStatus | 'all'>('active')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [fhRows, setFhRows] = useState<MesFutureHoldItem[]>([])
  const [fhTotal, setFhTotal] = useState(0)
  const [fhPage, setFhPage] = useState(1)
  const [fhQ, setFhQ] = useState('')
  const [fhKeyword, setFhKeyword] = useState('')
  const [fhStatus, setFhStatus] = useState<FutureHoldStatus | 'all'>('pending')
  const [fhLoading, setFhLoading] = useState(false)
  const [fhFailed, setFhFailed] = useState(false)
  const [fhFlashId, setFhFlashId] = useState<string | null>(null)

  const [reasons, setReasons] = useState<MesHoldReason[]>([])

  const [createOpen, setCreateOpen] = useState(false)
  const [lotQ, setLotQ] = useState('')
  const [lotHits, setLotHits] = useState<MesLotItem[]>([])
  const [lotSearching, setLotSearching] = useState(false)
  const [selectedLot, setSelectedLot] = useState<MesLotItem | null>(null)
  const [reasonCode, setReasonCode] = useState('')
  const [remark, setRemark] = useState('')
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [fhCreateOpen, setFhCreateOpen] = useState(false)
  const [fhTargetSort, setFhTargetSort] = useState('')
  const [fhTiming, setFhTiming] = useState<FutureHoldTiming>('PRE')
  const [fhReasonCode, setFhReasonCode] = useState('')
  const [fhRemark, setFhRemark] = useState('')
  const [fhCreateError, setFhCreateError] = useState('')
  const [savingFh, setSavingFh] = useState(false)

  const [releaseTarget, setReleaseTarget] = useState<MesHoldItem | null>(null)
  const [releaseRemark, setReleaseRemark] = useState('')
  const [releasing, setReleasing] = useState(false)

  const [cancelTarget, setCancelTarget] = useState<MesFutureHoldItem | null>(null)
  const [cancelRemark, setCancelRemark] = useState('')
  const [cancelling, setCancelling] = useState(false)

  const size = 20
  const canView = hasPermission('hold:list')
  const canCreate = hasPermission('hold:create')
  const canRelease = hasPermission('hold:release')
  const totalPages = Math.max(1, Math.ceil(total / size))
  const fhTotalPages = Math.max(1, Math.ceil(fhTotal / size))
  const otherSelected = reasonCode === 'OTHER'
  const fhOtherSelected = fhReasonCode === 'OTHER'

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listHoldsApi({
        keyword,
        status: statusFilter,
        page,
        size,
      })
      setRows(data.records)
      setTotal(data.total)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '锁批加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, page, statusFilter, toast])

  const loadFutureList = useCallback(async () => {
    setFhLoading(true)
    setFhFailed(false)
    try {
      const data = await listFutureHoldsApi({
        keyword: fhKeyword,
        status: fhStatus,
        page: fhPage,
        size,
      })
      setFhRows(data.records)
      setFhTotal(data.total)
    } catch (err) {
      setFhRows([])
      setFhTotal(0)
      setFhFailed(true)
      toast.error(err instanceof ApiError ? err.message : '预约锁批加载失败')
    } finally {
      setFhLoading(false)
    }
  }, [fhKeyword, fhPage, fhStatus, toast])

  const loadReasons = useCallback(async () => {
    try {
      const data = await listHoldReasonsApi(false)
      setReasons(data)
    } catch {
      setReasons([])
    }
  }, [])

  useEffect(() => {
    if (!canView) return
    if (mainTab === 'hold') void loadList()
    else void loadFutureList()
  }, [canView, mainTab, loadList, loadFutureList])

  useEffect(() => {
    if (!canView) return
    void loadReasons()
  }, [canView, loadReasons])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.hold-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [mainTab])

  const searchLots = async (forFuture: boolean) => {
    const kw = lotQ.trim()
    if (!kw) {
      setLotHits([])
      return
    }
    setLotSearching(true)
    try {
      const data = await listLotsApi({ keyword: kw, page: 1, size: 20 })
      const allowed = forFuture
        ? data.records.filter(
            (l) => l.status === 'wait' || l.status === 'processing' || l.status === 'held',
          )
        : data.records.filter((l) => l.status === 'wait' || l.status === 'processing')
      setLotHits(allowed)
    } catch (err) {
      setLotHits([])
      toast.error(err instanceof ApiError ? err.message : '批次搜索失败')
    } finally {
      setLotSearching(false)
    }
  }

  const openCreate = () => {
    setCreateOpen(true)
    setCreateError('')
    setSelectedLot(null)
    setLotQ('')
    setLotHits([])
    setRemark('')
    setReasonCode(reasons[0]?.reasonCode ?? '')
  }

  const openFhCreate = () => {
    setFhCreateOpen(true)
    setFhCreateError('')
    setSelectedLot(null)
    setLotQ('')
    setLotHits([])
    setFhTargetSort('')
    setFhTiming('PRE')
    setFhRemark('')
    setFhReasonCode(reasons[0]?.reasonCode ?? '')
  }

  const submitCreate = async () => {
    if (!selectedLot) {
      setCreateError('请选择批次')
      return
    }
    if (!reasonCode) {
      setCreateError('请选择原因码')
      return
    }
    if (otherSelected && !remark.trim()) {
      setCreateError('原因码为其它时须填写备注')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const row = await createHoldApi({
        lotId: selectedLot.id,
        reasonCode,
        remark: remark.trim() || undefined,
      })
      toast.success(`已锁批 ${row.lotNo}`)
      setCreateOpen(false)
      setFlashId(String(row.id))
      setQ('')
      setKeyword('')
      setStatusFilter('active')
      setPage(1)
      try {
        const data = await listHoldsApi({ status: 'active', page: 1, size })
        setRows(data.records)
        setTotal(data.total)
      } catch {
        /* list effect will retry */
      }
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : '锁批失败')
    } finally {
      setSavingCreate(false)
    }
  }

  const submitFhCreate = async () => {
    if (!selectedLot) {
      setFhCreateError('请选择批次')
      return
    }
    const sortNo = Number(fhTargetSort)
    if (!Number.isFinite(sortNo) || sortNo <= 0) {
      setFhCreateError('请填写有效目标站序')
      return
    }
    if (!fhReasonCode) {
      setFhCreateError('请选择原因码')
      return
    }
    if (fhOtherSelected && !fhRemark.trim()) {
      setFhCreateError('原因码为其它时须填写备注')
      return
    }
    setSavingFh(true)
    setFhCreateError('')
    try {
      const row = await createFutureHoldApi({
        lotId: selectedLot.id,
        targetSortNo: sortNo,
        timing: fhTiming,
        reasonCode: fhReasonCode,
        remark: fhRemark.trim() || undefined,
      })
      toast.success(`已预约 ${row.lotNo} · 站 ${row.targetSortNo} ${row.timing}`)
      setFhCreateOpen(false)
      setFhFlashId(String(row.id))
      setFhQ('')
      setFhKeyword('')
      setFhStatus('pending')
      setFhPage(1)
      setMainTab('future')
      try {
        const data = await listFutureHoldsApi({ status: 'pending', page: 1, size })
        setFhRows(data.records)
        setFhTotal(data.total)
      } catch {
        /* list effect will retry */
      }
    } catch (err) {
      setFhCreateError(err instanceof ApiError ? err.message : '预约失败')
    } finally {
      setSavingFh(false)
    }
  }

  const submitRelease = async () => {
    if (!releaseTarget) return
    if (releaseTarget.reasonCode === 'QTIME_EXCEED' && !releaseRemark.trim()) {
      toast.error('Queue Time 解锁须填写备注')
      return
    }
    setReleasing(true)
    try {
      await releaseHoldApi(releaseTarget.id, releaseRemark.trim() || undefined)
      toast.success(`已解锁 ${releaseTarget.lotNo}`)
      setFlashId(String(releaseTarget.id))
      setReleaseTarget(null)
      setReleaseRemark('')
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '解锁失败')
    } finally {
      setReleasing(false)
    }
  }

  const submitCancel = async () => {
    if (!cancelTarget) return
    setCancelling(true)
    try {
      await cancelFutureHoldApi(cancelTarget.id, cancelRemark.trim() || undefined)
      toast.success(`已取消预约 ${cancelTarget.lotNo}`)
      setFhFlashId(String(cancelTarget.id))
      setCancelTarget(null)
      setCancelRemark('')
      await loadFutureList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '取消失败')
    } finally {
      setCancelling(false)
    }
  }

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">锁批管理</h1>
        <p className="text-sm text-muted">无权限查看锁批（需要 hold:list）</p>
      </div>
    )
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="hold-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">锁批管理</h1>
          <p className="mt-1 text-sm text-muted">
            {mainTab === 'hold'
              ? '紧急刹车 · active 时禁止 Track 开工/完工'
              : '预约到站再锁 · pending 不拦过站，到站自动激活'}
          </p>
        </div>
        {canCreate ? (
          mainTab === 'hold' ? (
            <Button onClick={openCreate}>
              <Lock className="size-4" aria-hidden />
              发起锁批
            </Button>
          ) : (
            <Button onClick={openFhCreate}>
              <CalendarClock className="size-4" aria-hidden />
              预约锁批
            </Button>
          )
        ) : null}
      </header>

      <div className="hold-block flex gap-1 border-b border-border">
        {(
          [
            { key: 'hold' as const, label: '即时锁批', icon: Lock },
            { key: 'future' as const, label: '预约锁批', icon: CalendarClock },
          ] as const
        ).map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setMainTab(t.key)}
            className={cn(
              'inline-flex h-9 cursor-pointer items-center gap-1.5 border-b-2 px-3 text-sm font-medium transition-colors duration-150',
              mainTab === t.key
                ? 'border-primary text-ink'
                : 'border-transparent text-muted hover:text-ink',
            )}
          >
            <t.icon className="size-3.5" aria-hidden />
            {t.label}
          </button>
        ))}
      </div>

      {mainTab === 'hold' ? (
        <>
          <div className="hold-block flex flex-wrap items-center gap-2">
            <input
              className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
              placeholder="批次号"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setPage(1)
                  setKeyword(q.trim())
                }
              }}
              aria-label="搜索锁批"
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
              {HOLD_STATUS_FILTERS.map((s) => (
                <button
                  key={s.key}
                  type="button"
                  onClick={() => {
                    setStatusFilter(s.key)
                    setPage(1)
                  }}
                  className={cn(
                    'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
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

          <div className="hold-block overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[880px] text-left text-sm">
              <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
                <tr className="h-10 border-b border-border">
                  <th className="px-3">批次号</th>
                  <th className="px-3">状态</th>
                  <th className="px-3">原因</th>
                  <th className="px-3">上锁备注</th>
                  <th className="px-3">操作人</th>
                  <th className="px-3">上锁时间</th>
                  <th className="px-3">解锁时间</th>
                  <th className="px-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={8} className="px-3 py-8 text-center text-muted">
                      加载中…
                    </td>
                  </tr>
                ) : loadFailed ? (
                  <tr>
                    <td colSpan={8} className="px-3 py-8 text-center text-muted">
                      加载失败，请刷新重试
                    </td>
                  </tr>
                ) : rows.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-3 py-10 text-center">
                      <p className="text-sm text-muted">暂无锁批记录</p>
                      {canCreate ? (
                        <button
                          type="button"
                          className="mt-2 cursor-pointer text-sm text-accent hover:underline"
                          onClick={openCreate}
                        >
                          发起第一笔锁批
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ) : (
                  rows.map((row) => (
                    <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                      <td className="h-10 px-3 font-mono text-[13px] text-accent">{row.lotNo}</td>
                      <td className="px-3">
                        <HoldStatusPill status={row.status} />
                      </td>
                      <td className="px-3">
                        <span className="font-mono text-[13px]">{row.reasonCode}</span>
                        {row.reasonName ? (
                          <span className="ml-1.5 text-muted">{row.reasonName}</span>
                        ) : null}
                      </td>
                      <td
                        className="max-w-[180px] truncate px-3 text-muted"
                        title={row.remark ?? undefined}
                      >
                        {row.remark || '—'}
                      </td>
                      <td className="px-3 font-mono text-xs">{row.holdUserName ?? '—'}</td>
                      <td className="px-3 font-mono text-[13px] text-muted">{fmtTime(row.holdTime)}</td>
                      <td className="px-3 font-mono text-[13px] text-muted">
                        {row.status === 'released' ? fmtTime(row.releaseTime) : '—'}
                      </td>
                      <td className="px-3">
                        <div className="flex flex-wrap gap-1.5">
                          {row.status === 'active' && canRelease ? (
                            <TableAction
                              icon={Unlock}
                              label="解锁"
                              tone="warning"
                              onClick={() => {
                                setReleaseTarget(row)
                                setReleaseRemark('')
                              }}
                            />
                          ) : (
                            <span className="text-xs text-muted">—</span>
                          )}
                          <Link
                            to="/track"
                            className="inline-flex h-7 cursor-pointer items-center rounded-md border border-border px-2 text-xs font-medium text-accent hover:bg-surface"
                            title={`现场台载入 ${row.lotNo}`}
                          >
                            过账
                          </Link>
                        </div>
                      </td>
                    </FlashRow>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="hold-block flex items-center justify-between gap-3 text-sm text-muted">
            <span>共 {total} 条</span>
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
        </>
      ) : (
        <>
          <div className="hold-block flex flex-wrap items-center gap-2">
            <input
              className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
              placeholder="批次号"
              value={fhQ}
              onChange={(e) => setFhQ(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setFhPage(1)
                  setFhKeyword(fhQ.trim())
                }
              }}
              aria-label="搜索预约锁批"
            />
            <Button
              variant="secondary"
              onClick={() => {
                setFhPage(1)
                setFhKeyword(fhQ.trim())
              }}
            >
              <Search className="size-4" aria-hidden />
              搜索
            </Button>
            <div className="flex flex-wrap gap-1.5 sm:ml-2">
              {FUTURE_STATUS_FILTERS.map((s) => (
                <button
                  key={s.key}
                  type="button"
                  onClick={() => {
                    setFhStatus(s.key)
                    setFhPage(1)
                  }}
                  className={cn(
                    'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
                    fhStatus === s.key
                      ? 'border-primary bg-primary text-white'
                      : 'border-border bg-bg text-muted hover:bg-surface',
                  )}
                >
                  {s.label}
                </button>
              ))}
            </div>
          </div>

          <div className="hold-block overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[960px] text-left text-sm">
              <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
                <tr className="h-10 border-b border-border">
                  <th className="px-3">批次号</th>
                  <th className="px-3">状态</th>
                  <th className="px-3">目标站</th>
                  <th className="px-3">时机</th>
                  <th className="px-3">原因</th>
                  <th className="px-3">备注</th>
                  <th className="px-3">创建人</th>
                  <th className="px-3">创建时间</th>
                  <th className="px-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {fhLoading ? (
                  <tr>
                    <td colSpan={9} className="px-3 py-8 text-center text-muted">
                      加载中…
                    </td>
                  </tr>
                ) : fhFailed ? (
                  <tr>
                    <td colSpan={9} className="px-3 py-8 text-center text-muted">
                      加载失败，请刷新重试
                    </td>
                  </tr>
                ) : fhRows.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="px-3 py-10 text-center">
                      <p className="text-sm text-muted">暂无预约锁批</p>
                      {canCreate ? (
                        <button
                          type="button"
                          className="mt-2 cursor-pointer text-sm text-accent hover:underline"
                          onClick={openFhCreate}
                        >
                          创建第一笔预约
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ) : (
                  fhRows.map((row) => (
                    <FlashRow key={String(row.id)} active={fhFlashId === String(row.id)}>
                      <td className="h-10 px-3 font-mono text-[13px] text-accent">{row.lotNo}</td>
                      <td className="px-3">
                        <FutureStatusPill status={row.status} />
                      </td>
                      <td className="px-3 font-mono text-[13px]">{row.targetSortNo}</td>
                      <td className="px-3">
                        <span className="rounded-sm bg-surface px-1.5 py-0.5 font-mono text-xs">
                          {row.timing}
                        </span>
                        <span className="ml-1.5 text-xs text-muted">
                          {row.timing === 'PRE' ? '进站前' : '出站后'}
                        </span>
                      </td>
                      <td className="px-3">
                        <span className="font-mono text-[13px]">{row.reasonCode}</span>
                        {row.reasonName ? (
                          <span className="ml-1.5 text-muted">{row.reasonName}</span>
                        ) : null}
                      </td>
                      <td
                        className="max-w-[160px] truncate px-3 text-muted"
                        title={row.remark ?? undefined}
                      >
                        {row.remark || '—'}
                      </td>
                      <td className="px-3 font-mono text-xs">{row.createUserName ?? '—'}</td>
                      <td className="px-3 font-mono text-[13px] text-muted">
                        {fmtTime(row.createTime)}
                      </td>
                      <td className="px-3">
                        <div className="flex flex-wrap gap-1.5">
                          {row.status === 'pending' && canCreate ? (
                            <TableAction
                              icon={X}
                              label="取消"
                              tone="warning"
                              onClick={() => {
                                setCancelTarget(row)
                                setCancelRemark('')
                              }}
                            />
                          ) : (
                            <span className="text-xs text-muted">—</span>
                          )}
                          <Link
                            to="/track"
                            className="inline-flex h-7 cursor-pointer items-center rounded-md border border-border px-2 text-xs font-medium text-accent hover:bg-surface"
                            title={`现场台载入 ${row.lotNo}`}
                          >
                            过账
                          </Link>
                        </div>
                      </td>
                    </FlashRow>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="hold-block flex items-center justify-between gap-3 text-sm text-muted">
            <span>共 {fhTotal} 条</span>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                disabled={fhPage <= 1 || fhLoading}
                onClick={() => setFhPage((p) => Math.max(1, p - 1))}
              >
                上一页
              </Button>
              <span className="font-mono text-xs">
                {fhPage} / {fhTotalPages}
              </span>
              <Button
                variant="secondary"
                disabled={fhPage >= fhTotalPages || fhLoading}
                onClick={() => setFhPage((p) => p + 1)}
              >
                下一页
              </Button>
            </div>
          </div>
        </>
      )}

      <Drawer
        open={createOpen}
        title="发起锁批"
        onClose={() => !savingCreate && setCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" disabled={savingCreate} onClick={() => setCreateOpen(false)}>
              取消
            </Button>
            <Button loading={savingCreate} onClick={() => void submitCreate()}>
              确认锁批
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <LotPicker
            lotQ={lotQ}
            setLotQ={setLotQ}
            lotHits={lotHits}
            lotSearching={lotSearching}
            selectedLot={selectedLot}
            setSelectedLot={setSelectedLot}
            setLotHits={setLotHits}
            onSearch={() => void searchLots(false)}
            hint="选择批次（wait / processing）"
          />
          <ReasonSelect
            reasons={reasons}
            reasonCode={reasonCode}
            setReasonCode={setReasonCode}
          />
          <Field
            label={otherSelected ? '备注（必填）' : '备注'}
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
            placeholder={otherSelected ? '说明其它原因' : '可选'}
          />
          {createError ? <p className="text-xs text-danger">{createError}</p> : null}
        </div>
      </Drawer>

      <Drawer
        open={fhCreateOpen}
        title="预约锁批"
        onClose={() => !savingFh && setFhCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" disabled={savingFh} onClick={() => setFhCreateOpen(false)}>
              取消
            </Button>
            <Button loading={savingFh} onClick={() => void submitFhCreate()}>
              确认预约
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <p className="rounded-md border border-border bg-surface px-3 py-2 text-xs text-muted">
            到目标站才会锁批。PRE=进站前拦截，POST=出站落到该站后再锁。
          </p>
          <LotPicker
            lotQ={lotQ}
            setLotQ={setLotQ}
            lotHits={lotHits}
            lotSearching={lotSearching}
            selectedLot={selectedLot}
            setSelectedLot={setSelectedLot}
            setLotHits={setLotHits}
            onSearch={() => void searchLots(true)}
            hint="选择批次（wait / processing / held）"
          />
          {selectedLot?.currentSortNo != null ? (
            <p className="text-xs text-muted">
              当前站序{' '}
              <span className="font-mono text-ink">{selectedLot.currentSortNo}</span>
              ，目标须为当前站或之后
            </p>
          ) : null}
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">目标站序</span>
            <input
              className="h-9 rounded-md border border-border bg-bg px-3 font-mono text-sm"
              inputMode="numeric"
              placeholder="如 50"
              value={fhTargetSort}
              onChange={(e) => setFhTargetSort(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">触发时机</span>
            <select
              className="h-9 cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
              value={fhTiming}
              onChange={(e) => setFhTiming(e.target.value as FutureHoldTiming)}
            >
              <option value="PRE">PRE · 进站前</option>
              <option value="POST">POST · 出站后</option>
            </select>
          </label>
          <ReasonSelect
            reasons={reasons}
            reasonCode={fhReasonCode}
            setReasonCode={setFhReasonCode}
          />
          <Field
            label={fhOtherSelected ? '备注（必填）' : '备注'}
            value={fhRemark}
            onChange={(e) => setFhRemark(e.target.value)}
            placeholder={fhOtherSelected ? '说明其它原因' : '可选'}
          />
          {fhCreateError ? <p className="text-xs text-danger">{fhCreateError}</p> : null}
        </div>
      </Drawer>

      <Drawer
        open={!!releaseTarget}
        title="解锁"
        onClose={() => !releasing && setReleaseTarget(null)}
        footer={
          <>
            <Button variant="secondary" disabled={releasing} onClick={() => setReleaseTarget(null)}>
              取消
            </Button>
            <Button variant="danger" loading={releasing} onClick={() => void submitRelease()}>
              确认解锁
            </Button>
          </>
        }
      >
        {releaseTarget ? (
          <div className="space-y-4">
            <p className="text-sm text-muted">
              解锁后批次恢复为{' '}
              <span className="font-mono text-ink">{releaseTarget.prevStatus || 'wait'}</span>
              ，可继续 Track。
            </p>
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <dt className="text-xs text-muted">批次</dt>
                <dd className="mt-0.5 font-mono">{releaseTarget.lotNo}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">原因</dt>
                <dd className="mt-0.5">{releaseTarget.reasonName || releaseTarget.reasonCode}</dd>
              </div>
            </dl>
            <Field
              label={releaseTarget.reasonCode === 'QTIME_EXCEED' ? '解锁备注（必填）' : '解锁备注'}
              value={releaseRemark}
              onChange={(e) => setReleaseRemark(e.target.value)}
              placeholder={releaseTarget.reasonCode === 'QTIME_EXCEED' ? '说明放行原因' : '可选'}
            />
          </div>
        ) : null}
      </Drawer>

      <Drawer
        open={!!cancelTarget}
        title="取消预约"
        onClose={() => !cancelling && setCancelTarget(null)}
        footer={
          <>
            <Button variant="secondary" disabled={cancelling} onClick={() => setCancelTarget(null)}>
              返回
            </Button>
            <Button variant="danger" loading={cancelling} onClick={() => void submitCancel()}>
              确认取消
            </Button>
          </>
        }
      >
        {cancelTarget ? (
          <div className="space-y-4">
            <p className="text-sm text-muted">取消后到站不再自动锁批。已激活的须走解锁。</p>
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <dt className="text-xs text-muted">批次</dt>
                <dd className="mt-0.5 font-mono">{cancelTarget.lotNo}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">目标</dt>
                <dd className="mt-0.5 font-mono">
                  站 {cancelTarget.targetSortNo} · {cancelTarget.timing}
                </dd>
              </div>
            </dl>
            <Field
              label="取消备注"
              value={cancelRemark}
              onChange={(e) => setCancelRemark(e.target.value)}
              placeholder="可选"
            />
          </div>
        ) : null}
      </Drawer>
    </div>
  )
}

function ReasonSelect({
  reasons,
  reasonCode,
  setReasonCode,
}: {
  reasons: MesHoldReason[]
  reasonCode: string
  setReasonCode: (v: string) => void
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="text-xs font-medium text-muted">原因码</span>
      <select
        className="h-9 cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
        value={reasonCode}
        onChange={(e) => setReasonCode(e.target.value)}
      >
        {reasons.length === 0 ? <option value="">无可用原因码</option> : null}
        {reasons.map((r) => (
          <option key={String(r.id)} value={r.reasonCode}>
            {r.reasonCode} · {r.reasonName}
          </option>
        ))}
      </select>
    </label>
  )
}

function LotPicker({
  lotQ,
  setLotQ,
  lotHits,
  lotSearching,
  selectedLot,
  setSelectedLot,
  setLotHits,
  onSearch,
  hint,
}: {
  lotQ: string
  setLotQ: (v: string) => void
  lotHits: MesLotItem[]
  lotSearching: boolean
  selectedLot: MesLotItem | null
  setSelectedLot: (v: MesLotItem | null) => void
  setLotHits: (v: MesLotItem[]) => void
  onSearch: () => void
  hint: string
}) {
  return (
    <div>
      <label className="flex flex-col gap-1.5 text-sm">
        <span className="text-xs font-medium text-muted">{hint}</span>
        <div className="flex gap-2">
          <input
            className="h-9 min-w-0 flex-1 rounded-md border border-border bg-bg px-3 text-sm"
            placeholder="输入批次号搜索"
            value={lotQ}
            onChange={(e) => setLotQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') onSearch()
            }}
          />
          <Button variant="secondary" loading={lotSearching} onClick={onSearch}>
            搜索
          </Button>
        </div>
      </label>
      {selectedLot ? (
        <p className="mt-2 rounded-md border border-border bg-surface px-3 py-2 font-mono text-sm">
          {selectedLot.lotNo}
          <span className="ml-2 font-sans text-muted">{selectedLot.status}</span>
        </p>
      ) : null}
      {lotHits.length > 0 ? (
        <ul className="mt-2 max-h-40 overflow-auto rounded-md border border-border">
          {lotHits.map((lot) => (
            <li key={String(lot.id)}>
              <button
                type="button"
                className="flex w-full cursor-pointer items-center justify-between px-3 py-2 text-left text-sm hover:bg-surface"
                onClick={() => {
                  setSelectedLot(lot)
                  setLotHits([])
                  setLotQ(lot.lotNo)
                }}
              >
                <span className="font-mono text-[13px]">{lot.lotNo}</span>
                <span className="text-xs text-muted">{lot.status}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
