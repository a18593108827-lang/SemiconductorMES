import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import gsap from 'gsap'
import { ArrowDown, ArrowUp, GitBranch, Pencil, Plus, Trash2, Wrench } from 'lucide-react'
import {
  createRouteApi,
  createStepApi,
  getRouteVersionApi,
  listRouteVersionsApi,
  listRoutesApi,
  listStepsApi,
  publishRouteVersionApi,
  saveRouteDraftStepsApi,
  updateStepApi,
  upgradeRouteVersionApi,
  type MesRouteItem,
  type MesRouteStepItem,
  type MesRouteVersionDetail,
  type MesRouteVersionItem,
  type MesRouteEdgeItem,
  type MesStepItem,
} from '../api/route'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { EnablePill, RouteVersionPill } from '../components/ui/StatusPill'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

const STEP_TYPE_LABEL: Record<number, string> = {
  1: '加工',
  2: '量测',
  3: '其它',
}

type DraftRow = { key: string; stepId: string; pathKind: 'main' | 'off' }
type DraftEdge = {
  key: string
  edgeType: 'rework' | 'branch' | 'skip_allow' | 'off_flow' | 'time_link' | 'normal_qtime'
  fromSortNo: number
  toSortNo: number
  maxReworkCount: number
  reasonCodes: string
  conditionCode: string
  maxQueueMin: number | null
  onViolate: string
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function toDraftRows(steps: MesRouteStepItem[]): DraftRow[] {
  return steps.map((s, i) => ({
    key: `e-${s.id}-${i}`,
    stepId: String(s.stepId),
    pathKind: s.sortNo >= 200 ? 'off' : 'main',
  }))
}

function emptyQtimeFields() {
  return { maxQueueMin: null as number | null, onViolate: '' }
}

function toDraftEdges(edges: MesRouteEdgeItem[] | undefined): DraftEdge[] {
  return (edges ?? [])
    .filter(
      (e) =>
        e.edgeType === 'rework' ||
        e.edgeType === 'branch' ||
        e.edgeType === 'skip_allow' ||
        e.edgeType === 'off_flow' ||
        e.edgeType === 'time_link' ||
        (e.edgeType === 'normal' && e.maxQueueMin != null && e.maxQueueMin >= 1),
    )
    .map((e, i) => ({
      key: `edge-${e.id ?? i}`,
      edgeType:
        e.edgeType === 'normal'
          ? ('normal_qtime' as const)
          : (e.edgeType as DraftEdge['edgeType']),
      fromSortNo: e.fromSortNo,
      toSortNo: e.toSortNo,
      maxReworkCount: e.maxReworkCount ?? 1,
      reasonCodes: e.reasonCodes ?? '',
      conditionCode: e.conditionCode ?? '',
      maxQueueMin: e.maxQueueMin ?? null,
      onViolate: e.onViolate ?? '',
    }))
}

function toSaveSteps(rows: DraftRow[]) {
  const main = rows.filter((r) => r.pathKind === 'main')
  const off = rows.filter((r) => r.pathKind === 'off')
  const chain = (list: DraftRow[], base: number) =>
    list.map((r, i) => ({
      stepId: r.stepId,
      sortNo: base + (i + 1) * 10,
      nextSortNo: i < list.length - 1 ? base + (i + 2) * 10 : null,
    }))
  return [...chain(main, 0), ...chain(off, 200)]
}

function displaySortNo(rows: DraftRow[], index: number) {
  const row = rows[index]
  if (row.pathKind === 'off') {
    const offIdx = rows.slice(0, index + 1).filter((r) => r.pathKind === 'off').length
    return 200 + offIdx * 10
  }
  const mainIdx = rows.slice(0, index + 1).filter((r) => r.pathKind === 'main').length
  return mainIdx * 10
}

function toSaveEdges(edges: DraftEdge[]) {
  return edges.map((e, i) => {
    const qtime =
      e.maxQueueMin != null && e.maxQueueMin >= 1
        ? {
            maxQueueMin: e.maxQueueMin,
            onViolate: e.onViolate.trim() || null,
          }
        : {}
    if (e.edgeType === 'branch') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'branch',
        conditionCode: e.conditionCode.trim().toUpperCase() || null,
        sortNo: i,
        ...qtime,
      }
    }
    if (e.edgeType === 'skip_allow') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'skip_allow',
        reasonCodes: e.reasonCodes.trim() || null,
        sortNo: i,
        ...qtime,
      }
    }
    if (e.edgeType === 'off_flow') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'off_flow',
        maxReworkCount: e.maxReworkCount,
        reasonCodes: e.reasonCodes.trim() || null,
        sortNo: i,
        ...qtime,
      }
    }
    if (e.edgeType === 'time_link') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'time_link',
        maxQueueMin: e.maxQueueMin ?? 1,
        onViolate: e.onViolate.trim() || null,
        sortNo: i,
      }
    }
    if (e.edgeType === 'normal_qtime') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'normal',
        maxQueueMin: e.maxQueueMin ?? 1,
        onViolate: e.onViolate.trim() || null,
        sortNo: i,
      }
    }
    return {
      fromSortNo: e.fromSortNo,
      toSortNo: e.toSortNo,
      edgeType: 'rework',
      maxReworkCount: e.maxReworkCount,
      reasonCodes: e.reasonCodes.trim() || null,
      sortNo: i,
      ...qtime,
    }
  })
}

