import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import gsap from 'gsap'
import {
  Bookmark,
  BookmarkX,
  CirclePlay,
  ClipboardList,
  Inbox,
  Loader2,
  MapPin,
  RefreshCw,
  Search,
  Sparkles,
} from 'lucide-react'
import {
  createDispatchReserveApi,
  getDispatchCandidatesApi,
  listDispatchReservesApi,
  releaseDispatchReserveApi,
  type DispatchCandidateItem,
  type DispatchCandidates,
  type DispatchReserveItem,
} from '../api/dispatch'
import { getLotApi, listLotsApi, type MesLotItem } from '../api/lot'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Field } from '../components/ui/Field'
import { MesEqpStatusPill, MesLotStatusPill } from '../components/ui/StatusPill'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

function fmtTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 16)
}

function fmtRemain(expireTime: string | null | undefined) {
  if (!expireTime) return '-'
  const t = new Date(expireTime.replace(' ', 'T')).getTime() - Date.now()
  if (Number.isNaN(t) || t <= 0) return "已到期"
  const m = Math.floor(t / 60000)
  const s = Math.floor((t % 60000) / 1000)
  return `${m}:${String(s).padStart(2, '0')}`
}

function stepLabel(lot: MesLotItem | null) {
  if (!lot) return '-'
  const step = lot.steps?.find((s) => s.sortNo === lot.currentSortNo)
  if (step) return `${step.stepCode} ${step.stepName}`
  if (lot.currentSortNo != null) return `S${lot.currentSortNo}`
  return '-'
}

function reserveStatusLabel(status: string) {
  if (status === 'active') return "生效中"
  if (status === 'released') return "已释约"
  if (status === 'expired') return "已到期"
  if (status === 'consumed') return "已消费"
  return status
}

