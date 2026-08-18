import { useCallback, useEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import { Eye, FlaskConical, Link2, Plus, Power, Search, Trash2 } from 'lucide-react'
import { listEqpOptionsApi, type MesEqpOption } from '../api/eqp'
import {
  createRecipeApi,
  createRecipeBindingApi,
  createRecipeDraftApi,
  deleteRecipeBindingApi,
  getRecipeApi,
  getRecipeVersionApi,
  listRecipeBindingsApi,
  listRecipesApi,
  listRecipeVersionsApi,
  publishRecipeVersionApi,
  updateRecipeApi,
  updateRecipeDraftApi,
  updateRecipeEnabledApi,
  type MesRecipeBindingItem,
  type MesRecipeItem,
  type MesRecipeVersionDetail,
  type MesRecipeVersionItem,
} from '../api/recipe'
import { listStepsApi, type MesStepItem } from '../api/route'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { RecipeVersionPill } from '../components/ui/StatusPill'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

type TabKey = 'recipes' | 'bindings'

const emptyCreate = { recipeCode: '', recipeName: '', remark: '' }

export function RecipePage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)

  const [tab, setTab] = useState<TabKey>('recipes')

  const [rows, setRows] = useState<MesRecipeItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [enabledFilter, setEnabledFilter] = useState<0 | 1 | 'all'>('all')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreate)
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [detail, setDetail] = useState<MesRecipeItem | null>(null)
  const [versions, setVersions] = useState<MesRecipeVersionItem[]>([])
  const [versionDetail, setVersionDetail] = useState<MesRecipeVersionDetail | null>(null)
  const [selectedVersionId, setSelectedVersionId] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({ recipeName: '', remark: '' })
  const [editError, setEditError] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [toggling, setToggling] = useState(false)
  const [draftBody, setDraftBody] = useState('')
  const [draftRemark, setDraftRemark] = useState('')
  const [savingDraft, setSavingDraft] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [creatingDraft, setCreatingDraft] = useState(false)

  const [bindRows, setBindRows] = useState<MesRecipeBindingItem[]>([])
  const [bindTotal, setBindTotal] = useState(0)
  const [bindPage, setBindPage] = useState(1)
  const [bindLoading, setBindLoading] = useState(false)
  const [bindFailed, setBindFailed] = useState(false)
  const [bindFlashId, setBindFlashId] = useState<string | null>(null)
  const [bindOpen, setBindOpen] = useState(false)
  const [bindForm, setBindForm] = useState({
    stepId: '',
    bindMode: 'eqp' as 'eqp' | 'type',
    eqpId: '',
    eqpType: '',
    recipeId: '',
  })
  const [bindError, setBindError] = useState('')
  const [savingBind, setSavingBind] = useState(false)
  const [stepOptions, setStepOptions] = useState<MesStepItem[]>([])
  const [eqpOptions, setEqpOptions] = useState<MesEqpOption[]>([])
  const [recipeOptions, setRecipeOptions] = useState<MesRecipeItem[]>([])

  const size = 20
  const canView = hasPermission('recipe:view')
  const canEdit = hasPermission('recipe:edit')
  const canPublish = hasPermission('recipe:publish')
  const canBind = hasPermission('recipe:bind')
  const totalPages = Math.max(1, Math.ceil(total / size))
  const bindTotalPages = Math.max(1, Math.ceil(bindTotal / size))
  const hasDraft = versions.some((v) => v.status === 'draft')
  const isDraft = versionDetail?.status === 'draft'

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listRecipesApi({
        keyword,
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
      toast.error(err instanceof ApiError ? err.message : '配方加载失败')
    } finally {
      setLoading(false)
    }
  }, [enabledFilter, keyword, page, toast])

  const loadBindings = useCallback(async () => {
    setBindLoading(true)
    setBindFailed(false)
    try {
      const data = await listRecipeBindingsApi({ page: bindPage, size })
      setBindRows(data.records ?? [])
      setBindTotal(data.total ?? 0)
    } catch (err) {
      setBindRows([])
      setBindTotal(0)
      setBindFailed(true)
      toast.error(err instanceof ApiError ? err.message : '绑定加载失败')
    } finally {
      setBindLoading(false)
    }
  }, [bindPage, toast])

  useEffect(() => {
    if (!canView) return
    if (tab === 'recipes') void loadList()
    else void loadBindings()
  }, [canView, tab, loadList, loadBindings])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.rcp-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [tab])

  async function openDetail(row: MesRecipeItem) {
    setDetail(row)
    setEditing(false)
    setEditError('')
    setDetailLoading(true)
    setVersions([])
    setVersionDetail(null)
    setSelectedVersionId('')
    try {
      const full = await getRecipeApi(row.id)
      setDetail(full)
      setEditForm({ recipeName: full.recipeName ?? '', remark: full.remark ?? '' })
      const vers = await listRecipeVersionsApi(full.id)
      setVersions(vers)
      const prefer =
        vers.find((v) => v.status === 'draft') ??
        vers.find((v) => v.status === 'active') ??
        vers[0]
      if (prefer) {
        setSelectedVersionId(String(prefer.id))
        const vd = await getRecipeVersionApi(prefer.id)
        setVersionDetail(vd)
        setDraftBody(vd.bodyJson ?? '')
        setDraftRemark(vd.remark ?? '')
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function selectVersion(versionId: string) {
    setSelectedVersionId(versionId)
    setDetailLoading(true)
    try {
      const vd = await getRecipeVersionApi(versionId)
      setVersionDetail(vd)
      setDraftBody(vd.bodyJson ?? '')
      setDraftRemark(vd.remark ?? '')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载版本失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    if (!createForm.recipeCode.trim()) {
      setCreateError('配方编码不能为空')
      return
    }
    if (!createForm.recipeName.trim()) {
      setCreateError('配方名称不能为空')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const created = await createRecipeApi({
        recipeCode: createForm.recipeCode.trim(),
        recipeName: createForm.recipeName.trim(),
        remark: createForm.remark.trim() || undefined,
      })
      toast.success(`已创建 ${created.recipeCode}`)
      setFlashId(String(created.id))
      setCreateOpen(false)
      setPage(1)
      await loadList()
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setSavingCreate(false)
    }
  }

  async function submitEdit() {
    if (!detail) return
    if (!editForm.recipeName.trim()) {
      setEditError('配方名称不能为空')
      return
    }
    setSavingEdit(true)
    setEditError('')
    try {
      await updateRecipeApi(detail.id, {
        recipeName: editForm.recipeName.trim(),
        remark: editForm.remark.trim() || undefined,
      })
      toast.success('已保存')
      setEditing(false)
      setFlashId(String(detail.id))
      const full = await getRecipeApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingEdit(false)
    }
  }

  async function toggleEnabled() {
    if (!detail) return
    const next = detail.enabled === 1 ? 0 : 1
    const ok = await confirm({
      title: next === 1 ? '启用配方' : '停用配方',
      message:
        next === 1
          ? `确认启用 ${detail.recipeCode}？`
          : `确认停用 ${detail.recipeCode}？停用后解析不会命中。`,
      confirmText: next === 1 ? '启用' : '停用',
      danger: next === 0,
    })
    if (!ok) return
    setToggling(true)
    try {
      await updateRecipeEnabledApi(detail.id, next)
      toast.success(next === 1 ? '已启用' : '已停用')
      setFlashId(String(detail.id))
      const full = await getRecipeApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '启停失败')
    } finally {
      setToggling(false)
    }
  }

  async function createDraft() {
    if (!detail) return
    setCreatingDraft(true)
    try {
      const from =
        versions.find((v) => v.status === 'active')?.id ??
        versions.find((v) => v.status === 'obsolete')?.id
      const res = await createRecipeDraftApi(detail.id, from ? { fromVersionId: from } : {})
      toast.success('已创建草稿')
      const vers = await listRecipeVersionsApi(detail.id)
      setVersions(vers)
      setSelectedVersionId(String(res.versionId))
      const vd = await getRecipeVersionApi(res.versionId)
      setVersionDetail(vd)
      setDraftBody(vd.bodyJson ?? '')
      setDraftRemark(vd.remark ?? '')
      const full = await getRecipeApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '创建草稿失败')
    } finally {
      setCreatingDraft(false)
    }
  }

  async function saveDraft() {
    if (!versionDetail || versionDetail.status !== 'draft') return
    setSavingDraft(true)
    try {
      await updateRecipeDraftApi(versionDetail.id, {
        bodyJson: draftBody.trim() || undefined,
        remark: draftRemark.trim() || undefined,
      })
      toast.success('草稿已保存')
      const vd = await getRecipeVersionApi(versionDetail.id)
      setVersionDetail(vd)
      setDraftBody(vd.bodyJson ?? '')
      setDraftRemark(vd.remark ?? '')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '保存草稿失败')
    } finally {
      setSavingDraft(false)
    }
  }

  async function publishDraft() {
    if (!versionDetail || !detail) return
    const ok = await confirm({
      title: '发布配方版本',
      message: `确认发布 ${detail.recipeCode} v${versionDetail.versionNo}？当前生效版将变为停用。`,
      confirmText: '发布',
    })
    if (!ok) return
    setPublishing(true)
    try {
      await publishRecipeVersionApi(versionDetail.id)
      toast.success('已发布')
      const vers = await listRecipeVersionsApi(detail.id)
      setVersions(vers)
      const vd = await getRecipeVersionApi(versionDetail.id)
      setVersionDetail(vd)
      const full = await getRecipeApi(detail.id)
      setDetail(full)
      setFlashId(String(detail.id))
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '发布失败')
    } finally {
      setPublishing(false)
    }
  }

  async function openBindCreate() {
    setBindForm({ stepId: '', bindMode: 'eqp', eqpId: '', eqpType: '', recipeId: '' })
    setBindError('')
    setBindOpen(true)
    try {
      const [steps, eqps, recipes] = await Promise.all([
        listStepsApi({ page: 1, size: 200 }),
        listEqpOptionsApi(),
        listRecipesApi({ enabled: 1, page: 1, size: 200 }),
      ])
      setStepOptions(steps.records ?? [])
      setEqpOptions(eqps ?? [])
      setRecipeOptions(recipes.records ?? [])
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载选项失败')
    }
  }

  async function submitBind() {
    if (!bindForm.stepId) {
      setBindError('请选择工序')
      return
    }
    if (!bindForm.recipeId) {
      setBindError('请选择配方')
      return
    }
    if (bindForm.bindMode === 'eqp' && !bindForm.eqpId) {
      setBindError('请选择设备')
      return
    }
    if (bindForm.bindMode === 'type' && !bindForm.eqpType.trim()) {
      setBindError('请填写设备类型')
      return
    }
    setSavingBind(true)
    setBindError('')
    try {
      const created = await createRecipeBindingApi({
        stepId: bindForm.stepId,
        recipeId: bindForm.recipeId,
        eqpId: bindForm.bindMode === 'eqp' ? bindForm.eqpId : undefined,
        eqpType: bindForm.bindMode === 'type' ? bindForm.eqpType.trim() : undefined,
      })
      toast.success('绑定已创建')
      setBindFlashId(String(created.id))
      setBindOpen(false)
      setBindPage(1)
      await loadBindings()
    } catch (err) {
      setBindError(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setSavingBind(false)
    }
  }

  async function removeBind(row: MesRecipeBindingItem) {
    const ok = await confirm({
      title: '删除绑定',
      message: `确认删除 ${row.stepCode ?? row.stepId} → ${row.recipeCode ?? row.recipeId}？`,
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    try {
      await deleteRecipeBindingApi(row.id)
      toast.success('已删除')
      await loadBindings()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">配方管理</h1>
        <p className="text-sm text-muted">无权限查看配方（需要 recipe:view）</p>
      </div>
    )
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="rcp-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">配方管理</h1>
          <p className="mt-1 text-sm text-muted">主数据 · 版本 · Step×Eqp 绑定</p>
        </div>
        {tab === 'recipes' && canEdit ? (
          <Button
            onClick={() => {
              setCreateForm(emptyCreate)
              setCreateError('')
              setCreateOpen(true)
            }}
          >
            <Plus className="size-4" aria-hidden />
            新建配方
          </Button>
        ) : null}
        {tab === 'bindings' && canBind ? (
          <Button onClick={() => void openBindCreate()}>
            <Plus className="size-4" aria-hidden />
            新建绑定
          </Button>
        ) : null}
      </header>

      <div className="rcp-block flex gap-1.5">
        {(
          [
            { key: 'recipes' as const, label: '配方', icon: FlaskConical },
            { key: 'bindings' as const, label: '绑定', icon: Link2 },
          ] as const
        ).map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={cn(
              'inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-md border px-3 text-sm font-medium transition-colors duration-150',
              tab === t.key
                ? 'border-primary bg-primary text-white'
                : 'border-border bg-bg text-muted hover:bg-surface',
            )}
          >
            <t.icon className="size-4" aria-hidden />
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'recipes' ? (
        <>
          <div className="rcp-block flex flex-wrap items-center gap-2">
            <input
              className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
              placeholder="编码 / 名称"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setPage(1)
                  setKeyword(q.trim())
                }
              }}
              aria-label="搜索配方"
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
            <div className="flex flex-wrap gap-1.5">
              {(
                [
                  { key: 'all' as const, label: '启停' },
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

          <div className="rcp-block overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
                <tr className="h-10 border-b border-border">
                  <th className="px-3">编码</th>
                  <th className="px-3">名称</th>
                  <th className="px-3">生效版</th>
                  <th className="px-3">启用</th>
                  <th className="px-3">更新</th>
                  <th className="px-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={6} className="px-3 py-8 text-center text-muted">
                      加载中…
                    </td>
                  </tr>
                ) : loadFailed ? (
                  <tr>
                    <td colSpan={6} className="px-3 py-8 text-center text-muted">
                      加载失败，请刷新重试
                    </td>
                  </tr>
                ) : rows.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-3 py-10 text-center">
                      <p className="text-sm text-muted">暂无配方</p>
                      {canEdit ? (
                        <button
                          type="button"
                          className="mt-2 cursor-pointer text-sm text-accent hover:underline"
                          onClick={() => {
                            setCreateForm(emptyCreate)
                            setCreateError('')
                            setCreateOpen(true)
                          }}
                        >
                          创建第一个配方
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ) : (
                  rows.map((row) => (
                    <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                      <td className="h-10 px-3 font-mono text-[13px] text-accent">{row.recipeCode}</td>
                      <td className="px-3">{row.recipeName}</td>
                      <td className="px-3 font-mono text-[13px]">
                        {row.activeVersionNo != null ? `v${row.activeVersionNo}` : '—'}
                      </td>
                      <td className="px-3">
                        <span
                          className={cn(
                            'inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium',
                            row.enabled === 1 ? 'text-ink' : 'text-muted',
                          )}
                        >
                          <span
                            className={cn(
                              'size-1.5 rounded-full',
                              row.enabled === 1 ? 'bg-success' : 'bg-border',
                            )}
                            aria-hidden
                          />
                          {row.enabled === 1 ? '启用' : '停用'}
                        </span>
                      </td>
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

          <div className="rcp-block flex items-center justify-between text-sm text-muted">
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
        </>
      ) : (
        <>
          <div className="rcp-block overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[920px] text-left text-sm">
              <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
                <tr className="h-10 border-b border-border">
                  <th className="px-3">工序</th>
                  <th className="px-3">设备</th>
                  <th className="px-3">类型</th>
                  <th className="px-3">配方</th>
                  <th className="px-3">版本</th>
                  <th className="px-3">启用</th>
                  <th className="px-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {bindLoading ? (
                  <tr>
                    <td colSpan={7} className="px-3 py-8 text-center text-muted">
                      加载中…
                    </td>
                  </tr>
                ) : bindFailed ? (
                  <tr>
                    <td colSpan={7} className="px-3 py-8 text-center text-muted">
                      加载失败，请刷新重试
                    </td>
                  </tr>
                ) : bindRows.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-3 py-10 text-center">
                      <p className="text-sm text-muted">暂无绑定</p>
                      {canBind ? (
                        <button
                          type="button"
                          className="mt-2 cursor-pointer text-sm text-accent hover:underline"
                          onClick={() => void openBindCreate()}
                        >
                          创建第一条绑定
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ) : (
                  bindRows.map((row) => (
                    <FlashRow key={String(row.id)} active={bindFlashId === String(row.id)}>
                      <td className="h-10 px-3">
                        <span className="font-mono text-[13px] text-accent">{row.stepCode || row.stepId}</span>
                        {row.stepName ? <span className="ml-2 text-muted">{row.stepName}</span> : null}
                      </td>
                      <td className="px-3 font-mono text-[13px]">{row.eqpCode || '—'}</td>
                      <td className="px-3 font-mono text-[13px]">{row.eqpType || '—'}</td>
                      <td className="px-3 font-mono text-[13px] text-accent">{row.recipeCode || row.recipeId}</td>
                      <td className="px-3 font-mono text-[13px]">
                        {row.recipeVersionNo != null ? `v${row.recipeVersionNo}` : '跟 active'}
                      </td>
                      <td className="px-3">{row.enabled === 1 ? '启用' : '停用'}</td>
                      <td className="px-3">
                        {canBind ? (
                          <TableAction
                            icon={Trash2}
                            label="删除"
                            tone="danger"
                            onClick={() => void removeBind(row)}
                          />
                        ) : (
                          '—'
                        )}
                      </td>
                    </FlashRow>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="rcp-block flex items-center justify-between text-sm text-muted">
            <span>
              共 {bindTotal} 条 · 第 {bindPage}/{bindTotalPages} 页
            </span>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                disabled={bindPage <= 1}
                onClick={() => setBindPage((p) => Math.max(1, p - 1))}
              >
                上一页
              </Button>
              <Button
                variant="secondary"
                disabled={bindPage >= bindTotalPages}
                onClick={() => setBindPage((p) => p + 1)}
              >
                下一页
              </Button>
            </div>
          </div>
        </>
      )}

      <Drawer open={createOpen} onClose={() => setCreateOpen(false)} title="新建配方" width={420}>
        <div className="space-y-3">
          <Field
            label="编码"
            name="recipeCode"
            required
            className="font-mono"
            value={createForm.recipeCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, recipeCode: e.target.value }))}
          />
          <Field
            label="名称"
            name="recipeName"
            required
            value={createForm.recipeName}
            onChange={(e) => setCreateForm((f) => ({ ...f, recipeName: e.target.value }))}
          />
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
              创建
            </Button>
          </div>
        </div>
      </Drawer>

      <Drawer
        open={!!detail}
        onClose={() => setDetail(null)}
        title={detail ? detail.recipeCode : '配方详情'}
        width={560}
      >
        {detail ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="text-base font-semibold">{detail.recipeName}</p>
                <p className="mt-0.5 font-mono text-xs text-muted">{detail.recipeCode}</p>
              </div>
              {canEdit ? (
                <Button variant="secondary" loading={toggling} onClick={() => void toggleEnabled()}>
                  <Power className="size-4" aria-hidden />
                  {detail.enabled === 1 ? '停用' : '启用'}
                </Button>
              ) : null}
            </div>

            {editing ? (
              <div className="space-y-3 rounded-md border border-border p-3">
                <Field
                  label="名称"
                  name="editRecipeName"
                  required
                  value={editForm.recipeName}
                  onChange={(e) => setEditForm((f) => ({ ...f, recipeName: e.target.value }))}
                />
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs font-medium text-muted">备注</span>
                  <textarea
                    className="min-h-[64px] w-full rounded-md border border-border bg-bg px-3 py-2 text-sm"
                    value={editForm.remark}
                    onChange={(e) => setEditForm((f) => ({ ...f, remark: e.target.value }))}
                  />
                </label>
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
              <div className="rounded-md border border-border p-3 text-sm">
                <dl className="grid grid-cols-[88px_1fr] gap-y-2">
                  <dt className="text-muted">备注</dt>
                  <dd>{detail.remark || '—'}</dd>
                  <dt className="text-muted">生效版</dt>
                  <dd className="font-mono">
                    {detail.activeVersionNo != null ? `v${detail.activeVersionNo}` : '—'}
                  </dd>
                </dl>
                {canEdit ? (
                  <Button className="mt-3" variant="secondary" onClick={() => setEditing(true)}>
                    编辑主数据
                  </Button>
                ) : null}
              </div>
            )}

            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-sm font-semibold">版本</h3>
                {canEdit && !hasDraft ? (
                  <Button variant="secondary" loading={creatingDraft} onClick={() => void createDraft()}>
                    <Plus className="size-4" aria-hidden />
                    新建草稿
                  </Button>
                ) : null}
              </div>
              {detailLoading && !versions.length ? (
                <p className="text-sm text-muted">加载中…</p>
              ) : versions.length === 0 ? (
                <p className="text-sm text-muted">尚无版本，可新建草稿</p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {versions.map((v) => (
                    <button
                      key={String(v.id)}
                      type="button"
                      onClick={() => void selectVersion(String(v.id))}
                      className={cn(
                        'inline-flex h-8 cursor-pointer items-center gap-2 rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
                        selectedVersionId === String(v.id)
                          ? 'border-primary bg-primary/10 text-ink'
                          : 'border-border bg-bg text-muted hover:bg-surface',
                      )}
                    >
                      <span className="font-mono">v{v.versionNo}</span>
                      <RecipeVersionPill status={v.status} />
                    </button>
                  ))}
                </div>
              )}

              {versionDetail ? (
                <div className="space-y-3 rounded-md border border-border p-3">
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-mono font-medium">v{versionDetail.versionNo}</span>
                    <RecipeVersionPill status={versionDetail.status} />
                    <span className="text-muted">发布 {fmtTime(versionDetail.publishedAt)}</span>
                  </div>
                  {isDraft && canEdit ? (
                    <>
                      <label className="flex flex-col gap-1.5 text-sm">
                        <span className="text-xs font-medium text-muted">参数 JSON</span>
                        <textarea
                          className="min-h-[120px] w-full rounded-md border border-border bg-bg px-3 py-2 font-mono text-[12px]"
                          value={draftBody}
                          onChange={(e) => setDraftBody(e.target.value)}
                          placeholder='{"temp":300}'
                        />
                      </label>
                      <Field
                        label="备注"
                        name="draftRemark"
                        value={draftRemark}
                        onChange={(e) => setDraftRemark(e.target.value)}
                      />
                      <div className="flex flex-wrap justify-end gap-2">
                        <Button variant="secondary" loading={savingDraft} onClick={() => void saveDraft()}>
                          保存草稿
                        </Button>
                        {canPublish ? (
                          <Button loading={publishing} onClick={() => void publishDraft()}>
                            发布
                          </Button>
                        ) : null}
                      </div>
                    </>
                  ) : (
                    <pre className="max-h-48 overflow-auto rounded-md bg-surface p-3 font-mono text-[12px] text-ink whitespace-pre-wrap">
                      {versionDetail.bodyJson || '（无参数）'}
                    </pre>
                  )}
                </div>
              ) : null}
            </div>
          </div>
        ) : null}
      </Drawer>

      <Drawer open={bindOpen} onClose={() => setBindOpen(false)} title="新建绑定" width={420}>
        <div className="space-y-3">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">工序</span>
            <select
              className="h-9 w-full rounded-md border border-border bg-bg px-3 text-sm"
              value={bindForm.stepId}
              onChange={(e) => setBindForm((f) => ({ ...f, stepId: e.target.value }))}
            >
              <option value="">选择工序</option>
              {stepOptions.map((s) => (
                <option key={String(s.id)} value={String(s.id)}>
                  {s.stepCode} · {s.stepName}
                </option>
              ))}
            </select>
          </label>
          <div className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">绑定方式</span>
            <div className="flex gap-1.5">
              {(
                [
                  { key: 'eqp' as const, label: '具体设备' },
                  { key: 'type' as const, label: '设备类型' },
                ] as const
              ).map((m) => (
                <button
                  key={m.key}
                  type="button"
                  onClick={() => setBindForm((f) => ({ ...f, bindMode: m.key }))}
                  className={cn(
                    'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium',
                    bindForm.bindMode === m.key
                      ? 'border-primary bg-primary text-white'
                      : 'border-border bg-bg text-muted',
                  )}
                >
                  {m.label}
                </button>
              ))}
            </div>
          </div>
          {bindForm.bindMode === 'eqp' ? (
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted">设备</span>
              <select
                className="h-9 w-full rounded-md border border-border bg-bg px-3 text-sm"
                value={bindForm.eqpId}
                onChange={(e) => setBindForm((f) => ({ ...f, eqpId: e.target.value }))}
              >
                <option value="">选择设备</option>
                {eqpOptions.map((e) => (
                  <option key={String(e.id)} value={String(e.id)}>
                    {e.eqpCode} · {e.eqpName}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <Field
              label="设备类型"
              name="eqpType"
              required
              className="font-mono"
              value={bindForm.eqpType}
              onChange={(e) => setBindForm((f) => ({ ...f, eqpType: e.target.value }))}
              placeholder="如 ETCH"
            />
          )}
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">配方</span>
            <select
              className="h-9 w-full rounded-md border border-border bg-bg px-3 text-sm"
              value={bindForm.recipeId}
              onChange={(e) => setBindForm((f) => ({ ...f, recipeId: e.target.value }))}
            >
              <option value="">选择配方</option>
              {recipeOptions.map((r) => (
                <option key={String(r.id)} value={String(r.id)}>
                  {r.recipeCode} · {r.recipeName}
                </option>
              ))}
            </select>
          </label>
          {bindError ? <p className="text-sm text-danger">{bindError}</p> : null}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setBindOpen(false)}>
              取消
            </Button>
            <Button loading={savingBind} onClick={() => void submitBind()}>
              创建
            </Button>
          </div>
        </div>
      </Drawer>
    </div>
  )
}
