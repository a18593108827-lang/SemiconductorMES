import { useCallback, useEffect, useState } from 'react'
import { Eye, Pencil, Plus, Power, Search, Trash2 } from 'lucide-react'
import {
  createEdcPlanApi,
  getEdcPlanApi,
  listEdcParamsApi,
  listEdcPlansApi,
  listEdcSpecsApi,
  replaceEdcPlanItemsApi,
  updateEdcPlanApi,
  updateEdcPlanEnabledApi,
  type MesEdcParamItem,
  type MesEdcPlan,
  type MesEdcSpecItem,
} from '../../api/edc'
import { listStepsApi, type MesStepItem } from '../../api/route'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../ui/Button'
import { useConfirm } from '../ui/ConfirmDialog'
import { Drawer } from '../ui/Drawer'
import { FlashRow } from '../ui/FlashRow'
import { RecipeVersionPill } from '../ui/StatusPill'
import { TableAction } from '../ui/TableAction'
import { useToast } from '../ui/Toast'
import { ApiError } from '../../lib/http'
import { cn } from '../../lib/cn'

type DraftItem = {
  key: string
  paramId: string
  specId: string
  mandatory: 0 | 1
}

type Props = {
  onRequestCreate?: (open: () => void) => void
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function newKey() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function EnabledMark({ enabled }: { enabled: number }) {
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium',
        enabled === 1 ? 'text-ink' : 'text-muted',
      )}
    >
      <span
        className={cn('size-1.5 rounded-full', enabled === 1 ? 'bg-success' : 'bg-border')}
        aria-hidden
      />
      {enabled === 1 ? '启用' : '停用'}
    </span>
  )
}

function RequiredMark({ required }: { required: number }) {
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium',
        required === 1 ? 'text-ink' : 'text-muted',
      )}
    >
      <span
        className={cn('size-1.5 rounded-full', required === 1 ? 'bg-warning' : 'bg-border')}
        aria-hidden
      />
      {required === 1 ? '出门禁' : '不拦'}
    </span>
  )
}

function specLabel(s: MesEdcSpecItem) {
  const st = s.status === 'active' ? '生效' : s.status === 'draft' ? '草稿' : '停用'
  const prod = s.productCode ? s.productCode : '全产品'
  return `v${s.versionNo} · ${st} · ${prod}`
}