export function DispatchPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const navigate = useNavigate()
  const tableRef = useRef<HTMLTableSectionElement>(null)

  const canView = hasPermission('dispatch:view')
  const canReserve = hasPermission('dispatch:reserve')

  const [lotQ, setLotQ] = useState('')
  const [queue, setQueue] = useState<MesLotItem[]>([])
  const [queueLoading, setQueueLoading] = useState(false)
  const [searchHits, setSearchHits] = useState<MesLotItem[] | null>(null)
  const [lotSearching, setLotSearching] = useState(false)
  const [selectedLot, setSelectedLot] = useState<MesLotItem | null>(null)

  const [candidates, setCandidates] = useState<DispatchCandidates | null>(null)
  const [items, setItems] = useState<DispatchCandidateItem[]>([])
  const [selectedEqpId, setSelectedEqpId] = useState('')
  const [activeReserve, setActiveReserve] = useState<DispatchReserveItem | null>(null)
  const [reserveRows, setReserveRows] = useState<DispatchReserveItem[]>([])
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [remainTick, setRemainTick] = useState(0)

  const lotList = searchHits ?? queue

  const recommendCode = useMemo(() => {
    if (candidates?.recommendedEqpId == null) return null
    return items.find((c) => String(c.eqpId) === String(candidates.recommendedEqpId))?.eqpCode ?? null
  }, [candidates?.recommendedEqpId, items])

  const recommendReason = useMemo(() => {
    if (candidates?.recommendedEqpId == null) return null
    return items.find((c) => String(c.eqpId) === String(candidates.recommendedEqpId))?.reason ?? null
  }, [candidates?.recommendedEqpId, items])

  const loadQueue = useCallback(async () => {
    setQueueLoading(true)
    try {
      const [wait, processing] = await Promise.all([
        listLotsApi({ status: 'wait', page: 1, size: 20 }),
        listLotsApi({ status: 'processing', page: 1, size: 20 }),
      ])
      const merged = [...processing.records, ...wait.records]
      const seen = new Set<string>()
      setQueue(
        merged.filter((l) => {
          const k = String(l.id)
          if (seen.has(k)) return false
          seen.add(k)
          return true
        }),
      )
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "队列加载失败")
    } finally {
      setQueueLoading(false)
    }
  }, [toast])

  useEffect(() => {
    void loadQueue()
  }, [loadQueue])

  const selectLot = useCallback(async (lot: MesLotItem) => {
    try {
      const detail = lot.steps?.length ? lot : await getLotApi(lot.id)
      setSelectedLot(detail)
    } catch (e) {
      setSelectedLot(lot)
      toast.error(e instanceof ApiError ? e.message : "批次详情加载失败")
    }
  }, [toast])

  const loadForLot = useCallback(
    async (lot: MesLotItem) => {
      setLoading(true)
      try {
        const [cand, reserves] = await Promise.all([
          getDispatchCandidatesApi(lot.id),
          listDispatchReservesApi({ lotId: lot.id, status: 'active' }),
        ])
        setCandidates(cand)
        setItems(cand.candidates ?? [])
        const active = reserves[0] ?? null
        setActiveReserve(active)
        setReserveRows(reserves)
        const ids = (cand.candidates ?? []).map((c) => String(c.eqpId))
        setSelectedEqpId((prev) => {
          if (active && ids.includes(String(active.eqpId))) return String(active.eqpId)
          if (prev && ids.includes(prev)) return prev
          if (cand.recommendedEqpId != null && ids.includes(String(cand.recommendedEqpId))) {
            return String(cand.recommendedEqpId)
          }
          return ids[0] ?? ''
        })
        const d = motionMs() / 1000
        if (d > 0 && tableRef.current) {
          gsap.fromTo(
            tableRef.current.querySelectorAll('[data-row]'),
            { autoAlpha: 0.35, y: 4 },
            { autoAlpha: 1, y: 0, duration: d, stagger: 0.03, ease: 'power2.out' },
          )
        }
      } catch (e) {
        setCandidates(null)
        setItems([])
        setActiveReserve(null)
        setReserveRows([])
        toast.error(e instanceof ApiError ? e.message : "派工数据加载失败")
      } finally {
        setLoading(false)
      }
    },
    [toast],
  )

  useEffect(() => {
    if (!selectedLot) return
    void loadForLot(selectedLot)
  }, [selectedLot, loadForLot])

  useEffect(() => {
    if (!activeReserve?.expireTime) return
    const id = window.setInterval(() => setRemainTick((n) => n + 1), 1000)
    return () => window.clearInterval(id)
  }, [activeReserve?.id, activeReserve?.expireTime])

  async function searchLots() {
    const keyword = lotQ.trim()
    if (!keyword) {
      setSearchHits(null)
      return
    }
    setLotSearching(true)
    try {
      const page = await listLotsApi({ keyword, page: 1, size: 20 })
      setSearchHits(page.records ?? [])
      if (!page.records?.length) toast.error("未找到批次")
    } catch (e) {
      setSearchHits([])
      toast.error(e instanceof ApiError ? e.message : "查询失败")
    } finally {
      setLotSearching(false)
    }
  }

  async function onReserve() {
    if (!selectedLot || !selectedEqpId) return
    setBusy(true)
    try {
      const row = await createDispatchReserveApi({ lotId: selectedLot.id, eqpId: selectedEqpId })
      toast.success(`${"已预约 "}${row.eqpCode ?? ''}`)
      await loadForLot(selectedLot)
      await loadQueue()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "预约失败")
    } finally {
      setBusy(false)
    }
  }

  async function onRelease() {
    if (!selectedLot || !activeReserve) return
    const ok = await confirm({
      title: "释约确认",
      message: `${"释放 "}${selectedLot.lotNo}${" 对 "}${activeReserve.eqpCode ?? activeReserve.eqpId}${" 的预约，他批可能立刻约上该机．确认释约？"}`,
      confirmText: "释约",
      danger: true,
    })
    if (!ok) return
    setBusy(true)
    try {
      await releaseDispatchReserveApi(activeReserve.id)
      toast.success("已释约")
      await loadForLot(selectedLot)
      await loadQueue()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "释约失败")
    } finally {
      setBusy(false)
    }
  }

  void remainTick

  if (!canView) {
    return (
      <div className="rounded-lg border border-border bg-surface p-8 text-center text-sm text-muted">
        无派工查看权限(dispatch:view)
      </div>
    )
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">派工</h1>
          <p className="mt-1 text-sm text-muted">待派队列选批，看推荐机并预约占机</p>
        </div>
        {activeReserve ? (
          <div className="flex items-center gap-2.5 rounded-md border border-accent/35 bg-accent/10 px-3 py-2 text-xs text-ink">
            <span className="flex size-6 items-center justify-center rounded bg-accent/20 text-accent">
              <Bookmark className="size-3.5" aria-hidden />
            </span>
            <div className="leading-tight">
              <div className="font-medium">已预约</div>
              <div className="text-muted">
                <span className="font-mono text-ink">{activeReserve.eqpCode}</span>
                {' · 剩余 '}
                <span className="font-mono text-ink">{fmtRemain(activeReserve.expireTime)}</span>
              </div>
            </div>
          </div>
        ) : null}
      </div>

      <div className="grid min-h-0 flex-1 gap-4 xl:grid-cols-[300px_minmax(0,1fr)]">
        <section className="flex min-h-0 flex-col overflow-hidden rounded-lg border border-border bg-surface">
          <div className="border-b border-border bg-bg/60 px-4 py-3">
            <div className="flex items-center gap-2 text-sm font-medium text-ink">
              <Search className="size-4 text-muted" aria-hidden />
              选批次
            </div>
            <div className="mt-3 flex gap-2">
              <Field
                label="批次号"
                value={lotQ}
                onChange={(e) => {
                  setLotQ(e.target.value)
                  if (!e.target.value.trim()) setSearchHits(null)
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void searchLots()
                }}
                placeholder="留空显示待派队列"
                className="min-w-0 flex-1"
              />
              <Button
                className="mt-6 cursor-pointer shrink-0"
                loading={lotSearching}
                onClick={() => void searchLots()}
              >
                查询
              </Button>
            </div>
          </div>
          <div className="flex items-center justify-between border-b border-border px-4 py-2">
            <span className="text-xs font-medium text-muted">
              {searchHits ? `搜索结果 · ${lotList.length}` : `待派队列 · ${queue.length}`}
            </span>
            <button
              type="button"
              className="inline-flex cursor-pointer items-center gap-1 text-xs text-accent transition-opacity hover:opacity-80"
              onClick={() => {
                setSearchHits(null)
                setLotQ('')
                void loadQueue()
              }}
            >
              <RefreshCw className="size-3" aria-hidden />
              刷新
            </button>
          </div>
          <ul className="min-h-0 flex-1 space-y-0.5 overflow-auto p-2">
            {queueLoading && !searchHits ? (
              <li className="flex items-center justify-center gap-2 px-3 py-12 text-sm text-muted">
                <Loader2 className="size-4 animate-spin" aria-hidden />
                加载中
              </li>
            ) : lotList.length === 0 ? (
              <li className="flex flex-col items-center gap-2 px-3 py-12 text-center">
                <Inbox className="size-8 text-muted/50" aria-hidden />
                <p className="text-sm text-muted">
                  {searchHits ? "无搜索结果" : "无 wait / processing 批次"}
                </p>
                {!searchHits ? (
                  <p className="text-xs text-muted">放行后的批次会出现在此</p>
                ) : null}
              </li>
            ) : (
              lotList.map((lot) => {
                const active = selectedLot && String(selectedLot.id) === String(lot.id)
                return (
                  <li key={String(lot.id)}>
                    <button
                      type="button"
                      className={cn(
                        'relative flex w-full cursor-pointer flex-col gap-1.5 rounded-md px-3 py-2.5 text-left transition-colors duration-150',
                        active ? 'bg-accent/12 text-ink' : 'text-ink hover:bg-bg',
                      )}
                      onClick={() => void selectLot(lot)}
                    >
                      {active ? (
                        <span
                          className="absolute inset-y-1.5 left-0 w-0.5 rounded-full bg-accent"
                          aria-hidden
                        />
                      ) : null}
                      <span className="font-mono text-sm font-medium tracking-tight">{lot.lotNo}</span>
                      <span className="flex items-center justify-between gap-2">
                        <MesLotStatusPill status={lot.status} />
                        <span className="rounded bg-bg px-1.5 py-0.5 font-mono text-[11px] text-muted">
                          {lot.currentSortNo != null ? `S${lot.currentSortNo}` : '-'}
                        </span>
                      </span>
                    </button>
                  </li>
                )
              })
            )}
          </ul>
        </section>

        <section className="flex min-h-0 flex-col gap-4">
          <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-border bg-surface">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border bg-bg/60 px-4 py-3">
              <div className="flex min-w-0 items-center gap-2.5">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-muted">
                  <ClipboardList className="size-4" aria-hidden />
                </span>
                <div className="min-w-0">
                  <div className="text-sm font-medium text-ink">
                    {selectedLot ? (
                      <>
                        候选设备 · <span className="font-mono">{selectedLot.lotNo}</span>
                      </>
                    ) : (
                      "候选设备"
                    )}
                  </div>
                  <div className="mt-0.5 text-xs text-muted">
                    {selectedLot ? "对比负载与分数后预约" : "从左侧选择批次"}
                  </div>
                </div>
              </div>
              {selectedLot ? (
                <div className="flex flex-wrap gap-2">
                  {canReserve ? (
                    <>
                      <Button
                        size="md"
                        className="cursor-pointer"
                        disabled={!selectedEqpId || items.length === 0 || busy}
                        loading={busy && !activeReserve}
                        onClick={() => void onReserve()}
                      >
                        <Bookmark className="size-3.5" aria-hidden />
                        预约选中
                      </Button>
                      <Button
                        size="md"
                        variant="secondary"
                        className="cursor-pointer"
                        disabled={!activeReserve || busy}
                        loading={busy && !!activeReserve}
                        onClick={() => void onRelease()}
                      >
                        <BookmarkX className="size-3.5" aria-hidden />
                        释约
                      </Button>
                    </>
                  ) : null}
                  <Button
                    size="md"
                    variant="secondary"
                    className="cursor-pointer"
                    onClick={() =>
                      navigate('/track', {
                        state: { lotId: selectedLot.id, lotNo: selectedLot.lotNo },
                      })
                    }
                  >
                    <CirclePlay className="size-3.5" aria-hidden />
                    去开工
                  </Button>
                </div>
              ) : null}
            </div>

            {selectedLot ? (
              <div className="grid gap-px border-b border-border bg-border sm:grid-cols-2">
                <div className="flex items-start gap-2.5 bg-surface px-4 py-3">
                  <MapPin className="mt-0.5 size-3.5 shrink-0 text-muted" aria-hidden />
                  <div className="min-w-0">
                    <div className="text-[11px] font-medium text-muted">当前站</div>
                    <div className="mt-0.5 truncate text-sm font-medium text-ink" title={stepLabel(selectedLot)}>
                      {stepLabel(selectedLot)}
                    </div>
                    {candidates?.eqpType ? (
                      <div className="mt-0.5 text-xs text-muted">类型 {candidates.eqpType}</div>
                    ) : null}
                  </div>
                </div>
                <div className="flex items-start gap-2.5 bg-surface px-4 py-3">
                  <Sparkles className="mt-0.5 size-3.5 shrink-0 text-accent" aria-hidden />
                  <div className="min-w-0 flex-1">
                    <div className="text-[11px] font-medium text-muted">推荐机台</div>
                    {loading ? (
                      <div className="mt-0.5 text-sm text-muted">加载中…</div>
                    ) : recommendCode ? (
                      <>
                        <div className="mt-0.5 flex flex-wrap items-center gap-2">
                          <span className="font-mono text-sm font-semibold text-ink">{recommendCode}</span>
                          {selectedEqpId !== String(candidates?.recommendedEqpId) ? (
                            <button
                              type="button"
                              className="cursor-pointer text-xs font-medium text-accent hover:underline"
                              onClick={() => setSelectedEqpId(String(candidates?.recommendedEqpId))}
                            >
                              采用
                            </button>
                          ) : (
                            <span className="rounded bg-accent/15 px-1.5 py-0.5 text-[11px] font-medium text-accent">
                              已选
                            </span>
                          )}
                        </div>
                        {recommendReason ? (
                          <div className="mt-0.5 truncate text-xs text-muted" title={recommendReason}>
                            {recommendReason}
                          </div>
                        ) : null}
                      </>
                    ) : (
                      <div className="mt-0.5 text-sm text-muted">
                        {candidates?.message || "暂无推荐"}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ) : null}

            {candidates?.message && items.length === 0 ? (
              <div className="border-b border-danger/20 bg-danger/5 px-4 py-2.5 text-sm text-danger">
                {candidates.message}
              </div>
            ) : null}

            <div className="min-h-0 flex-1 overflow-auto">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead className="sticky top-0 z-[1] border-b border-border bg-bg text-xs text-muted">
                  <tr>
                    <th className="px-4 py-2.5 font-medium">设备</th>
                    <th className="px-4 py-2.5 font-medium">状态</th>
                    <th className="px-4 py-2.5 font-medium">负载</th>
                    <th className="px-4 py-2.5 font-medium">分数</th>
                    <th className="px-4 py-2.5 font-medium">理由</th>
                    <th className="px-4 py-2.5 font-medium">选择</th>
                  </tr>
                </thead>
                <tbody ref={tableRef}>
                  {!selectedLot ? (
                    <tr>
                      <td colSpan={6} className="px-4 py-16 text-center">
                        <div className="flex flex-col items-center gap-2 text-muted">
                          <ClipboardList className="size-8 opacity-40" aria-hidden />
                          <p className="text-sm">请先从左侧选择批次</p>
                        </div>
                      </td>
                    </tr>
                  ) : loading ? (
                    <tr>
                      <td colSpan={6} className="px-4 py-16 text-center text-sm text-muted">
                        <span className="inline-flex items-center gap-2">
                          <Loader2 className="size-4 animate-spin" aria-hidden />
                          加载候选…
                        </span>
                      </td>
                    </tr>
                  ) : items.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="px-4 py-16 text-center text-sm text-muted">
                        无候选设备
                      </td>
                    </tr>
                  ) : (
                    items.map((row) => {
                      const id = String(row.eqpId)
                      const rec = id === String(candidates?.recommendedEqpId)
                      const selected = id === selectedEqpId
                      return (
                        <tr
                          key={id}
                          data-row
                          className={cn(
                            'border-b border-border transition-colors duration-150',
                            selected && 'bg-accent/10',
                            !selected && rec && 'bg-accent/5',
                            !selected && !rec && 'hover:bg-bg',
                          )}
                        >
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              {rec ? (
                                <span className="flex size-5 shrink-0 items-center justify-center rounded bg-accent/15 text-accent">
                                  <Sparkles className="size-3" aria-hidden />
                                </span>
                              ) : (
                                <span className="size-5 shrink-0" aria-hidden />
                              )}
                              <div>
                                <div className="font-mono font-medium text-ink">{row.eqpCode}</div>
                                <div className="text-xs text-muted">{row.eqpName}</div>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3">
                            <MesEqpStatusPill status={row.status} />
                          </td>
                          <td className="px-4 py-3 font-mono text-ink">{row.loadCount ?? 0}</td>
                          <td className="px-4 py-3 font-mono text-ink">{row.score ?? '-'}</td>
                          <td className="max-w-[200px] truncate px-4 py-3 text-muted" title={row.reason ?? undefined}>
                            {row.reason ?? '-'}
                          </td>
                          <td className="px-4 py-3">
                            <button
                              type="button"
                              className={cn(
                                'cursor-pointer rounded-md border px-2.5 py-1 text-xs font-medium transition-colors duration-150',
                                selected
                                  ? 'border-accent bg-accent text-white'
                                  : 'border-border bg-surface text-ink hover:border-accent/50 hover:bg-bg',
                              )}
                              onClick={() => setSelectedEqpId(id)}
                            >
                              {selected ? "已选" : "选择"}
                            </button>
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="overflow-hidden rounded-lg border border-border bg-surface">
            <div className="flex items-center justify-between border-b border-border bg-bg/60 px-4 py-2.5">
              <span className="text-sm font-medium text-ink">本批生效预约</span>
              <span className="text-xs text-muted">{reserveRows.length} 条</span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[480px] text-left text-sm">
                <thead className="border-b border-border bg-bg text-xs text-muted">
                  <tr>
                    <th className="px-4 py-2.5 font-medium">设备</th>
                    <th className="px-4 py-2.5 font-medium">状态</th>
                    <th className="px-4 py-2.5 font-medium">到期</th>
                    <th className="px-4 py-2.5 font-medium">备注</th>
                  </tr>
                </thead>
                <tbody>
                  {reserveRows.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="px-4 py-8 text-center text-sm text-muted">
                        无生效预约
                      </td>
                    </tr>
                  ) : (
                    reserveRows.map((r) => (
                      <tr key={String(r.id)} className="border-b border-border last:border-b-0">
                        <td className="px-4 py-3 font-mono font-medium text-ink">{r.eqpCode ?? r.eqpId}</td>
                        <td className="px-4 py-3">
                          <span className="inline-flex items-center gap-1.5 rounded bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">
                            <span className="size-1.5 rounded-full bg-accent" aria-hidden />
                            {reserveStatusLabel(String(r.status))}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-mono text-muted">{fmtTime(r.expireTime)}</td>
                        <td className="px-4 py-3 text-muted">{r.remark ?? '-'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}
