import { useCallback, useEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import { Eye, Pencil, Plus, Power, Ruler, Search } from 'lucide-react'
import {
  createEdcParamApi,
  getEdcParamApi,
  listEdcParamsApi,
  updateEdcParamApi,
  updateEdcParamEnabledApi,
  type MesEdcParamItem,
} from '../api/edc'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { EdcPlanPanel } from '../components/edc/EdcPlanPanel'
import { EdcSpecPanel } from '../components/edc/EdcSpecPanel'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

type TabKey = 'params' | 'specs' | 'plans'

const emptyCreate = { paramCode: '', paramName: '', unit: '', remark: '' }

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
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

export function EdcPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)

  const [tab, setTab] = useState<TabKey>('params')

  const [rows, setRows] = useState<MesEdcParamItem[]>([])
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

  const [detail, setDetail] = useState<MesEdcParamItem | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({ paramName: '', unit: '', remark: '' })
  const [editError, setEditError] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [toggling, setToggling] = useState(false)
  const openSpecCreateRef = useRef<(() => void) | null>(null)
  const openPlanCreateRef = useRef<(() => void) | null>(null)

  const size = 20
  const canView = hasPermission('edc:view')
  const canEdit = hasPermission('edc:edit')
  const totalPages = Math.max(1, Math.ceil(total / size))

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listEdcParamsApi({
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
      toast.error(err instanceof ApiError ? err.message : '特性加载失败')
    } finally {
      setLoading(false)
    }
  }, [enabledFilter, keyword, page, toast])

  useEffect(() => {
    if (!canView || tab !== 'params') return
    void loadList()
  }, [canView, tab, loadList])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.edc-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [tab])

  function openCreate() {
    setCreateForm(emptyCreate)
    setCreateError('')
    setCreateOpen(true)
  }

  async function openDetail(row: MesEdcParamItem) {
    setDetail(row)
    setEditing(false)
    setEditError('')
    setDetailLoading(true)
    try {
      const full = await getEdcParamApi(row.id)
      setDetail(full)
      setEditForm({
        paramName: full.paramName ?? '',
        unit: full.unit ?? '',
        remark: full.remark ?? '',
      })
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    if (!createForm.paramCode.trim()) {
      setCreateError('特性编码不能为空')
      return
    }
    if (!createForm.paramName.trim()) {
      setCreateError('特性名称不能为空')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const created = await createEdcParamApi({
        paramCode: createForm.paramCode.trim(),
        paramName: createForm.paramName.trim(),
        unit: createForm.unit.trim() || undefined,
        remark: createForm.remark.trim() || undefined,
      })
      toast.success(`已创建 ${created.paramCode}`)
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
    if (!editForm.paramName.trim()) {
      setEditError('特性名称不能为空')
      return
    }
    setSavingEdit(true)
    setEditError('')
    try {
      await updateEdcParamApi(detail.id, {
        paramName: editForm.paramName.trim(),
        unit: editForm.unit.trim() || undefined,
        remark: editForm.remark.trim() || undefined,
      })
      toast.success('已保存')
      setFlashId(String(detail.id))
      setEditing(false)
      const full = await getEdcParamApi(detail.id)
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
      title: next === 1 ? '启用特性' : '停用特性',
      message:
        next === 1
          ? `确认启用 ${detail.paramCode}？启用后可挂到站计划。`
          : `确认停用 ${detail.paramCode}？已挂的 Spec / Plan 仍保留，新建计划请勿再选。`,
      confirmText: next === 1 ? '启用' : '停用',
      danger: next === 0,
    })
    if (!ok) return
    setToggling(true)
    try {
      await updateEdcParamEnabledApi(detail.id, next)
      toast.success(next === 1 ? '已启用' : '已停用')
      setFlashId(String(detail.id))
      const full = await getEdcParamApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '启停失败')
    } finally {
      setToggling(false)
    }
  }

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">量测 EDC</h1>
        <p className="text-sm text-muted">无权限查看（需要 edc:view）</p>
      </div>
    )
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="edc-block flex flex-wrap items-end justify-between gap-3">
        <div className="flex items-start gap-3">
          <div
            className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-accent"
            aria-hidden
          >
            <Ruler className="size-5" strokeWidth={1.75} />
          </div>
          <div>
            <h1 className="text-xl font-semibold tracking-tight">量测 EDC</h1>
            <p className="mt-1 text-sm text-muted">特性 · 规格 · 站计划</p>
          </div>
        </div>
        {tab === 'params' && canEdit ? (
          <Button onClick={openCreate}>
            <Plus className="size-4" aria-hidden />
            新建特性
          </Button>
        ) : null}
        {tab === 'specs' && canEdit ? (
          <Button onClick={() => openSpecCreateRef.current?.()}>
            <Plus className="size-4" aria-hidden />
            新建草稿
          </Button>
        ) : null}
        {tab === 'plans' && canEdit ? (
          <Button onClick={() => openPlanCreateRef.current?.()}>
            <Plus className="size-4" aria-hidden />
            新建计划
          </Button>
        ) : null}
      </header>

      <div
        className="edc-block flex gap-1 border-b border-border"
        role="tablist"
        aria-label="EDC 分区"
      >
        {(
          [
            { key: 'params' as const, label: '特性', ready: true },
            { key: 'specs' as const, label: '规格', ready: true },
            { key: 'plans' as const, label: '站计划', ready: true },
          ] as const
        ).map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            disabled={!t.ready}
            onClick={() => t.ready && setTab(t.key)}
            className={cn(
              'relative h-10 cursor-pointer px-3 text-sm font-medium transition-colors duration-150',
              t.ready
                ? tab === t.key
                  ? 'text-ink'
                  : 'text-muted hover:text-ink'
                : 'cursor-not-allowed text-muted/60',
            )}
          >
            {t.label}
            {!t.ready ? <span className="ml-1 text-[11px] font-normal">即将开放</span> : null}
            {tab === t.key && t.ready ? (
              <span className="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-primary" aria-hidden />
            ) : null}
          </button>
        ))}
      </div>

      {tab === 'params' ? (
        <>
          <div className="edc-block flex flex-wrap items-center gap-2">
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
              aria-label="搜索特性"
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
              {(
                [
                  { key: 'all' as const, label: '全部' },
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
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
                <tr className="h-10 border-b border-border">
                  <th className="px-3">编码</th>
                  <th className="px-3">名称</th>
                  <th className="px-3">单位</th>
                  <th className="px-3">类型</th>
                  <th className="px-3">状态</th>
                  <th className="px-3">更新</th>
                  <th className="px-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  Array.from({ length: 6 }).map((_, i) => (
                    <tr key={i} className="h-10 border-b border-border/60">
                      {Array.from({ length: 7 }).map((__, j) => (
                        <td key={j} className="px-3">
                          <div className="h-3.5 w-[72%] max-w-[140px] animate-pulse rounded-sm bg-surface" />
                        </td>
                      ))}
                    </tr>
                  ))
                ) : loadFailed ? (
                  <tr>
                    <td colSpan={7} className="px-3 py-10 text-center">
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
                    <td colSpan={7} className="px-3 py-12 text-center">
                      <div className="mx-auto flex max-w-sm flex-col items-center gap-2">
                        <div className="flex size-11 items-center justify-center rounded-md border border-border bg-surface text-muted">
                          <Ruler className="size-5" aria-hidden />
                        </div>
                        <p className="text-sm text-ink">还没有量测特性</p>
                        <p className="text-xs text-muted">先建编码（如 CD / THK），再配规格与站计划</p>
                        {canEdit ? (
                          <Button className="mt-2" onClick={openCreate}>
                            <Plus className="size-4" aria-hidden />
                            新建特性
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ) : (
                  rows.map((row) => (
                    <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                      <td className="h-10 px-3 font-mono text-[13px] text-accent">{row.paramCode}</td>
                      <td className="px-3">{row.paramName}</td>
                      <td className="px-3 font-mono text-[13px] text-muted">{row.unit || '—'}</td>
                      <td className="px-3">
                        <span className="inline-flex h-[22px] items-center rounded-sm border border-border bg-bg px-2 font-mono text-[11px] text-muted">
                          {row.valueType}
                        </span>
                      </td>
                      <td className="px-3">
                        <EnabledMark enabled={row.enabled} />
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

          <div className="edc-block flex items-center justify-between text-sm text-muted">
            <span>
              共 {total} 项 · 第 {page}/{totalPages} 页
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
      ) : null}

      {tab === 'specs' ? (
        <EdcSpecPanel
          onRequestCreate={(fn) => {
            openSpecCreateRef.current = fn
          }}
        />
      ) : null}

      {tab === 'plans' ? (
        <EdcPlanPanel
          onRequestCreate={(fn) => {
            openPlanCreateRef.current = fn
          }}
        />
      ) : null}

      <Drawer open={createOpen} onClose={() => setCreateOpen(false)} title="新建特性" width={420}>
        <div className="space-y-4">
          <Field
            label="特性编码"
            name="paramCode"
            required
            className="font-mono"
            value={createForm.paramCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, paramCode: e.target.value }))}
            placeholder="CD / THK / OVLY"
          />
          <Field
            label="特性名称"
            name="paramName"
            required
            value={createForm.paramName}
            onChange={(e) => setCreateForm((f) => ({ ...f, paramName: e.target.value }))}
            placeholder="关键尺寸"
          />
          <Field
            label="单位"
            name="unit"
            className="font-mono"
            value={createForm.unit}
            onChange={(e) => setCreateForm((f) => ({ ...f, unit: e.target.value }))}
            placeholder="nm / Å / um"
          />
          <p className="text-xs text-muted">值类型一期固定 NUMBER，建好后编码不可改。</p>
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
              <Ruler className="size-4" aria-hidden />
              创建
            </Button>
          </div>
        </div>
      </Drawer>

      <Drawer
        open={detail != null}
        onClose={() => setDetail(null)}
        title={detail ? detail.paramCode : '特性详情'}
        width={440}
      >
        {detailLoading && !detail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : detail ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <EnabledMark enabled={detail.enabled} />
              <span className="inline-flex h-[22px] items-center rounded-sm border border-border bg-bg px-2 font-mono text-[11px] text-muted">
                {detail.valueType}
              </span>
            </div>

            {editing ? (
              <div className="space-y-3">
                <Field
                  label="特性名称"
                  name="editParamName"
                  required
                  value={editForm.paramName}
                  onChange={(e) => setEditForm((f) => ({ ...f, paramName: e.target.value }))}
                />
                <Field
                  label="单位"
                  name="editUnit"
                  className="font-mono"
                  value={editForm.unit}
                  onChange={(e) => setEditForm((f) => ({ ...f, unit: e.target.value }))}
                />
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs font-medium text-muted">备注</span>
                  <textarea
                    className="min-h-[72px] w-full rounded-md border border-border bg-bg px-3 py-2 text-sm"
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
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div className="col-span-2">
                  <dt className="text-xs text-muted">名称</dt>
                  <dd className="mt-0.5">{detail.paramName}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">单位</dt>
                  <dd className="mt-0.5 font-mono text-[13px]">{detail.unit || '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">乐观锁</dt>
                  <dd className="mt-0.5 font-mono text-[13px]">{detail.version}</dd>
                </div>
                <div className="col-span-2">
                  <dt className="text-xs text-muted">备注</dt>
                  <dd className="mt-0.5">{detail.remark || '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">创建</dt>
                  <dd className="mt-0.5 font-mono text-[12px]">{fmtTime(detail.createTime)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">更新</dt>
                  <dd className="mt-0.5 font-mono text-[12px]">{fmtTime(detail.updateTime)}</dd>
                </div>
              </dl>
            )}

            {!editing && canEdit ? (
              <Button variant="secondary" onClick={() => setEditing(true)}>
                <Pencil className="size-4" aria-hidden />
                编辑
              </Button>
            ) : null}

            {canEdit ? (
              <div className="border-t border-border pt-4">
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
    </div>
  )
}
