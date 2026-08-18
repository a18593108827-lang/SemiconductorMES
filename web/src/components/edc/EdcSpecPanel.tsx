import { useCallback, useEffect, useState } from 'react'
import { Eye, Pencil, Plus, Rocket, Search } from 'lucide-react'
import {
  createEdcSpecApi,
  getEdcSpecApi,
  listEdcParamsApi,
  listEdcSpecsApi,
  publishEdcSpecApi,
  updateEdcSpecApi,
  type EdcSpecStatus,
  type MesEdcParamItem,
  type MesEdcSpecItem,
} from '../../api/edc'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../ui/Button'
import { useConfirm } from '../ui/ConfirmDialog'
import { Drawer } from '../ui/Drawer'
import { Field } from '../ui/Field'
import { FlashRow } from '../ui/FlashRow'
import { RecipeVersionPill } from '../ui/StatusPill'
import { TableAction } from '../ui/TableAction'
import { useToast } from '../ui/Toast'
import { ApiError } from '../../lib/http'
import { cn } from '../../lib/cn'

const emptyCreate = {
  paramId: '',
  productCode: '',
  usl: '',
  lsl: '',
  target: '',
  remark: '',
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function fmtNum(v: number | string | null | undefined) {
  if (v === null || v === undefined || v === '') return '—'
  return String(v)
}

function optNum(v: string): string | undefined {
  const t = v.trim()
  return t ? t : undefined
}

type Props = {
  onRequestCreate?: (open: () => void) => void
}

export function EdcSpecPanel({ onRequestCreate }: Props) {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()

  const [rows, setRows] = useState<MesEdcSpecItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [paramFilter, setParamFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<EdcSpecStatus | 'all'>('all')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [paramOptions, setParamOptions] = useState<MesEdcParamItem[]>([])

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreate)
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [detail, setDetail] = useState<MesEdcSpecItem | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({ usl: '', lsl: '', target: '', remark: '' })
  const [editError, setEditError] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [publishing, setPublishing] = useState(false)

  const size = 20
  const canEdit = hasPermission('edc:edit')
  const canPublish = hasPermission('edc:publish')
  const totalPages = Math.max(1, Math.ceil(total / size))

  const openCreate = useCallback(() => {
    setCreateForm(emptyCreate)
    setCreateError('')
    setCreateOpen(true)
  }, [])

  useEffect(() => {
    onRequestCreate?.(openCreate)
  }, [onRequestCreate, openCreate])

  const loadParams = useCallback(async () => {
    try {
      const data = await listEdcParamsApi({ enabled: 1, page: 1, size: 200 })
      setParamOptions(data.records ?? [])
    } catch {
      setParamOptions([])
    }
  }, [])

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listEdcSpecsApi({
        paramId: paramFilter || undefined,
        status: statusFilter === 'all' ? '' : statusFilter,
        page,
        size,
      })
      setRows(data.records ?? [])
      setTotal(data.total ?? 0)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '规格加载失败')
    } finally {
      setLoading(false)
    }
  }, [paramFilter, statusFilter, page, toast])

  useEffect(() => {
    void loadList()
  }, [loadList])

  useEffect(() => {
    void loadParams()
  }, [loadParams])

  async function openDetail(row: MesEdcSpecItem) {
    setDetail(row)
    setEditing(false)
    setEditError('')
    setDetailLoading(true)
    try {
      const full = await getEdcSpecApi(row.id)
      setDetail(full)
      setEditForm({
        usl: full.usl != null ? String(full.usl) : '',
        lsl: full.lsl != null ? String(full.lsl) : '',
        target: full.target != null ? String(full.target) : '',
        remark: full.remark ?? '',
      })
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    if (!createForm.paramId) {
      setCreateError('请选择特性')
      return
    }
    if (!createForm.usl.trim() && !createForm.lsl.trim()) {
      setCreateError('上限与下限不能同时为空')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const created = await createEdcSpecApi({
        paramId: createForm.paramId,
        productCode: createForm.productCode.trim() || undefined,
        usl: optNum(createForm.usl),
        lsl: optNum(createForm.lsl),
        target: optNum(createForm.target),
        remark: createForm.remark.trim() || undefined,
      })
      toast.success(`已创建草稿 v${created.versionNo}`)
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
    if (!editForm.usl.trim() && !editForm.lsl.trim()) {
      setEditError('上限与下限不能同时为空')
      return
    }
    setSavingEdit(true)
    setEditError('')
    try {
      await updateEdcSpecApi(detail.id, {
        usl: optNum(editForm.usl),
        lsl: optNum(editForm.lsl),
        target: optNum(editForm.target),
        remark: editForm.remark.trim() || undefined,
      })
      toast.success('草稿已保存')
      setFlashId(String(detail.id))
      setEditing(false)
      const full = await getEdcSpecApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingEdit(false)
    }
  }

  async function publish() {
    if (!detail) return
    const label = `${detail.paramCode ?? detail.paramId} v${detail.versionNo}`
    const ok = await confirm({
      title: '发布规格',
      message: `确认发布 ${label}？同特性+产品下当前生效版将变为停用。`,
      confirmText: '发布',
    })
    if (!ok) return
    setPublishing(true)
    try {
      await publishEdcSpecApi(detail.id)
      toast.success('已发布')
      setFlashId(String(detail.id))
      const full = await getEdcSpecApi(detail.id)
      setDetail(full)
      setEditing(false)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '发布失败')
    } finally {
      setPublishing(false)
    }
  }

  return (
    <>
      <div className="edc-block flex flex-wrap items-center gap-2">
        <select
          className="h-9 min-w-[180px] cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
          value={paramFilter}
          onChange={(e) => {
            setParamFilter(e.target.value)
            setPage(1)
          }}
          aria-label="按特性筛选"
        >
          <option value="">全部特性</option>
          {paramOptions.map((p) => (
            <option key={String(p.id)} value={String(p.id)}>
              {p.paramCode} · {p.paramName}
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
              { key: 'all' as const, label: '全部' },
              { key: 'draft' as const, label: '草稿' },
              { key: 'active' as const, label: '生效' },
              { key: 'obsolete' as const, label: '停用' },
            ] as const
          ).map((s) => (
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
        <table className="w-full min-w-[960px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">特性</th>
              <th className="px-3">产品</th>
              <th className="px-3">版本</th>
              <th className="px-3">状态</th>
              <th className="px-3">USL</th>
              <th className="px-3">LSL</th>
              <th className="px-3">发布</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 6 }).map((_, i) => (
                <tr key={i} className="h-10 border-b border-border/60">
                  {Array.from({ length: 8 }).map((__, j) => (
                    <td key={j} className="px-3">
                      <div className="h-3.5 w-[70%] max-w-[120px] animate-pulse rounded-sm bg-surface" />
                    </td>
                  ))}
                </tr>
              ))
            ) : loadFailed ? (
              <tr>
                <td colSpan={8} className="px-3 py-10 text-center">
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
                <td colSpan={8} className="px-3 py-12 text-center">
                  <div className="mx-auto flex max-w-sm flex-col items-center gap-2">
                    <p className="text-sm text-ink">还没有规格</p>
                    <p className="text-xs text-muted">先选特性建草稿，配好上下限再发布生效</p>
                    {canEdit ? (
                      <Button className="mt-2" onClick={openCreate}>
                        <Plus className="size-4" aria-hidden />
                        新建草稿
                      </Button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                  <td className="h-10 px-3">
                    <div className="font-mono text-[13px] text-accent">{row.paramCode ?? '—'}</div>
                    <div className="text-xs text-muted">{row.paramName ?? ''}</div>
                  </td>
                  <td className="px-3 font-mono text-[13px]">
                    {row.productCode ? row.productCode : <span className="text-muted">全产品</span>}
                  </td>
                  <td className="px-3 font-mono text-[13px]">v{row.versionNo}</td>
                  <td className="px-3">
                    <RecipeVersionPill status={row.status} />
                  </td>
                  <td className="px-3 font-mono text-[13px]">{fmtNum(row.usl)}</td>
                  <td className="px-3 font-mono text-[13px]">{fmtNum(row.lsl)}</td>
                  <td className="px-3 font-mono text-[12px] text-muted">{fmtTime(row.publishedAt)}</td>
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

      <Drawer open={createOpen} onClose={() => setCreateOpen(false)} title="新建规格草稿" width={440}>
        <div className="space-y-4">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">
              特性 <span className="text-danger">*</span>
            </span>
            <select
              className="h-9 cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
              value={createForm.paramId}
              onChange={(e) => setCreateForm((f) => ({ ...f, paramId: e.target.value }))}
            >
              <option value="">请选择</option>
              {paramOptions.map((p) => (
                <option key={String(p.id)} value={String(p.id)}>
                  {p.paramCode} · {p.paramName}
                  {p.unit ? ` (${p.unit})` : ''}
                </option>
              ))}
            </select>
          </label>
          <Field
            label="产品编码"
            name="productCode"
            className="font-mono"
            value={createForm.productCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, productCode: e.target.value }))}
            placeholder="空=全产品默认"
          />
          <div className="grid grid-cols-2 gap-3">
            <Field
              label="上限 USL"
              name="usl"
              className="font-mono"
              value={createForm.usl}
              onChange={(e) => setCreateForm((f) => ({ ...f, usl: e.target.value }))}
              placeholder="可空"
            />
            <Field
              label="下限 LSL"
              name="lsl"
              className="font-mono"
              value={createForm.lsl}
              onChange={(e) => setCreateForm((f) => ({ ...f, lsl: e.target.value }))}
              placeholder="可空"
            />
          </div>
          <Field
            label="目标 Target"
            name="target"
            className="font-mono"
            value={createForm.target}
            onChange={(e) => setCreateForm((f) => ({ ...f, target: e.target.value }))}
            placeholder="门禁用不到，SPC 预留"
          />
          <p className="text-xs text-muted">上下限至少填一侧；两边都填时 USL 不能小于 LSL。</p>
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
              创建草稿
            </Button>
          </div>
        </div>
      </Drawer>

      <Drawer
        open={detail != null}
        onClose={() => setDetail(null)}
        title={
          detail
            ? `${detail.paramCode ?? '规格'} · v${detail.versionNo}`
            : '规格详情'
        }
        width={460}
      >
        {detailLoading && !detail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : detail ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <RecipeVersionPill status={detail.status} />
              <span className="font-mono text-xs text-muted">
                {detail.productCode ? detail.productCode : '全产品'}
              </span>
            </div>

            {editing ? (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <Field
                    label="上限 USL"
                    name="editUsl"
                    className="font-mono"
                    value={editForm.usl}
                    onChange={(e) => setEditForm((f) => ({ ...f, usl: e.target.value }))}
                  />
                  <Field
                    label="下限 LSL"
                    name="editLsl"
                    className="font-mono"
                    value={editForm.lsl}
                    onChange={(e) => setEditForm((f) => ({ ...f, lsl: e.target.value }))}
                  />
                </div>
                <Field
                  label="目标 Target"
                  name="editTarget"
                  className="font-mono"
                  value={editForm.target}
                  onChange={(e) => setEditForm((f) => ({ ...f, target: e.target.value }))}
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
                <div>
                  <dt className="text-xs text-muted">特性</dt>
                  <dd className="mt-0.5 font-mono text-[13px] text-accent">{detail.paramCode}</dd>
                  <dd className="text-xs text-muted">{detail.paramName}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">版本</dt>
                  <dd className="mt-0.5 font-mono text-[13px]">v{detail.versionNo}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">USL</dt>
                  <dd className="mt-0.5 font-mono text-[13px]">{fmtNum(detail.usl)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">LSL</dt>
                  <dd className="mt-0.5 font-mono text-[13px]">{fmtNum(detail.lsl)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">Target</dt>
                  <dd className="mt-0.5 font-mono text-[13px]">{fmtNum(detail.target)}</dd>
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
                  <dt className="text-xs text-muted">发布</dt>
                  <dd className="mt-0.5 font-mono text-[12px]">{fmtTime(detail.publishedAt)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">更新</dt>
                  <dd className="mt-0.5 font-mono text-[12px]">{fmtTime(detail.updateTime)}</dd>
                </div>
              </dl>
            )}

            {!editing && canEdit && detail.status === 'draft' ? (
              <Button variant="secondary" onClick={() => setEditing(true)}>
                <Pencil className="size-4" aria-hidden />
                编辑草稿
              </Button>
            ) : null}

            {canPublish && detail.status === 'draft' ? (
              <div className="border-t border-border pt-4">
                <Button loading={publishing} onClick={() => void publish()}>
                  <Rocket className="size-4" aria-hidden />
                  发布生效
                </Button>
              </div>
            ) : null}
          </div>
        ) : null}
      </Drawer>
    </>
  )
}
