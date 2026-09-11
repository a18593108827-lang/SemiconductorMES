import { useCallback, useEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import { Box, Link2, Link2Off, Pencil, Plus, Search } from 'lucide-react'
import {
  bindCarrierApi,
  changeCarrierStatusApi,
  createCarrierApi,
  getCarrierApi,
  listCarriersApi,
  unbindCarrierByIdApi,
  updateCarrierApi,
  type MesCarrierItem,
  type MesCarrierStatus,
} from '../api/carrier'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { MesCarrierStatusPill, mesCarrierStatusLabel } from '../components/ui/StatusPill'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

const STATUS_FILTERS: Array<{ key: MesCarrierStatus | 'all'; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'AVAILABLE', label: mesCarrierStatusLabel.AVAILABLE.label },
  { key: 'IN_USE', label: mesCarrierStatusLabel.IN_USE.label },
  { key: 'QUARANTINE', label: mesCarrierStatusLabel.QUARANTINE.label },
  { key: 'SCRAPPED', label: mesCarrierStatusLabel.SCRAPPED.label },
]

const STATUS_OPTIONS: MesCarrierStatus[] = ['AVAILABLE', 'QUARANTINE', 'SCRAPPED']

const emptyCreate = {
  carrierCode: '',
  carrierType: 'FOUP',
  capacity: '25',
  cleanStatus: 'UNKNOWN',
  locationType: 'NONE',
  locationRef: '',
  remark: '',
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function fmtLocation(row: MesCarrierItem) {
  const t = row.locationType && row.locationType !== 'NONE' ? row.locationType : null
  if (!t && !row.locationRef) return '—'
  if (t && row.locationRef) return `${t} · ${row.locationRef}`
  return t || row.locationRef || '—'
}

export function CarrierPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)

  const [rows, setRows] = useState<MesCarrierItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<MesCarrierStatus | 'all'>('all')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreate)
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [detail, setDetail] = useState<MesCarrierItem | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({
    capacity: '25',
    cleanStatus: 'UNKNOWN',
    locationType: 'NONE',
    locationRef: '',
    remark: '',
  })
  const [editError, setEditError] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [statusDraft, setStatusDraft] = useState<MesCarrierStatus>('AVAILABLE')
  const [savingStatus, setSavingStatus] = useState(false)

  const [bindLotRef, setBindLotRef] = useState('')
  const [binding, setBinding] = useState(false)
  const [unbinding, setUnbinding] = useState(false)

  const size = 20
  const canView = hasPermission('carrier:view')
  const canEdit = hasPermission('carrier:edit')
  const canBind = hasPermission('carrier:bind')
  const totalPages = Math.max(1, Math.ceil(total / size))

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listCarriersApi({
        keyword,
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
      toast.error(err instanceof ApiError ? err.message : '载具加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, page, statusFilter, toast])

  useEffect(() => {
    if (!canView) return
    void loadList()
  }, [canView, loadList])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.carrier-block', {
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

  async function openDetail(row: MesCarrierItem) {
    setDetail(row)
    setEditing(false)
    setEditError('')
    setBindLotRef('')
    setDetailLoading(true)
    try {
      const full = await getCarrierApi(row.id)
      setDetail(full)
      setStatusDraft(
        full.status === 'IN_USE' || full.status === 'SCRAPPED' ? 'AVAILABLE' : (full.status as MesCarrierStatus),
      )
      setEditForm({
        capacity: String(full.capacity ?? 25),
        cleanStatus: full.cleanStatus ?? 'UNKNOWN',
        locationType: full.locationType ?? 'NONE',
        locationRef: full.locationRef ?? '',
        remark: full.remark ?? '',
      })
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    if (!createForm.carrierCode.trim()) {
      setCreateError('载具编码不能为空')
      return
    }
    const capacity = Number(createForm.capacity)
    if (!Number.isFinite(capacity) || capacity < 1) {
      setCreateError('容量须为正整数')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const created = await createCarrierApi({
        carrierCode: createForm.carrierCode.trim(),
        carrierType: createForm.carrierType.trim() || 'FOUP',
        capacity,
        cleanStatus: createForm.cleanStatus || undefined,
        locationType: createForm.locationType || undefined,
        locationRef: createForm.locationRef.trim() || undefined,
        remark: createForm.remark.trim() || undefined,
      })
      toast.success(`已创建 · ${created.carrierCode}`)
      setCreateOpen(false)
      setFlashId(String(created.id))
      setTimeout(() => setFlashId(null), 1200)
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
    const capacity = Number(editForm.capacity)
    if (!Number.isFinite(capacity) || capacity < 1) {
      setEditError('容量须为正整数')
      return
    }
    setSavingEdit(true)
    setEditError('')
    try {
      await updateCarrierApi(detail.id, {
        capacity,
        cleanStatus: editForm.cleanStatus || undefined,
        locationType: editForm.locationType || undefined,
        locationRef: editForm.locationRef.trim() || undefined,
        remark: editForm.remark.trim() || undefined,
      })
      toast.success('已保存')
      setFlashId(String(detail.id))
      setEditing(false)
      const full = await getCarrierApi(detail.id)
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
      await changeCarrierStatusApi(detail.id, statusDraft)
      toast.success(`状态 → ${mesCarrierStatusLabel[statusDraft]?.label ?? statusDraft}`)
      setFlashId(String(detail.id))
      const full = await getCarrierApi(detail.id)
      setDetail(full)
      setStatusDraft(
        full.status === 'IN_USE' || full.status === 'SCRAPPED' ? 'AVAILABLE' : (full.status as MesCarrierStatus),
      )
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '改态失败')
    } finally {
      setSavingStatus(false)
    }
  }

  async function submitBind() {
    if (!detail || !bindLotRef.trim()) {
      toast.error('请填写有效的批次 ID')
      return
    }
    const lotId = Number(bindLotRef.trim())
    if (!Number.isFinite(lotId) || lotId <= 0) {
      toast.error('请填写有效的批次 ID')
      return
    }
    setBinding(true)
    try {
      await bindCarrierApi(lotId, String(detail.id))
      toast.success('已绑定')
      setBindLotRef('')
      setFlashId(String(detail.id))
      const full = await getCarrierApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '绑定失败')
    } finally {
      setBinding(false)
    }
  }

  async function submitUnbind() {
    if (!detail) return
    const ok = await confirm({
      title: '解绑载具',
      message: `确认解绑 ${detail.carrierCode}${detail.boundLotNo ? ` ↔ ${detail.boundLotNo}` : ''}？将释放盒子并清空批次载具引用。`,
      confirmText: '解绑',
      danger: true,
    })
    if (!ok) return
    setUnbinding(true)
    try {
      await unbindCarrierByIdApi(detail.id)
      toast.success('已解绑')
      setFlashId(String(detail.id))
      const full = await getCarrierApi(detail.id)
      setDetail(full)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '解绑失败')
    } finally {
      setUnbinding(false)
    }
  }

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">载具台账</h1>
        <p className="text-sm text-muted">无权限查看载具（需要 carrier:view）</p>
      </div>
    )
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="carrier-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">载具台账</h1>
          <p className="mt-1 text-sm text-muted">主数据 · 绑解 · TrackIn 闸前置</p>
        </div>
        {canEdit ? (
          <Button onClick={openCreate}>
            <Plus className="size-4" aria-hidden />
            新建载具
          </Button>
        ) : null}
      </header>

      <div className="carrier-block flex flex-wrap items-center gap-2">
        <input
          className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
          placeholder="编码 / 备注"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setPage(1)
              setKeyword(q.trim())
            }
          }}
          aria-label="搜索载具"
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
      </div>

      <div className="carrier-block overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[920px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">载具号</th>
              <th className="px-3">类型</th>
              <th className="px-3">状态</th>
              <th className="px-3">绑定 Lot</th>
              <th className="px-3">位置</th>
              <th className="px-3">容量</th>
              <th className="px-3">更新</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 6 }).map((_, i) => (
                <tr key={i} className="h-10 border-b border-border">
                  <td colSpan={8} className="px-3">
                    <div className="h-4 w-full animate-pulse rounded bg-surface" />
                  </td>
                </tr>
              ))
            ) : loadFailed ? (
              <tr>
                <td colSpan={8} className="px-3 py-8 text-center text-muted">
                  加载失败，请刷新重试
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-3 py-10 text-center">
                  <p className="text-sm text-muted">还没有载具</p>
                  {canEdit ? (
                    <button
                      type="button"
                      className="mt-2 cursor-pointer text-sm text-accent hover:underline"
                      onClick={openCreate}
                    >
                      创建第一个载具
                    </button>
                  ) : null}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                  <td className="h-10 px-3 font-mono text-[13px] text-accent">{row.carrierCode}</td>
                  <td className="px-3 font-mono text-[13px]">{row.carrierType || '—'}</td>
                  <td className="px-3">
                    <MesCarrierStatusPill status={row.status} />
                  </td>
                  <td className="px-3 font-mono text-[13px]">
                    {row.boundLotNo ? (
                      <span className="text-ink">{row.boundLotNo}</span>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td className="max-w-[180px] truncate px-3 text-muted" title={fmtLocation(row)}>
                    {fmtLocation(row)}
                  </td>
                  <td className="px-3 font-mono text-[13px]">{row.capacity}</td>
                  <td className="px-3 font-mono text-[12px] text-muted">{fmtTime(row.updateTime)}</td>
                  <td className="px-3">
                    <TableAction icon={Box} label="详情" onClick={() => void openDetail(row)} />
                  </td>
                </FlashRow>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="carrier-block flex items-center justify-between text-sm text-muted">
        <span>
          共 {total} 个 · 第 {page}/{totalPages} 页
        </span>
        <div className="flex gap-2">
          <Button variant="secondary" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
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

      <Drawer open={createOpen} onClose={() => setCreateOpen(false)} title="新建载具" width={420}>
        <div className="space-y-4">
          <Field
            label="载具编码"
            name="carrierCode"
            required
            className="font-mono"
            value={createForm.carrierCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, carrierCode: e.target.value }))}
            placeholder="FOUP-A-001"
          />
          <Field
            label="类型"
            name="carrierType"
            className="font-mono"
            value={createForm.carrierType}
            onChange={(e) => setCreateForm((f) => ({ ...f, carrierType: e.target.value }))}
            placeholder="FOUP"
          />
          <Field
            label="容量"
            name="capacity"
            className="font-mono"
            value={createForm.capacity}
            onChange={(e) => setCreateForm((f) => ({ ...f, capacity: e.target.value }))}
          />
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">洁净状态</span>
            <select
              className="h-9 rounded-md border border-border bg-bg px-3 text-sm"
              value={createForm.cleanStatus}
              onChange={(e) => setCreateForm((f) => ({ ...f, cleanStatus: e.target.value }))}
            >
              <option value="UNKNOWN">未知</option>
              <option value="CLEAN">已清洗</option>
              <option value="DIRTY">脏盒</option>
            </select>
          </label>
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
              <Box className="size-4" aria-hidden />
              创建
            </Button>
          </div>
        </div>
      </Drawer>

      <Drawer
        open={detail != null}
        onClose={() => setDetail(null)}
        title={detail ? detail.carrierCode : '载具详情'}
        width={440}
      >
        {detailLoading && !detail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : detail ? (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <MesCarrierStatusPill status={detail.status} />
              <span className="font-mono text-xs text-muted">{detail.carrierType}</span>
            </div>

            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs font-medium text-muted">绑定 Lot</dt>
                <dd className="mt-0.5 font-mono">{detail.boundLotNo || '—'}</dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-muted">容量</dt>
                <dd className="mt-0.5 font-mono">{detail.capacity}</dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-muted">洁净</dt>
                <dd className="mt-0.5 font-mono">{detail.cleanStatus || '—'}</dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-muted">位置</dt>
                <dd className="mt-0.5 font-mono text-[13px]">{fmtLocation(detail)}</dd>
              </div>
              {detail.remark ? (
                <div className="sm:col-span-2">
                  <dt className="text-xs font-medium text-muted">备注</dt>
                  <dd className="mt-0.5">{detail.remark}</dd>
                </div>
              ) : null}
            </dl>

            {canBind ? (
              <section className="space-y-3 border-t border-border pt-4">
                <h3 className="text-sm font-semibold">绑解</h3>
                {detail.boundLotId != null ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm text-muted">
                      当前绑定{' '}
                      <span className="font-mono text-ink">{detail.boundLotNo || detail.boundLotId}</span>
                    </p>
                    <Button
                      variant="secondary"
                      loading={unbinding}
                      onClick={() => void submitUnbind()}
                    >
                      <Link2Off className="size-4" aria-hidden />
                      解绑
                    </Button>
                  </div>
                ) : detail.status === 'SCRAPPED' || detail.status === 'QUARANTINE' ? (
                  <p className="text-sm text-muted">隔离/报废不可绑定</p>
                ) : (
                  <div className="flex flex-wrap items-end gap-2">
                    <Field
                      label="批次 ID"
                      name="bindLotId"
                      className="font-mono"
                      value={bindLotRef}
                      onChange={(e) => setBindLotRef(e.target.value)}
                      placeholder="数字 Lot ID"
                    />
                    <Button loading={binding} onClick={() => void submitBind()}>
                      <Link2 className="size-4" aria-hidden />
                      绑定
                    </Button>
                  </div>
                )}
              </section>
            ) : null}

            {canEdit ? (
              <section className="space-y-3 border-t border-border pt-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold">台账</h3>
                  {!editing ? (
                    <Button variant="secondary" onClick={() => setEditing(true)}>
                      <Pencil className="size-4" aria-hidden />
                      编辑
                    </Button>
                  ) : null}
                </div>
                {editing ? (
                  <div className="space-y-3">
                    <Field
                      label="容量"
                      name="capacity"
                      className="font-mono"
                      value={editForm.capacity}
                      onChange={(e) => setEditForm((f) => ({ ...f, capacity: e.target.value }))}
                    />
                    <label className="flex flex-col gap-1.5 text-sm">
                      <span className="text-xs font-medium text-muted">洁净状态</span>
                      <select
                        className="h-9 rounded-md border border-border bg-bg px-3 text-sm"
                        value={editForm.cleanStatus}
                        onChange={(e) => setEditForm((f) => ({ ...f, cleanStatus: e.target.value }))}
                      >
                        <option value="UNKNOWN">未知</option>
                        <option value="CLEAN">已清洗</option>
                        <option value="DIRTY">脏盒</option>
                      </select>
                    </label>
                    <label className="flex flex-col gap-1.5 text-sm">
                      <span className="text-xs font-medium text-muted">位置类型</span>
                      <select
                        className="h-9 rounded-md border border-border bg-bg px-3 text-sm"
                        value={editForm.locationType}
                        onChange={(e) => setEditForm((f) => ({ ...f, locationType: e.target.value }))}
                      >
                        <option value="NONE">未记位</option>
                        <option value="STOCKER">库</option>
                        <option value="PORT">设备口</option>
                        <option value="OHB">架空缓冲</option>
                        <option value="MANUAL">人工站</option>
                      </select>
                    </label>
                    <Field
                      label="位置引用"
                      name="locationRef"
                      className="font-mono"
                      value={editForm.locationRef}
                      onChange={(e) => setEditForm((f) => ({ ...f, locationRef: e.target.value }))}
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
                ) : null}

                {detail.status !== 'IN_USE' && detail.status !== 'SCRAPPED' ? (
                  <div className="flex flex-wrap items-end gap-2 pt-1">
                    <label className="flex flex-col gap-1.5 text-sm">
                      <span className="text-xs font-medium text-muted">改态</span>
                      <select
                        className="h-9 min-w-[140px] rounded-md border border-border bg-bg px-3 text-sm"
                        value={statusDraft}
                        onChange={(e) => setStatusDraft(e.target.value as MesCarrierStatus)}
                      >
                        {STATUS_OPTIONS.map((s) => (
                          <option key={s} value={s}>
                            {mesCarrierStatusLabel[s].label}
                          </option>
                        ))}
                      </select>
                    </label>
                    <Button
                      variant="secondary"
                      loading={savingStatus}
                      disabled={statusDraft === detail.status}
                      onClick={() => void submitStatus()}
                    >
                      应用
                    </Button>
                  </div>
                ) : detail.status === 'IN_USE' ? (
                  <p className="text-xs text-muted">使用中请先解绑再改态；IN_USE 不可手改。</p>
                ) : null}
              </section>
            ) : null}
          </div>
        ) : null}
      </Drawer>
    </div>
  )
}