export function EdcPlanPanel({ onRequestCreate }: Props) {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()

  const [rows, setRows] = useState<MesEdcPlan[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [stepFilter, setStepFilter] = useState('')
  const [requiredFilter, setRequiredFilter] = useState<0 | 1 | 'all'>('all')
  const [enabledFilter, setEnabledFilter] = useState<0 | 1 | 'all'>('all')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [stepOptions, setStepOptions] = useState<MesStepItem[]>([])
  const [paramOptions, setParamOptions] = useState<MesEdcParamItem[]>([])
  const [specMap, setSpecMap] = useState<Record<string, MesEdcSpecItem[]>>({})

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState({ stepId: '', remark: '' })
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [detail, setDetail] = useState<MesEdcPlan | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({ required: 0 as 0 | 1, remark: '' })
  const [draftItems, setDraftItems] = useState<DraftItem[]>([])
  const [editError, setEditError] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [toggling, setToggling] = useState(false)

  const size = 20
  const canEdit = hasPermission('edc:edit')
  const totalPages = Math.max(1, Math.ceil(total / size))

  const openCreate = useCallback(() => {
    setCreateForm({ stepId: '', remark: '' })
    setCreateError('')
    setCreateOpen(true)
  }, [])

  useEffect(() => {
    onRequestCreate?.(openCreate)
  }, [onRequestCreate, openCreate])

  const loadOptions = useCallback(async () => {
    try {
      const [steps, params] = await Promise.all([
        listStepsApi({ status: 1, page: 1, size: 200 }),
        listEdcParamsApi({ enabled: 1, page: 1, size: 200 }),
      ])
      setStepOptions(steps.records ?? [])
      setParamOptions(params.records ?? [])
    } catch {
      setStepOptions([])
      setParamOptions([])
    }
  }, [])

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listEdcPlansApi({
        stepId: stepFilter || undefined,
        required: requiredFilter === 'all' ? '' : requiredFilter,
        enabled: enabledFilter === 'all' ? '' : enabledFilter,
        page,
        size,
      })
      setRows(data.records ?? [])
      setTotal(data.total ?? 0)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '站计划加载失败')
    } finally {
      setLoading(false)
    }
  }, [stepFilter, requiredFilter, enabledFilter, page, toast])

  useEffect(() => {
    void loadList()
  }, [loadList])

  useEffect(() => {
    void loadOptions()
  }, [loadOptions])

  async function loadSpecs(paramId: string) {
    if (!paramId || specMap[paramId]) return
    try {
      const data = await listEdcSpecsApi({ paramId, page: 1, size: 100 })
      setSpecMap((m) => ({ ...m, [paramId]: data.records ?? [] }))
    } catch {
      setSpecMap((m) => ({ ...m, [paramId]: [] }))
    }
  }

  function toDraft(plan: MesEdcPlan): DraftItem[] {
    return (plan.items ?? []).map((it) => ({
      key: it.id != null ? String(it.id) : newKey(),
      paramId: String(it.paramId),
      specId: it.specId != null && it.specId !== '' ? String(it.specId) : '',
      mandatory: it.mandatory === 0 ? 0 : 1,
    }))
  }

  async function openDetail(row: MesEdcPlan, startEditing = false) {
    setDetail(row)
    setEditing(false)
    setEditError('')
    setDetailLoading(true)
    try {
      const full = await getEdcPlanApi(row.id)
      setDetail(full)
      setEditForm({
        required: full.required === 1 ? 1 : 0,
        remark: full.remark ?? '',
      })
      const drafts = toDraft(full)
      setDraftItems(drafts)
      await Promise.all(drafts.map((d) => loadSpecs(d.paramId)))
      if (startEditing) setEditing(true)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    if (!createForm.stepId) {
      setCreateError('请选择工序')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const created = await createEdcPlanApi({
        stepId: createForm.stepId,
        remark: createForm.remark.trim() || undefined,
      })
      toast.success('已建计划，配好采集项后再开门禁')
      setFlashId(String(created.id))
      setCreateOpen(false)
      setPage(1)
      await loadList()
      await openDetail(created, true)
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setSavingCreate(false)
    }
  }

  function beginEdit() {
    if (!detail) return
    setEditForm({
      required: detail.required === 1 ? 1 : 0,
      remark: detail.remark ?? '',
    })
    setDraftItems(toDraft(detail))
    setEditError('')
    setEditing(true)
  }

  function addDraft() {
    setDraftItems((list) => [...list, { key: newKey(), paramId: '', specId: '', mandatory: 1 }])
  }

  function patchDraft(key: string, patch: Partial<DraftItem>) {
    setDraftItems((list) => list.map((it) => (it.key === key ? { ...it, ...patch } : it)))
  }

  async function onParamChange(key: string, paramId: string) {
    patchDraft(key, { paramId, specId: '' })
    await loadSpecs(paramId)
  }

  async function submitEdit() {
    if (!detail) return
    const filled = draftItems.filter((it) => it.paramId)
    if (filled.length !== draftItems.length) {
      setEditError('请为每一行选择特性，或删掉空行')
      return
    }
    const seen = new Set<string>()
    for (const it of filled) {
      if (seen.has(it.paramId)) {
        setEditError('同一计划内特性不能重复')
        return
      }
      seen.add(it.paramId)
    }
    if (editForm.required === 1 && filled.length === 0) {
      setEditError('开门禁前必须先配置计划项')
      return
    }
    if (detail.required !== 1 && editForm.required === 1) {
      const ok = await confirm({
        title: '打开出门禁',
        message: '打开后，该站未采完必采项将无法 TrackOut。',
        confirmText: '打开门禁',
      })
      if (!ok) return
    }

    setSavingEdit(true)
    setEditError('')
    const payload = filled.map((it, i) => ({
      paramId: it.paramId,
      specId: it.specId || undefined,
      sortNo: i + 1,
      mandatory: it.mandatory,
    }))
    try {
      if (editForm.required === 1) {
        await replaceEdcPlanItemsApi(detail.id, payload)
        await updateEdcPlanApi(detail.id, {
          required: 1,
          remark: editForm.remark.trim() || undefined,
        })
      } else {
        await updateEdcPlanApi(detail.id, {
          required: 0,
          remark: editForm.remark.trim() || undefined,
        })
        await replaceEdcPlanItemsApi(detail.id, payload)
      }
      toast.success('计划已保存')
      setFlashId(String(detail.id))
      setEditing(false)
      const full = await getEdcPlanApi(detail.id)
      setDetail(full)
      setDraftItems(toDraft(full))
      await loadList()
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingEdit(false)
    }
  }

  async function toggleEnabled() {
    if (!detail) return
    const next: 0 | 1 = detail.enabled === 1 ? 0 : 1
    if (next === 0) {
      const ok = await confirm({
        title: '停用站计划',
        message: `停用后 ${detail.stepCode ?? ''} 不再按此计划采集。`,
        confirmText: '停用',
      })
      if (!ok) return
    }
    setToggling(true)
    try {
      await updateEdcPlanEnabledApi(detail.id, next)
      toast.success(next === 1 ? '已启用' : '已停用')
      setFlashId(String(detail.id))
      const full = await getEdcPlanApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '启停失败')
    } finally {
      setToggling(false)
    }
  }

  const usedParamIds = new Set(draftItems.map((it) => it.paramId).filter(Boolean))

  return (
    <>
      <div className="edc-block flex flex-wrap items-center gap-2">
        <select
          className="h-9 min-w-[180px] cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
          value={stepFilter}
          onChange={(e) => {
            setStepFilter(e.target.value)
            setPage(1)
          }}
          aria-label="按工序筛选"
        >
          <option value="">全部工序</option>
          {stepOptions.map((s) => (
            <option key={String(s.id)} value={String(s.id)}>
              {s.stepCode} · {s.stepName}
            </option>
          ))}
        </select>
        <Button
          variant="secondary"
          onClick={() => {
            setPage(1)
            void loadList()
          }}
        >
          <Search className="size-4" aria-hidden />
          刷新
        </Button>
        <div className="flex flex-wrap gap-1.5 sm:ml-2">
          {(
            [
              { key: 'all' as const, label: '门禁全部' },
              { key: 1 as const, label: '出门禁' },
              { key: 0 as const, label: '不拦' },
            ] as const
          ).map((s) => (
            <button
              key={String(s.key)}
              type="button"
              onClick={() => {
                setRequiredFilter(s.key)
                setPage(1)
              }}
              className={cn(
                'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
                requiredFilter === s.key
                  ? 'border-accent bg-accent/10 text-accent'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-1.5">
          {(
            [
              { key: 'all' as const, label: '启停全部' },
              { key: 1 as const, label: '启用' },
              { key: 0 as const, label: '停用' },
            ] as const
          ).map((s) => (
            <button
              key={String(s.key)}
              type="button"
              onClick={() => {
                setEnabledFilter(s.key)
                setPage(1)
              }}
              className={cn(
                'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
                enabledFilter === s.key
                  ? 'border-accent bg-accent/10 text-accent'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="edc-block overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[880px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">工序</th>
              <th className="px-3">门禁</th>
              <th className="px-3">启停</th>
              <th className="px-3">项数</th>
              <th className="px-3">更新</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 6 }).map((_, i) => (
                <tr key={i} className="h-10 border-b border-border/60">
                  {Array.from({ length: 6 }).map((__, j) => (
                    <td key={j} className="px-3">
                      <div className="h-3.5 w-[70%] max-w-[120px] animate-pulse rounded-sm bg-surface" />
                    </td>
                  ))}
                </tr>
              ))
            ) : loadFailed ? (
              <tr>
                <td colSpan={6} className="px-3 py-10 text-center">
                  <p className="text-sm text-muted">加载失败</p>
                  <button
                    type="button"
                    className="mt-2 cursor-pointer text-sm text-accent hover:underline"
                    onClick={() => void loadList()}
                  >
                    重试
                  </button>
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-3 py-12 text-center">
                  <div className="mx-auto flex max-w-sm flex-col items-center gap-2">
                    <p className="text-sm text-ink">还没有站计划</p>
                    <p className="text-xs text-muted">按工序建计划，配好采集项后再开 TrackOut 门禁</p>
                    {canEdit ? (
                      <Button className="mt-2" onClick={openCreate}>
                        <Plus className="size-4" aria-hidden />
                        新建计划
                      </Button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                  <td className="h-10 px-3">
                    <div className="font-mono text-[13px] text-accent">{row.stepCode ?? '—'}</div>
                    <div className="text-xs text-muted">{row.stepName ?? ''}</div>
                  </td>
                  <td className="px-3">
                    <RequiredMark required={row.required} />
                  </td>
                  <td className="px-3">
                    <EnabledMark enabled={row.enabled} />
                  </td>
                  <td className="px-3 font-mono text-[13px]">{row.itemCount ?? 0}</td>
                  <td className="px-3 font-mono text-[12px] text-muted">{fmtTime(row.updateTime)}</td>
                  <td className="px-3">
                    <TableAction icon={Eye} label="详情" onClick={() => void openDetail(row)} />
                  </td>
                </FlashRow>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="edc-block flex items-center justify-between text-sm text-muted">
        <span>
          共 {total} 条 · 第 {page}/{totalPages} 页
        </span>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </Button>
          <Button
            variant="secondary"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </div>
      </div>

      <Drawer open={createOpen} onClose={() => setCreateOpen(false)} title="新建站计划" width={420}>
        <div className="space-y-4">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">
              工序 <span className="text-danger">*</span>
            </span>
            <select
              className="h-9 cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
              value={createForm.stepId}
              onChange={(e) => setCreateForm((f) => ({ ...f, stepId: e.target.value }))}
            >
              <option value="">请选择</option>
              {stepOptions.map((s) => (
                <option key={String(s.id)} value={String(s.id)}>
                  {s.stepCode} · {s.stepName}
                </option>
              ))}
            </select>
          </label>
          <p className="text-xs text-muted">一门一计划。新建后门禁关闭，配完采集项再开。</p>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">备注</span>
            <textarea
              className="min-h-[72px] w-full rounded-md border border-border bg-bg px-3 py-2 text-sm"
              value={createForm.remark}
              onChange={(e) => setCreateForm((f) => ({ ...f, remark: e.target.value }))}
            />
          </label>
          {createError ? <p className="text-sm text-danger">{createError}</p> : null}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              取消
            </Button>
            <Button loading={savingCreate} onClick={() => void submitCreate()}>
              <Plus className="size-4" aria-hidden />
              创建计划
            </Button>
          </div>
        </div>
      </Drawer>

      <Drawer
        open={detail != null}
        onClose={() => setDetail(null)}
        title={detail ? `${detail.stepCode ?? '站计划'} · ${detail.stepName ?? ''}` : '站计划详情'}
        width={560}
      >
        {detailLoading && !detail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : detail ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <RequiredMark required={editing ? editForm.required : detail.required} />
              <EnabledMark enabled={detail.enabled} />
              <span className="font-mono text-xs text-muted">{detail.itemCount ?? 0} 项</span>
            </div>

            {editing ? (
              <div className="space-y-4">
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="mt-0.5 size-4 accent-primary"
                    checked={editForm.required === 1}
                    onChange={(e) =>
                      setEditForm((f) => ({ ...f, required: e.target.checked ? 1 : 0 }))
                    }
                  />
                  <span>
                    TrackOut 出门禁
                    <span className="mt-0.5 block text-xs text-muted">
                      开了之后，该站没采完必采项不能出站
                    </span>
                  </span>
                </label>
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs font-medium text-muted">备注</span>
                  <textarea
                    className="min-h-[64px] w-full rounded-md border border-border bg-bg px-3 py-2 text-sm"
                    value={editForm.remark}
                    onChange={(e) => setEditForm((f) => ({ ...f, remark: e.target.value }))}
                  />
                </label>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-muted">采集项</span>
                    <button
                      type="button"
                      className="inline-flex h-8 cursor-pointer items-center gap-1 rounded-md border border-border px-2 text-xs font-medium text-ink hover:bg-surface"
                      onClick={addDraft}
                    >
                      <Plus className="size-3.5" aria-hidden />
                      加一项
                    </button>
                  </div>
                  {draftItems.length === 0 ? (
                    <p className="rounded-md border border-dashed border-border px-3 py-6 text-center text-xs text-muted">
                      还没有采集项。先加特性，规格可空（跟生效规格）。
                    </p>
                  ) : (
                    <div className="space-y-2">
                      {draftItems.map((it) => {
                        const specs = specMap[it.paramId] ?? []
                        return (
                          <div
                            key={it.key}
                            className="grid grid-cols-[1fr_1fr_auto_auto] items-end gap-2 rounded-md border border-border bg-surface p-2"
                          >
                            <label className="flex min-w-0 flex-col gap-1 text-xs">
                              <span className="text-muted">特性</span>
                              <select
                                className="h-8 cursor-pointer rounded-md border border-border bg-bg px-2 text-sm"
                                value={it.paramId}
                                onChange={(e) => void onParamChange(it.key, e.target.value)}
                              >
                                <option value="">请选择</option>
                                {paramOptions.map((p) => (
                                  <option
                                    key={String(p.id)}
                                    value={String(p.id)}
                                    disabled={
                                      usedParamIds.has(String(p.id)) && String(p.id) !== it.paramId
                                    }
                                  >
                                    {p.paramCode}
                                    {p.unit ? ` (${p.unit})` : ''}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label className="flex min-w-0 flex-col gap-1 text-xs">
                              <span className="text-muted">规格</span>
                              <select
                                className="h-8 cursor-pointer rounded-md border border-border bg-bg px-2 text-sm"
                                value={it.specId}
                                disabled={!it.paramId}
                                onChange={(e) => patchDraft(it.key, { specId: e.target.value })}
                              >
                                <option value="">跟生效规格</option>
                                {specs.map((s) => (
                                  <option key={String(s.id)} value={String(s.id)}>
                                    {specLabel(s)}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label className="flex h-8 items-center gap-1.5 whitespace-nowrap text-xs">
                              <input
                                type="checkbox"
                                className="size-3.5 accent-primary"
                                checked={it.mandatory === 1}
                                onChange={(e) =>
                                  patchDraft(it.key, { mandatory: e.target.checked ? 1 : 0 })
                                }
                              />
                              必采
                            </label>
                            <button
                              type="button"
                              className="inline-flex size-8 cursor-pointer items-center justify-center rounded-md border border-border text-muted hover:bg-bg hover:text-danger"
                              aria-label="删除此项"
                              onClick={() =>
                                setDraftItems((list) => list.filter((x) => x.key !== it.key))
                              }
                            >
                              <Trash2 className="size-3.5" aria-hidden />
                            </button>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>

                {editError ? <p className="text-sm text-danger">{editError}</p> : null}
                <div className="flex justify-end gap-2">
                  <Button variant="secondary" onClick={() => setEditing(false)}>
                    取消
                  </Button>
                  <Button loading={savingEdit} onClick={() => void submitEdit()}>
                    保存
                  </Button>
                </div>
              </div>
            ) : (
              <>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-xs text-muted">工序</dt>
                    <dd className="mt-0.5 font-mono text-[13px] text-accent">{detail.stepCode}</dd>
                    <dd className="text-xs text-muted">{detail.stepName}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted">更新</dt>
                    <dd className="mt-0.5 font-mono text-[12px]">{fmtTime(detail.updateTime)}</dd>
                  </div>
                  <div className="col-span-2">
                    <dt className="text-xs text-muted">备注</dt>
                    <dd className="mt-0.5">{detail.remark || '—'}</dd>
                  </div>
                </dl>

                <div>
                  <p className="mb-2 text-xs font-medium text-muted">采集项</p>
                  {(detail.items ?? []).length === 0 ? (
                    <p className="rounded-md border border-dashed border-border px-3 py-6 text-center text-xs text-muted">
                      尚未配置。编辑后添加特性。
                    </p>
                  ) : (
                    <table className="w-full text-left text-sm">
                      <thead className="text-xs text-muted">
                        <tr className="h-8 border-b border-border">
                          <th className="pr-2 font-medium">特性</th>
                          <th className="pr-2 font-medium">规格</th>
                          <th className="font-medium">必采</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(detail.items ?? []).map((it) => (
                          <tr key={String(it.id ?? it.paramId)} className="h-9 border-b border-border/60">
                            <td className="pr-2">
                              <span className="font-mono text-[13px] text-accent">
                                {it.paramCode ?? '—'}
                              </span>
                              {it.unit ? (
                                <span className="ml-1 text-xs text-muted">{it.unit}</span>
                              ) : null}
                            </td>
                            <td className="pr-2">
                              {it.specId != null && it.specId !== '' ? (
                                <span className="inline-flex items-center gap-1.5">
                                  <span className="font-mono text-[12px]">v{it.specVersionNo}</span>
                                  {it.specStatus ? (
                                    <RecipeVersionPill status={it.specStatus} />
                                  ) : null}
                                </span>
                              ) : (
                                <span className="text-xs text-muted">跟生效规格</span>
                              )}
                            </td>
                            <td className="text-xs">{it.mandatory === 0 ? '选采' : '必采'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </>
            )}

            {!editing && canEdit ? (
              <div className="flex flex-wrap gap-2 border-t border-border pt-4">
                <Button variant="secondary" onClick={beginEdit}>
                  <Pencil className="size-4" aria-hidden />
                  编辑
                </Button>
                <Button
                  variant={detail.enabled === 1 ? 'danger' : 'secondary'}
                  loading={toggling}
                  onClick={() => void toggleEnabled()}
                >
                  <Power className="size-4" aria-hidden />
                  {detail.enabled === 1 ? '停用' : '启用'}
                </Button>
              </div>
            ) : null}
          </div>
        ) : null}
      </Drawer>
    </>
  )
}
