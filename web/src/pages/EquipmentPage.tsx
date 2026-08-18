import { useCallback, useEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import { Cpu, Eye, Pencil, Plus, Power, Search } from 'lucide-react'
import {
  createEqpApi,
  getEqpApi,
  listEqpsApi,
  updateEqpApi,
  updateEqpEnabledApi,
  updateEqpStatusApi,
  type MesEqpItem,
  type MesEqpStatus,
} from '../api/eqp'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { MesEqpStatusPill, mesEqpStatusLabel } from '../components/ui/StatusPill'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

const STATUS_FILTERS: Array<{ key: MesEqpStatus | 'all'; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'idle', label: mesEqpStatusLabel.idle.label },
  { key: 'running', label: mesEqpStatusLabel.running.label },
  { key: 'down', label: mesEqpStatusLabel.down.label },
  { key: 'pm', label: mesEqpStatusLabel.pm.label },
  { key: 'eng', label: mesEqpStatusLabel.eng.label },
  { key: 'offline', label: mesEqpStatusLabel.offline.label },
]

const STATUS_OPTIONS: MesEqpStatus[] = ['idle', 'running', 'down', 'pm', 'eng', 'offline']

const emptyCreate = {
  eqpCode: '',
  eqpName: '',
  eqpType: '',
  area: '',
  remark: '',
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

export function EquipmentPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)

  const [rows, setRows] = useState<MesEqpItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<MesEqpStatus | 'all'>('all')
  const [enabledFilter, setEnabledFilter] = useState<0 | 1 | 'all'>('all')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreate)
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [detail, setDetail] = useState<MesEqpItem | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({
    eqpName: '',
    eqpType: '',
    area: '',
    remark: '',
  })
  const [editError, setEditError] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [statusDraft, setStatusDraft] = useState<MesEqpStatus>('idle')
  const [savingStatus, setSavingStatus] = useState(false)
  const [toggling, setToggling] = useState(false)

  const size = 20
  const canView = hasPermission('eqp:list')
  const canAdd = hasPermission('eqp:add')
  const canEdit = hasPermission('eqp:edit')
  const canStatus = hasPermission('eqp:status')
  const totalPages = Math.max(1, Math.ceil(total / size))

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listEqpsApi({
        keyword,
        status: statusFilter === 'all' ? '' : statusFilter,
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
      toast.error(err instanceof ApiError ? err.message : '设备加载失败')
    } finally {
      setLoading(false)
    }
  }, [enabledFilter, keyword, page, statusFilter, toast])

  useEffect(() => {
    if (!canView) return
    void loadList()
  }, [canView, loadList])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.eqp-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [])

  function openCreate() {
    setCreateForm(emptyCreate)
    setCreateError('')
    setCreateOpen(true)
  }

  async function openDetail(row: MesEqpItem) {
    setDetail(row)
    setEditing(false)
    setEditError('')
    setStatusDraft(row.status)
    setDetailLoading(true)
    try {
      const full = await getEqpApi(row.id)
      setDetail(full)
      setStatusDraft(full.status)
      setEditForm({
        eqpName: full.eqpName ?? '',
        eqpType: full.eqpType ?? '',
        area: full.area ?? '',
        remark: full.remark ?? '',
      })
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    if (!createForm.eqpCode.trim()) {
      setCreateError('设备编码不能为空')
      return
    }
    if (!createForm.eqpName.trim()) {
      setCreateError('设备名称不能为空')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const created = await createEqpApi({
        eqpCode: createForm.eqpCode.trim(),
        eqpName: createForm.eqpName.trim(),
        eqpType: createForm.eqpType.trim() || undefined,
        area: createForm.area.trim() || undefined,
        remark: createForm.remark.trim() || undefined,
      })
      toast.success(`已创建 ${created.eqpCode}`)
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
    if (!editForm.eqpName.trim()) {
      setEditError('设备名称不能为空')
      return
    }
    setSavingEdit(true)
    setEditError('')
    try {
      await updateEqpApi(detail.id, {
        eqpName: editForm.eqpName.trim(),
        eqpType: editForm.eqpType.trim() || undefined,
        area: editForm.area.trim() || undefined,
        remark: editForm.remark.trim() || undefined,
      })
      toast.success('已保存')
      setFlashId(String(detail.id))
      setEditing(false)
      const full = await getEqpApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingEdit(false)
    }
  }

  async function submitStatus() {
    if (!detail) return
    if (statusDraft === detail.status) return
    setSavingStatus(true)
    try {
      await updateEqpStatusApi(detail.id, statusDraft)
      toast.success(`状态 → ${mesEqpStatusLabel[statusDraft]?.label ?? statusDraft}`)
      setFlashId(String(detail.id))
      const full = await getEqpApi(detail.id)
      setDetail(full)
      setStatusDraft(full.status)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '改态失败')
    } finally {
      setSavingStatus(false)
    }
  }

  async function toggleEnabled() {
    if (!detail) return
    const next = detail.enabled === 1 ? 0 : 1
    const ok = await confirm({
      title: next === 1 ? '启用设备' : '停用设备',
      message:
        next === 1
          ? `确认启用 ${detail.eqpCode}？启用后 idle/running 可供开工。`
          : `确认停用 ${detail.eqpCode}？停用后禁止 TrackIn。`,
      confirmText: next === 1 ? '启用' : '停用',
      danger: next === 0,
    })
    if (!ok) return
    setToggling(true)
    try {
      await updateEqpEnabledApi(detail.id, next)
      toast.success(next === 1 ? '已启用' : '已停用')
      setFlashId(String(detail.id))
      const full = await getEqpApi(detail.id)
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
        <h1 className="text-xl font-semibold tracking-tight">设备管理</h1>
        <p className="text-sm text-muted">无权限查看设备（需要 eqp:list）</p>
      </div>
    )
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="eqp-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">设备管理</h1>
          <p className="mt-1 text-sm text-muted">主数据 · 业务态 · TrackIn 可用性</p>
        </div>
        {canAdd ? (
          <Button onClick={openCreate}>
            <Plus className="size-4" aria-hidden />
            新建设备
          </Button>
        ) : null}
      </header>

      <div className="eqp-block flex flex-wrap items-center gap-2">
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
          aria-label="搜索设备"
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
          {STATUS_FILTERS.map((s) => (
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

      <div className="eqp-block overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[880px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">设备号</th>
              <th className="px-3">名称</th>
              <th className="px-3">类型</th>
              <th className="px-3">区域</th>
              <th className="px-3">状态</th>
              <th className="px-3">启用</th>
              <th className="px-3">更新</th>
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
                  <p className="text-sm text-muted">暂无设备</p>
                  {canAdd ? (
                    <button
                      type="button"
                      className="mt-2 cursor-pointer text-sm text-accent hover:underline"
                      onClick={openCreate}
                    >
                      创建第一台设备
                    </button>
                  ) : null}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                  <td className="h-10 px-3 font-mono text-[13px] text-accent">{row.eqpCode}</td>
                  <td className="px-3">{row.eqpName}</td>
                  <td className="px-3 font-mono text-[13px]">{row.eqpType || '—'}</td>
                  <td className="px-3">{row.area || '—'}</td>
                  <td className="px-3">
                    <MesEqpStatusPill status={row.status} />
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

      <div className="eqp-block flex items-center justify-between text-sm text-muted">
        <span>
          共 {total} 台 · 第 {page}/{totalPages} 页
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

      <Drawer open={createOpen} onClose={() => setCreateOpen(false)} title="新建设备" width={420}>
        <div className="space-y-4">
          <Field
            label="设备编码"
            name="eqpCode"
            required
            className="font-mono"
            value={createForm.eqpCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, eqpCode: e.target.value }))}
            placeholder="EQP-ETCH-A1"
          />
          <Field
            label="设备名称"
            name="eqpName"
            required
            value={createForm.eqpName}
            onChange={(e) => setCreateForm((f) => ({ ...f, eqpName: e.target.value }))}
          />
          <Field
            label="类型"
            name="eqpType"
            className="font-mono"
            value={createForm.eqpType}
            onChange={(e) => setCreateForm((f) => ({ ...f, eqpType: e.target.value }))}
            placeholder="ETCH / CMP / PHOTO"
          />
          <Field
            label="区域"
            name="area"
            value={createForm.area}
            onChange={(e) => setCreateForm((f) => ({ ...f, area: e.target.value }))}
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
              <Cpu className="size-4" aria-hidden />
              创建
            </Button>
          </div>
        </div>
      </Drawer>

      <Drawer
        open={detail != null}
        onClose={() => setDetail(null)}
        title={detail ? detail.eqpCode : '设备详情'}
        width={440}
      >
        {detailLoading && !detail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : detail ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <MesEqpStatusPill status={detail.status} />
              <span
                className={cn(
                  'inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium',
                  detail.enabled === 1 ? 'text-ink' : 'text-muted',
                )}
              >
                <span
                  className={cn(
                    'size-1.5 rounded-full',
                    detail.enabled === 1 ? 'bg-success' : 'bg-border',
                  )}
                  aria-hidden
                />
                {detail.enabled === 1 ? '启用' : '停用'}
              </span>
            </div>

            {editing ? (
              <div className="space-y-3">
                <Field
                  label="设备名称"
                  name="editEqpName"
                  required
                  value={editForm.eqpName}
                  onChange={(e) => setEditForm((f) => ({ ...f, eqpName: e.target.value }))}
                />
                <Field
                  label="类型"
                  name="editEqpType"
                  className="font-mono"
                  value={editForm.eqpType}
                  onChange={(e) => setEditForm((f) => ({ ...f, eqpType: e.target.value }))}
                />
                <Field
                  label="区域"
                  name="editArea"
                  value={editForm.area}
                  onChange={(e) => setEditForm((f) => ({ ...f, area: e.target.value }))}
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
                  <dt className="text-xs text-muted">名称</dt>
                  <dd className="mt-0.5">{detail.eqpName}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">类型</dt>
                  <dd className="mt-0.5 font-mono text-[13px]">{detail.eqpType || '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">区域</dt>
                  <dd className="mt-0.5">{detail.area || '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted">版本</dt>
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
                编辑主数据
              </Button>
            ) : null}

            {canStatus ? (
              <div className="space-y-2 border-t border-border pt-4">
                <p className="text-xs font-medium text-muted">业务态</p>
                <div className="flex flex-wrap gap-2">
                  <select
                    className="h-9 min-w-[140px] cursor-pointer rounded-md border border-border bg-bg px-3 text-sm"
                    value={statusDraft}
                    onChange={(e) => setStatusDraft(e.target.value as MesEqpStatus)}
                    aria-label="业务状态"
                  >
                    {STATUS_OPTIONS.map((s) => (
                      <option key={s} value={s}>
                        {mesEqpStatusLabel[s].label}
                      </option>
                    ))}
                  </select>
                  <Button
                    loading={savingStatus}
                    disabled={statusDraft === detail.status}
                    onClick={() => void submitStatus()}
                  >
                    改态
                  </Button>
                </div>
                <p className="text-xs text-muted">仅 idle / running 且启用可 TrackIn</p>
              </div>
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
