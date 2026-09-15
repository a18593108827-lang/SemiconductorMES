import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import gsap from 'gsap'
import {
  ArrowRight,
  Bookmark,
  BookmarkX,
  CalendarClock,
  CheckCircle2,
  CircleAlert,
  CirclePlay,
  CircleStop,
  Ban,
  Diff,
  ArrowRightLeft,
  History,
  Loader2,
  LogIn,
  LogOut,
  PauseCircle,
  RotateCcw,
  Route,
  Search,
  SkipForward,
  Sparkles,
  Split,
  GitMerge,
  Undo2,
  XCircle,
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
import { getLotApi, listLotsApi, type MesLotItem, type MesLotStatus } from '../api/lot'
import {
  cancelFutureHoldApi,
  createFutureHoldApi,
  createHoldApi,
  listHoldReasonsApi,
  listLotHoldsApi,
  releaseHoldApi,
  type FutureHoldTiming,
  type MesHoldItem,
  type MesHoldReason,
} from '../api/hold'
import {
  getAbortReasonCodesApi,
  getBonusReasonCodesApi,
  getLotHistoryApi,
  getMergeCandidatesApi,
  getScrapReasonCodesApi,
  getTrackContextApi,
  trackAbortApi,
  trackBonusApi,
  trackInApi,
  trackMergeApi,
  trackMoveApi,
  trackOffFlowApi,
  trackOffFlowResumeApi,
  trackOutApi,
  trackReworkApi,
  trackScrapApi,
  trackSkipApi,
  trackSplitApi,
  type MesTxLogItem,
  type TrackAbortReason,
  type TrackBonusReason,
  type TrackContext,
  type TrackMergeCandidate,
  type TrackScrapReason,
} from '../api/track'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { MesLotStatusPill } from '../components/ui/StatusPill'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'
import { QueueTimeBanner } from '../components/track/QueueTimeBanner'
import { ProcessTimeBanner, useLiveProcessGate } from '../components/track/ProcessTimeBanner'
import { EdcCollectDock } from '../components/track/EdcCollectDock'
import { EdcGateHint } from '../components/track/EdcGateHint'
import { AbortPanel } from '../components/track/AbortPanel'
import { MovePanel } from '../components/track/MovePanel'

type Feedback = { type: 'ok' | 'err'; text: string } | null

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

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** 载具闸错误可读化 */
function formatCarrierGateError(msg: string) {
  if (msg.includes('CARRIER_MISMATCH')) {
    return '扫码与绑定载具不一致，请核对实物标签后重扫'
  }
  if (msg.includes('CARRIER_SCAN_REQUIRED')) {
    return '请扫描载具编码后再开工'
  }
  if (msg.includes('CARRIER_NOT_FOUND') && msg.includes('脏绑定')) {
    return '载具绑定数据异常（台账缺失），请联系工程清理后重试'
  }
  if (msg.includes('CARRIER_REQUIRED')) {
    return '批次未绑定载具，请先完成绑定'
  }
  return msg
}

function fmtRemain(expireTime: string | null | undefined) {
  if (!expireTime) return '—'
  const t = new Date(expireTime.replace(' ', 'T')).getTime() - Date.now()
  if (Number.isNaN(t) || t <= 0) return '已到期'
  const m = Math.floor(t / 60000)
  const s = Math.floor((t % 60000) / 1000)
  return `${m}分${String(s).padStart(2, '0')}秒`
}

function requiresHoldReleaseRemark(code?: string | null) {
  return code === 'QTIME_EXCEED' || code === 'EDC_OOS'
}

export function TrackPage() {
  const toast = useToast()
  const { hasPermission } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const bootLotRef = useRef(false)
  const [queue, setQueue] = useState<MesLotItem[]>([])
  const [queueLoading, setQueueLoading] = useState(false)
  const [ctx, setCtx] = useState<TrackContext | null>(null)
  const [history, setHistory] = useState<MesTxLogItem[]>([])
  const [loadingLot, setLoadingLot] = useState(false)
  const [action, setAction] = useState<
    | 'in'
    | 'out'
    | 'hold'
    | 'release'
    | 'rework'
    | 'skip'
    | 'offflow'
    | 'resume'
    | 'future'
    | 'cancelFh'
    | 'split'
    | 'merge'
    | 'scrap'
    | 'bonus'
    | 'abort'
    | 'move'
    | null
  >(null)
  const [lotQty, setLotQty] = useState<number | null>(null)
  const [lotScrapQty, setLotScrapQty] = useState<number | null>(null)
  const [lotProductCode, setLotProductCode] = useState<string | null>(null)
  const [splitPanel, setSplitPanel] = useState(false)
  const [splitRows, setSplitRows] = useState<{ qty: string; lotNo: string }[]>([
    { qty: '1', lotNo: '' },
  ])
  const [splitReason, setSplitReason] = useState('')
  const [splitRemark, setSplitRemark] = useState('')
  const splitPanelRef = useRef<HTMLDivElement>(null)
  const [mergePanel, setMergePanel] = useState(false)
  const [mergeCandidates, setMergeCandidates] = useState<TrackMergeCandidate[]>([])
  const [mergeSelected, setMergeSelected] = useState<string[]>([])
  const [mergeLoading, setMergeLoading] = useState(false)
  const [mergeReason, setMergeReason] = useState('')
  const [mergeRemark, setMergeRemark] = useState('')
  const mergePanelRef = useRef<HTMLDivElement>(null)
  const [scrapPanel, setScrapPanel] = useState(false)
  const [scrapQtyInput, setScrapQtyInput] = useState('1')
  const [scrapReasons, setScrapReasons] = useState<TrackScrapReason[]>([])
  const [scrapReason, setScrapReason] = useState('')
  const [scrapRemark, setScrapRemark] = useState('')
  const scrapPanelRef = useRef<HTMLDivElement>(null)
  const [bonusPanel, setBonusPanel] = useState(false)
  const [bonusDeltaInput, setBonusDeltaInput] = useState('1')
  const [bonusReasons, setBonusReasons] = useState<TrackBonusReason[]>([])
  const [bonusReason, setBonusReason] = useState('')
  const [bonusRemark, setBonusRemark] = useState('')
  const bonusPanelRef = useRef<HTMLDivElement>(null)
  const [abortPanel, setAbortPanel] = useState(false)
  const [abortReasons, setAbortReasons] = useState<TrackAbortReason[]>([])
  const [abortReason, setAbortReason] = useState('')
  const [abortRemark, setAbortRemark] = useState('')
  const [movePanel, setMovePanel] = useState(false)
  const [moveRemark, setMoveRemark] = useState('')
  const [feedback, setFeedback] = useState<Feedback>(null)
  const [holdPanel, setHoldPanel] = useState(false)
  const [releaseHoldPanel, setReleaseHoldPanel] = useState(false)
  const [releaseHoldRemark, setReleaseHoldRemark] = useState('')
  const [futurePanel, setFuturePanel] = useState(false)
  const [fhTargetSort, setFhTargetSort] = useState('')
  const [fhTiming, setFhTiming] = useState<FutureHoldTiming>('PRE')
  const [fhReasonCode, setFhReasonCode] = useState('')
  const [fhRemark, setFhRemark] = useState('')
  const [reworkPanel, setReworkPanel] = useState(false)
  const [reworkToSort, setReworkToSort] = useState('')
  const [reworkReason, setReworkReason] = useState('')
  const [reworkRemark, setReworkRemark] = useState('')
  const [skipPanel, setSkipPanel] = useState(false)
  const [skipToSort, setSkipToSort] = useState('')
  const [skipReason, setSkipReason] = useState('')
  const [skipRemark, setSkipRemark] = useState('')
  const [offFlowPanel, setOffFlowPanel] = useState(false)
  const [offFlowToSort, setOffFlowToSort] = useState('')
  const [offFlowReason, setOffFlowReason] = useState('')
  const [offFlowRemark, setOffFlowRemark] = useState('')
  const [resultCode, setResultCode] = useState('')
  const [reasons, setReasons] = useState<MesHoldReason[]>([])
  const [reasonCode, setReasonCode] = useState('')
  const [holdRemark, setHoldRemark] = useState('')
  const [activeHold, setActiveHold] = useState<MesHoldItem | null>(null)
  const [candidates, setCandidates] = useState<DispatchCandidates | null>(null)
  const [candidateItems, setCandidateItems] = useState<DispatchCandidateItem[]>([])
  const [selectedEqpId, setSelectedEqpId] = useState('')
  const [scannedCarrierCode, setScannedCarrierCode] = useState('')
  const [activeReserve, setActiveReserve] = useState<DispatchReserveItem | null>(null)
  const [reserveAction, setReserveAction] = useState(false)
  const [remainTick, setRemainTick] = useState(0)

  const canHold = hasPermission('hold:create')
  const canReleaseHold = hasPermission('hold:release')
  const canReserve = hasPermission('dispatch:reserve')
  const processGate = useLiveProcessGate(ctx?.processTime)

  const feedbackRef = useRef<HTMLDivElement>(null)
  const statusFlashRef = useRef<HTMLDivElement>(null)
  const stationHopRef = useRef<HTMLDivElement>(null)
  const lastSortRef = useRef<number | null>(null)
  const historyRef = useRef<HTMLUListElement>(null)
  const holdPanelRef = useRef<HTMLDivElement>(null)
  const futurePanelRef = useRef<HTMLDivElement>(null)
  const recommendRef = useRef<HTMLParagraphElement>(null)

  const loadQueue = useCallback(async () => {
    setQueueLoading(true)
    try {
      const [wait, processing] = await Promise.all([
        listLotsApi({ status: 'wait', page: 1, size: 20 }),
        listLotsApi({ status: 'processing', page: 1, size: 20 }),
      ])
      const merged = [...processing.records, ...wait.records]
      const seen = new Set<string>()
      const next = merged.filter((l) => {
        const k = String(l.id)
        if (seen.has(k)) return false
        seen.add(k)
        return true
      })
      setQueue(next)
      return next
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '队列加载失败')
      return [] as MesLotItem[]
    } finally {
      setQueueLoading(false)
    }
  }, [toast])

  useEffect(() => {
    void loadQueue()
  }, [loadQueue])

  const flashFeedback = useCallback((next: Feedback) => {
    setFeedback(next)
    const el = feedbackRef.current
    if (!el || !next) return
    if (prefersReducedMotion()) {
      gsap.set(el, { autoAlpha: 1, y: 0 })
      return
    }
    gsap.fromTo(
      el,
      { autoAlpha: 0, y: -8 },
      { autoAlpha: 1, y: 0, duration: 0.2, ease: 'power2.out' },
    )
  }, [])

  const flashStatus = useCallback(() => {
    const el = statusFlashRef.current
    if (!el || prefersReducedMotion()) return
    gsap.fromTo(
      el,
      { boxShadow: '0 0 0 0 oklch(0.58 0.12 230 / 0)' },
      {
        boxShadow: '0 0 0 2px oklch(0.58 0.12 230 / 0.55)',
        duration: 0.18,
        yoyo: true,
        repeat: 1,
        ease: 'power2.out',
      },
    )
  }, [])

  const loadLotById = useCallback(
    async (lotId: number | string, opts?: { keepFeedback?: boolean }) => {
      setLoadingLot(true)
      if (!opts?.keepFeedback) {
        setFeedback(null)
      }
      setHoldPanel(false)
      setFuturePanel(false)
      setReworkPanel(false)
      setSkipPanel(false)
      setOffFlowPanel(false)
      setSplitPanel(false)
      setMergePanel(false)
      setScrapPanel(false)
      setBonusPanel(false)
      setAbortPanel(false); setMovePanel(false)
      try {
        const [context, logs, holds, lot] = await Promise.all([
          getTrackContextApi(lotId),
          getLotHistoryApi(lotId),
          listLotHoldsApi(lotId).catch(() => [] as MesHoldItem[]),
          getLotApi(lotId).catch(() => null),
        ])
        setCtx(context)
        setLotQty(lot?.qty ?? null)
        setLotScrapQty(lot?.scrapQty ?? null)
        setLotProductCode(lot?.productCode ?? null)
        setHistory(logs)
        setSplitRows([{ qty: '1', lotNo: '' }])
        setSplitReason('')
        setSplitRemark('')
        setMergeCandidates([])
        setMergeSelected([])
        setMergeReason('')
        setMergeRemark('')
        setScrapQtyInput('1')
        setScrapReason('')
        setScrapRemark('')
        setBonusDeltaInput('1')
        setBonusReason('')
        setBonusRemark('')
        setQuery(context.lotNo)
        setActiveHold(holds.find((h) => h.status === 'active') ?? null)
        setSelectedEqpId(context.currentEqpId != null ? String(context.currentEqpId) : '')
        const firstOpt = context.reworkOptions?.[0]
        setReworkToSort(firstOpt ? String(firstOpt.toSortNo) : '')
        setReworkReason(firstOpt?.reasonCodes?.[0] ?? '')
        setReworkRemark('')
        const firstSkip = context.skipOptions?.[0]
        setSkipToSort(firstSkip ? String(firstSkip.toSortNo) : '')
        setSkipReason(firstSkip?.reasonCodes?.[0] ?? '')
        setSkipRemark('')
        const firstOff = context.offFlowOptions?.[0]
        setOffFlowToSort(firstOff ? String(firstOff.toSortNo) : '')
        setOffFlowReason(firstOff?.reasonCodes?.[0] ?? '')
        setOffFlowRemark('')
        setFhTargetSort(
          context.nextStep?.sortNo != null
            ? String(context.nextStep.sortNo)
            : context.currentSortNo != null
              ? String(context.currentSortNo)
              : '',
        )
        setFhTiming('PRE')
        setFhRemark('')
        setResultCode('')
        setScannedCarrierCode('')
        flashStatus()
        if (historyRef.current && !prefersReducedMotion()) {
          gsap.fromTo(
            historyRef.current.children,
            { autoAlpha: 0.35 },
            { autoAlpha: 1, duration: 0.18, stagger: 0.03, ease: 'power2.out' },
          )
        }
      } catch (e) {
        setCtx(null)
        setHistory([])
        setLotProductCode(null)
        setActiveHold(null)
        setSelectedEqpId('')
        setCandidates(null)
        setCandidateItems([])
        setActiveReserve(null)
        toast.error(e instanceof ApiError ? e.message : '载入失败')
      } finally {
        setLoadingLot(false)
      }
    },
    [flashStatus, toast],
  )

  useEffect(() => {
    if (bootLotRef.current) return
    const st = location.state as { lotId?: number | string; lotNo?: string } | null
    if (st?.lotId == null) return
    bootLotRef.current = true
    void loadLotById(st.lotId)
    navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, loadLotById, navigate])

  const loadDispatch = useCallback(async (lotId: number | string, preferEqpId?: string) => {
    try {
      const [cand, reserves] = await Promise.all([
        getDispatchCandidatesApi(lotId),
        listDispatchReservesApi({ lotId, status: 'active' }).catch(() => [] as DispatchReserveItem[]),
      ])
      setCandidates(cand)
      setCandidateItems(cand.candidates ?? [])
      const reserve = reserves[0] ?? null
      setActiveReserve(reserve)
      setSelectedEqpId((prev) => {
        const ids = (cand.candidates ?? []).map((c) => String(c.eqpId))
        if (preferEqpId && ids.includes(String(preferEqpId))) return String(preferEqpId)
        if (reserve && ids.includes(String(reserve.eqpId))) return String(reserve.eqpId)
        if (prev && ids.includes(prev)) return prev
        if (cand.recommendedEqpId != null && ids.includes(String(cand.recommendedEqpId))) {
          return String(cand.recommendedEqpId)
        }
        return ids[0] ?? ''
      })
      if (recommendRef.current && cand.recommendedEqpId != null && !prefersReducedMotion()) {
        gsap.fromTo(
          recommendRef.current,
          { autoAlpha: 0.4 },
          { autoAlpha: 1, duration: 0.2, ease: 'power2.out' },
        )
      }
    } catch {
      setCandidates(null)
      setCandidateItems([])
      setActiveReserve(null)
    }
  }, [])

  useEffect(() => {
    if (!ctx?.lotId) {
      setCandidates(null)
      setCandidateItems([])
      setActiveReserve(null)
      return
    }
    if (!ctx.canTrackIn && ctx.status !== 'wait' && ctx.status !== 'processing') {
      setCandidates(null)
      setCandidateItems([])
      return
    }
    void loadDispatch(ctx.lotId, ctx.currentEqpId != null ? String(ctx.currentEqpId) : undefined)
  }, [
    ctx?.canTrackIn,
    ctx?.lotId,
    ctx?.status,
    ctx?.currentEqpId,
    ctx?.currentSortNo,
    ctx?.currentStepId,
    loadDispatch,
  ])

  // 站号变了：当前站/下一站卡片闪一下，让人看清已经搬家
  useEffect(() => {
    const sort = ctx?.currentSortNo ?? null
    const prev = lastSortRef.current
    lastSortRef.current = sort
    if (prev == null || sort == null || prev === sort) return
    const el = stationHopRef.current
    if (!el || prefersReducedMotion()) return
    gsap.fromTo(
      el,
      { boxShadow: '0 0 0 0 oklch(0.58 0.12 230 / 0)' },
      {
        boxShadow: '0 0 0 2px oklch(0.58 0.12 230 / 0.55)',
        duration: 0.2,
        yoyo: true,
        repeat: 1,
        ease: 'power2.out',
      },
    )
  }, [ctx?.lotId, ctx?.currentSortNo])

  useEffect(() => {
    if (!activeReserve?.expireTime) return
    const id = window.setInterval(() => setRemainTick((n) => n + 1), 1000)
    return () => window.clearInterval(id)
  }, [activeReserve?.id, activeReserve?.expireTime])

  useEffect(() => {
    if (!canHold) return
    void listHoldReasonsApi(false)
      .then((data) => {
        setReasons(data)
        if (data[0]) setReasonCode(data[0].reasonCode)
      })
      .catch(() => setReasons([]))
  }, [canHold])

  useEffect(() => {
    if (!holdPanel || !holdPanelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      holdPanelRef.current,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [holdPanel])

  useEffect(() => {
    if (!futurePanel || !futurePanelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      futurePanelRef.current,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [futurePanel])

  useEffect(() => {
    if (!splitPanel || !splitPanelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      splitPanelRef.current,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [splitPanel])

  useEffect(() => {
    if (!mergePanel || !mergePanelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      mergePanelRef.current,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [mergePanel])

  useEffect(() => {
    if (!scrapPanel || !scrapPanelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      scrapPanelRef.current,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [scrapPanel])

  useEffect(() => {
    if (!bonusPanel || !bonusPanelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      bonusPanelRef.current,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [bonusPanel])

  async function openHoldPanel() {
    setHoldRemark('')
    setHoldPanel(true)
    setReleaseHoldPanel(false)
    setFuturePanel(false)
    setMergePanel(false)
    setScrapPanel(false)
    setBonusPanel(false)
    setAbortPanel(false); setMovePanel(false)
  }

  function openFuturePanel() {
    setFhReasonCode(reasons[0]?.reasonCode ?? reasonCode)
    setFhRemark('')
    if (!fhTargetSort && ctx) {
      setFhTargetSort(
        ctx.nextStep?.sortNo != null
          ? String(ctx.nextStep.sortNo)
          : ctx.currentSortNo != null
            ? String(ctx.currentSortNo)
            : '',
      )
    }
    setFuturePanel(true)
    setHoldPanel(false)
    setReworkPanel(false)
    setSkipPanel(false)
    setOffFlowPanel(false)
    setSplitPanel(false)
    setMergePanel(false)
    setScrapPanel(false)
    setBonusPanel(false)
    setAbortPanel(false); setMovePanel(false)
  }

  async function openMergePanel() {
    if (!ctx) return
    setMergeReason('')
    setMergeRemark('')
    setMergeSelected([])
    setMergeCandidates([])
    setReworkPanel(false)
    setSkipPanel(false)
    setOffFlowPanel(false)
    setSplitPanel(false)
    setHoldPanel(false)
    setFuturePanel(false)
    setScrapPanel(false)
    setBonusPanel(false)
    setAbortPanel(false); setMovePanel(false)
    setMergePanel(true)
    setMergeLoading(true)
    try {
      const rows = await getMergeCandidatesApi(ctx.lotId)
      setMergeCandidates(rows)
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '加载合批候选失败',
      })
      setMergePanel(false)
    } finally {
      setMergeLoading(false)
    }
  }

  async function runHold() {
    if (!ctx) return
    if (!reasonCode) {
      flashFeedback({ type: 'err', text: '请选择原因码' })
      return
    }
    if (reasonCode === 'OTHER' && !holdRemark.trim()) {
      flashFeedback({ type: 'err', text: '原因码为其它时须填写备注' })
      return
    }
    setAction('hold')
    try {
      const row = await createHoldApi({
        lotId: ctx.lotId,
        reasonCode,
        remark: holdRemark.trim() || undefined,
      })
      flashFeedback({ type: 'ok', text: `已锁批 · ${row.lotNo}` })
      setHoldPanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({ type: 'err', text: e instanceof ApiError ? e.message : '锁批失败' })
    } finally {
      setAction(null)
    }
  }

  async function runReleaseHold() {
    if (!ctx || !activeHold) return
    if (requiresHoldReleaseRemark(activeHold.reasonCode) && !releaseHoldPanel) {
      setHoldPanel(false)
      setReleaseHoldRemark('')
      setReleaseHoldPanel(true)
      return
    }
    if (requiresHoldReleaseRemark(activeHold.reasonCode) && !releaseHoldRemark.trim()) {
      flashFeedback({
        type: 'err',
        text: activeHold.reasonCode === 'EDC_OOS' ? '量测超规解锁须填写备注' : 'Queue Time 解锁须填写备注',
      })
      return
    }
    setAction('release')
    try {
      await releaseHoldApi(
        activeHold.id,
        requiresHoldReleaseRemark(activeHold.reasonCode) ? releaseHoldRemark.trim() : undefined,
      )
      flashFeedback({ type: 'ok', text: `已解锁 · ${ctx.lotNo}` })
      setReleaseHoldPanel(false)
      setReleaseHoldRemark('')
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({ type: 'err', text: e instanceof ApiError ? e.message : '解锁失败' })
    } finally {
      setAction(null)
    }
  }

  async function runFutureHold() {
    if (!ctx) return
    const sortNo = Number(fhTargetSort)
    if (!Number.isFinite(sortNo) || sortNo <= 0) {
      flashFeedback({ type: 'err', text: '请填写有效目标站序' })
      return
    }
    if (!fhReasonCode) {
      flashFeedback({ type: 'err', text: '请选择原因码' })
      return
    }
    if (fhReasonCode === 'OTHER' && !fhRemark.trim()) {
      flashFeedback({ type: 'err', text: '原因码为其它时须填写备注' })
      return
    }
    setAction('future')
    try {
      const row = await createFutureHoldApi({
        lotId: ctx.lotId,
        targetSortNo: sortNo,
        timing: fhTiming,
        reasonCode: fhReasonCode,
        remark: fhRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `已预约 · 站 ${row.targetSortNo} ${row.timing}`,
      })
      setFuturePanel(false)
      await loadLotById(ctx.lotId)
    } catch (e) {
      flashFeedback({ type: 'err', text: e instanceof ApiError ? e.message : '预约失败' })
    } finally {
      setAction(null)
    }
  }

  async function runCancelFutureHold(id: number | string) {
    if (!ctx) return
    const ok = window.confirm('确认取消该预约锁批？')
    if (!ok) return
    setAction('cancelFh')
    try {
      await cancelFutureHoldApi(id)
      flashFeedback({ type: 'ok', text: '已取消预约' })
      await loadLotById(ctx.lotId)
    } catch (e) {
      flashFeedback({ type: 'err', text: e instanceof ApiError ? e.message : '取消失败' })
    } finally {
      setAction(null)
    }
  }

  async function resolveAndLoad() {
    const keyword = query.trim()
    if (!keyword) {
      toast.error('请输入批次号')
      return
    }
    setLoadingLot(true)
    try {
      const page = await listLotsApi({ keyword, page: 1, size: 20 })
      const exact = page.records.find((r) => r.lotNo.toUpperCase() === keyword.toUpperCase())
      const hit = exact ?? page.records[0]
      if (!hit) {
        toast.error('未找到批次')
        setCtx(null)
        setHistory([])
        setLoadingLot(false)
        return
      }
      await loadLotById(hit.id)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '查询失败')
      setLoadingLot(false)
    }
  }

  async function runTrackIn() {
    if (!ctx) return
    if (!selectedEqpId) {
      flashFeedback({ type: 'err', text: '请选择可开工设备' })
      return
    }
    if (ctx.carrierScanRequired && !scannedCarrierCode.trim()) {
      flashFeedback({ type: 'err', text: '请扫描载具编码后再开工' })
      return
    }
    const lotId = ctx.lotId
    setAction('in')
    try {
      const res = await trackInApi(lotId, selectedEqpId, scannedCarrierCode.trim() || undefined)
      flashFeedback({ type: 'ok', text: `开工成功 · ${res.lotNo}` })
      await loadLotById(lotId, { keepFeedback: true })
      await loadQueue()
    } catch (e) {
      const raw = e instanceof ApiError ? e.message : '开工失败'
      flashFeedback({
        type: 'err',
        text: formatCarrierGateError(raw),
      })
      await loadLotById(lotId, { keepFeedback: true })
      await loadQueue()
    } finally {
      setAction(null)
    }
  }

  async function runTrackOut() {
    if (!ctx) return
    const options = ctx.branchOptions ?? []
    if (options.length > 0 && !resultCode.trim()) {
      flashFeedback({ type: 'err', text: '请选择分支结果码，或选「默认」' })
      return
    }
    setAction('out')
    try {
      const code = resultCode.trim()
      const res = await trackOutApi(ctx.lotId, code && code !== '__DEFAULT__' ? code : undefined)
      flashFeedback({
        type: 'ok',
        text: res.completed ? `末站完工 · ${res.lotNo}` : `完工并进下一站 · ${res.lotNo}`,
      })
      const q = await loadQueue()
      if (res.completed) {
        const nextLot = q.find((l) => String(l.id) !== String(ctx.lotId))
        if (nextLot) {
          await loadLotById(nextLot.id, { keepFeedback: true })
        } else {
          await loadLotById(ctx.lotId, { keepFeedback: true })
        }
      } else {
        await loadLotById(ctx.lotId, { keepFeedback: true })
      }
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '完工失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runRework() {
    if (!ctx) return
    const toSortNo = Number(reworkToSort)
    if (!toSortNo) {
      flashFeedback({ type: 'err', text: '请选择回流目标站' })
      return
    }
    const opt = ctx.reworkOptions?.find((o) => o.toSortNo === toSortNo)
    if (opt && opt.reasonCodes.length > 0 && !reworkReason.trim()) {
      flashFeedback({ type: 'err', text: '请选择返工原因' })
      return
    }
    setAction('rework')
    try {
      const res = await trackReworkApi({
        lotId: ctx.lotId,
        toSortNo,
        reasonCode: reworkReason.trim() || undefined,
        remark: reworkRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `返工成功 · ${res.lotNo} · ${res.reworkCount}/${res.maxReworkCount}`,
      })
      setReworkPanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '返工失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runSplit() {
    if (!ctx) return
    if (lotQty == null || lotQty < 1) {
      flashFeedback({ type: 'err', text: '父批数量不足' })
      return
    }
    const children: { qty: number; lotNo?: string }[] = []
    for (const row of splitRows) {
      const q = Number(row.qty)
      if (!Number.isFinite(q) || q < 1) {
        flashFeedback({ type: 'err', text: '子批数量须为 ≥1 的整数' })
        return
      }
      children.push({
        qty: Math.floor(q),
        lotNo: row.lotNo.trim() || undefined,
      })
    }
    if (children.length === 0) {
      flashFeedback({ type: 'err', text: '请至少拆出一个子批' })
      return
    }
    const sum = children.reduce((a, c) => a + c.qty, 0)
    if (sum > lotQty) {
      flashFeedback({ type: 'err', text: `子批合计 ${sum} 超过父批 ${lotQty}` })
      return
    }
    setAction('split')
    try {
      const res = await trackSplitApi({
        parentLotId: ctx.lotId,
        children,
        reasonCode: splitReason.trim() || undefined,
        remark: splitRemark.trim() || undefined,
      })
      const childNos = res.children.map((c) => c.lotNo).join('、')
      flashFeedback({
        type: 'ok',
        text: `分批成功 · 父剩 ${res.parent.qty} · 子批 ${childNos}`,
      })
      setSplitPanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '分批失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runMerge() {
    if (!ctx) return
    if (mergeSelected.length === 0) {
      flashFeedback({ type: 'err', text: '请至少勾选一个源批' })
      return
    }
    const picked = mergeCandidates.filter((c) => mergeSelected.includes(String(c.lotId)))
    if (picked.length === 0) {
      flashFeedback({ type: 'err', text: '所选源批已失效，请重新打开合批' })
      return
    }
    const addQty = picked.reduce((a, c) => a + (c.qty ?? 0), 0)
    const afterQty = (lotQty ?? 0) + addQty
    const names = picked.map((c) => c.lotNo).join('、')
    const ok = window.confirm(
      `确认将 ${names}（共 ${addQty}）并入主批 ${ctx.lotNo}？\n合批后主批 qty=${afterQty}，源批不可再过站。`,
    )
    if (!ok) return
    setAction('merge')
    try {
      const res = await trackMergeApi({
        mainLotId: ctx.lotId,
        sourceLotIds: picked.map((c) => c.lotId),
        reasonCode: mergeReason.trim() || undefined,
        remark: mergeRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `合批成功 · 主批 ${res.main.lotNo} qty=${res.main.qty} · 并入 ${res.merged.length} 批`,
      })
      setMergePanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '合批失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function openScrapPanel() {
    if (!ctx) return
    setScrapQtyInput(lotQty != null && lotQty >= 1 ? '1' : '')
    setScrapRemark('')
    setReworkPanel(false)
    setSkipPanel(false)
    setOffFlowPanel(false)
    setSplitPanel(false)
    setHoldPanel(false)
    setFuturePanel(false)
    setMergePanel(false)
    setBonusPanel(false)
    setAbortPanel(false); setMovePanel(false)
    setScrapPanel(true)
    try {
      const rows = await getScrapReasonCodesApi()
      setScrapReasons(rows)
      setScrapReason((prev) => prev || rows[0]?.code || '')
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '加载报废原因失败',
      })
      setScrapPanel(false)
    }
  }

  async function openBonusPanel() {
    if (!ctx) return
    setBonusDeltaInput('1')
    setBonusRemark('')
    setReworkPanel(false)
    setSkipPanel(false)
    setOffFlowPanel(false)
    setSplitPanel(false)
    setHoldPanel(false)
    setFuturePanel(false)
    setMergePanel(false)
    setScrapPanel(false)
    setAbortPanel(false); setMovePanel(false)
    setBonusPanel(true)
    try {
      const rows = await getBonusReasonCodesApi()
      setBonusReasons(rows)
      setBonusReason((prev) => prev || rows[0]?.code || '')
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '加载调整原因失败',
      })
      setBonusPanel(false)
    }
  }

  async function openAbortPanel() {
    if (!ctx) return
    setAbortRemark('')
    setReworkPanel(false)
    setSkipPanel(false)
    setOffFlowPanel(false)
    setSplitPanel(false)
    setHoldPanel(false)
    setFuturePanel(false)
    setMergePanel(false)
    setScrapPanel(false)
    setBonusPanel(false)
    setMovePanel(false)
    setAbortPanel(true)
    try {
      const rows = await getAbortReasonCodesApi()
      setAbortReasons(rows)
      setAbortReason(rows[0]?.code || '')
      setAbortRemark('')
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '加载中止原因失败',
      })
      setAbortPanel(false); setMovePanel(false)
    }
  }

  async function runAbort() {
    if (!ctx) return
    if (!abortReason.trim()) {
      flashFeedback({ type: 'err', text: '请选择中止原因' })
      return
    }
    if (abortReason === 'OTHER' && !abortRemark.trim()) {
      flashFeedback({ type: 'err', text: '原因码为其他时须填写备注' })
      return
    }
    const reasonLabel =
      abortReasons.find((r) => r.code === abortReason)?.label ?? abortReason
    const ok = window.confirm(
      `确认中止 ${ctx.lotNo}？\n原因：${reasonLabel}\n将回到本站 wait，机台释放，可再次开工。\n站别与数量不动。`,
    )
    if (!ok) return
    setAction('abort')
    try {
      const res = await trackAbortApi({
        lotId: ctx.lotId,
        reasonCode: abortReason,
        remark: abortRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `中止成功 · ${res.lotNo} · 回本站 wait`,
      })
      setAbortPanel(false); setMovePanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '中止失败',
      })
    } finally {
      setAction(null)
    }
  }

  function openMovePanel() {
    if (!ctx) return
    setMoveRemark('')
    setReworkPanel(false)
    setSkipPanel(false)
    setOffFlowPanel(false)
    setSplitPanel(false)
    setHoldPanel(false)
    setFuturePanel(false)
    setMergePanel(false)
    setScrapPanel(false)
    setBonusPanel(false)
    setAbortPanel(false)
    setMovePanel(true)
  }

  async function runMove() {
    if (!ctx) return
    const toSort =
      ctx.nextSortNo ??
      ctx.nextStep?.sortNo ??
      null
    if (toSort == null) {
      flashFeedback({ type: 'err', text: '无下一站，无法移站' })
      return
    }
    const toName =
      ctx.nextStepName ||
      ctx.nextStep?.stepName ||
      `S${toSort}`
    const fromSort = ctx.currentSortNo
    const ok = window.confirm(
      `确认移站 ${ctx.lotNo}？\nS${fromSort ?? '—'} → S${toSort}（${toName}）\n不经加工，仍为 wait。`,
    )
    if (!ok) return
    setAction('move')
    try {
      const res = await trackMoveApi({
        lotId: ctx.lotId,
        toSortNo: toSort,
        remark: moveRemark.trim() || undefined,
      })
      setMovePanel(false)
      // 先刷新上下文/履历/队列，再提示；避免 load 里把成功条清掉
      await loadLotById(ctx.lotId, { keepFeedback: true })
      await loadQueue()
      flashFeedback({
        type: 'ok',
        text: `移站成功 · ${res.lotNo} · S${fromSort ?? '—'}→S${res.currentSortNo ?? toSort}`,
      })
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '移站失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runScrap() {
    if (!ctx) return
    if (lotQty == null || lotQty < 1) {
      flashFeedback({ type: 'err', text: '当前数量不足，无法报废' })
      return
    }
    const n = Number(scrapQtyInput)
    if (!Number.isFinite(n) || n < 1 || !Number.isInteger(n)) {
      flashFeedback({ type: 'err', text: '报废数量须为 ≥1 的整数' })
      return
    }
    if (n > lotQty) {
      flashFeedback({ type: 'err', text: `报废数量不能超过当前 qty ${lotQty}` })
      return
    }
    if (!scrapReason.trim()) {
      flashFeedback({ type: 'err', text: '请选择报废原因' })
      return
    }
    if (scrapReason === 'OTHER' && !scrapRemark.trim()) {
      flashFeedback({ type: 'err', text: '原因码为其他时备注必填' })
      return
    }
    const afterQty = lotQty - n
    const full = afterQty === 0
    const reasonLabel =
      scrapReasons.find((r) => r.code === scrapReason)?.label ?? scrapReason
    const ok = window.confirm(
      full
        ? `确认整批报废 ${ctx.lotNo}（${n}）？\n原因：${reasonLabel}\n状态将变为已报废，不可再过站，二期不可撤销。`
        : `确认报废 ${ctx.lotNo} 数量 ${n}？\n原因：${reasonLabel}\n报废后 qty=${afterQty}，累计 scrap=${(lotScrapQty ?? 0) + n}。\n二期不可撤销。`,
    )
    if (!ok) return
    setAction('scrap')
    try {
      const res = await trackScrapApi({
        lotId: ctx.lotId,
        scrapQty: n,
        reasonCode: scrapReason,
        remark: scrapRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text:
          res.mode === 'full'
            ? `整批报废成功 · ${res.lotNo} → scrapped`
            : `报废成功 · qty=${res.qty} · 累计 scrap=${res.scrapQty}`,
      })
      setScrapPanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '报废失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runBonus() {
    if (!ctx) return
    if (lotQty == null) {
      flashFeedback({ type: 'err', text: '当前数量未知，无法调整' })
      return
    }
    const delta = Number(bonusDeltaInput)
    if (!Number.isFinite(delta) || !Number.isInteger(delta) || delta === 0) {
      flashFeedback({ type: 'err', text: '调整量须为非 0 整数' })
      return
    }
    const afterQty = lotQty + delta
    if (afterQty < 0) {
      flashFeedback({ type: 'err', text: `调整后数量不能为负（当前 ${lotQty}）` })
      return
    }
    if (!bonusReason.trim()) {
      flashFeedback({ type: 'err', text: '请选择调整原因' })
      return
    }
    if (bonusReason === 'OTHER' && !bonusRemark.trim()) {
      flashFeedback({ type: 'err', text: '原因码为其他时备注必填' })
      return
    }
    const reasonLabel =
      bonusReasons.find((r) => r.code === bonusReason)?.label ?? bonusReason
    const ok = window.confirm(
      afterQty === 0
        ? `确认将 ${ctx.lotNo} 数量调整为 0？\nΔ=${delta} · 原因：${reasonLabel}\n不计入报废、状态不变。若为不良核销请改用报废。`
        : `确认调整 ${ctx.lotNo} 数量？\nΔ=${delta > 0 ? '+' : ''}${delta} · ${lotQty} → ${afterQty}\n原因：${reasonLabel}\n不计入报废，不可代替 Scrap。`,
    )
    if (!ok) return
    setAction('bonus')
    try {
      const res = await trackBonusApi({
        lotId: ctx.lotId,
        delta,
        reasonCode: bonusReason,
        remark: bonusRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `数量调整成功 · Δ=${res.delta > 0 ? '+' : ''}${res.delta} · qty=${res.qty} · scrap=${res.scrapQty}`,
      })
      setBonusPanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '数量调整失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runSkip() {
    if (!ctx) return
    const toSortNo = Number(skipToSort)
    if (!toSortNo) {
      flashFeedback({ type: 'err', text: '请选择跳站目标' })
      return
    }
    const opt = ctx.skipOptions?.find((o) => o.toSortNo === toSortNo)
    if (opt && opt.reasonCodes.length > 0 && !skipReason.trim()) {
      flashFeedback({ type: 'err', text: '请选择跳站原因' })
      return
    }
    const skipped = opt?.skippedSortNos?.length
      ? `跳过 ${opt.skippedSortNos.join(',')}`
      : '跳站'
    const ok = window.confirm(`确认${skipped}，进入站 ${toSortNo}？`)
    if (!ok) return
    setAction('skip')
    try {
      const res = await trackSkipApi({
        lotId: ctx.lotId,
        toSortNo,
        reasonCode: skipReason.trim() || undefined,
        remark: skipRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `跳站成功 · ${res.lotNo} · →${res.currentSortNo}`,
      })
      setSkipPanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '跳站失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runEnterOffFlow() {
    if (!ctx) return
    const toSortNo = Number(offFlowToSort)
    if (!toSortNo) {
      flashFeedback({ type: 'err', text: '请选择 Off-Flow 入口' })
      return
    }
    const opt = ctx.offFlowOptions?.find((o) => o.toSortNo === toSortNo)
    if (opt && opt.reasonCodes.length > 0 && !offFlowReason.trim()) {
      flashFeedback({ type: 'err', text: '请选择 Off-Flow 原因' })
      return
    }
    const ok = window.confirm(
      `确认进入 Off-Flow 站 ${toSortNo}？结束后将回锚点 ${ctx.currentSortNo}`,
    )
    if (!ok) return
    setAction('offflow')
    try {
      const res = await trackOffFlowApi({
        lotId: ctx.lotId,
        toSortNo,
        reasonCode: offFlowReason.trim() || undefined,
        remark: offFlowRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `已进 Off-Flow · ${res.lotNo} · →${res.currentSortNo}${
          res.offFlowCount != null && res.maxOffFlowCount != null
            ? ` · ${res.offFlowCount}/${res.maxOffFlowCount}`
            : ''
        }`,
      })
      setOffFlowPanel(false)
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '进入 Off-Flow 失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runResumeOffFlow() {
    if (!ctx) return
    const ok = window.confirm(`确认回主路径锚点 ${ctx.offFlowAnchorSortNo ?? '—'}？`)
    if (!ok) return
    setAction('resume')
    try {
      const res = await trackOffFlowResumeApi({
        lotId: ctx.lotId,
        remark: offFlowRemark.trim() || undefined,
      })
      flashFeedback({
        type: 'ok',
        text: `已回主路径 · ${res.lotNo} · →${res.currentSortNo}`,
      })
      await loadLotById(ctx.lotId)
      await loadQueue()
    } catch (e) {
      flashFeedback({
        type: 'err',
        text: e instanceof ApiError ? e.message : '回主路径失败',
      })
    } finally {
      setAction(null)
    }
  }

  async function runReserve() {
    if (!ctx || !selectedEqpId) return
    setReserveAction(true)
    try {
      const row = await createDispatchReserveApi({ lotId: ctx.lotId, eqpId: selectedEqpId })
      setActiveReserve(row)
      flashFeedback({ type: 'ok', text: `已预约 · ${row.eqpCode ?? selectedEqpId}` })
      await loadDispatch(ctx.lotId, selectedEqpId)
    } catch (e) {
      flashFeedback({ type: 'err', text: e instanceof ApiError ? e.message : '预约失败' })
    } finally {
      setReserveAction(false)
    }
  }

  async function runReleaseReserve() {
    if (!ctx || !activeReserve) return
    setReserveAction(true)
    try {
      await releaseDispatchReserveApi(activeReserve.id)
      setActiveReserve(null)
      flashFeedback({ type: 'ok', text: '已释约' })
      await loadDispatch(ctx.lotId, selectedEqpId)
    } catch (e) {
      flashFeedback({ type: 'err', text: e instanceof ApiError ? e.message : '释约失败' })
    } finally {
      setReserveAction(false)
    }
  }

  const currentLabel = ctx?.currentStep
    ? `${ctx.currentStep.sortNo} · ${ctx.currentStep.stepName || ctx.currentStep.stepCode}${
        ctx.currentStep.eqpType ? ` · ${ctx.currentStep.eqpType}` : ''
      }${ctx.offFlow ? ' · Off-Flow' : ''}`
    : ctx?.currentSortNo != null
      ? `站序 ${ctx.currentSortNo}${ctx.offFlow ? ' · Off-Flow' : ''}`
      : '—'

  const nextLabel = ctx?.offFlow && !ctx.nextStep
    ? `回锚点 ${ctx.offFlowAnchorSortNo ?? '—'}`
    : ctx?.nextStep
      ? `${ctx.nextStep.sortNo} · ${ctx.nextStep.stepName || ctx.nextStep.stepCode}`
      : ctx?.completed
        ? '已完工'
        : '末站'

  const selectedCandidate = candidateItems.find((c) => String(c.eqpId) === selectedEqpId)
  const recommendCode =
    candidateItems.find((c) => String(c.eqpId) === String(candidates?.recommendedEqpId))?.eqpCode ??
    null
  void remainTick

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      {feedback ? (
        <div
          ref={feedbackRef}
          role="status"
          className={cn(
            'flex items-center gap-2 rounded-md border px-4 py-3 text-base font-medium',
            feedback.type === 'ok'
              ? 'border-success/40 bg-success/15 text-success'
              : 'border-danger/40 bg-danger/15 text-danger',
          )}
        >
          {feedback.type === 'ok' ? (
            <CheckCircle2 className="size-5 shrink-0" aria-hidden />
          ) : (
            <XCircle className="size-5 shrink-0" aria-hidden />
          )}
          {feedback.text}
        </div>
      ) : null}

      <div className="grid min-h-0 flex-1 gap-4 xl:grid-cols-[280px_minmax(0,1fr)_300px]">
        <section className="flex min-h-0 flex-col rounded-lg border border-field-border bg-field-surface">
          <div className="border-b border-field-border px-4 py-3">
            <h2 className="text-sm font-semibold">载入批次</h2>
            <p className="mt-0.5 text-xs text-field-muted">扫码或输入 Lot，3 步内过账</p>
          </div>
          <div className="space-y-3 p-4">
            <Field
              label="批次号"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') void resolveAndLoad()
              }}
              fieldSize="field"
              autoComplete="off"
              placeholder="LOT-…"
              className="border-field-border bg-field-bg font-mono text-field-ink"
            />
            <Button
              size="field"
              className="w-full cursor-pointer"
              loading={loadingLot && !ctx}
              onClick={() => void resolveAndLoad()}
            >
              <Search className="size-4" aria-hidden />
              载入
            </Button>
          </div>
          <div className="flex items-center justify-between border-t border-field-border px-4 py-2">
            <span className="text-xs font-medium text-field-muted">待办队列</span>
            <button
              type="button"
              className="cursor-pointer text-xs text-accent hover:underline"
              onClick={() => void loadQueue()}
            >
              刷新
            </button>
          </div>
          <ul className="min-h-0 flex-1 space-y-1 overflow-auto px-2 pb-3">
            {queueLoading ? (
              <li className="flex items-center gap-2 px-2 py-3 text-sm text-field-muted">
                <Loader2 className="size-4 animate-spin" aria-hidden />
                加载中
              </li>
            ) : queue.length === 0 ? (
              <li className="px-2 py-3 text-sm text-field-muted">无 wait / processing 批次</li>
            ) : (
              queue.map((lot) => {
                const active = ctx && String(ctx.lotId) === String(lot.id)
                return (
                  <li key={String(lot.id)}>
                    <button
                      type="button"
                      onClick={() => void loadLotById(lot.id)}
                      className={cn(
                        'flex w-full cursor-pointer flex-col gap-1 rounded-md border px-3 py-2.5 text-left transition-colors duration-150',
                        active
                          ? 'border-accent/50 bg-accent/15'
                          : 'border-transparent hover:border-field-border hover:bg-field-bg',
                      )}
                    >
                      <span className="font-mono text-sm font-medium">{lot.lotNo}</span>
                      <span className="flex items-center justify-between gap-2">
                        <MesLotStatusPill status={lot.status} />
                        <span className="font-mono text-xs text-field-muted">
                          {lot.currentSortNo != null ? `S${lot.currentSortNo}` : '—'}
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
          <div
            ref={statusFlashRef}
            className="rounded-lg border border-field-border bg-field-surface p-5"
          >
            {!ctx ? (
              <div className="flex min-h-[200px] flex-col items-center justify-center gap-2 text-center text-field-muted">
                <CirclePlay className="size-8 opacity-50" aria-hidden />
                <p className="text-base">选择或载入批次后开始过账</p>
              </div>
            ) : (
              <div className="space-y-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="text-xs font-medium text-field-muted">当前批次</div>
                    <div className="mt-1 font-mono text-3xl font-semibold tracking-tight">
                      {ctx.lotNo}
                    </div>
                  </div>
                  <MesLotStatusPill status={ctx.status as MesLotStatus} />
                </div>

                <div
                  ref={stationHopRef}
                  className="grid gap-3 rounded-md sm:grid-cols-[1fr_auto_1fr] sm:items-center"
                >
                  <div className="rounded-md border border-field-border bg-field-bg px-4 py-3">
                    <div className="text-xs text-field-muted">当前站</div>
                    <div className="mt-1 text-lg font-semibold">{currentLabel}</div>
                  </div>
                  <ArrowRight className="mx-auto hidden size-5 text-field-muted sm:block" aria-hidden />
                  <div className="rounded-md border border-field-border bg-field-bg px-4 py-3">
                    <div className="text-xs text-field-muted">下一站</div>
                    <div className="mt-1 text-lg font-semibold">{nextLabel}</div>
                  </div>
                </div>

                <dl className="grid grid-cols-2 gap-3 sm:grid-cols-5">
                  <div className="min-w-0">
                    <dt className="text-xs text-field-muted">版本</dt>
                    <dd className="mt-0.5 truncate font-mono text-sm" title={String(ctx.routeVersionId ?? '')}>
                      {ctx.routeVersionNo != null ? `v${ctx.routeVersionNo}` : '—'}
                    </dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-xs text-field-muted">站序</dt>
                    <dd className="mt-0.5 font-mono text-sm">{ctx.currentSortNo ?? '—'}</dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-xs text-field-muted">设备</dt>
                    <dd className="mt-0.5 truncate font-mono text-sm" title={ctx.currentEqpId != null ? String(ctx.currentEqpId) : undefined}>
                      {candidateItems.find((o) => String(o.eqpId) === String(ctx.currentEqpId))?.eqpCode ??
                        (ctx.currentEqpId != null ? String(ctx.currentEqpId) : '—')}
                    </dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-xs text-field-muted">数量</dt>
                    <dd className="mt-0.5 font-mono text-sm">
                      {lotQty ?? '—'}
                      {lotScrapQty != null && lotScrapQty > 0 ? (
                        <span className="ml-1.5 text-field-muted">· scrap {lotScrapQty}</span>
                      ) : null}
                    </dd>
                  </div>
                  <div className="min-w-0">
                    <dt className="text-xs text-field-muted">载具</dt>
                    <dd
                      className="mt-0.5 truncate font-mono text-sm"
                      title={ctx.carrierCode ?? undefined}
                    >
                      {ctx.carrierCode || '—'}
                    </dd>
                  </div>
                </dl>
                {ctx.carrierRequired && !ctx.carrierCode ? (
                  <p className="rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-xs text-warning">
                    开工要求已绑定载具，请先在批次或载具台账完成绑定
                  </p>
                ) : null}
                {ctx.carrierScanRequired ? (
                  <p className="rounded-md border border-accent/30 bg-accent/10 px-3 py-2 text-xs text-field-ink">
                    开工须扫描实物载具编码
                    {ctx.carrierCode ? (
                      <>
                        （期望{' '}
                        <span className="font-mono text-accent">{ctx.carrierCode}</span>
                        ，请扫盒上标签，勿手抄期望码）
                      </>
                    ) : (
                      '；当前未绑定载具，请先绑定'
                    )}
                  </p>
                ) : null}

                {(ctx.pendingFutureHolds?.length ?? 0) > 0 ? (
                  <div className="rounded-md border border-accent/30 bg-accent/10 px-3 py-2">
                    <div className="mb-1.5 flex items-center gap-1.5 text-xs font-medium text-field-ink">
                      <CalendarClock className="size-3.5 text-accent" aria-hidden />
                      未生效预约 · {ctx.pendingFutureHolds!.length}
                    </div>
                    <ul className="space-y-1.5">
                      {ctx.pendingFutureHolds!.map((fh) => (
                        <li
                          key={String(fh.id)}
                          className="flex flex-wrap items-center justify-between gap-2 text-xs"
                        >
                          <span className="font-mono text-field-ink">
                            站 {fh.targetSortNo} · {fh.timing}
                            <span className="ml-1.5 text-field-muted">
                              {fh.reasonName || fh.reasonCode}
                            </span>
                          </span>
                          {canHold ? (
                            <button
                              type="button"
                              className="cursor-pointer text-danger hover:underline"
                              disabled={action != null}
                              onClick={() => void runCancelFutureHold(fh.id)}
                            >
                              取消
                            </button>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                {ctx.queueTime ? <QueueTimeBanner queueTime={ctx.queueTime} /> : null}
                {ctx.processTime ? <ProcessTimeBanner processTime={ctx.processTime} /> : null}
                {ctx.status === 'processing' && ctx.currentStepId ? (
                  <EdcCollectDock
                    key={`${ctx.lotId}-${ctx.currentStepId}`}
                    lotId={ctx.lotId}
                    lotNo={ctx.lotNo}
                    stepId={ctx.currentStepId}
                    stepLabel={currentLabel}
                    productCode={lotProductCode}
                    history={history}
                    gate={ctx.edc}
                    onSubmitted={() => void loadLotById(ctx.lotId, { keepFeedback: true })}
                  />
                ) : null}
              </div>
            )}
          </div>

          <div className="rounded-lg border border-field-border bg-field-surface p-5">
            <h2 className="mb-1 text-sm font-semibold">允许事务</h2>
            <p className="mb-4 text-xs text-field-muted">
              {ctx ? `状态 ${ctx.status} · 按路线快照推进` : '未载入批次'}
            </p>
            {ctx?.canTrackIn ? (
              <div className="mb-3 space-y-2">
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs text-field-muted">开工设备（派工候选）</span>
                  <select
                    className="h-11 cursor-pointer rounded-md border border-field-border bg-field-bg px-3 font-mono text-base text-field-ink"
                    value={selectedEqpId}
                    onChange={(e) => setSelectedEqpId(e.target.value)}
                    disabled={candidateItems.length === 0}
                    aria-label="开工设备"
                  >
                    {candidateItems.length === 0 ? (
                      <option value="">{candidates?.message || '无可用设备'}</option>
                    ) : (
                      candidateItems.map((o) => {
                        const rec = String(o.eqpId) === String(candidates?.recommendedEqpId)
                        return (
                          <option key={String(o.eqpId)} value={String(o.eqpId)}>
                            {rec ? '★ ' : ''}
                            {o.eqpCode} · {o.eqpName}
                            {o.reason ? ` · ${o.reason}` : ''}
                          </option>
                        )
                      })
                    )}
                  </select>
                </label>
                {ctx.carrierScanRequired ? (
                  <label className="flex flex-col gap-1.5 text-sm">
                    <span className="text-xs text-field-muted">
                      载具扫码
                      {ctx.carrierCode ? (
                        <span className="ml-1.5 font-mono text-field-ink">期望 {ctx.carrierCode}</span>
                      ) : null}
                    </span>
                    <input
                      type="text"
                      autoComplete="off"
                      className="h-11 rounded-md border border-field-border bg-field-bg px-3 font-mono text-base text-field-ink"
                      value={scannedCarrierCode}
                      onChange={(e) => setScannedCarrierCode(e.target.value)}
                      placeholder="扫描或输入载具编码"
                      aria-label="载具扫码"
                    />
                  </label>
                ) : null}
                {candidates?.held ? (
                  <p className="text-xs text-danger">{candidates.message || '批次已锁批，不可派工'}</p>
                ) : null}
                {!candidates?.held && candidateItems.length === 0 && candidates?.message ? (
                  <p className="text-xs text-danger">{candidates.message}</p>
                ) : null}
                {ctx.queueTime?.violated ? (
                  <p className="text-xs text-danger">
                    Queue Time 已超时，禁止开工（解锁须填备注）
                  </p>
                ) : null}
                {recommendCode ? (
                  <p ref={recommendRef} className="flex items-center gap-1.5 text-xs text-field-muted">
                    <Sparkles className="size-3.5 text-accent" aria-hidden />
                    推荐机台 <span className="font-mono text-field-ink">{recommendCode}</span>
                    {selectedCandidate?.reason ? ` · ${selectedCandidate.reason}` : null}
                  </p>
                ) : null}
                {activeReserve ? (
                  <div className="flex flex-wrap items-center gap-2 rounded-md border border-accent/30 bg-accent/10 px-3 py-2 text-xs">
                    <Bookmark className="size-3.5 text-accent" aria-hidden />
                    <span>
                      已预约 <span className="font-mono font-medium">{activeReserve.eqpCode}</span>
                      <span className="text-field-muted"> · 剩余 {fmtRemain(activeReserve.expireTime)}</span>
                    </span>
                  </div>
                ) : null}
                {canReserve ? (
                  <div className="flex gap-2">
                    <Button
                      size="md"
                      variant="secondary"
                      className="cursor-pointer border-field-border bg-field-bg text-field-ink"
                      disabled={!selectedEqpId || candidateItems.length === 0 || reserveAction}
                      loading={reserveAction && !activeReserve}
                      onClick={() => void runReserve()}
                    >
                      <Bookmark className="size-3.5" aria-hidden />
                      预约
                    </Button>
                    <Button
                      size="md"
                      variant="secondary"
                      className="cursor-pointer border-field-border bg-field-bg text-field-ink"
                      disabled={!activeReserve || reserveAction}
                      loading={reserveAction && !!activeReserve}
                      onClick={() => void runReleaseReserve()}
                    >
                      <BookmarkX className="size-3.5" aria-hidden />
                      释约
                    </Button>
                  </div>
                ) : null}
              </div>
            ) : null}
            {(ctx?.branchOptions?.length ?? 0) > 0 ? (
              <label className="mb-3 block space-y-1 text-xs text-field-muted">
                <span>分支结果码</span>
                <select
                  className="w-full rounded-md border border-field-border bg-field-panel px-2 py-2 text-sm text-field-ink"
                  value={resultCode}
                  onChange={(e) => setResultCode(e.target.value)}
                >
                  <option value="">请选择</option>
                  <option value="__DEFAULT__">默认下一站</option>
                  {(ctx?.branchOptions ?? []).map((o) => (
                    <option key={o.conditionCode} value={o.conditionCode}>
                      {o.conditionCode} → {o.toSortNo} · {o.toStepName || o.toStepCode || '—'}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Button
                size="field"
                className="w-full cursor-pointer"
                disabled={
                  !ctx?.canTrackIn ||
                  !selectedEqpId ||
                  candidateItems.length === 0 ||
                  (!!ctx?.carrierScanRequired && !scannedCarrierCode.trim())
                }
                loading={action === 'in'}
                onClick={() => void runTrackIn()}
              >
                <LogIn className="size-4" aria-hidden />
                开工
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={
                  !ctx?.canTrackOut ||
                  !processGate.canTrackOutByTime ||
                  ((ctx.branchOptions?.length ?? 0) > 0 && !resultCode.trim())
                }
                aria-describedby={
                  ctx?.edc?.required && !ctx.edc.clear ? 'edc-gate-hint' : undefined
                }
                loading={action === 'out'}
                onClick={() => void runTrackOut()}
              >
                <LogOut className="size-4" aria-hidden />
                完工
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-warning/50 bg-warning/10 text-warning hover:bg-warning/20"
                disabled={!ctx?.canAbort || action != null}
                loading={action === 'abort'}
                title={
                  ctx?.canAbort
                    ? '加工中止：回本站等待，机台释放'
                    : '仅加工中可中止'
                }
                onClick={() => {
                  if (abortPanel) {
                    setAbortPanel(false); setMovePanel(false)
                    return
                  }
                  void openAbortPanel()
                }}
              >
                <Ban className="size-4" aria-hidden />
                中止
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-accent/50 bg-accent/10 text-accent hover:bg-accent/20"
                disabled={!ctx?.canMove || action != null}
                loading={action === 'move'}
                title={
                  ctx?.canMove
                    ? `移至下一站 S${ctx.nextSortNo ?? ctx.nextStep?.sortNo ?? '—'}（不经加工）`
                    : '仅等待中且有下一站可移站'
                }
                onClick={() => {
                  if (movePanel) {
                    setMovePanel(false)
                    return
                  }
                  openMovePanel()
                }}
              >
                <ArrowRightLeft className="size-4" aria-hidden />
                移站
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={!ctx?.canRework || action != null}
                loading={action === 'rework'}
                onClick={() => {
                  setSkipPanel(false)
                  setOffFlowPanel(false)
                  setSplitPanel(false)
                  setMergePanel(false)
                  setScrapPanel(false)
                  setBonusPanel(false)
                  setAbortPanel(false); setMovePanel(false)
                  setReworkPanel((v) => !v)
                }}
              >
                <RotateCcw className="size-4" aria-hidden />
                返工
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={!ctx?.canSplit || action != null}
                loading={action === 'split'}
                title={ctx?.canSplit ? '按数量拆出子批' : '当前不可分批'}
                onClick={() => {
                  setReworkPanel(false)
                  setSkipPanel(false)
                  setOffFlowPanel(false)
                  setFuturePanel(false)
                  setHoldPanel(false)
                  setMergePanel(false)
                  setScrapPanel(false)
                  setBonusPanel(false)
                  setAbortPanel(false); setMovePanel(false)
                  setSplitPanel((v) => !v)
                }}
              >
                <Split className="size-4" aria-hidden />
                分批
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={!ctx?.canMerge || action != null}
                loading={action === 'merge' || mergeLoading}
                title={ctx?.canMerge ? '同站同快照合批' : '当前不可合批'}
                onClick={() => {
                  if (mergePanel) {
                    setMergePanel(false)
                    return
                  }
                  void openMergePanel()
                }}
              >
                <GitMerge className="size-4" aria-hidden />
                合批
              </Button>
              <Button
                size="field"
                variant="danger"
                className="w-full cursor-pointer"
                disabled={!ctx?.canScrap || action != null}
                loading={action === 'scrap'}
                title={ctx?.canScrap ? '部分或整批报废' : '当前不可报废'}
                onClick={() => {
                  if (scrapPanel) {
                    setScrapPanel(false)
                    return
                  }
                  void openScrapPanel()
                }}
              >
                <CircleAlert className="size-4" aria-hidden />
                报废
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={!ctx?.canBonus || action != null}
                loading={action === 'bonus'}
                title={ctx?.canBonus ? '盘点/计量数量调整（不计入报废）' : '当前不可数量调整'}
                onClick={() => {
                  if (bonusPanel) {
                    setBonusPanel(false)
                    return
                  }
                  void openBonusPanel()
                }}
              >
                <Diff className="size-4" aria-hidden />
                数量调整
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={!ctx?.canSkip || action != null}
                loading={action === 'skip'}
                onClick={() => {
                  setReworkPanel(false)
                  setOffFlowPanel(false)
                  setSplitPanel(false)
                  setMergePanel(false)
                  setScrapPanel(false)
                  setBonusPanel(false)
                  setAbortPanel(false); setMovePanel(false)
                  setSkipPanel((v) => !v)
                }}
              >
                <SkipForward className="size-4" aria-hidden />
                跳站
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={
                  action != null ||
                  (!ctx?.canEnterOffFlow && !ctx?.canResumeOffFlow)
                }
                loading={action === 'offflow' || action === 'resume'}
                onClick={() => {
                  if (ctx?.canResumeOffFlow) {
                    void runResumeOffFlow()
                    return
                  }
                  setReworkPanel(false)
                  setSkipPanel(false)
                  setSplitPanel(false)
                  setMergePanel(false)
                  setScrapPanel(false)
                  setBonusPanel(false)
                  setAbortPanel(false); setMovePanel(false)
                  setOffFlowPanel((v) => !v)
                }}
              >
                {ctx?.canResumeOffFlow ? (
                  <Undo2 className="size-4" aria-hidden />
                ) : (
                  <Route className="size-4" aria-hidden />
                )}
                {ctx?.canResumeOffFlow ? '回主路径' : 'Off-Flow'}
              </Button>
              <Button
                size="field"
                variant="danger"
                className="w-full cursor-pointer"
                disabled={
                  !ctx ||
                  action != null ||
                  (ctx.status === 'held'
                    ? !canReleaseHold || !activeHold
                    : !canHold || (ctx.status !== 'wait' && ctx.status !== 'processing'))
                }
                loading={action === 'hold' || action === 'release'}
                title={
                  ctx?.status === 'held'
                    ? canReleaseHold
                      ? '解锁'
                      : '无解锁权限'
                    : canHold
                      ? '发起锁批'
                      : '无锁批权限'
                }
                onClick={() => {
                  if (ctx?.status === 'held') void runReleaseHold()
                  else void openHoldPanel()
                }}
              >
                <PauseCircle className="size-4" aria-hidden />
                {ctx?.status === 'held' ? '解锁' : '锁批'}
              </Button>
              <Button
                size="field"
                variant="secondary"
                className="w-full cursor-pointer border-field-border bg-field-bg text-field-ink hover:bg-field-border/50"
                disabled={
                  !ctx ||
                  action != null ||
                  !canHold ||
                  !!ctx.offFlow ||
                  (ctx.status !== 'wait' &&
                    ctx.status !== 'processing' &&
                    ctx.status !== 'held')
                }
                loading={action === 'future'}
                title={canHold ? '预约到站再锁' : '无锁批权限'}
                onClick={() => openFuturePanel()}
              >
                <CalendarClock className="size-4" aria-hidden />
                预约
              </Button>
            </div>
            {!processGate.canTrackOutByTime ? (
              <p className="mt-2 text-xs text-danger" role="alert">
                未满最短加工时间，禁止完工
              </p>
            ) : processGate.exceededMax || ctx?.processTime?.willHoldOnOut ? (
              <p className="mt-2 text-xs text-warning" role="status">
                已超最大加工时间，可完工；出站后将自动锁批
              </p>
            ) : null}
            <EdcGateHint edc={ctx?.edc} />
            {splitPanel && ctx?.canSplit ? (
              <div
                ref={splitPanelRef}
                className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="text-xs font-medium text-field-ink">分批 · 父批保留余量</p>
                  <p className="font-mono text-xs text-field-muted">
                    当前 qty {lotQty ?? '—'} · 拆走{' '}
                    {splitRows.reduce((a, r) => a + (Number(r.qty) > 0 ? Math.floor(Number(r.qty)) : 0), 0)}{' '}
                    · 剩余{' '}
                    {lotQty == null
                      ? '—'
                      : Math.max(
                          0,
                          lotQty -
                            splitRows.reduce(
                              (a, r) => a + (Number(r.qty) > 0 ? Math.floor(Number(r.qty)) : 0),
                              0,
                            ),
                        )}
                  </p>
                </div>
                <div className="space-y-2">
                  {splitRows.map((row, idx) => (
                    <div key={idx} className="grid gap-2 sm:grid-cols-[1fr_1.4fr_auto]">
                      <Field
                        label={`子批数量 ${idx + 1}`}
                        name={`splitQty-${idx}`}
                        type="number"
                        min={1}
                        className="font-mono"
                        value={row.qty}
                        onChange={(e) =>
                          setSplitRows((rows) =>
                            rows.map((r, i) => (i === idx ? { ...r, qty: e.target.value } : r)),
                          )
                        }
                      />
                      <Field
                        label={`子批号 ${idx + 1}`}
                        name={`splitLotNo-${idx}`}
                        className="font-mono"
                        value={row.lotNo}
                        placeholder="空则自动 父号.01"
                        onChange={(e) =>
                          setSplitRows((rows) =>
                            rows.map((r, i) => (i === idx ? { ...r, lotNo: e.target.value } : r)),
                          )
                        }
                      />
                      <div className="flex items-end pb-0.5">
                        <Button
                          size="md"
                          variant="ghost"
                          className="text-field-muted"
                          disabled={splitRows.length <= 1}
                          onClick={() =>
                            setSplitRows((rows) => rows.filter((_, i) => i !== idx))
                          }
                        >
                          移除
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
                <Button
                  size="md"
                  variant="secondary"
                  className="border-field-border bg-field-panel text-field-ink"
                  disabled={splitRows.length >= 8}
                  onClick={() => setSplitRows((rows) => [...rows, { qty: '1', lotNo: '' }])}
                >
                  增加子批
                </Button>
                <Field
                  label="原因码"
                  name="splitReason"
                  className="font-mono"
                  value={splitReason}
                  onChange={(e) => setSplitReason(e.target.value)}
                  placeholder="可选，如 ENG_DOE"
                />
                <Field
                  label="备注"
                  name="splitRemark"
                  value={splitRemark}
                  onChange={(e) => setSplitRemark(e.target.value)}
                  placeholder="可选"
                />
                <p className="text-xs text-field-muted">
                  子批继承当前工艺快照与站点，拆完后各自独立过站。数量变更不可撤销，请确认后再提交。
                </p>
                <div className="flex gap-2">
                  <Button size="md" loading={action === 'split'} onClick={() => void runSplit()}>
                    确认分批
                  </Button>
                  <Button size="md" variant="secondary" onClick={() => setSplitPanel(false)}>
                    取消
                  </Button>
                </div>
              </div>
            ) : null}
            {mergePanel && ctx?.canMerge ? (
              <div
                ref={mergePanelRef}
                className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="text-xs font-medium text-field-ink">合批 · 源批并入主批</p>
                  <p className="font-mono text-xs text-field-muted">
                    主批 qty {lotQty ?? '—'} · 并入{' '}
                    {mergeCandidates
                      .filter((c) => mergeSelected.includes(String(c.lotId)))
                      .reduce((a, c) => a + (c.qty ?? 0), 0)}{' '}
                    · 合后{' '}
                    {(lotQty ?? 0) +
                      mergeCandidates
                        .filter((c) => mergeSelected.includes(String(c.lotId)))
                        .reduce((a, c) => a + (c.qty ?? 0), 0)}
                  </p>
                </div>
                {mergeLoading ? (
                  <p className="flex items-center gap-2 text-sm text-field-muted">
                    <Loader2 className="size-4 animate-spin" aria-hidden />
                    加载同站候选…
                  </p>
                ) : mergeCandidates.length === 0 ? (
                  <p className="text-sm text-field-muted">
                    暂无同产品、同快照、同站的可合批源批。
                  </p>
                ) : (
                  <ul className="max-h-56 space-y-1 overflow-y-auto rounded-md border border-field-border divide-y divide-field-border">
                    {mergeCandidates.map((c) => {
                      const id = String(c.lotId)
                      const checked = mergeSelected.includes(id)
                      return (
                        <li key={id}>
                          <label className="flex min-h-12 cursor-pointer items-center gap-3 px-3 py-2 hover:bg-field-border/30">
                            <input
                              type="checkbox"
                              className="size-4"
                              checked={checked}
                              onChange={() => {
                                setMergeSelected((prev) =>
                                  checked ? prev.filter((x) => x !== id) : [...prev, id],
                                )
                              }}
                            />
                            <span className="min-w-0 flex-1 font-mono text-sm text-field-ink">
                              {c.lotNo}
                            </span>
                            <span className="font-mono text-xs text-field-muted">qty {c.qty}</span>
                          </label>
                        </li>
                      )
                    })}
                  </ul>
                )}
                <Field
                  label="原因码"
                  name="mergeReason"
                  className="font-mono"
                  value={mergeReason}
                  onChange={(e) => setMergeReason(e.target.value)}
                  placeholder="可选，如 WIP_CONSOLIDATE"
                />
                <Field
                  label="备注"
                  name="mergeRemark"
                  value={mergeRemark}
                  onChange={(e) => setMergeRemark(e.target.value)}
                  placeholder="可选"
                />
                <p className="text-xs text-field-muted">
                  仅同产品 / 同工艺快照 / 同当前站可合。源批合后状态为 merged，不可再过站。
                </p>
                <div className="flex gap-2">
                  <Button
                    size="md"
                    loading={action === 'merge'}
                    disabled={mergeLoading || mergeSelected.length === 0}
                    onClick={() => void runMerge()}
                  >
                    确认合批
                  </Button>
                  <Button size="md" variant="secondary" onClick={() => setMergePanel(false)}>
                    取消
                  </Button>
                </div>
              </div>
            ) : null}
            {abortPanel && ctx?.canAbort ? (
              <AbortPanel
                lotNo={ctx.lotNo}
                currentSortNo={ctx.currentSortNo}
                currentEqpId={ctx.currentEqpId}
                processTime={ctx.processTime}
                reasons={abortReasons}
                reason={abortReason}
                remark={abortRemark}
                loading={action === 'abort'}
                onReasonChange={setAbortReason}
                onRemarkChange={setAbortRemark}
                onConfirm={() => void runAbort()}
                onCancel={() => {
                  setAbortPanel(false); setMovePanel(false)
                }}
              />
            ) : null}
            {movePanel && ctx?.canMove ? (
              <MovePanel
                lotNo={ctx.lotNo}
                currentSortNo={ctx.currentSortNo}
                currentStep={ctx.currentStep}
                nextSortNo={ctx.nextSortNo ?? ctx.nextStep?.sortNo ?? null}
                nextStepName={ctx.nextStepName ?? ctx.nextStep?.stepName ?? null}
                nextStep={ctx.nextStep}
                remark={moveRemark}
                loading={action === 'move'}
                onRemarkChange={setMoveRemark}
                onConfirm={() => void runMove()}
                onCancel={() => setMovePanel(false)}
              />
            ) : null}
            {scrapPanel && ctx?.canScrap ? (
              <div
                ref={scrapPanelRef}
                className="mt-4 space-y-3 rounded-md border border-danger/40 bg-field-bg p-4"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="text-xs font-medium text-field-ink">报废 · 不良核销</p>
                  <p className="font-mono text-xs text-field-muted">
                    当前 qty {lotQty ?? '—'} · scrap {lotScrapQty ?? 0} · 报废后 qty{' '}
                    {lotQty == null
                      ? '—'
                      : Math.max(
                          0,
                          lotQty -
                            (Number(scrapQtyInput) > 0
                              ? Math.floor(Number(scrapQtyInput))
                              : 0),
                        )}
                    {lotQty != null &&
                    Number(scrapQtyInput) > 0 &&
                    Math.floor(Number(scrapQtyInput)) >= lotQty
                      ? ' · 整批终态'
                      : ''}
                  </p>
                </div>
                <Field
                  label="报废数量"
                  name="scrapQty"
                  type="number"
                  min={1}
                  max={lotQty ?? undefined}
                  className="font-mono"
                  value={scrapQtyInput}
                  onChange={(e) => setScrapQtyInput(e.target.value)}
                />
                <div>
                  <label
                    htmlFor="scrapReason"
                    className="mb-1 block text-xs font-medium text-field-muted"
                  >
                    原因码
                  </label>
                  <select
                    id="scrapReason"
                    className="h-11 w-full rounded-md border border-field-border bg-field-panel px-3 font-mono text-sm text-field-ink"
                    value={scrapReason}
                    onChange={(e) => setScrapReason(e.target.value)}
                  >
                    {scrapReasons.length === 0 ? (
                      <option value="">加载中…</option>
                    ) : (
                      scrapReasons.map((r) => (
                        <option key={r.code} value={r.code}>
                          {r.code} · {r.label}
                        </option>
                      ))
                    )}
                  </select>
                </div>
                <Field
                  label={scrapReason === 'OTHER' ? '备注（必填）' : '备注'}
                  name="scrapRemark"
                  value={scrapRemark}
                  onChange={(e) => setScrapRemark(e.target.value)}
                  placeholder={scrapReason === 'OTHER' ? '请说明其他原因' : '可选'}
                />
                <p className="text-xs text-danger/90">
                  报废不可用 Bonus 代替。整批后不可再过站；二期不可撤销，请确认实物后再提交。
                </p>
                <div className="flex gap-2">
                  <Button
                    size="md"
                    variant="danger"
                    loading={action === 'scrap'}
                    disabled={!scrapReason}
                    onClick={() => void runScrap()}
                  >
                    确认报废
                  </Button>
                  <Button size="md" variant="secondary" onClick={() => setScrapPanel(false)}>
                    取消
                  </Button>
                </div>
              </div>
            ) : null}
            {bonusPanel && ctx?.canBonus ? (
              <div
                ref={bonusPanelRef}
                className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="text-xs font-medium text-field-ink">数量调整 · 账实纠偏</p>
                  <p className="font-mono text-xs text-field-muted">
                    当前 qty {lotQty ?? '—'} · scrap {lotScrapQty ?? 0} · 调整后 qty{' '}
                    {lotQty == null ||
                    !Number.isFinite(Number(bonusDeltaInput)) ||
                    !Number.isInteger(Number(bonusDeltaInput))
                      ? '—'
                      : lotQty + Number(bonusDeltaInput)}
                  </p>
                </div>
                <Field
                  label="调整量 Δ（可负）"
                  name="bonusDelta"
                  type="number"
                  step={1}
                  className="font-mono"
                  value={bonusDeltaInput}
                  onChange={(e) => setBonusDeltaInput(e.target.value)}
                />
                {lotQty != null &&
                Number.isInteger(Number(bonusDeltaInput)) &&
                lotQty + Number(bonusDeltaInput) === 0 ? (
                  <p className="text-xs font-medium text-danger">
                    调整后为 0：状态仍为 wait，不计入报废。不良核销请用「报废」。
                  </p>
                ) : null}
                <div>
                  <label
                    htmlFor="bonusReason"
                    className="mb-1 block text-xs font-medium text-field-muted"
                  >
                    原因码
                  </label>
                  <select
                    id="bonusReason"
                    className="h-11 w-full rounded-md border border-field-border bg-field-panel px-3 font-mono text-sm text-field-ink"
                    value={bonusReason}
                    onChange={(e) => setBonusReason(e.target.value)}
                  >
                    {bonusReasons.length === 0 ? (
                      <option value="">加载中…</option>
                    ) : (
                      bonusReasons.map((r) => (
                        <option key={r.code} value={r.code}>
                          {r.code} · {r.label}
                        </option>
                      ))
                    )}
                  </select>
                </div>
                <Field
                  label={bonusReason === 'OTHER' ? '备注（必填）' : '备注'}
                  name="bonusRemark"
                  value={bonusRemark}
                  onChange={(e) => setBonusRemark(e.target.value)}
                  placeholder={bonusReason === 'OTHER' ? '请说明其他原因' : '可选'}
                />
                <p className="text-xs text-field-muted">
                  不计入累计报废，不可代替 Scrap / Split / Merge。请确认实物后再提交。
                </p>
                <div className="flex gap-2">
                  <Button
                    size="md"
                    loading={action === 'bonus'}
                    disabled={!bonusReason}
                    onClick={() => void runBonus()}
                  >
                    确认调整
                  </Button>
                  <Button size="md" variant="secondary" onClick={() => setBonusPanel(false)}>
                    取消
                  </Button>
                </div>
              </div>
            ) : null}
            {reworkPanel && ctx?.canRework ? (
              <div className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4">
                <p className="text-xs font-medium text-field-ink">
                  返工回流 · 当前站已返工 {ctx.reworkCount ?? 0} 次
                </p>
                <label className="block space-y-1 text-xs text-field-muted">
                  <span>回流目标</span>
                  <select
                    className="w-full rounded-md border border-field-border bg-field-panel px-2 py-2 text-sm text-field-ink"
                    value={reworkToSort}
                    onChange={(e) => {
                      const v = e.target.value
                      setReworkToSort(v)
                      const opt = ctx.reworkOptions?.find((o) => String(o.toSortNo) === v)
                      setReworkReason(opt?.reasonCodes?.[0] ?? '')
                    }}
                  >
                    {(ctx.reworkOptions ?? []).map((o) => (
                      <option key={o.toSortNo} value={o.toSortNo}>
                        {o.toSortNo} · {o.toStepName || o.toStepCode || '—'} · 剩余{o.remainCount}/
                        {o.maxReworkCount}
                      </option>
                    ))}
                  </select>
                </label>
                {(() => {
                  const opt = ctx.reworkOptions?.find((o) => String(o.toSortNo) === reworkToSort)
                  if (!opt || opt.reasonCodes.length === 0) return null
                  return (
                    <label className="block space-y-1 text-xs text-field-muted">
                      <span>原因码</span>
                      <select
                        className="w-full rounded-md border border-field-border bg-field-panel px-2 py-2 text-sm text-field-ink"
                        value={reworkReason}
                        onChange={(e) => setReworkReason(e.target.value)}
                      >
                        {opt.reasonCodes.map((c) => (
                          <option key={c} value={c}>
                            {c}
                          </option>
                        ))}
                      </select>
                    </label>
                  )
                })()}
                <Field
                  label="备注"
                  name="reworkRemark"
                  value={reworkRemark}
                  onChange={(e) => setReworkRemark(e.target.value)}
                  placeholder="可选"
                />
                <div className="flex gap-2">
                  <Button
                    size="md"
                    loading={action === 'rework'}
                    onClick={() => void runRework()}
                  >
                    确认返工
                  </Button>
                  <Button size="md" variant="secondary" onClick={() => setReworkPanel(false)}>
                    取消
                  </Button>
                </div>
              </div>
            ) : null}
            {skipPanel && ctx?.canSkip ? (
              <div className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4">
                <p className="text-xs font-medium text-field-ink">
                  前向跳站 · 仅等待（未开工）可跳
                </p>
                <label className="block space-y-1 text-xs text-field-muted">
                  <span>目标站</span>
                  <select
                    className="w-full rounded-md border border-field-border bg-field-panel px-2 py-2 text-sm text-field-ink"
                    value={skipToSort}
                    onChange={(e) => {
                      const v = e.target.value
                      setSkipToSort(v)
                      const opt = ctx.skipOptions?.find((o) => String(o.toSortNo) === v)
                      setSkipReason(opt?.reasonCodes?.[0] ?? '')
                    }}
                  >
                    {(ctx.skipOptions ?? []).map((o) => (
                      <option key={o.toSortNo} value={o.toSortNo}>
                        →{o.toSortNo} · {o.toStepName || o.toStepCode || '—'}
                        {o.skippedSortNos?.length
                          ? ` · 跳过[${o.skippedSortNos.join(',')}]`
                          : ''}
                      </option>
                    ))}
                  </select>
                </label>
                {(() => {
                  const opt = ctx.skipOptions?.find((o) => String(o.toSortNo) === skipToSort)
                  if (!opt || opt.reasonCodes.length === 0) return null
                  return (
                    <label className="block space-y-1 text-xs text-field-muted">
                      <span>原因码</span>
                      <select
                        className="w-full rounded-md border border-field-border bg-field-panel px-2 py-2 text-sm text-field-ink"
                        value={skipReason}
                        onChange={(e) => setSkipReason(e.target.value)}
                      >
                        {opt.reasonCodes.map((c) => (
                          <option key={c} value={c}>
                            {c}
                          </option>
                        ))}
                      </select>
                    </label>
                  )
                })()}
                <Field
                  label="备注"
                  name="skipRemark"
                  value={skipRemark}
                  onChange={(e) => setSkipRemark(e.target.value)}
                  placeholder="可选"
                />
                <div className="flex gap-2">
                  <Button size="md" loading={action === 'skip'} onClick={() => void runSkip()}>
                    确认跳站
                  </Button>
                  <Button size="md" variant="secondary" onClick={() => setSkipPanel(false)}>
                    取消
                  </Button>
                </div>
              </div>
            ) : null}
            {offFlowPanel && ctx?.canEnterOffFlow ? (
              <div className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4">
                <p className="text-xs font-medium text-field-ink">
                  临时离线 · 结束后回锚点 {ctx.currentSortNo}
                </p>
                <label className="block space-y-1 text-xs text-field-muted">
                  <span>旁路入口</span>
                  <select
                    className="w-full rounded-md border border-field-border bg-field-panel px-2 py-2 text-sm text-field-ink"
                    value={offFlowToSort}
                    onChange={(e) => {
                      const v = e.target.value
                      setOffFlowToSort(v)
                      const opt = ctx.offFlowOptions?.find((o) => String(o.toSortNo) === v)
                      setOffFlowReason(opt?.reasonCodes?.[0] ?? '')
                    }}
                  >
                    {(ctx.offFlowOptions ?? []).map((o) => (
                      <option key={o.toSortNo} value={o.toSortNo}>
                        →{o.toSortNo} · {o.toStepName || o.toStepCode || '—'} · 剩余
                        {o.remainCount}/{o.maxOffFlowCount}
                      </option>
                    ))}
                  </select>
                </label>
                {(() => {
                  const opt = ctx.offFlowOptions?.find((o) => String(o.toSortNo) === offFlowToSort)
                  if (!opt || opt.reasonCodes.length === 0) return null
                  return (
                    <label className="block space-y-1 text-xs text-field-muted">
                      <span>原因码</span>
                      <select
                        className="w-full rounded-md border border-field-border bg-field-panel px-2 py-2 text-sm text-field-ink"
                        value={offFlowReason}
                        onChange={(e) => setOffFlowReason(e.target.value)}
                      >
                        {opt.reasonCodes.map((c) => (
                          <option key={c} value={c}>
                            {c}
                          </option>
                        ))}
                      </select>
                    </label>
                  )
                })()}
                <Field
                  label="备注"
                  name="offFlowRemark"
                  value={offFlowRemark}
                  onChange={(e) => setOffFlowRemark(e.target.value)}
                  placeholder="可选"
                />
                <div className="flex gap-2">
                  <Button
                    size="md"
                    loading={action === 'offflow'}
                    onClick={() => void runEnterOffFlow()}
                  >
                    确认进入
                  </Button>
                  <Button size="md" variant="secondary" onClick={() => setOffFlowPanel(false)}>
                    取消
                  </Button>
                </div>
              </div>
            ) : null}
            {holdPanel && ctx ? (
              <div
                ref={holdPanelRef}
                className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4"
              >
                <p className="text-sm font-medium">锁批 · {ctx.lotNo}</p>
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs text-field-muted">原因码</span>
                  <select
                    className="h-11 cursor-pointer rounded-md border border-field-border bg-field-surface px-3 text-base text-field-ink"
                    value={reasonCode}
                    onChange={(e) => setReasonCode(e.target.value)}
                  >
                    {reasons.map((r) => (
                      <option key={String(r.id)} value={r.reasonCode}>
                        {r.reasonCode} · {r.reasonName}
                      </option>
                    ))}
                  </select>
                </label>
                <Field
                  label={reasonCode === 'OTHER' ? '备注（必填）' : '备注'}
                  fieldSize="field"
                  value={holdRemark}
                  onChange={(e) => setHoldRemark(e.target.value)}
                  placeholder={reasonCode === 'OTHER' ? '说明其它原因' : '可选'}
                  className="border-field-border bg-field-surface text-field-ink"
                />
                <div className="flex gap-2">
                  <Button
                    size="field"
                    variant="secondary"
                    className="flex-1 border-field-border bg-field-surface text-field-ink"
                    disabled={action === 'hold'}
                    onClick={() => setHoldPanel(false)}
                  >
                    取消
                  </Button>
                  <Button
                    size="field"
                    variant="danger"
                    className="flex-1"
                    loading={action === 'hold'}
                    onClick={() => void runHold()}
                  >
                    确认锁批
                  </Button>
                </div>
              </div>
            ) : null}
            {releaseHoldPanel && ctx && activeHold ? (
              <div className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4">
                <p className="text-sm font-medium">解锁 · {ctx.lotNo}</p>
                <p className="text-xs text-field-muted">
                  {activeHold.reasonCode === 'EDC_OOS'
                    ? '量测超规锁批，解锁后须重采合格才能完工，须填写备注。'
                    : 'Queue Time 超时锁批，解锁即允许继续开工，须填写备注。'}
                </p>
                <Field
                  label="解锁备注（必填）"
                  fieldSize="field"
                  value={releaseHoldRemark}
                  onChange={(e) => setReleaseHoldRemark(e.target.value)}
                  placeholder="说明放行原因"
                  className="border-field-border bg-field-surface text-field-ink"
                />
                <div className="flex gap-2">
                  <Button
                    size="field"
                    variant="secondary"
                    className="flex-1 border-field-border bg-field-surface text-field-ink"
                    disabled={action === 'release'}
                    onClick={() => setReleaseHoldPanel(false)}
                  >
                    取消
                  </Button>
                  <Button
                    size="field"
                    variant="danger"
                    className="flex-1"
                    loading={action === 'release'}
                    onClick={() => void runReleaseHold()}
                  >
                    确认解锁
                  </Button>
                </div>
              </div>
            ) : null}
            {futurePanel && ctx ? (
              <div
                ref={futurePanelRef}
                className="mt-4 space-y-3 rounded-md border border-field-border bg-field-bg p-4"
              >
                <p className="text-sm font-medium">预约锁批 · {ctx.lotNo}</p>
                <p className="text-xs text-field-muted">
                  当前站 {ctx.currentSortNo ?? '—'} · 到目标站才激活，此前可继续过站
                </p>
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs text-field-muted">目标站序</span>
                  <input
                    className="h-11 rounded-md border border-field-border bg-field-surface px-3 font-mono text-base text-field-ink"
                    inputMode="numeric"
                    value={fhTargetSort}
                    onChange={(e) => setFhTargetSort(e.target.value)}
                    placeholder="如 50"
                  />
                </label>
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs text-field-muted">触发时机</span>
                  <select
                    className="h-11 cursor-pointer rounded-md border border-field-border bg-field-surface px-3 text-base text-field-ink"
                    value={fhTiming}
                    onChange={(e) => setFhTiming(e.target.value as FutureHoldTiming)}
                  >
                    <option value="PRE">PRE · 进站前</option>
                    <option value="POST">POST · 出站后</option>
                  </select>
                </label>
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs text-field-muted">原因码</span>
                  <select
                    className="h-11 cursor-pointer rounded-md border border-field-border bg-field-surface px-3 text-base text-field-ink"
                    value={fhReasonCode}
                    onChange={(e) => setFhReasonCode(e.target.value)}
                  >
                    {reasons.map((r) => (
                      <option key={String(r.id)} value={r.reasonCode}>
                        {r.reasonCode} · {r.reasonName}
                      </option>
                    ))}
                  </select>
                </label>
                <Field
                  label={fhReasonCode === 'OTHER' ? '备注（必填）' : '备注'}
                  fieldSize="field"
                  value={fhRemark}
                  onChange={(e) => setFhRemark(e.target.value)}
                  placeholder={fhReasonCode === 'OTHER' ? '说明其它原因' : '可选'}
                  className="border-field-border bg-field-surface text-field-ink"
                />
                <div className="flex gap-2">
                  <Button
                    size="field"
                    variant="secondary"
                    className="flex-1 border-field-border bg-field-surface text-field-ink"
                    disabled={action === 'future'}
                    onClick={() => setFuturePanel(false)}
                  >
                    取消
                  </Button>
                  <Button
                    size="field"
                    className="flex-1"
                    loading={action === 'future'}
                    onClick={() => void runFutureHold()}
                  >
                    确认预约
                  </Button>
                </div>
              </div>
            ) : null}
          </div>
        </section>

        <section className="flex min-h-0 flex-col rounded-lg border border-field-border bg-field-surface">
          <div className="flex items-center gap-2 border-b border-field-border px-4 py-3">
            <History className="size-4 text-field-muted" aria-hidden />
            <h2 className="text-sm font-semibold">事务履历</h2>
          </div>
          {!ctx ? (
            <p className="p-4 text-sm text-field-muted">载入批次后显示流水</p>
          ) : history.length === 0 ? (
            <p className="p-4 text-sm text-field-muted">暂无履历</p>
          ) : (
            <ul ref={historyRef} className="min-h-0 flex-1 space-y-0 overflow-auto p-2">
              {[...history].reverse().map((row) => (
                <li
                  key={String(row.id)}
                  className="border-b border-field-border/60 px-2 py-3 last:border-0"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="inline-flex items-center gap-1.5 text-sm font-medium">
                      {row.txType === 'TRACK_IN' ? (
                        <CirclePlay className="size-3.5 text-success" aria-hidden />
                      ) : row.txType === 'TRACK_OUT' ? (
                        <CircleStop className="size-3.5 text-accent" aria-hidden />
                      ) : row.txType === 'ABORT' ? (
                        <Ban className="size-3.5 text-warning" aria-hidden />
                      ) : row.txType === 'MOVE' ? (
                        <ArrowRightLeft className="size-3.5 text-accent" aria-hidden />
                      ) : row.txType === 'SCRAP' ? (
                        <CircleAlert className="size-3.5 text-danger" aria-hidden />
                      ) : row.txType === 'BONUS' ? (
                        <Diff className="size-3.5 text-accent" aria-hidden />
                      ) : (
                        <History className="size-3.5 text-field-muted" aria-hidden />
                      )}
                      {TX_LABEL[row.txType] ?? row.txType}
                    </span>
                    <span className="font-mono text-[11px] text-field-muted">
                      {row.createTime?.replace('T', ' ').slice(0, 19) ?? '—'}
                    </span>
                  </div>
                  <div className="mt-1 font-mono text-xs text-field-muted">
                    {row.fromStatus ?? '—'} → {row.toStatus ?? '—'}
                    {row.fromSortNo != null || row.toSortNo != null
                      ? ` · S${row.fromSortNo ?? '—'}→S${row.toSortNo ?? '—'}`
                      : ''}
                  </div>
                  <div className="mt-0.5 text-xs text-field-muted">
                    {row.stepName || (row.stepId != null ? `工序 ${row.stepId}` : '')}
                    {row.operUserName ? ` · ${row.operUserName}` : ''}
                    {row.remark ? ` · ${row.remark}` : ''}
                    {row.txType === 'REWORK' && row.extJson
                      ? (() => {
                          try {
                            const ext = JSON.parse(row.extJson) as {
                              reasonCode?: string
                              reworkCount?: number
                              maxReworkCount?: number
                            }
                            const parts: string[] = []
                            if (ext.reasonCode) parts.push(ext.reasonCode)
                            if (ext.reworkCount != null && ext.maxReworkCount != null) {
                              parts.push(`${ext.reworkCount}/${ext.maxReworkCount}`)
                            }
                            return parts.length ? ` · ${parts.join(' · ')}` : ''
                          } catch {
                            return ''
                          }
                        })()
                      : ''}
                    {(row.txType === 'BONUS' ||
                      row.txType === 'SCRAP' ||
                      row.txType === 'ABORT' ||
                      row.txType === 'MOVE') &&
                    row.extJson
                      ? (() => {
                          try {
                            const ext = JSON.parse(row.extJson) as {
                              reasonCode?: string
                              delta?: number
                              scrapQty?: number
                              qtyBefore?: number
                              qtyAfter?: number
                              processElapsedMin?: number
                              moveKind?: string
                              fromSortNo?: number
                              toSortNo?: number
                            }
                            const parts: string[] = []
                            if (ext.reasonCode) parts.push(ext.reasonCode)
                            if (row.txType === 'BONUS' && ext.delta != null) {
                              parts.push(`Δ=${ext.delta > 0 ? '+' : ''}${ext.delta}`)
                            }
                            if (row.txType === 'SCRAP' && ext.scrapQty != null) {
                              parts.push(`-${ext.scrapQty}`)
                            }
                            if (ext.qtyBefore != null && ext.qtyAfter != null) {
                              parts.push(`${ext.qtyBefore}→${ext.qtyAfter}`)
                            }
                            if (row.txType === 'ABORT' && ext.processElapsedMin != null) {
                              parts.push(`已加工 ${ext.processElapsedMin} 分`)
                            }
                            if (row.txType === 'MOVE') {
                              if (ext.moveKind) parts.push(ext.moveKind)
                              if (ext.fromSortNo != null && ext.toSortNo != null) {
                                parts.push(`S${ext.fromSortNo}→S${ext.toSortNo}`)
                              }
                            }
                            return parts.length ? ` · ${parts.join(' · ')}` : ''
                          } catch {
                            return ''
                          }
                        })()
                      : ''}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
