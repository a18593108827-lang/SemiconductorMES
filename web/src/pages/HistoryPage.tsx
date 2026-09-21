import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import gsap from 'gsap'
import {
  ArrowRightLeft,
  Ban,
  CalendarClock,
  ChevronDown,
  CircleAlert,
  CirclePlay,
  CircleStop,
  Cpu,
  Diff,
  GitBranch,
  History,
  Package,
  Pause,
  Search,
  SkipForward,
} from 'lucide-react'
import { listEqpsApi, type MesEqpItem } from '../api/eqp'
import { queryHistoryApi, type HistorySeverity, type HistoryTxItem } from '../api/history'
import { listLotsApi, type MesLotItem } from '../api/lot'
import { getComplaintEnabledApi } from '../api/complaint'
import { useAuth } from '../auth/AuthContext'
import { ComplaintPackageDrawer } from '../components/lot/ComplaintPackageDrawer'
import { Button } from '../components/ui/Button'
import { Drawer } from '../components/ui/Drawer'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

type Mode = 'lot' | 'eqp'
type TxFilter = 'all' | 'alert' | string
type TimePreset = 'all' | 'today' | 'h24' | 'd7' | 'custom'

const TX_LABEL: Record<string, string> = {
  RELEASE: '放行',
  TRACK_IN: '开工',
  TRACK_OUT: '完工',
  REWORK: '返工',
  SPLIT: '分批',
  MERGE: '合批',
  SCRAP: '报废',
  BONUS: '数量调整',
  ABORT: '中止',
  MOVE: '移站',
  SKIP: '跳站',
  OFF_FLOW: '离线',
  OFF_FLOW_RESUME: '回主路径',
  HOLD: '锁批',
  RELEASE_HOLD: '解锁',
  FUTURE_HOLD_SET: '预约锁批',
  FUTURE_HOLD_CANCEL: '取消预约',
  FUTURE_HOLD_ACTIVATE: '激活预约',
  QTIME_SUPERSEDED: 'QTime覆盖',
  QTIME_CLEARED: 'QTime清除',
  EDC_COLLECT: '量测采集',
}

const TX_FILTERS: Array<{ key: TxFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'alert', label: '异常' },
  { key: 'HOLD', label: '锁批' },
  { key: 'SCRAP', label: '报废' },
  { key: 'ABORT', label: '中止' },
  { key: 'REWORK', label: '返工' },
  { key: 'SPLIT', label: '分批' },
]

const TIME_PRESETS: Array<{ key: TimePreset; label: string }> = [
  { key: 'all', label: '不限' },
  { key: 'today', label: '今天' },
  { key: 'h24', label: '近24小时' },
  { key: 'd7', label: '近7天' },
  { key: 'custom', label: '自定义' },
]

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function toLocalValue(d: Date) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

function fmtClock(v: string | null | undefined) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(11, 16)
}

function toDisplayStamp(v: string) {
  if (!v) return ''
  return v.replace('T', ' ').slice(5, 16)
}

function parseStamp(raw: string, year: number): string | null {
  const t = raw.trim().replace('T', ' ')
  const full = t.match(/^(\d{4})-(\d{2})-(\d{2})[ ](\d{2}):(\d{2})$/)
  if (full) return `${full[1]}-${full[2]}-${full[3]}T${full[4]}:${full[5]}`
  const short = t.match(/^(\d{2})-(\d{2})[ ](\d{2}):(\d{2})$/)
  if (short) return `${year}-${short[1]}-${short[2]}T${short[3]}:${short[4]}`
  return null
}

function toApiTime(v: string) {
  if (!v.trim()) return undefined
  return v.length === 16 ? `${v}:00` : v.trim()
}

function txLabel(code: string) {
  return TX_LABEL[code] ?? code
}

function severityOf(row: HistoryTxItem): HistorySeverity {
  if (row.severity === 'danger' || row.severity === 'warning' || row.severity === 'info') {
    return row.severity
  }
  const t = row.txType
  if (t === 'HOLD' || t === 'SCRAP') return 'danger'
  if (t === 'ABORT' || t === 'REWORK' || t === 'SKIP' || t === 'OFF_FLOW' || t === 'BONUS') return 'warning'
  return 'info'
}

