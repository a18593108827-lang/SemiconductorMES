import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowDown, ArrowLeft, ArrowUp, Pencil, Plus, Trash2, Wrench } from 'lucide-react'
import {
  createStepApi,
  getRouteApi,
  getRouteVersionApi,
  listRouteVersionsApi,
  listStepsApi,
  publishRouteVersionApi,
  saveRouteDraftStepsApi,
  updateStepApi,
  upgradeRouteVersionApi,
  type MesRouteItem,
  type MesRouteVersionDetail,
  type MesRouteVersionItem,
  type MesStepItem,
} from '../api/route'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { EnablePill, RouteVersionPill } from '../components/ui/StatusPill'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import {
  type DraftEdge,
  type DraftRow,
  STEP_TYPE_LABEL,
  VERSION_STATUS_LABEL,
  displaySortNo,
  emptyQtimeFields,
  toDraftEdges,
  toDraftRows,
  toSaveEdges,
  toSaveSteps,
} from './route/routeDraft'

export function RouteEditorPage() {
  const { routeId } = useParams<{ routeId: string }>()
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()

  const [detailRoute, setDetailRoute] = useState<MesRouteItem | null>(null)
  const [versions, setVersions] = useState<MesRouteVersionItem[]>([])
  const [versionDetail, setVersionDetail] = useState<MesRouteVersionDetail | null>(null)
  const [selectedVersionId, setSelectedVersionId] = useState<string>('')
  const [detailLoading, setDetailLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [draftRows, setDraftRows] = useState<DraftRow[]>([])
  const [draftEdges, setDraftEdges] = useState<DraftEdge[]>([])
  const [savingSteps, setSavingSteps] = useState(false)
  const [editorTab, setEditorTab] = useState<'steps' | 'edges'>('steps')

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

  const canAdd = hasPermission('route:add')
  const canEdit = hasPermission('route:edit')
  const isDraft = versionDetail?.status === 'draft'
  const hasDraftVersion = versions.some((v) => v.status === 'draft')

  const stepNameMap = useMemo(
    () => Object.fromEntries(stepOptions.map((s) => [String(s.id), s])),
    [stepOptions],
  )

  const loadStepOptions = useCallback(async () => {
    try {
      const data = await listStepsApi({ status: 1, page: 1, size: 200 })
      setStepOptions(data.records)
    } catch {
      setStepOptions([])
    }
  }, [])

  const loadRouteDetail = useCallback(
    async (route: MesRouteItem) => {
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
        setLoadError(err instanceof ApiError ? err.message : '加载版本失败')
      } finally {
        setDetailLoading(false)
      }
    },
    [toast],
  )

  useEffect(() => {
    void loadStepOptions()
  }, [loadStepOptions])

  useEffect(() => {
    if (!routeId) {
      setLoadError('路线不存在')
      setDetailLoading(false)
      return
    }
    let cancelled = false
    ;(async () => {
      setDetailLoading(true)
      setLoadError('')
      try {
        const route = await getRouteApi(routeId)
        if (cancelled) return
        await loadRouteDetail(route)
      } catch (err) {
        if (cancelled) return
        const msg = err instanceof ApiError ? err.message : '加载失败'
        setLoadError(msg)
        toast.error(msg)
      } finally {
        if (!cancelled) setDetailLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [routeId, loadRouteDetail, toast])

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
      if (detailRoute) await loadRouteDetail(detailRoute)
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
      await loadRouteDetail(detailRoute)
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

  function goBack() {
    navigate('/app/route')
  }

  const actions = (
    <div className="flex flex-wrap items-center gap-2">
      <Button variant="secondary" onClick={goBack}>
        返回列表
      </Button>
      {canEdit || canAdd ? (
        <Button variant="secondary" onClick={() => void openStepsLibrary()}>
          <Wrench className="size-4" aria-hidden />
          工序库
        </Button>
      ) : null}
      {versionDetail && canAdd ? (
        <Button
          variant="secondary"
          onClick={() => void upgradeFromCurrent()}
          disabled={hasDraftVersion}
          title={hasDraftVersion ? '已有草稿，请先发布或编辑现有草稿' : undefined}
        >
          升版
        </Button>
      ) : null}
      {versionDetail && isDraft && canEdit ? (
        <>
          <Button variant="secondary" loading={savingSteps} onClick={() => void saveDraft()}>
            保存草稿
          </Button>
          <Button loading={savingSteps} onClick={() => void publishVersion()}>
            发布
          </Button>
        </>
      ) : null}
    </div>
  )

  return (
    <div className="flex min-h-0 flex-1 flex-col pb-20">
      {loadError && !detailRoute ? (
        <div className="flex flex-col items-start gap-3 py-8">
          <p className="text-sm text-danger">{loadError}</p>
          <Button variant="secondary" onClick={goBack}>
            返回列表
          </Button>
        </div>
      ) : null}
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={goBack}
              className="inline-flex cursor-pointer items-center gap-1 text-sm text-muted hover:text-ink"
            >
              <ArrowLeft className="size-4" aria-hidden />
              返回
            </button>
            <h1 className="text-xl font-semibold tracking-tight">
              维护 · {detailRoute?.routeCode ?? '…'}
            </h1>
          </div>
          {versionDetail || detailRoute ? (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-medium">{versionDetail?.routeName ?? detailRoute?.routeName}</span>
              {versionDetail ? <RouteVersionPill status={versionDetail.status} /> : null}
            </div>
          ) : null}
          <label className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-muted">版本:</span>
            <select
              className="h-9 min-w-[10rem] cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
              value={selectedVersionId}
              disabled={detailLoading || versions.length === 0}
              onChange={(e) => void selectVersion(e.target.value)}
              aria-label="选择版本"
            >
              {versions.length === 0 ? (
                <option value="">暂无版本</option>
              ) : (
                versions.map((v) => (
                  <option key={String(v.id)} value={String(v.id)}>
                    v{v.versionNo} · {VERSION_STATUS_LABEL[v.status] ?? v.status}
                  </option>
                ))
              )}
            </select>
          </label>
        </div>
        <div className="hidden sm:block">{actions}</div>
      </header>

      {detailLoading && !versionDetail ? (
        <p className="mt-6 text-sm text-muted">加载中…</p>
      ) : versionDetail ? (
        <div className="mt-4 space-y-4">
          <div className="flex gap-1.5">
            {(
              [
                { key: 'steps' as const, label: '步骤' },
                { key: 'edges' as const, label: '边约束' },
              ] as const
            ).map((t) => (
              <button
                key={t.key}
                type="button"
                onClick={() => setEditorTab(t.key)}
                className={cn(
                  'inline-flex h-9 cursor-pointer items-center rounded-md border px-3 text-sm font-medium transition-colors duration-150',
                  editorTab === t.key
                    ? 'border-primary bg-primary text-white'
                    : 'border-border bg-bg text-muted hover:bg-surface',
                )}
              >
                {t.label}
              </button>
            ))}
          </div>

          {editorTab === 'steps' ? (
            <div className="space-y-3">
              <div className="overflow-x-auto rounded-md border border-border">
                <table className="w-full min-w-[640px] text-left text-sm">
                  <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
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
                        const nextRow =
                          kindIdx >= 0 && kindIdx < sameKind.length - 1 ? sameKind[kindIdx + 1] : null
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
            </div>
          ) : (
            <div className="space-y-3">
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

              <div className="overflow-x-auto rounded-md border border-border">
                <table className="w-full min-w-[640px] text-left text-sm">
                  <thead className="sticky top-0 bg-surface text-[11px] uppercase tracking-wide text-muted">
                    <tr className="h-9 border-b border-border">
                      <th className="px-2 font-medium">类型</th>
                      <th className="px-2 font-medium">触发站</th>
                      <th className="px-2 font-medium">目标站</th>
                      <th className="px-2 font-medium">条件/上限</th>
                      <th className="px-2 font-medium">QTime</th>
                      <th className="px-2 font-medium">原因码</th>
                      {isDraft && canEdit ? <th className="w-10 px-2 font-medium" /> : null}
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
          )}

          {!isDraft ? (
            <p className="text-xs text-muted">
              生效/归档版本只读。需要改工艺时请「升版」生成草稿后再编辑发布。
            </p>
          ) : null}
        </div>
      ) : (
        <p className="mt-6 text-sm text-muted">暂无版本数据</p>
      )}

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-bg/95 px-4 py-3 backdrop-blur sm:px-6">
        <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-end gap-2">
          {actions}
        </div>
      </div>

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
        size="md"
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
                    <td colSpan={6} className="h-14 px-2 text-center text-muted">
                      加载中…
                    </td>
                  </tr>
                ) : stepRows.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="h-14 px-2 text-center text-muted">
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