export function RoutePage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)

  const [routes, setRoutes] = useState<MesRouteItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState({
    routeCode: '',
    routeName: '',
    productCode: '',
    remark: '',
  })
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [detailRoute, setDetailRoute] = useState<MesRouteItem | null>(null)
  const [versions, setVersions] = useState<MesRouteVersionItem[]>([])
  const [versionDetail, setVersionDetail] = useState<MesRouteVersionDetail | null>(null)
  const [selectedVersionId, setSelectedVersionId] = useState<string>('')
  const [detailLoading, setDetailLoading] = useState(false)
  const [draftRows, setDraftRows] = useState<DraftRow[]>([])
  const [draftEdges, setDraftEdges] = useState<DraftEdge[]>([])
  const [savingSteps, setSavingSteps] = useState(false)

  const [stepOptions, setStepOptions] = useState<MesStepItem[]>([])
  const [stepsOpen, setStepsOpen] = useState(false)
  const [stepRows, setStepRows] = useState<MesStepItem[]>([])
  const [stepsLoading, setStepsLoading] = useState(false)
  const [stepQ, setStepQ] = useState('')
  const [stepKeyword, setStepKeyword] = useState('')
  const [stepMode, setStepMode] = useState<'create' | 'edit'>('create')
  const [editingStep, setEditingStep] = useState<MesStepItem | null>(null)
  const [stepForm, setStepForm] = useState({
    stepCode: '',
    stepName: '',
    stepType: 1,
    eqpType: '',
    allowSkip: 0,
    minProcessMin: '' as string,
    maxProcessMin: '' as string,
    status: 1,
  })
  const [stepError, setStepError] = useState('')
  const [savingStep, setSavingStep] = useState(false)
  const [quickStepOpen, setQuickStepOpen] = useState(false)
  const [quickStepForm, setQuickStepForm] = useState({
    stepCode: '',
    stepName: '',
    stepType: 1,
  })
  const [quickStepError, setQuickStepError] = useState('')
  const [savingQuickStep, setSavingQuickStep] = useState(false)
  const [quickFillRowKey, setQuickFillRowKey] = useState<string | null>(null)

  const size = 20
  const canAdd = hasPermission('route:add')
  const canEdit = hasPermission('route:edit')
  const totalPages = Math.max(1, Math.ceil(total / size))
  const isDraft = versionDetail?.status === 'draft'
  const hasDraftVersion = versions.some((v) => v.status === 'draft')

  const stepNameMap = useMemo(
    () => Object.fromEntries(stepOptions.map((s) => [String(s.id), s])),
    [stepOptions],
  )

  const loadRoutes = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listRoutesApi({ keyword, page, size })
      setRoutes(data.records)
      setTotal(data.total)
    } catch (err) {
      setRoutes([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, page, toast])

  const loadStepOptions = useCallback(async () => {
    try {
      const data = await listStepsApi({ status: 1, page: 1, size: 200 })
      setStepOptions(data.records)
    } catch {
      setStepOptions([])
    }
  }, [])

  useEffect(() => {
    void loadRoutes()
  }, [loadRoutes])

  useEffect(() => {
    void loadStepOptions()
  }, [loadStepOptions])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.route-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [])

  async function openDetail(route: MesRouteItem) {
    setDetailRoute(route)
    setDetailLoading(true)
    setVersionDetail(null)
    setDraftRows([])
    setDraftEdges([])
    try {
      const vers = await listRouteVersionsApi(route.id)
      setVersions(vers)
      const prefer =
        vers.find((v) => v.status === 'active') ??
        vers.find((v) => v.status === 'draft') ??
        vers[0]
      if (prefer) {
        setSelectedVersionId(String(prefer.id))
        const detail = await getRouteVersionApi(prefer.id)
        setVersionDetail(detail)
        setDraftRows(toDraftRows(detail.steps))
        setDraftEdges(toDraftEdges(detail.edges))
      } else {
        setSelectedVersionId('')
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载版本失败')
      setDetailRoute(null)
    } finally {
      setDetailLoading(false)
    }
  }

  async function selectVersion(versionId: string) {
    setSelectedVersionId(versionId)
    setDetailLoading(true)
    try {
      const detail = await getRouteVersionApi(versionId)
      setVersionDetail(detail)
      setDraftRows(toDraftRows(detail.steps))
      setDraftEdges(toDraftEdges(detail.edges))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载版本失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    const routeCode = createForm.routeCode.trim()
    const routeName = createForm.routeName.trim()
    if (!routeCode || !routeName) {
      setCreateError('请填写路线编码和名称')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const { id } = await createRouteApi({
        routeCode,
        routeName,
        productCode: createForm.productCode.trim() || undefined,
        remark: createForm.remark.trim() || undefined,
      })
      setCreateOpen(false)
      setCreateForm({ routeCode: '', routeName: '', productCode: '', remark: '' })
      toast.success('路线已创建')
      setFlashId(String(id))
      if (page === 1) await loadRoutes()
      else setPage(1)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setSavingCreate(false)
    }
  }

  async function saveDraft() {
    if (!versionDetail || !isDraft) return
    if (draftRows.some((r) => !r.stepId)) {
      toast.error('请为每个步骤选择工序')
      return
    }
    setSavingSteps(true)
    try {
      await saveRouteDraftStepsApi(versionDetail.id, toSaveSteps(draftRows), toSaveEdges(draftEdges))
      toast.success('草稿已保存')
      const detail = await getRouteVersionApi(versionDetail.id)
      setVersionDetail(detail)
      setDraftRows(toDraftRows(detail.steps))
      setDraftEdges(toDraftEdges(detail.edges))
      setFlashId(String(detailRoute?.id ?? ''))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingSteps(false)
    }
  }

  async function publishVersion() {
    if (!versionDetail || !isDraft) return
    if (draftRows.length === 0) {
      toast.error('至少配置一个步骤才能发布')
      return
    }
    const ok = await confirm({
      title: '发布版本',
      message: `确认发布 v${versionDetail.versionNo}？原生效版将归档。`,
      confirmText: '发布',
    })
    if (!ok) return
    setSavingSteps(true)
    try {
      if (draftRows.some((r) => !r.stepId)) {
        toast.error('请为每个步骤选择工序')
        return
      }
      await saveRouteDraftStepsApi(versionDetail.id, toSaveSteps(draftRows), toSaveEdges(draftEdges))
      await publishRouteVersionApi(versionDetail.id)
      toast.success('版本已发布')
      if (detailRoute) await openDetail(detailRoute)
      await loadRoutes()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '发布失败')
    } finally {
      setSavingSteps(false)
    }
  }

  async function upgradeFromCurrent() {
    if (!detailRoute || !versionDetail) return
    if (hasDraftVersion) {
      toast.error('已有草稿版本，请先发布或继续编辑现有草稿')
      const draft = versions.find((v) => v.status === 'draft')
      if (draft) void selectVersion(String(draft.id))
      return
    }
    const ok = await confirm({
      title: '升版',
      message: `基于 v${versionDetail.versionNo} 复制为新草稿？同一路线同时仅允许一个草稿。`,
      confirmText: '升版',
    })
    if (!ok) return
    try {
      const { versionId } = await upgradeRouteVersionApi(detailRoute.id, versionDetail.id)
      toast.success('已生成新草稿')
      await openDetail(detailRoute)
      await selectVersion(String(versionId))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '升版失败')
    }
  }

  function addDraftRow() {
    setDraftRows((rows) => [...rows, { key: `n-${Date.now()}`, stepId: '', pathKind: 'main' }])
  }

  function addOffDraftRow() {
    setDraftRows((rows) => [...rows, { key: `o-${Date.now()}`, stepId: '', pathKind: 'off' }])
  }

  function moveDraftRow(index: number, dir: -1 | 1) {
    setDraftRows((rows) => {
      const next = [...rows]
      const j = index + dir
      if (j < 0 || j >= next.length) return rows
      if (next[index].pathKind !== next[j].pathKind) return rows
      ;[next[index], next[j]] = [next[j], next[index]]
      return next
    })
  }

  function resetStepForm() {
    setStepMode('create')
    setEditingStep(null)
    setStepForm({
      stepCode: '',
      stepName: '',
      stepType: 1,
      eqpType: '',
      allowSkip: 0,
      minProcessMin: '',
      maxProcessMin: '',
      status: 1,
    })
    setStepError('')
  }

  async function loadStepRows(kw = stepKeyword) {
    setStepsLoading(true)
    try {
      const data = await listStepsApi({
        keyword: kw.trim() || undefined,
        page: 1,
        size: 200,
      })
      setStepRows(data.records)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载工序失败')
      setStepRows([])
    } finally {
      setStepsLoading(false)
    }
  }

  async function openStepsLibrary() {
    setStepsOpen(true)
    resetStepForm()
    setStepQ('')
    setStepKeyword('')
    await loadStepRows('')
  }

  async function saveStep() {
    const stepName = stepForm.stepName.trim()
    if (!stepName) {
      setStepError('请填写工序名称')
      return
    }
    if (stepMode === 'create' && !stepForm.stepCode.trim()) {
      setStepError('请填写工序编码')
      return
    }
    setSavingStep(true)
    setStepError('')
    const minProcessMin = stepForm.minProcessMin.trim()
      ? Number(stepForm.minProcessMin)
      : null
    const maxProcessMin = stepForm.maxProcessMin.trim()
      ? Number(stepForm.maxProcessMin)
      : null
    if (minProcessMin != null && (!Number.isFinite(minProcessMin) || minProcessMin < 1)) {
      setStepError('最小加工分钟须≥1或留空')
      setSavingStep(false)
      return
    }
    if (maxProcessMin != null && (!Number.isFinite(maxProcessMin) || maxProcessMin < 1)) {
      setStepError('最大加工分钟须≥1或留空')
      setSavingStep(false)
      return
    }
    if (minProcessMin != null && maxProcessMin != null && minProcessMin > maxProcessMin) {
      setStepError('最小加工不能大于最大加工')
      setSavingStep(false)
      return
    }
    try {
      if (stepMode === 'create') {
        await createStepApi({
          stepCode: stepForm.stepCode.trim(),
          stepName,
          stepType: stepForm.stepType,
          eqpType: stepForm.eqpType.trim() || undefined,
          allowSkip: stepForm.allowSkip,
          minProcessMin,
          maxProcessMin,
        })
        toast.success('工序已新增')
      } else if (editingStep) {
        await updateStepApi(editingStep.id, {
          stepName,
          stepType: stepForm.stepType,
          status: stepForm.status,
          eqpType: stepForm.eqpType.trim() || undefined,
          allowSkip: stepForm.allowSkip,
          minProcessMin,
          maxProcessMin,
        })
        toast.success('工序已保存')
      }
      resetStepForm()
      await loadStepRows()
      await loadStepOptions()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingStep(false)
    }
  }

  function openQuickStep(rowKey?: string) {
    setQuickFillRowKey(rowKey ?? null)
    setQuickStepOpen(true)
    setQuickStepError('')
    setQuickStepForm({ stepCode: '', stepName: '', stepType: 1 })
  }

  async function saveQuickStep() {
    const stepCode = quickStepForm.stepCode.trim()
    const stepName = quickStepForm.stepName.trim()
    if (!stepCode || !stepName) {
      setQuickStepError('请填写工序编码和名称')
      return
    }
    setSavingQuickStep(true)
    setQuickStepError('')
    try {
      await createStepApi({
        stepCode,
        stepName,
        stepType: quickStepForm.stepType,
      })
      const data = await listStepsApi({ status: 1, page: 1, size: 200 })
      setStepOptions(data.records)
      const created = data.records.find((s) => s.stepCode === stepCode)
      if (created) {
        const id = String(created.id)
        setDraftRows((rows) => {
          if (quickFillRowKey) {
            return rows.map((r) => (r.key === quickFillRowKey ? { ...r, stepId: id } : r))
          }
          const empty = rows.find((r) => !r.stepId)
          if (empty) {
            return rows.map((r) => (r.key === empty.key ? { ...r, stepId: id } : r))
          }
          return [...rows, { key: `n-${Date.now()}`, stepId: id, pathKind: 'main' }]
        })
      }
      setQuickStepOpen(false)
      toast.success('工序已新增并选用')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '新增失败')
    } finally {
      setSavingQuickStep(false)
    }
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="route-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">工艺路线</h1>
          <p className="mt-1 text-sm text-muted">维护工序与有序路线版本；生效版只读，变更请升版</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canEdit || canAdd ? (
            <Button variant="secondary" onClick={() => void openStepsLibrary()}>
              <Wrench className="size-4" aria-hidden />
              工序库
            </Button>
          ) : null}
          {canAdd ? (
            <Button
              onClick={() => {
                setCreateOpen(true)
                setCreateError('')
                setCreateForm({ routeCode: '', routeName: '', productCode: '', remark: '' })
              }}
            >
              <Plus className="size-4" aria-hidden />
              新建路线
            </Button>
          ) : null}
        </div>
      </header>

      <div className="route-block flex flex-wrap items-center gap-2">
        <input
          className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
          placeholder="编码 / 名称 / 产品"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setPage(1)
              setKeyword(q.trim())
            }
          }}
          aria-label="搜索路线"
        />
        <Button
          variant="secondary"
          onClick={() => {
            setPage(1)
            setKeyword(q.trim())
          }}
        >
          搜索
        </Button>
      </div>

      <div className="route-block overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">路线编码</th>
              <th className="px-3">名称</th>
              <th className="px-3">产品</th>
              <th className="px-3">生效版本</th>
              <th className="px-3">状态</th>
              <th className="px-3">更新</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="h-16 px-3 text-center text-muted">
                  加载中…
                </td>
              </tr>
            ) : loadFailed ? (
              <tr>
                <td colSpan={7} className="h-20 px-3 text-center text-muted">
                  加载失败
                  <button
                    type="button"
                    className="ml-2 cursor-pointer text-accent hover:underline"
                    onClick={() => void loadRoutes()}
                  >
                    重试
                  </button>
                </td>
              </tr>
            ) : routes.length === 0 ? (
              <tr>
                <td colSpan={7} className="h-24 px-3 text-center text-muted">
                  暂无路线。
                  {canAdd ? '点击「新建路线」开始配置。' : null}
                </td>
              </tr>
            ) : (
              routes.map((r) => (
                <FlashRow key={String(r.id)} active={flashId === String(r.id)}>
                  <td className="h-10 border-b border-border px-3 font-mono text-[13px] font-medium">
                    {r.routeCode}
                  </td>
                  <td className="border-b border-border px-3">{r.routeName}</td>
                  <td className="border-b border-border px-3 font-mono text-[13px] text-muted">
                    {r.productCode || '—'}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">
                    {r.activeVersionNo != null ? `v${r.activeVersionNo}` : '—'}
                  </td>
                  <td className="border-b border-border px-3">
                    <EnablePill enabled={r.status === 1} />
                  </td>
                  <td className="border-b border-border px-3 font-mono text-xs text-muted">
                    {fmtTime(r.updateTime)}
                  </td>
                  <td className="border-b border-border px-3">
                    <TableAction
                      icon={GitBranch}
                      label="维护"
                      tone="accent"
                      onClick={() => void openDetail(r)}
                    />
                  </td>
                </FlashRow>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-end gap-2 text-sm text-muted">
        <span>
          共 {total} 条 · {page}/{totalPages}
        </span>
        <Button
          variant="secondary"
          disabled={page <= 1 || loading}
          onClick={() => setPage((p) => Math.max(1, p - 1))}
        >
          上一页
        </Button>
        <Button
          variant="secondary"
          disabled={page >= totalPages || loading}
          onClick={() => setPage((p) => p + 1)}
        >
          下一页
        </Button>
      </div>

      <Drawer
        open={createOpen}
        title="新建路线"
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)} disabled={savingCreate}>
              取消
            </Button>
            <Button onClick={() => void submitCreate()} loading={savingCreate}>
              创建
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {createError ? <p className="text-xs text-danger">{createError}</p> : null}
          <Field
            label="路线编码"
            name="routeCode"
            value={createForm.routeCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, routeCode: e.target.value }))}
          />
          <Field
            label="路线名称"
            name="routeName"
            value={createForm.routeName}
            onChange={(e) => setCreateForm((f) => ({ ...f, routeName: e.target.value }))}
          />
          <Field
            label="产品编码"
            name="productCode"
            value={createForm.productCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, productCode: e.target.value }))}
            placeholder="可选"
          />
          <Field
            label="备注"
            name="remark"
            value={createForm.remark}
            onChange={(e) => setCreateForm((f) => ({ ...f, remark: e.target.value }))}
            placeholder="可选"
          />
          <p className="text-xs text-muted">创建后自动生成草稿 v1，需配置步骤并发布后才会成为生效版。</p>
        </div>
      </Drawer>

      <Drawer
        open={!!detailRoute}
        title={detailRoute ? `维护 · ${detailRoute.routeCode}` : '维护路线'}
        onClose={() => setDetailRoute(null)}
        width={560}
        footer={
          versionDetail ? (
            <>
              <Button variant="secondary" onClick={() => setDetailRoute(null)}>
                关闭
              </Button>
              {canAdd ? (
                <Button
                  variant="secondary"
                  onClick={() => void upgradeFromCurrent()}
                  disabled={hasDraftVersion}
                  title={hasDraftVersion ? '已有草稿，请先发布或编辑现有草稿' : undefined}
                >
                  升版
                </Button>
              ) : null}
              {isDraft && canEdit ? (
                <>
                  <Button variant="secondary" loading={savingSteps} onClick={() => void saveDraft()}>
                    保存草稿
                  </Button>
                  <Button loading={savingSteps} onClick={() => void publishVersion()}>
                    发布
                  </Button>
                </>
              ) : null}
            </>
          ) : null
        }
      >
        {detailLoading && !versionDetail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : versionDetail ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-medium">{versionDetail.routeName}</span>
              <RouteVersionPill status={versionDetail.status} />
              <span className="font-mono text-xs text-muted">v{versionDetail.versionNo}</span>
            </div>

            <div className="flex flex-wrap gap-1.5">
              {versions.map((v) => (
                <button
                  key={String(v.id)}
                  type="button"
                  onClick={() => void selectVersion(String(v.id))}
                  className={cn(
                    'cursor-pointer rounded-md border px-2.5 py-1 text-xs font-medium transition-colors duration-150',
                    selectedVersionId === String(v.id)
                      ? 'border-primary bg-primary text-white'
                      : 'border-border bg-bg text-muted hover:bg-surface',
                  )}
                >
                  v{v.versionNo}
                  <span className="ml-1 opacity-80">
                    {v.status === 'draft' ? '草稿' : v.status === 'active' ? '生效' : '归档'}
                  </span>
                </button>
              ))}
            </div>

            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full min-w-[460px] text-left text-sm">
                <thead className="bg-surface text-xs font-medium text-muted">
                  <tr className="h-9 border-b border-border">
                    <th className="px-2">顺序</th>
                    <th className="px-2">路径</th>
                    <th className="px-2">工序</th>
                    <th className="px-2">设备类型</th>
                    <th className="px-2">下一站</th>
                    {isDraft && canEdit ? <th className="px-2">操作</th> : null}
                  </tr>
                </thead>
                <tbody>
                  {(isDraft ? draftRows : versionDetail.steps).length === 0 ? (
                    <tr>
                        <td
                          colSpan={isDraft && canEdit ? 6 : 5}
                          className="h-14 px-2 text-center text-muted"
                        >
                        暂无步骤
                        {isDraft && canEdit ? '，点击下方添加。' : '。'}
                      </td>
                    </tr>
                  ) : isDraft ? (
                    draftRows.map((row, index) => {
                      const sortNo = displaySortNo(draftRows, index)
                      const sameKind = draftRows.filter((r) => r.pathKind === row.pathKind)
                      const kindIdx = sameKind.findIndex((r) => r.key === row.key)
                      const nextRow = kindIdx >= 0 && kindIdx < sameKind.length - 1 ? sameKind[kindIdx + 1] : null
                      const nextLabel = nextRow
                        ? stepNameMap[nextRow.stepId]?.stepCode ??
                          `站${displaySortNo(
                            draftRows,
                            draftRows.findIndex((r) => r.key === nextRow.key),
                          )}`
                        : '结束'
                      return (
                        <tr key={row.key} className="h-10 border-b border-border">
                          <td className="px-2 font-mono text-[13px]">{sortNo}</td>
                          <td className="px-2 text-[12px] text-muted">
                            {row.pathKind === 'off' ? '旁路' : '主'}
                          </td>
                          <td className="px-2">
                            <div className="flex items-center gap-1.5">
                              <select
                                className="h-8 w-full max-w-[200px] rounded-md border border-border bg-bg px-2 text-sm"
                                value={row.stepId}
                                disabled={!canEdit}
                                onChange={(e) =>
                                  setDraftRows((rows) =>
                                    rows.map((r) =>
                                      r.key === row.key ? { ...r, stepId: e.target.value } : r,
                                    ),
                                  )
                                }
                                aria-label={`步骤${index + 1}工序`}
                              >
                                <option value="">选择工序</option>
                                {stepOptions.map((s) => (
                                  <option key={String(s.id)} value={String(s.id)}>
                                    {s.stepCode} · {s.stepName}
                                  </option>
                                ))}
                              </select>
                              {canAdd && !row.stepId ? (
                                <button
                                  type="button"
                                  className="shrink-0 cursor-pointer text-[11px] text-accent hover:underline"
                                  onClick={() => openQuickStep(row.key)}
                                >
                                  新建
                                </button>
                              ) : null}
                            </div>
                          </td>
                          <td className="px-2 font-mono text-[12px] text-muted">
                            {stepNameMap[row.stepId]?.eqpType || '—'}
                          </td>
                          <td className="px-2 font-mono text-[12px] text-muted">{nextLabel}</td>
                          {canEdit ? (
                            <td className="px-2">
                              <div className="flex gap-1">
                                <button
                                  type="button"
                                  className="cursor-pointer rounded-sm p-1 text-muted hover:bg-surface hover:text-ink"
                                  aria-label="上移"
                                  onClick={() => moveDraftRow(index, -1)}
                                >
                                  <ArrowUp className="size-3.5" />
                                </button>
                                <button
                                  type="button"
                                  className="cursor-pointer rounded-sm p-1 text-muted hover:bg-surface hover:text-ink"
                                  aria-label="下移"
                                  onClick={() => moveDraftRow(index, 1)}
                                >
                                  <ArrowDown className="size-3.5" />
                                </button>
                                <button
                                  type="button"
                                  className="cursor-pointer rounded-sm p-1 text-muted hover:bg-danger/10 hover:text-danger"
                                  aria-label="删除"
                                  onClick={() =>
                                    setDraftRows((rows) => rows.filter((r) => r.key !== row.key))
                                  }
                                >
                                  <Trash2 className="size-3.5" />
                                </button>
                              </div>
                            </td>
                          ) : null}
                        </tr>
                      )
                    })
                  ) : (
                    versionDetail.steps.map((s) => (
                      <tr key={String(s.id)} className="h-10 border-b border-border">
                        <td className="px-2 font-mono text-[13px]">{s.sortNo}</td>
                        <td className="px-2 text-[12px] text-muted">
                          {s.sortNo >= 200 ? '旁路' : '主'}
                        </td>
                        <td className="px-2">
                          <span className="font-mono text-[13px]">{s.stepCode}</span>
                          <span className="ml-2 text-muted">{s.stepName}</span>
                        </td>
                        <td className="px-2 font-mono text-[12px] text-muted">
                          {s.eqpType || '—'}
                        </td>
                        <td className="px-2 font-mono text-[12px] text-muted">
                          {s.nextSortNo ?? '结束'}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {isDraft && canEdit ? (
              <div className="flex flex-wrap items-center gap-2">
                <Button variant="secondary" onClick={addDraftRow}>
                  <Plus className="size-4" aria-hidden />
                  添加步骤
                </Button>
                <Button variant="secondary" onClick={addOffDraftRow}>
                  <Plus className="size-4" aria-hidden />
                  旁路步骤
                </Button>
                {canAdd ? (
                  <button
                    type="button"
                    className="cursor-pointer text-xs text-accent hover:underline"
                    onClick={() => openQuickStep()}
                  >
                    缺少工序？快速新建
                  </button>
                ) : null}
              </div>
            ) : null}

            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-medium text-ink">边（回流 / 分支 / 跳站 / Off-Flow / QTime）</p>
                {isDraft && canEdit ? (
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="secondary"
                      onClick={() =>
                        setDraftEdges((rows) => [
                          ...rows,
                          {
                            key: `edge-${Date.now()}`,
                            edgeType: 'rework',
                            fromSortNo: draftRows.filter((r) => r.pathKind === 'main').length
                              ? draftRows.filter((r) => r.pathKind === 'main').length * 10
                              : 10,
                            toSortNo: 10,
                            maxReworkCount: 2,
                            reasonCodes: '',
                            conditionCode: '',
                            ...emptyQtimeFields(),
                          },
                        ])
                      }
                    >
                      <Plus className="size-4" aria-hidden />
                      回流
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() =>
                        setDraftEdges((rows) => [
                          ...rows,
                          {
                            key: `edge-b-${Date.now()}`,
                            edgeType: 'branch',
                            fromSortNo: draftRows.filter((r) => r.pathKind === 'main').length
                              ? draftRows.filter((r) => r.pathKind === 'main').length * 10
                              : 10,
                            toSortNo: 10,
                            maxReworkCount: 1,
                            reasonCodes: '',
                            conditionCode: 'PASS',
                            ...emptyQtimeFields(),
                          },
                        ])
                      }
                    >
                      <Plus className="size-4" aria-hidden />
                      分支
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() =>
                        setDraftEdges((rows) => [
                          ...rows,
                          {
                            key: `edge-s-${Date.now()}`,
                            edgeType: 'skip_allow',
                            fromSortNo: 10,
                            toSortNo: draftRows.filter((r) => r.pathKind === 'main').length
                              ? draftRows.filter((r) => r.pathKind === 'main').length * 10
                              : 20,
                            maxReworkCount: 1,
                            reasonCodes: '',
                            conditionCode: '',
                            ...emptyQtimeFields(),
                          },
                        ])
                      }
                    >
                      <Plus className="size-4" aria-hidden />
                      跳站
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => {
                        const mainN = draftRows.filter((r) => r.pathKind === 'main').length
                        const offN = draftRows.filter((r) => r.pathKind === 'off').length
                        setDraftEdges((rows) => [
                          ...rows,
                          {
                            key: `edge-o-${Date.now()}`,
                            edgeType: 'off_flow',
                            fromSortNo: mainN ? mainN * 10 : 10,
                            toSortNo: offN ? 210 : 210,
                            maxReworkCount: 1,
                            reasonCodes: '',
                            conditionCode: '',
                            ...emptyQtimeFields(),
                          },
                        ])
                      }}
                    >
                      <Plus className="size-4" aria-hidden />
                      Off-Flow
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => {
                        const mainN = draftRows.filter((r) => r.pathKind === 'main').length
                        setDraftEdges((rows) => [
                          ...rows,
                          {
                            key: `edge-q-${Date.now()}`,
                            edgeType: 'time_link',
                            fromSortNo: 10,
                            toSortNo: mainN > 1 ? mainN * 10 : 20,
                            maxReworkCount: 1,
                            reasonCodes: '',
                            conditionCode: '',
                            maxQueueMin: 120,
                            onViolate: 'HOLD',
                          },
                        ])
                      }}
                    >
                      <Plus className="size-4" aria-hidden />
                      QTime
                    </Button>
                  </div>
                ) : null}
              </div>
              <div className="overflow-hidden rounded-md border border-border">
                <table className="w-full text-left text-sm">
                  <thead className="bg-surface text-[11px] uppercase tracking-wide text-muted">
                    <tr className="h-9">
                      <th className="px-2 font-medium">类型</th>
                      <th className="px-2 font-medium">触发站</th>
                      <th className="px-2 font-medium">目标站</th>
                      <th className="px-2 font-medium">条件/上限</th>
                      <th className="px-2 font-medium">QTime</th>
                      <th className="px-2 font-medium">原因码</th>
                      {isDraft && canEdit ? <th className="px-2 font-medium w-10" /> : null}
                    </tr>
                  </thead>
                  <tbody>
                    {(isDraft ? draftEdges : toDraftEdges(versionDetail.edges)).length === 0 ? (
                      <tr>
                        <td
                          colSpan={isDraft && canEdit ? 7 : 6}
                          className="px-2 py-4 text-center text-muted"
                        >
                          无特殊边（默认下一站由步骤顺序生成；QTime 可单独配置）
                        </td>
                      </tr>
                    ) : (
                      (isDraft ? draftEdges : toDraftEdges(versionDetail.edges)).map((edge) => (
                        <tr key={edge.key} className="h-10 border-b border-border">
                          {isDraft && canEdit ? (
                            <>
                              <td className="px-2 font-mono text-[12px]">
                                {edge.edgeType === 'normal_qtime' ? 'normal+qtime' : edge.edgeType}
                              </td>
                              <td className="px-2">
                                <input
                                  className="w-16 rounded border border-border bg-bg px-1 py-0.5 font-mono text-[12px]"
                                  type="number"
                                  value={edge.fromSortNo}
                                  onChange={(e) =>
                                    setDraftEdges((rows) =>
                                      rows.map((r) =>
                                        r.key === edge.key
                                          ? { ...r, fromSortNo: Number(e.target.value) || 0 }
                                          : r,
                                      ),
                                    )
                                  }
                                />
                              </td>
                              <td className="px-2">
                                <input
                                  className="w-16 rounded border border-border bg-bg px-1 py-0.5 font-mono text-[12px]"
                                  type="number"
                                  value={edge.toSortNo}
                                  onChange={(e) =>
                                    setDraftEdges((rows) =>
                                      rows.map((r) =>
                                        r.key === edge.key
                                          ? { ...r, toSortNo: Number(e.target.value) || 0 }
                                          : r,
                                      ),
                                    )
                                  }
                                />
                              </td>
                              <td className="px-2">
                                {edge.edgeType === 'branch' ? (
                                  <input
                                    className="w-20 rounded border border-border bg-bg px-1 py-0.5 font-mono text-[12px]"
                                    value={edge.conditionCode}
                                    placeholder="PASS"
                                    onChange={(e) =>
                                      setDraftEdges((rows) =>
                                        rows.map((r) =>
                                          r.key === edge.key
                                            ? { ...r, conditionCode: e.target.value }
                                            : r,
                                        ),
                                      )
                                    }
                                  />
                                ) : edge.edgeType === 'rework' || edge.edgeType === 'off_flow' ? (
                                  <input
                                    className="w-14 rounded border border-border bg-bg px-1 py-0.5 font-mono text-[12px]"
                                    type="number"
                                    min={1}
                                    value={edge.maxReworkCount}
                                    onChange={(e) =>
                                      setDraftEdges((rows) =>
                                        rows.map((r) =>
                                          r.key === edge.key
                                            ? {
                                                ...r,
                                                maxReworkCount: Math.max(
                                                  1,
                                                  Number(e.target.value) || 1,
                                                ),
                                              }
                                            : r,
                                        ),
                                      )
                                    }
                                  />
                                ) : edge.edgeType === 'time_link' ||
                                  edge.edgeType === 'normal_qtime' ? (
                                  <span className="text-[12px] text-muted">时间窗</span>
                                ) : (
                                  <span className="text-[12px] text-muted">前向</span>
                                )}
                              </td>
                              <td className="px-2">
                                <div className="flex items-center gap-1">
                                  <input
                                    className="w-14 rounded border border-border bg-bg px-1 py-0.5 font-mono text-[12px]"
                                    type="number"
                                    min={1}
                                    placeholder="分"
                                    value={edge.maxQueueMin ?? ''}
                                    onChange={(e) =>
                                      setDraftEdges((rows) =>
                                        rows.map((r) =>
                                          r.key === edge.key
                                            ? {
                                                ...r,
                                                maxQueueMin: e.target.value
                                                  ? Math.max(1, Number(e.target.value) || 1)
                                                  : null,
                                              }
                                            : r,
                                        ),
                                      )
                                    }
                                  />
                                  <select
                                    className="h-7 max-w-[5.5rem] cursor-pointer rounded border border-border bg-bg px-1 font-mono text-[11px]"
                                    value={edge.onViolate || ''}
                                    onChange={(e) =>
                                      setDraftEdges((rows) =>
                                        rows.map((r) =>
                                          r.key === edge.key
                                            ? { ...r, onViolate: e.target.value }
                                            : r,
                                        ),
                                      )
                                    }
                                    aria-label="超时策略"
                                  >
                                    <option value="">默认</option>
                                    <option value="HOLD">HOLD</option>
                                    <option value="ALARM">ALARM</option>
                                    <option value="HOLD_ALARM">HOLD+ALARM</option>
                                  </select>
                                </div>
                              </td>
                              <td className="px-2">
                                {edge.edgeType === 'rework' ||
                                edge.edgeType === 'skip_allow' ||
                                edge.edgeType === 'off_flow' ? (
                                  <input
                                    className="w-full rounded border border-border bg-bg px-1 py-0.5 font-mono text-[12px]"
                                    value={edge.reasonCodes}
                                    placeholder="EQP_DOWN,..."
                                    onChange={(e) =>
                                      setDraftEdges((rows) =>
                                        rows.map((r) =>
                                          r.key === edge.key
                                            ? { ...r, reasonCodes: e.target.value }
                                            : r,
                                        ),
                                      )
                                    }
                                  />
                                ) : (
                                  <span className="text-[12px] text-muted">—</span>
                                )}
                              </td>
                              <td className="px-2">
                                <button
                                  type="button"
                                  className="cursor-pointer rounded-sm p-1 text-muted hover:bg-danger/10 hover:text-danger"
                                  aria-label="删除边"
                                  onClick={() =>
                                    setDraftEdges((rows) => rows.filter((r) => r.key !== edge.key))
                                  }
                                >
                                  <Trash2 className="size-3.5" />
                                </button>
                              </td>
                            </>
                          ) : (
                            <>
                              <td className="px-2 font-mono text-[12px]">
                                {edge.edgeType === 'normal_qtime' ? 'normal+qtime' : edge.edgeType}
                              </td>
                              <td className="px-2 font-mono text-[13px]">{edge.fromSortNo}</td>
                              <td className="px-2 font-mono text-[13px]">{edge.toSortNo}</td>
                              <td className="px-2 font-mono text-[13px]">
                                {edge.edgeType === 'branch'
                                  ? edge.conditionCode || '—'
                                  : edge.edgeType === 'rework' || edge.edgeType === 'off_flow'
                                    ? edge.maxReworkCount
                                    : edge.edgeType === 'time_link' ||
                                        edge.edgeType === 'normal_qtime'
                                      ? '时间窗'
                                      : '前向'}
                              </td>
                              <td className="px-2 font-mono text-[12px]">
                                {edge.maxQueueMin != null
                                  ? `${edge.maxQueueMin}分${edge.onViolate ? '/' + edge.onViolate : ''}`
                                  : '—'}
                              </td>
                              <td className="px-2 font-mono text-[12px] text-muted">
                                {edge.edgeType === 'rework' ||
                                edge.edgeType === 'skip_allow' ||
                                edge.edgeType === 'off_flow'
                                  ? edge.reasonCodes || '任意'
                                  : '—'}
                              </td>
                            </>
                          )}
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {isDraft && canAdd && quickStepOpen ? (
              <div className="space-y-3 rounded-md border border-accent/25 bg-accent/5 p-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-xs font-medium text-ink">快速新建工序</p>
                  <button
                    type="button"
                    className="cursor-pointer text-xs text-muted hover:text-ink hover:underline"
                    onClick={() => setQuickStepOpen(false)}
                  >
                    收起
                  </button>
                </div>
                {quickStepError ? <p className="text-xs text-danger">{quickStepError}</p> : null}
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field
                    label="工序编码"
                    name="quickStepCode"
                    value={quickStepForm.stepCode}
                    onChange={(e) =>
                      setQuickStepForm((f) => ({ ...f, stepCode: e.target.value }))
                    }
                  />
                  <Field
                    label="工序名称"
                    name="quickStepName"
                    value={quickStepForm.stepName}
                    onChange={(e) =>
                      setQuickStepForm((f) => ({ ...f, stepName: e.target.value }))
                    }
                  />
                </div>
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs font-medium text-muted">类型</span>
                  <select
                    className="h-9 rounded-md border border-border bg-bg px-3"
                    value={quickStepForm.stepType}
                    onChange={(e) =>
                      setQuickStepForm((f) => ({ ...f, stepType: Number(e.target.value) }))
                    }
                  >
                    <option value={1}>加工</option>
                    <option value={2}>量测</option>
                    <option value={3}>其它</option>
                  </select>
                </label>
                <Button loading={savingQuickStep} onClick={() => void saveQuickStep()}>
                  新建并选用
                </Button>
              </div>
            ) : null}

            {!isDraft ? (
              <p className="text-xs text-muted">
                生效/归档版本只读。需要改工艺时请「升版」生成草稿后再编辑发布。
              </p>
            ) : null}
          </div>
        ) : (
          <p className="text-sm text-muted">暂无版本数据</p>
        )}
      </Drawer>

      <Drawer
        open={stepsOpen}
        title={
          stepMode === 'edit' && editingStep
            ? `工序库 · 编辑 ${editingStep.stepCode}`
            : '工序库'
        }
        onClose={() => {
          setStepsOpen(false)
          resetStepForm()
        }}
        width={520}
        footer={
          canAdd || canEdit ? (
            <>
              {stepMode === 'edit' ? (
                <Button variant="secondary" onClick={resetStepForm} disabled={savingStep}>
                  取消编辑
                </Button>
              ) : (
                <Button variant="secondary" onClick={resetStepForm} disabled={savingStep}>
                  清空表单
                </Button>
              )}
              <Button onClick={() => void saveStep()} loading={savingStep}>
                {stepMode === 'create' ? '新增工序' : '保存修改'}
              </Button>
            </>
          ) : null
        }
      >
        <div className="space-y-4">
          {(canAdd || canEdit) && (
            <div
              className={cn(
                'space-y-3 rounded-md border p-3',
                stepMode === 'edit'
                  ? 'border-accent/30 bg-accent/5'
                  : 'border-border bg-surface/50',
              )}
            >
              <p className="text-xs font-medium text-ink">
                {stepMode === 'edit' && editingStep
                  ? `正在编辑 · ${editingStep.stepCode}`
                  : '新增工序'}
              </p>
              {stepError ? <p className="text-xs text-danger">{stepError}</p> : null}
              {stepMode === 'create' ? (
                <Field
                  label="工序编码"
                  name="stepCode"
                  value={stepForm.stepCode}
                  onChange={(e) => setStepForm((f) => ({ ...f, stepCode: e.target.value }))}
                />
              ) : (
                <p className="text-sm text-muted">
                  编码{' '}
                  <span className="font-mono text-ink">{editingStep?.stepCode}</span>
                  <span className="ml-2">（不可改）</span>
                </p>
              )}
              <Field
                label="工序名称"
                name="stepName"
                value={stepForm.stepName}
                onChange={(e) => setStepForm((f) => ({ ...f, stepName: e.target.value }))}
              />
              <label className="flex flex-col gap-1.5 text-sm">
                <span className="text-xs font-medium text-muted">类型</span>
                <select
                  className="h-9 rounded-md border border-border bg-bg px-3"
                  value={stepForm.stepType}
                  onChange={(e) =>
                    setStepForm((f) => ({ ...f, stepType: Number(e.target.value) }))
                  }
                >
                  <option value={1}>加工</option>
                  <option value={2}>量测</option>
                  <option value={3}>其它</option>
                </select>
              </label>
              <Field
                label="设备类型"
                name="eqpType"
                value={stepForm.eqpType}
                onChange={(e) => setStepForm((f) => ({ ...f, eqpType: e.target.value }))}
                placeholder="如 ETCHER，空=不限"
              />
              <label className="flex flex-col gap-1.5 text-sm">
                <span className="text-xs font-medium text-muted">允许跳站</span>
                <select
                  className="h-9 rounded-md border border-border bg-bg px-3"
                  value={stepForm.allowSkip}
                  onChange={(e) =>
                    setStepForm((f) => ({ ...f, allowSkip: Number(e.target.value) }))
                  }
                >
                  <option value={0}>否</option>
                  <option value={1}>是</option>
                </select>
              </label>
              <Field
                label="最小加工(分)"
                name="minProcessMin"
                value={stepForm.minProcessMin}
                onChange={(e) => setStepForm((f) => ({ ...f, minProcessMin: e.target.value }))}
                placeholder="空=不控"
              />
              <Field
                label="最大加工(分)"
                name="maxProcessMin"
                value={stepForm.maxProcessMin}
                onChange={(e) => setStepForm((f) => ({ ...f, maxProcessMin: e.target.value }))}
                placeholder="空=不控"
              />
              {stepMode === 'edit' ? (
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs font-medium text-muted">状态</span>
                  <select
                    className="h-9 rounded-md border border-border bg-bg px-3"
                    value={stepForm.status}
                    onChange={(e) =>
                      setStepForm((f) => ({ ...f, status: Number(e.target.value) }))
                    }
                  >
                    <option value={1}>正常</option>
                    <option value={0}>禁用</option>
                  </select>
                </label>
              ) : null}
            </div>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <input
              className="h-9 w-48 rounded-md border border-border bg-bg px-3 text-sm"
              placeholder="编码 / 名称"
              value={stepQ}
              onChange={(e) => setStepQ(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  const kw = stepQ.trim()
                  setStepKeyword(kw)
                  void loadStepRows(kw)
                }
              }}
              aria-label="搜索工序"
            />
            <Button
              variant="secondary"
              onClick={() => {
                const kw = stepQ.trim()
                setStepKeyword(kw)
                void loadStepRows(kw)
              }}
            >
              搜索
            </Button>
          </div>

          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[420px] text-left text-sm">
              <thead className="bg-surface text-xs font-medium text-muted">
                <tr className="h-9 border-b border-border">
                  <th className="px-2">编码</th>
                  <th className="px-2">名称</th>
                  <th className="px-2">类型</th>
                  <th className="px-2">设备类型</th>
                  <th className="px-2">状态</th>
                  <th className="px-2">操作</th>
                </tr>
              </thead>
              <tbody>
                {stepsLoading ? (
                  <tr>
                    <td colSpan={5} className="h-14 px-2 text-center text-muted">
                      加载中…
                    </td>
                  </tr>
                ) : stepRows.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="h-14 px-2 text-center text-muted">
                      {stepKeyword ? '无匹配工序' : '暂无工序'}
                    </td>
                  </tr>
                ) : (
                  stepRows.map((s) => (
                    <tr
                      key={String(s.id)}
                      className={cn(
                        'h-10 border-b border-border',
                        editingStep && String(editingStep.id) === String(s.id) && 'bg-accent/8',
                      )}
                    >
                      <td className="px-2 font-mono text-[13px]">{s.stepCode}</td>
                      <td className="px-2">{s.stepName}</td>
                      <td className="px-2 text-muted">{STEP_TYPE_LABEL[s.stepType] ?? s.stepType}</td>
                      <td className="px-2 font-mono text-[12px] text-muted">{s.eqpType || '—'}</td>
                      <td className="px-2">
                        <EnablePill enabled={s.status === 1} />
                      </td>
                      <td className="px-2">
                        {canEdit ? (
                          <TableAction
                            icon={Pencil}
                            label="编辑"
                            onClick={() => {
                              setStepMode('edit')
                              setEditingStep(s)
                              setStepForm({
                                stepCode: s.stepCode,
                                stepName: s.stepName,
                                stepType: s.stepType,
                                eqpType: s.eqpType ?? '',
                                allowSkip: s.allowSkip === 1 ? 1 : 0,
                                minProcessMin:
                                  s.minProcessMin != null ? String(s.minProcessMin) : '',
                                maxProcessMin:
                                  s.maxProcessMin != null ? String(s.maxProcessMin) : '',
                                status: s.status,
                              })
                              setStepError('')
                            }}
                          />
                        ) : (
                          '—'
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </Drawer>
    </div>
  )
}