function TxIcon({ txType, className }: { txType: string; className?: string }) {
  const cls = cn('size-3.5 shrink-0', className)
  if (txType === 'TRACK_IN') return <CirclePlay className={cls} aria-hidden />
  if (txType === 'TRACK_OUT') return <CircleStop className={cls} aria-hidden />
  if (txType === 'ABORT') return <Ban className={cls} aria-hidden />
  if (txType === 'MOVE') return <ArrowRightLeft className={cls} aria-hidden />
  if (txType === 'SCRAP' || txType === 'HOLD') return <CircleAlert className={cls} aria-hidden />
  if (txType === 'BONUS') return <Diff className={cls} aria-hidden />
  if (txType === 'SKIP') return <SkipForward className={cls} aria-hidden />
  if (txType === 'SPLIT' || txType === 'MERGE') return <GitBranch className={cls} aria-hidden />
  if (txType === 'RELEASE_HOLD') return <Pause className={cls} aria-hidden />
  return <History className={cls} aria-hidden />
}

function extEntries(row: HistoryTxItem): Array<[string, string]> {
  const src =
    row.ext && typeof row.ext === 'object'
      ? row.ext
      : row.extJson
        ? (JSON.parse(row.extJson) as Record<string, unknown>)
        : null
  if (!src) return []
  return Object.entries(src)
    .filter(([, v]) => v != null && v !== '')
    .map(([k, v]) => [k, typeof v === 'object' ? JSON.stringify(v) : String(v)])
}

function dayKey(row: HistoryTxItem) {
  return row.createTime?.slice(0, 10) ?? ''
}

function fmtDay(iso: string) {
  if (!iso) return '未知日期'
  const today = toLocalValue(new Date()).slice(0, 10)
  if (iso === today) return '今天'
  return iso.slice(5)
}

function groupByDay(rows: HistoryTxItem[]) {
  const groups: Array<{ day: string; rows: HistoryTxItem[] }> = []
  for (const row of rows) {
    const day = dayKey(row)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.rows.push(row)
    else groups.push({ day, rows: [row] })
  }
  return groups
}

function FlashItem({
  active,
  className,
  children,
  onClick,
}: {
  active: boolean
  className?: string
  children: ReactNode
  onClick: () => void
}) {
  const ref = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (!active || !ref.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      ref.current,
      { backgroundColor: 'oklch(0.58 0.12 230 / 0.18)' },
      { backgroundColor: 'transparent', duration: 0.6, ease: 'power2.out', clearProps: 'backgroundColor' },
    )
  }, [active])
  return (
    <button type="button" ref={ref} className={className} onClick={onClick}>
      {children}
    </button>
  )
}

export function HistoryPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const canView = hasPermission('history:list')
  const canComplaintView = hasPermission('complaint:view')
  const canComplaintBuild = hasPermission('complaint:build')

  const [mode, setMode] = useState<Mode>('lot')
  const [txFilter, setTxFilter] = useState<TxFilter>('all')
  const [timePreset, setTimePreset] = useState<TimePreset>('all')
  const [fromTime, setFromTime] = useState('')
  const [toTime, setToTime] = useState('')
  const [timeOpen, setTimeOpen] = useState(false)
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [customError, setCustomError] = useState('')
  const timeWrapRef = useRef<HTMLDivElement>(null)
  const timePanelRef = useRef<HTMLDivElement>(null)

  const [lotQ, setLotQ] = useState('')
  const [lotHits, setLotHits] = useState<MesLotItem[]>([])
  const [lotSearching, setLotSearching] = useState(false)
  const [selectedLot, setSelectedLot] = useState<MesLotItem | null>(null)

  const [eqpQ, setEqpQ] = useState('')
  const [eqpHits, setEqpHits] = useState<MesEqpItem[]>([])
  const [eqpSearching, setEqpSearching] = useState(false)
  const [selectedEqp, setSelectedEqp] = useState<MesEqpItem | null>(null)

  const [rows, setRows] = useState<HistoryTxItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)
  const [queried, setQueried] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)
  const [detail, setDetail] = useState<HistoryTxItem | null>(null)
  const [complaintOn, setComplaintOn] = useState(false)
  const [pkgOpen, setPkgOpen] = useState(false)

  const size = 50
  const totalPages = Math.max(1, Math.ceil(total / size))
  const subjectReady = mode === 'lot' ? !!selectedLot : !!selectedEqp
  const year = new Date().getFullYear()

  const visibleRows = useMemo(() => {
    if (txFilter !== 'alert') return rows
    return rows.filter((r) => severityOf(r) !== 'info')
  }, [rows, txFilter])

  const groups = useMemo(() => groupByDay(visibleRows), [visibleRows])
  const alertCount = useMemo(() => rows.filter((r) => severityOf(r) !== 'info').length, [rows])

  useEffect(() => {
    if (!canComplaintView) {
      setComplaintOn(false)
      return
    }
    let cancelled = false
    void getComplaintEnabledApi()
      .then((on) => {
        if (!cancelled) setComplaintOn(!!on)
      })
      .catch(() => {
        if (!cancelled) setComplaintOn(false)
      })
    return () => {
      cancelled = true
    }
  }, [canComplaintView])

  useEffect(() => {
    if (!selectedLot) setPkgOpen(false)
  }, [selectedLot])

  const timeLabel =
    timePreset === 'all'
      ? '时间不限'
      : timePreset === 'today'
        ? '今天'
        : timePreset === 'h24'
          ? '近24小时'
          : timePreset === 'd7'
            ? '近7天'
            : fromTime && toTime
              ? `${toDisplayStamp(fromTime)} - ${toDisplayStamp(toTime)}`
              : '自定义'

  const loadRows = useCallback(async () => {
    if (mode === 'lot' && !selectedLot) return
    if (mode === 'eqp' && !selectedEqp) return
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await queryHistoryApi({
        lotId: mode === 'lot' ? selectedLot?.id : undefined,
        eqpId: mode === 'eqp' ? selectedEqp?.id : undefined,
        txType: txFilter === 'all' || txFilter === 'alert' ? undefined : txFilter,
        fromTime: toApiTime(fromTime),
        toTime: toApiTime(toTime),
        page,
        size,
      })
      setRows(data.records)
      setTotal(data.total)
      setQueried(true)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '履历加载失败')
    } finally {
      setLoading(false)
    }
  }, [mode, selectedLot, selectedEqp, txFilter, fromTime, toTime, page, toast])

  useEffect(() => {
    if (!canView || !subjectReady) return
    void loadRows()
  }, [canView, subjectReady, loadRows])

  useEffect(() => {
    if (!timeOpen) return
    const onDoc = (e: MouseEvent) => {
      if (timeWrapRef.current?.contains(e.target as Node)) return
      setTimeOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setTimeOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  }, [timeOpen])

  useEffect(() => {
    if (!timeOpen || !timePanelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) {
      gsap.set(timePanelRef.current, { autoAlpha: 1, y: 0 })
      return
    }
    gsap.fromTo(
      timePanelRef.current,
      { autoAlpha: 0, y: 4 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [timeOpen])

  const searchLots = async () => {
    const kw = lotQ.trim()
    if (!kw) {
      setLotHits([])
      return
    }
    setLotSearching(true)
    try {
      const data = await listLotsApi({ keyword: kw, page: 1, size: 20 })
      setLotHits(data.records)
      if (data.records.length === 0) toast.error('没有匹配的批次')
    } catch (err) {
      setLotHits([])
      toast.error(err instanceof ApiError ? err.message : '批次搜索失败')
    } finally {
      setLotSearching(false)
    }
  }

  const searchEqps = async () => {
    const kw = eqpQ.trim()
    if (!kw) {
      setEqpHits([])
      return
    }
    setEqpSearching(true)
    try {
      const data = await listEqpsApi({ keyword: kw, page: 1, size: 20 })
      setEqpHits(data.records)
      if (data.records.length === 0) toast.error('没有匹配的设备')
    } catch (err) {
      setEqpHits([])
      toast.error(err instanceof ApiError ? err.message : '设备搜索失败')
    } finally {
      setEqpSearching(false)
    }
  }

  const pickLot = (lot: MesLotItem) => {
    setSelectedLot(lot)
    setLotQ(lot.lotNo)
    setLotHits([])
    setPage(1)
    setDetail(null)
  }

  const pickEqp = (eqp: MesEqpItem) => {
    setSelectedEqp(eqp)
    setEqpQ(`${eqp.eqpCode} ${eqp.eqpName}`)
    setEqpHits([])
    setPage(1)
    setDetail(null)
  }

  const switchMode = (next: Mode) => {
    if (next === mode) return
    setMode(next)
    setPage(1)
    setRows([])
    setTotal(0)
    setQueried(false)
    setLoadFailed(false)
    setDetail(null)
    setFlashId(null)
    setPkgOpen(false)
  }

  const applyTimePreset = (key: TimePreset) => {
    setCustomError('')
    const now = new Date()
    if (key === 'custom') {
      setTimePreset('custom')
      const start = new Date(now)
      start.setHours(0, 0, 0, 0)
      const end = new Date(now)
      end.setHours(23, 59, 0, 0)
      const from = fromTime || toLocalValue(start)
      const to = toTime || toLocalValue(end)
      setCustomFrom(toDisplayStamp(from))
      setCustomTo(toDisplayStamp(to))
      return
    }
    setTimePreset(key)
    setPage(1)
    setTimeOpen(false)
    if (key === 'all') {
      setFromTime('')
      setToTime('')
      return
    }
    if (key === 'today') {
      const start = new Date(now)
      start.setHours(0, 0, 0, 0)
      const end = new Date(now)
      end.setHours(23, 59, 0, 0)
      setFromTime(toLocalValue(start))
      setToTime(toLocalValue(end))
      return
    }
    if (key === 'h24') {
      setFromTime(toLocalValue(new Date(now.getTime() - 24 * 60 * 60 * 1000)))
      setToTime(toLocalValue(now))
      return
    }
    setFromTime(toLocalValue(new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)))
    setToTime(toLocalValue(now))
  }

  const applyCustomRange = () => {
    const from = parseStamp(customFrom, year)
    const to = parseStamp(customTo, year)
    if (!from || !to) {
      setCustomError('格式用 08-19 00:00')
      return
    }
    setCustomError('')
    setTimePreset('custom')
    setFromTime(from)
    setToTime(to)
    setPage(1)
    setTimeOpen(false)
  }

  const openRow = (row: HistoryTxItem) => {
    setDetail(row)
    setFlashId(String(row.id))
  }

  const jumpGenealogy = (lotId: number | string) => {
    navigate(`/app/lots?lotId=${lotId}`)
  }

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">履历追溯</h1>
        <p className="text-sm text-muted">无权限查看履历（需要 history:list）</p>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col gap-3">
      <header className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">履历追溯</h1>
        </div>
        <div className="flex items-center gap-3">
          <Link to="/app/report" className="text-xs text-accent hover:underline">
            打开报表
          </Link>
          {queried ? (
            <p className="font-mono text-xs tabular-nums text-muted">
              {visibleRows.length} 笔{alertCount > 0 ? ` / 异常 ${alertCount}` : ''}
              {total > 0 ? ` / 共 ${total}` : ''}
            </p>
          ) : null}
        </div>
      </header>

      <div className="flex flex-wrap items-center gap-2">
        <div className="inline-flex h-9 rounded-md border border-border p-0.5">
          {(
            [
              { key: 'lot' as const, label: '按批次', icon: Package },
              { key: 'eqp' as const, label: '按设备', icon: Cpu },
            ] as const
          ).map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => switchMode(t.key)}
              className={cn(
                'inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-[5px] px-2.5 text-sm font-medium transition-colors duration-150',
                mode === t.key ? 'bg-primary text-white' : 'text-muted hover:text-ink',
              )}
            >
              <t.icon className="size-3.5" aria-hidden />
              {t.label}
            </button>
          ))}
        </div>

        <div className="relative">
          <label className="sr-only" htmlFor="hist-q">
            {mode === 'lot' ? '批次号' : '设备编码'}
          </label>
          <input
            id="hist-q"
            className="h-9 w-56 rounded-md border border-border bg-bg px-3 font-mono text-sm"
            placeholder={mode === 'lot' ? '批次号' : '设备编码 / 名称'}
            value={mode === 'lot' ? lotQ : eqpQ}
            onChange={(e) => {
              if (mode === 'lot') {
                setLotQ(e.target.value)
                if (selectedLot && e.target.value !== selectedLot.lotNo) setSelectedLot(null)
              } else {
                setEqpQ(e.target.value)
                if (selectedEqp) setSelectedEqp(null)
              }
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void (mode === 'lot' ? searchLots() : searchEqps())
            }}
          />
          {(mode === 'lot' ? lotHits : eqpHits).length > 0 ? (
            <ul className="absolute z-20 mt-1 max-h-56 w-full overflow-auto rounded-md border border-border bg-bg py-1 shadow-[0_8px_24px_oklch(0_0_0/0.12)]">
              {mode === 'lot'
                ? lotHits.map((lot) => (
                    <li key={String(lot.id)}>
                      <button
                        type="button"
                        className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-surface"
                        onClick={() => pickLot(lot)}
                      >
                        <span className="font-mono text-[13px]">{lot.lotNo}</span>
                        <span className="text-xs text-muted">{lot.status}</span>
                      </button>
                    </li>
                  ))
                : eqpHits.map((eqp) => (
                    <li key={String(eqp.id)}>
                      <button
                        type="button"
                        className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-surface"
                        onClick={() => pickEqp(eqp)}
                      >
                        <span className="font-mono text-[13px]">{eqp.eqpCode}</span>
                        <span className="truncate text-xs text-muted">{eqp.eqpName}</span>
                      </button>
                    </li>
                  ))}
            </ul>
          ) : null}
        </div>

        <Button
          variant="secondary"
          loading={mode === 'lot' ? lotSearching : eqpSearching}
          onClick={() => void (mode === 'lot' ? searchLots() : searchEqps())}
        >
          <Search className="size-4" aria-hidden />
          搜索
        </Button>
        {mode === 'lot' && selectedLot && canComplaintView && complaintOn ? (
          <Button variant="secondary" onClick={() => setPkgOpen(true)}>
            生成追溯包
          </Button>
        ) : null}

        <div ref={timeWrapRef} className="relative">
          <button
            type="button"
            aria-expanded={timeOpen}
            aria-haspopup="dialog"
            onClick={() => setTimeOpen((v) => !v)}
            className={cn(
              'inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-md border px-2.5 text-sm transition-colors duration-150',
              timePreset === 'all'
                ? 'border-border bg-bg text-muted hover:bg-surface'
                : 'border-primary bg-primary text-white',
            )}
          >
            <CalendarClock className="size-3.5" aria-hidden />
            <span className="font-mono text-[13px] tabular-nums">{timeLabel}</span>
            <ChevronDown className="size-3.5 opacity-70" aria-hidden />
          </button>
          {timeOpen ? (
            <div
              ref={timePanelRef}
              role="dialog"
              aria-label="时间范围"
              className="absolute left-0 z-30 mt-1 w-[240px] rounded-md border border-border bg-bg p-2 shadow-[0_8px_24px_oklch(0_0_0/0.12)]"
            >
              <div className="flex flex-col">
                {TIME_PRESETS.map((s) => (
                  <button
                    key={s.key}
                    type="button"
                    onClick={() => applyTimePreset(s.key)}
                    className={cn(
                      'flex h-8 cursor-pointer items-center rounded-md px-2 text-left text-sm',
                      timePreset === s.key ? 'bg-surface font-medium text-ink' : 'text-muted hover:bg-surface hover:text-ink',
                    )}
                  >
                    {s.label}
                  </button>
                ))}
              </div>
              {timePreset === 'custom' ? (
                <div className="mt-2 space-y-2 border-t border-border pt-2">
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium text-muted">从</span>
                    <input
                      className="h-9 rounded-md border border-border bg-bg px-2.5 font-mono text-[13px] tabular-nums"
                      placeholder="08-19 00:00"
                      value={customFrom}
                      onChange={(e) => setCustomFrom(e.target.value)}
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="text-xs font-medium text-muted">到</span>
                    <input
                      className="h-9 rounded-md border border-border bg-bg px-2.5 font-mono text-[13px] tabular-nums"
                      placeholder="08-19 23:59"
                      value={customTo}
                      onChange={(e) => setCustomTo(e.target.value)}
                    />
                  </label>
                  {customError ? <p className="text-xs text-danger">{customError}</p> : null}
                  <Button className="w-full" onClick={applyCustomRange}>
                    应用
                  </Button>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="flex flex-wrap gap-1">
          {TX_FILTERS.map((s) => (
            <button
              key={s.key}
              type="button"
              onClick={() => {
                setTxFilter(s.key)
                setPage(1)
              }}
              className={cn(
                'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
                txFilter === s.key
                  ? s.key === 'alert'
                    ? 'border-danger bg-danger text-white'
                    : 'border-primary bg-primary text-white'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto rounded-md border border-border">
        {loading ? (
          <div>
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="flex h-10 items-center gap-3 border-b border-border px-3 last:border-0">
                <span className="h-3 w-10 rounded-sm bg-surface" />
                <span className="h-3 w-14 rounded-sm bg-surface" />
                <span className="h-3 flex-1 max-w-xs rounded-sm bg-surface" />
              </div>
            ))}
          </div>
        ) : loadFailed ? (
          <div className="flex h-40 flex-col items-center justify-center gap-2 text-sm text-muted">
            <p>加载失败</p>
            <button type="button" className="cursor-pointer text-accent hover:underline" onClick={() => void loadRows()}>
              重试
            </button>
          </div>
        ) : !subjectReady ? (
          <div className="flex h-40 items-center justify-center px-4 text-center text-sm text-muted">
            {mode === 'lot' ? '搜批次号并点选，查看这批走过的事务。' : '搜设备并点选，圈这台机跑过的批。'}
          </div>
        ) : visibleRows.length === 0 ? (
          <div className="flex h-40 items-center justify-center px-4 text-center text-sm text-muted">
            {queried ? '没有符合条件的事务。' : '尚未查询。'}
          </div>
        ) : (
          <div>
            {groups.map((g) => (
              <section key={g.day || 'none'}>
                <h2 className="sticky top-0 z-10 border-b border-border bg-surface px-3 py-1.5 font-mono text-xs font-medium tabular-nums text-muted">
                  {fmtDay(g.day)}
                </h2>
                <ul>
                  {g.rows.map((row) => {
                    const sev = severityOf(row)
                    const splitOrMerge = row.txType === 'SPLIT' || row.txType === 'MERGE'
                    const meta = [
                      mode === 'eqp' ? row.lotNo : null,
                      row.stepName || (row.stepId != null ? `工序 ${row.stepId}` : null),
                      row.eqpCode || null,
                      row.operUserName || null,
                    ].filter(Boolean)
                    return (
                      <li
                        key={String(row.id)}
                        className={cn(
                          'flex border-b border-border last:border-0',
                          sev === 'danger' && 'bg-danger/[0.06]',
                          sev === 'warning' && 'bg-warning/[0.10]',
                          String(detail?.id) === String(row.id) && 'bg-surface',
                        )}
                      >
                        <FlashItem
                          active={flashId === String(row.id)}
                          onClick={() => openRow(row)}
                          className={cn(
                            'grid h-10 min-w-0 flex-1 grid-cols-[52px_minmax(72px,92px)_1fr] items-center gap-2 px-3 text-left hover:bg-surface',
                            sev === 'danger' && 'hover:bg-danger/[0.09]',
                            sev === 'warning' && 'hover:bg-warning/[0.14]',
                          )}
                        >
                          <span className="font-mono text-[13px] tabular-nums text-muted">{fmtClock(row.createTime)}</span>
                          <span
                            className={cn(
                              'inline-flex items-center gap-1.5 text-sm font-medium',
                              sev === 'danger' && 'text-danger',
                            )}
                          >
                            <TxIcon
                              txType={row.txType}
                              className={
                                sev === 'danger' ? 'text-danger' : sev === 'warning' ? 'text-warning' : 'text-muted'
                              }
                            />
                            {txLabel(row.txType)}
                          </span>
                          <span className="truncate font-mono text-[12px] text-muted">{meta.join('  ')}</span>
                        </FlashItem>
                        {splitOrMerge ? (
                          <button
                            type="button"
                            className="inline-flex h-10 shrink-0 items-center gap-1 px-3 text-xs font-medium text-accent hover:underline"
                            onClick={() => jumpGenealogy(row.lotId)}
                          >
                            <GitBranch className="size-3" aria-hidden />
                            谱系
                          </button>
                        ) : null}
                      </li>
                    )
                  })}
                </ul>
              </section>
            ))}
          </div>
        )}
      </div>

      {subjectReady && queried && total > 0 ? (
        <div className="flex items-center justify-end gap-2">
          <Button variant="secondary" disabled={page <= 1 || loading} onClick={() => setPage((p) => p - 1)}>
            上一页
          </Button>
          <span className="font-mono text-xs tabular-nums">
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
      ) : null}

      <Drawer
        open={!!detail}
        title={detail ? txLabel(detail.txType) : '事务详情'}
        onClose={() => setDetail(null)}
        width={480}
      >
        {detail ? (
          <div className="space-y-4">
            {severityOf(detail) !== 'info' ? (
              <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
                <span
                  className={cn(
                    'size-1.5 rounded-full',
                    severityOf(detail) === 'danger' ? 'bg-danger' : 'bg-warning',
                  )}
                  aria-hidden
                />
                {severityOf(detail) === 'danger' ? '严重' : '注意'}
              </span>
            ) : null}
            <dl className="grid grid-cols-[88px_1fr] gap-x-3 gap-y-2 text-sm">
              <dt className="text-xs font-medium text-muted">时间</dt>
              <dd className="font-mono text-[13px] tabular-nums">{fmtTime(detail.createTime)}</dd>
              <dt className="text-xs font-medium text-muted">事务码</dt>
              <dd className="font-mono text-[13px]">{detail.txType}</dd>
              <dt className="text-xs font-medium text-muted">批次</dt>
              <dd className="font-mono text-[13px]">{detail.lotNo}</dd>
              <dt className="text-xs font-medium text-muted">工序</dt>
              <dd>{detail.stepName || (detail.stepId != null ? String(detail.stepId) : '-')}</dd>
              <dt className="text-xs font-medium text-muted">设备</dt>
              <dd className="font-mono text-[13px]">
                {detail.eqpCode || '-'}
                {detail.eqpName ? ` ${detail.eqpName}` : ''}
              </dd>
              <dt className="text-xs font-medium text-muted">Recipe</dt>
              <dd className="font-mono text-[13px]">
                {detail.recipeVersionNo != null ? `v${detail.recipeVersionNo}` : '-'}
              </dd>
              <dt className="text-xs font-medium text-muted">状态</dt>
              <dd className="font-mono text-xs">
                {detail.fromStatus ?? '-'} → {detail.toStatus ?? '-'}
                {detail.fromSortNo != null || detail.toSortNo != null
                  ? `  S${detail.fromSortNo ?? '-'}→S${detail.toSortNo ?? '-'}`
                  : ''}
              </dd>
              <dt className="text-xs font-medium text-muted">操作人</dt>
              <dd>{detail.operUserName || '-'}</dd>
              <dt className="text-xs font-medium text-muted">备注</dt>
              <dd>{detail.remark || '-'}</dd>
            </dl>
            {(() => {
              try {
                const entries = extEntries(detail)
                if (entries.length === 0) return null
                return (
                  <div>
                    <p className="mb-2 text-xs font-medium text-muted">扩展</p>
                    <dl className="grid grid-cols-[88px_1fr] gap-x-3 gap-y-1.5 text-sm">
                      {entries.map(([k, v]) => (
                        <div key={k} className="contents">
                          <dt className="font-mono text-[11px] text-muted">{k}</dt>
                          <dd className="break-all font-mono text-[13px]">{v}</dd>
                        </div>
                      ))}
                    </dl>
                  </div>
                )
              } catch {
                return detail.extJson ? (
                  <p className="break-all font-mono text-xs text-muted">{detail.extJson}</p>
                ) : null
              }
            })()}
            {detail.txType === 'SPLIT' || detail.txType === 'MERGE' ? (
              <Button variant="secondary" onClick={() => jumpGenealogy(detail.lotId)}>
                <GitBranch className="size-4" aria-hidden />
                查看谱系
              </Button>
            ) : null}
          </div>
        ) : null}
      </Drawer>
      <ComplaintPackageDrawer
        open={pkgOpen}
        onClose={() => setPkgOpen(false)}
        anchorLotId={selectedLot?.id}
        anchorLotNo={selectedLot?.lotNo ?? ''}
        canBuild={canComplaintBuild}
      />
    </div>
  )
}
