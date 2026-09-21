import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import gsap from 'gsap'
import { Flame, GitBranch, Link2, Link2Off, PackagePlus, Pencil, Rocket } from 'lucide-react'
import {
  createLotApi,
  getLotApi,
  getLotGenealogyApi,
  listLotsApi,
  releaseLotApi,
  updateLotApi,
  type MesLotGenealogyNode,
  type MesLotItem,
  type MesLotStatus,
} from '../api/lot'
import {
  bindLotCarrierApi,
  getLotCarrierApi,
  unbindLotCarrierApi,
  type MesCarrierBinding,
} from '../api/carrier'
import { getComplaintEnabledApi } from '../api/complaint'
import { GenealogyTree } from '../components/lot/GenealogyTree'
import { ComplaintPackageDrawer } from '../components/lot/ComplaintPackageDrawer'
import { listRoutesApi, type MesRouteItem } from '../api/route'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { HotLotPill, MesLotStatusPill, mesLotStatusLabel } from '../components/ui/StatusPill'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

const STATUS_FILTERS: Array<{ key: MesLotStatus | 'All'; label: string }> = [
  { key: 'All', label: '全部' },
  { key: 'created', label: mesLotStatusLabel.created.label },
  { key: 'wait', label: mesLotStatusLabel.wait.label },
  { key: 'processing', label: mesLotStatusLabel.processing.label },
  { key: 'held', label: mesLotStatusLabel.held.label },
  { key: 'completed', label: mesLotStatusLabel.completed.label },
  { key: 'scrapped', label: mesLotStatusLabel.scrapped.label },
  { key: 'merged', label: mesLotStatusLabel.merged.label },
]

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

const HOT_PRIORITY_FLOOR = 80

const emptyCreate = {
  lotNo: '',
  productCode: '',
  qty: '25',
  priority: '50',
  hotFlag: false,
  customerLot: '',
  routeId: '',
  remark: '',
}

export function LotsPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)
  const [searchParams] = useSearchParams()
  const jumpTried = useRef(false)

  const [lots, setLots] = useState<MesLotItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<MesLotStatus | 'All'>('All')
  const [hotFilter, setHotFilter] = useState<'all' | 'hot' | 'normal'>('all')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)

  const [routeOptions, setRouteOptions] = useState<MesRouteItem[]>([])

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreate)
  const createPriorityBeforeHot = useRef<string | null>(null)
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

  const [detail, setDetail] = useState<MesLotItem | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [genealogy, setGenealogy] = useState<MesLotGenealogyNode | null>(null)
  const [genealogyLoading, setGenealogyLoading] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({
    productCode: '',
    qty: '25',
    priority: '50',
    hotFlag: false,
    customerLot: '',
    routeId: '',
    remark: '',
  })
  const [editError, setEditError] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [releasing, setReleasing] = useState(false)
  const [lotCarrier, setLotCarrier] = useState<MesCarrierBinding | null>(null)
  const [carrierRefInput, setCarrierRefInput] = useState('')
  const [carrierBusy, setCarrierBusy] = useState(false)
  const [complaintOn, setComplaintOn] = useState(false)
  const [pkgOpen, setPkgOpen] = useState(false)

  const size = 20
  const canAdd = hasPermission('lot:add')
  const canEdit = hasPermission('lot:edit')
  const canRelease = hasPermission('lot:release')
  const canCarrierBind = hasPermission('carrier:bind')
  const canCarrierView = hasPermission('carrier:view') || hasPermission('lot:list')
  const canComplaintView = hasPermission('complaint:view')
  const canComplaintBuild = hasPermission('complaint:build')
  const totalPages = Math.max(1, Math.ceil(total / size))
  const isCreated = detail?.status === 'created'
  const canEditDetail =
    detail &&
    (detail.status === 'created' ||
      detail.status === 'released' ||
      detail.status === 'wait' ||
      detail.status === 'processing' ||
      detail.status === 'held')

  const loadLots = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listLotsApi({
        keyword,
        status: statusFilter === 'All' ? '' : statusFilter,
        hotFlag: hotFilter === 'hot' ? 1 : hotFilter === 'normal' ? 0 : undefined,
        page,
        size,
      })
      setLots(data.records)
      setTotal(data.total)
    } catch (err) {
      setLots([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, statusFilter, hotFilter, page, toast])

  const loadRouteOptions = useCallback(async () => {
    try {
      const data = await listRoutesApi({ page: 1, size: 100, status: 1 })
      setRouteOptions(data.records)
    } catch {
      setRouteOptions([])
    }
  }, [])

  useEffect(() => {
    void loadLots()
  }, [loadLots])

  useEffect(() => {
    void loadRouteOptions()
  }, [loadRouteOptions])

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
    if (!detail) setPkgOpen(false)
  }, [detail])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.lot-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [])

  async function openDetail(row: MesLotItem) {
    setDetail(row)
    setEditing(false)
    setEditError('')
    setDetailLoading(true)
    setGenealogy(null)
    setGenealogyLoading(true)
    setLotCarrier(null)
    setCarrierRefInput('')
    try {
      const full = await getLotApi(row.id)
      setDetail(full)
      setEditForm({
        productCode: full.productCode ?? '',
        qty: String(full.qty ?? 0),
        priority: String(full.priority ?? 50),
        hotFlag: full.hotFlag === 1,
        customerLot: full.customerLot ?? '',
        routeId: full.routeId != null ? String(full.routeId) : '',
        remark: full.remark ?? '',
      })
      if (canCarrierView) {
        void getLotCarrierApi(full.id)
          .then((b) => setLotCarrier(b ?? null))
          .catch(() => setLotCarrier(null))
      }
      void getLotGenealogyApi(row.id, { direction: 'both', depth: 5 })
        .then((tree) => setGenealogy(tree))
        .catch(() => setGenealogy(null))
        .finally(() => setGenealogyLoading(false))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
      setGenealogyLoading(false)
    } finally {
      setDetailLoading(false)
    }
  }

  async function doBindCarrier() {
    if (!detail || !carrierRefInput.trim()) {
      toast.error('请填写载具编码或 ID')
      return
    }
    setCarrierBusy(true)
    try {
      const b = await bindLotCarrierApi(detail.id, carrierRefInput.trim())
      setLotCarrier(b)
      setCarrierRefInput('')
      toast.success(`已绑定 · ${b.carrierCode}`)
      setFlashId(String(detail.id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '绑定失败')
    } finally {
      setCarrierBusy(false)
    }
  }

  async function doUnbindCarrier() {
    if (!detail) return
    const ok = await confirm({
      title: '解绑载具',
      message: `确认解绑 ${detail.lotNo}${lotCarrier?.carrierCode ? ` ↔ ${lotCarrier.carrierCode}` : ''}？`,
      confirmText: '解绑',
      danger: true,
    })
    if (!ok) return
    setCarrierBusy(true)
    try {
      await unbindLotCarrierApi(detail.id)
      setLotCarrier(null)
      toast.success('已解绑')
      setFlashId(String(detail.id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '解绑失败')
    } finally {
      setCarrierBusy(false)
    }
  }

  async function openGenealogyLot(lotId: string | number) {
    if (detail && String(lotId) === String(detail.id)) return
    try {
      const full = await getLotApi(lotId)
      await openDetail(full)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载批次失败')
    }
  }

  useEffect(() => {
    const lotId = searchParams.get('lotId')
    if (!lotId || jumpTried.current) return
    jumpTried.current = true
    void getLotApi(lotId)
      .then((full) => openDetail(full))
      .catch((err) => toast.error(err instanceof ApiError ? err.message : '加载批次失败'))
  }, [searchParams, toast])

  async function submitCreate() {
    const qty = Number(createForm.qty)
    if (!Number.isFinite(qty) || qty < 0) {
      setCreateError('数量无效')
      return
    }
    let priority: number | undefined
    if (createForm.priority.trim()) {
      priority = Number(createForm.priority)
      if (!Number.isFinite(priority) || priority < 1 || priority > 100) {
        setCreateError('优先级须为 1–100')
        return
      }
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const { id, lotNo } = await createLotApi({
        lotNo: createForm.lotNo.trim() || undefined,
        productCode: createForm.productCode.trim() || undefined,
        qty,
        priority,
        hotFlag: createForm.hotFlag ? 1 : 0,
        customerLot: createForm.customerLot.trim() || undefined,
        routeId: createForm.routeId || undefined,
        remark: createForm.remark.trim() || undefined,
      })
      toast.success(`批次已创建 · ${lotNo}`)
      setCreateOpen(false)
      setFlashId(String(id))
      setTimeout(() => setFlashId(null), 1200)
      setPage(1)
      await loadLots()
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setSavingCreate(false)
    }
  }

  async function submitEdit() {
    if (!detail) return
    const qty = Number(editForm.qty)
    const priority = Number(editForm.priority)
    if (!Number.isFinite(qty) || qty < 0) {
      setEditError('数量无效')
      return
    }
    if (!Number.isFinite(priority) || priority < 1 || priority > 100) {
      setEditError('优先级须为 1–100')
      return
    }
    setSavingEdit(true)
    setEditError('')
    try {
      await updateLotApi(detail.id, {
        productCode: editForm.productCode.trim() || undefined,
        qty: isCreated ? qty : detail.qty,
        priority,
        hotFlag: editForm.hotFlag ? 1 : 0,
        customerLot: editForm.customerLot.trim() || undefined,
        routeId: isCreated ? editForm.routeId || null : detail.routeId,
        remark: editForm.remark.trim() || undefined,
      })
      toast.success('已保存')
      setEditing(false)
      setFlashId(String(detail.id))
      setTimeout(() => setFlashId(null), 1200)
      await loadLots()
      await openDetail(detail)
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingEdit(false)
    }
  }

  async function doRelease() {
    if (!detail) return
    const ok = await confirm({
      title: '放行批次',
      message: `将 ${detail.lotNo} 绑定路线当前生效版本，之后不可改版本。确认放行？`,
      confirmText: '放行',
    })
    if (!ok) return
    setReleasing(true)
    try {
      await releaseLotApi(detail.id)
      toast.success('已放行')
      setFlashId(String(detail.id))
      setTimeout(() => setFlashId(null), 1200)
      await loadLots()
      await openDetail(detail)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '放行失败')
    } finally {
      setReleasing(false)
    }
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="lot-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">批次管理</h1>
          <p className="mt-1 text-sm text-muted">创建与维护 Lot；放行后绑定工艺路线版本快照，供后续过站</p>
        </div>
        {canAdd ? (
          <Button
            onClick={() => {
              setCreateOpen(true)
              setCreateError('')
              setCreateForm(emptyCreate)
              createPriorityBeforeHot.current = null
            }}
          >
            <PackagePlus className="size-4" aria-hidden />
            创建批次
          </Button>
        ) : null}
      </header>

      <div className="lot-block flex flex-wrap items-center gap-2">
        <input
          className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
          placeholder="批次号 / 产品 / 客户Lot"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setPage(1)
              setKeyword(q.trim())
            }
          }}
          aria-label="搜索批次"
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
                'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors',
                statusFilter === s.key
                  ? 'border-primary bg-primary text-white'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-1.5 border-l border-border pl-2 sm:ml-1">
          {(
            [
              { key: 'all' as const, label: '全部急度' },
              { key: 'hot' as const, label: 'Hot' },
              { key: 'normal' as const, label: '普通' },
            ] as const
          ).map((s) => (
            <button
              key={s.key}
              type="button"
              onClick={() => {
                setHotFilter(s.key)
                setPage(1)
              }}
              className={cn(
                'inline-flex h-8 cursor-pointer items-center gap-1 rounded-md border px-2.5 text-xs font-medium transition-colors',
                hotFilter === s.key
                  ? s.key === 'hot'
                    ? 'border-warning bg-warning/20 text-ink'
                    : 'border-primary bg-primary text-white'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.key === 'hot' ? <Flame className="size-3.5 text-warning" aria-hidden /> : null}
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="lot-block overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[960px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">批次号</th>
              <th className="px-3">状态</th>
              <th className="px-3">急度</th>
              <th className="px-3">产品</th>
              <th className="px-3">数量</th>
              <th className="px-3">优先级</th>
              <th className="px-3">路线</th>
              <th className="px-3">版本</th>
              <th className="px-3">更新</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={10} className="h-16 px-3 text-center text-muted">
                  加载中…
                </td>
              </tr>
            ) : loadFailed ? (
              <tr>
                <td colSpan={10} className="h-20 px-3 text-center text-muted">
                  加载失败
                  <button
                    type="button"
                    className="ml-2 cursor-pointer text-accent hover:underline"
                    onClick={() => void loadLots()}
                  >
                    重试
                  </button>
                </td>
              </tr>
            ) : lots.length === 0 ? (
              <tr>
                <td colSpan={10} className="h-24 px-3 text-center text-muted">
                  暂无批次。
                  {canAdd ? '点击「创建批次」开始。' : null}
                </td>
              </tr>
            ) : (
              lots.map((lot) => (
                <FlashRow key={String(lot.id)} active={flashId === String(lot.id)}>
                  <td
                    className={cn(
                      'h-10 border-b border-border px-3 font-mono text-[13px] font-medium',
                      lot.hotFlag === 1 && 'border-l-2 border-l-warning',
                    )}
                  >
                    {lot.lotNo}
                  </td>
                  <td className="border-b border-border px-3">
                    <MesLotStatusPill status={lot.status} />
                  </td>
                  <td className="border-b border-border px-3">
                    <HotLotPill hot={lot.hotFlag} />
                    {lot.hotFlag !== 1 ? <span className="text-xs text-muted">—</span> : null}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-[13px] text-muted">
                    {lot.productCode || '—'}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">{lot.qty}</td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">{lot.priority}</td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">
                    {lot.routeCode || '—'}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">
                    {lot.routeVersionNo != null ? `v${lot.routeVersionNo}` : '—'}
                  </td>
                  <td className="border-b border-border px-3 text-muted">{fmtTime(lot.updateTime)}</td>
                  <td className="border-b border-border px-3">
                    <TableAction
                      icon={GitBranch}
                      label="维护"
                      tone="accent"
                      onClick={() => void openDetail(lot)}
                    />
                  </td>
                </FlashRow>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="lot-block flex items-center justify-between text-sm text-muted">
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

      <Drawer
        open={createOpen}
        onClose={() => !savingCreate && setCreateOpen(false)}
        title="创建批次"
        width={520}
        footer={
          <>
            <Button variant="secondary" disabled={savingCreate} onClick={() => setCreateOpen(false)}>
              取消
            </Button>
            <Button disabled={savingCreate} onClick={() => void submitCreate()}>
              {savingCreate ? '创建中…' : '创建'}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field
              label="产品编码"
              name="productCode"
              className="font-mono"
              value={createForm.productCode}
              onChange={(e) => setCreateForm((f) => ({ ...f, productCode: e.target.value }))}
              placeholder="可选"
            />
            <Field
              label="数量"
              name="qty"
              type="number"
              min={0}
              className="font-mono"
              value={createForm.qty}
              onChange={(e) => setCreateForm((f) => ({ ...f, qty: e.target.value }))}
            />
            <Field
              label="优先级 (1–100)"
              name="priority"
              type="number"
              min={1}
              max={100}
              className="font-mono"
              value={createForm.priority}
              onChange={(e) => setCreateForm((f) => ({ ...f, priority: e.target.value }))}
            />
            <Field
              label="客户 Lot"
              name="customerLot"
              className="font-mono"
              value={createForm.customerLot}
              onChange={(e) => setCreateForm((f) => ({ ...f, customerLot: e.target.value }))}
              placeholder="可选"
            />
          </div>
          <label className="flex cursor-pointer items-start gap-3 rounded-md border border-border bg-surface px-3 py-2.5">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-[oklch(0.78_0.14_75)]"
              checked={createForm.hotFlag}
              onChange={(e) => {
                const on = e.target.checked
                setCreateForm((f) => {
                  const p = Number(f.priority)
                  if (on) {
                    const bump = Number.isFinite(p) && p < HOT_PRIORITY_FLOOR
                    createPriorityBeforeHot.current = bump ? f.priority : null
                    return {
                      ...f,
                      hotFlag: true,
                      priority: bump ? String(HOT_PRIORITY_FLOOR) : f.priority,
                    }
                  }
                  const restore = createPriorityBeforeHot.current
                  createPriorityBeforeHot.current = null
                  return {
                    ...f,
                    hotFlag: false,
                    priority:
                      restore != null && f.priority === String(HOT_PRIORITY_FLOOR)
                        ? restore
                        : f.priority,
                  }
                })
              }}
            />
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 text-sm font-medium text-ink">
                <Flame className="size-3.5 text-warning" aria-hidden />
                Hot Lot 特急
              </span>
              <span className="mt-0.5 block text-xs text-muted">
                开启后优先级不低于 {HOT_PRIORITY_FLOOR}；派工/在制队列优先
              </span>
            </span>
          </label>
          <Field
            label="批次号（高级，可选）"
            name="lotNo"
            className="font-mono"
            value={createForm.lotNo}
            onChange={(e) => setCreateForm((f) => ({ ...f, lotNo: e.target.value }))}
            placeholder="留空自动生成 LOT-日期-流水"
          />
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">工艺路线</span>
            <select
              className="h-9 w-full rounded-md border border-border bg-bg px-3 text-sm"
              value={createForm.routeId}
              onChange={(e) => setCreateForm((f) => ({ ...f, routeId: e.target.value }))}
            >
              <option value="">暂不指定</option>
              {routeOptions.map((r) => (
                <option key={String(r.id)} value={String(r.id)}>
                  {r.routeCode} · {r.routeName}
                  {r.activeVersionNo != null ? ` (v${r.activeVersionNo})` : ' (无生效版)'}
                </option>
              ))}
            </select>
          </label>
          <Field
            label="备注"
            name="remark"
            value={createForm.remark}
            onChange={(e) => setCreateForm((f) => ({ ...f, remark: e.target.value }))}
            placeholder="可选"
          />
          {createError ? <p className="text-sm text-danger">{createError}</p> : null}
          <p className="text-xs text-muted">
            默认自动生成批次号（如 LOT-20260726-001）。创建后为「已创建」；指定路线并放行后绑定生效版本快照。
          </p>
        </div>
      </Drawer>

      <Drawer
        open={!!detail}
        onClose={() => !savingEdit && !releasing && setDetail(null)}
        title={detail ? `维护 · ${detail.lotNo}` : '维护批次'}
        width={560}
        footer={
          detail && !editing ? (
            <>
              <Button variant="secondary" onClick={() => setDetail(null)}>
                关闭
              </Button>
              {canEdit && canEditDetail ? (
                <Button variant="secondary" onClick={() => setEditing(true)}>
                  <Pencil className="size-4" aria-hidden />
                  改属性
                </Button>
              ) : null}
              {canRelease && isCreated ? (
                <Button disabled={releasing || !detail.routeId} onClick={() => void doRelease()}>
                  <Rocket className="size-4" aria-hidden />
                  {releasing ? '放行中…' : '放行'}
                </Button>
              ) : null}
              {canComplaintView && complaintOn ? (
                <Button variant="secondary" onClick={() => setPkgOpen(true)}>
                  生成追溯包
                </Button>
              ) : null}
            </>
          ) : detail && editing ? (
            <>
              <Button
                variant="secondary"
                disabled={savingEdit}
                onClick={() => {
                  setEditing(false)
                  setEditError('')
                }}
              >
                取消
              </Button>
              <Button disabled={savingEdit} onClick={() => void submitEdit()}>
                {savingEdit ? '保存中…' : '保存'}
              </Button>
            </>
          ) : undefined
        }
      >
        {detailLoading || !detail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <MesLotStatusPill status={detail.status} />
              <HotLotPill hot={detail.hotFlag} />
              {detail.routeVersionNo != null ? (
                <span className="font-mono text-xs text-muted">快照 v{detail.routeVersionNo}</span>
              ) : null}
            </div>

            {editing ? (
              <div className="space-y-3">
                {!isCreated ? (
                  <p className="rounded-md border border-border bg-surface px-3 py-2 text-xs text-muted">
                    已放行：可改优先级 / Hot / 客户 Lot / 备注。产品、路线锁定；数量请走现场台
                    Split / Merge / Scrap / Bonus。
                  </p>
                ) : null}
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="flex flex-col gap-1">
                    <Field
                      label="产品编码"
                      name="editProductCode"
                      className="font-mono"
                      value={editForm.productCode}
                      disabled={!isCreated}
                      onChange={(e) => setEditForm((f) => ({ ...f, productCode: e.target.value }))}
                    />
                    {!isCreated ? (
                      <span className="text-xs text-muted">已放行不可修改产品编码</span>
                    ) : null}
                  </div>
                  <div className="flex flex-col gap-1">
                    <Field
                      label="数量"
                      name="editQty"
                      type="number"
                      min={0}
                      className="font-mono"
                      value={editForm.qty}
                      disabled={!isCreated}
                      onChange={(e) => setEditForm((f) => ({ ...f, qty: e.target.value }))}
                    />
                    {!isCreated ? (
                      <span className="text-xs text-muted">
                        数量变更请走 Split/Merge/Scrap/Bonus 事务
                      </span>
                    ) : null}
                  </div>
                  <Field
                    label="优先级 (1–100)"
                    name="editPriority"
                    type="number"
                    min={1}
                    max={100}
                    className="font-mono"
                    value={editForm.priority}
                    onChange={(e) => setEditForm((f) => ({ ...f, priority: e.target.value }))}
                  />
                  <Field
                    label="客户 Lot"
                    name="editCustomerLot"
                    className="font-mono"
                    value={editForm.customerLot}
                    onChange={(e) => setEditForm((f) => ({ ...f, customerLot: e.target.value }))}
                  />
                </div>
                <label className="flex cursor-pointer items-start gap-3 rounded-md border border-border bg-surface px-3 py-2.5">
                  <input
                    type="checkbox"
                    className="mt-0.5 size-4 accent-[oklch(0.78_0.14_75)]"
                    checked={editForm.hotFlag}
                    onChange={(e) => {
                      const on = e.target.checked
                      setEditForm((f) => {
                        const p = Number(f.priority)
                        return {
                          ...f,
                          hotFlag: on,
                          priority:
                            on && Number.isFinite(p) && p < HOT_PRIORITY_FLOOR
                              ? String(HOT_PRIORITY_FLOOR)
                              : f.priority,
                        }
                      })
                    }}
                  />
                  <span className="min-w-0">
                    <span className="flex items-center gap-1.5 text-sm font-medium text-ink">
                      <Flame className="size-3.5 text-warning" aria-hidden />
                      Hot Lot 特急
                    </span>
                    <span className="mt-0.5 block text-xs text-muted">
                      开启后优先级不低于 {HOT_PRIORITY_FLOOR}；取消 Hot 不自动降优先级
                    </span>
                  </span>
                </label>
                <label className="flex flex-col gap-1.5 text-sm">
                  <span className="text-xs font-medium text-muted">工艺路线</span>
                  <select
                    className="h-9 w-full rounded-md border border-border bg-bg px-3 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                    value={editForm.routeId}
                    disabled={!isCreated}
                    onChange={(e) => setEditForm((f) => ({ ...f, routeId: e.target.value }))}
                  >
                    <option value="">暂不指定</option>
                    {routeOptions.map((r) => (
                      <option key={String(r.id)} value={String(r.id)}>
                        {r.routeCode} · {r.routeName}
                      </option>
                    ))}
                  </select>
                  {!isCreated ? (
                    <span className="text-xs text-muted">
                      已放行不可修改路线
                      {detail.routeVersionNo != null ? `（快照 v${detail.routeVersionNo}）` : ''}
                    </span>
                  ) : null}
                </label>
                <Field
                  label="备注"
                  name="editRemark"
                  value={editForm.remark}
                  onChange={(e) => setEditForm((f) => ({ ...f, remark: e.target.value }))}
                />
                {editError ? <p className="text-sm text-danger">{editError}</p> : null}
              </div>
            ) : (
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-xs font-medium text-muted">产品</dt>
                  <dd className="mt-0.5 font-mono">{detail.productCode || '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-muted">数量</dt>
                  <dd className="mt-0.5 font-mono">{detail.qty}</dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-muted">累计报废</dt>
                  <dd className="mt-0.5 font-mono">{detail.scrapQty ?? 0}</dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-muted">优先级</dt>
                  <dd className="mt-0.5 flex items-center gap-2 font-mono">
                    {detail.priority}
                    <HotLotPill hot={detail.hotFlag} />
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-muted">客户 Lot</dt>
                  <dd className="mt-0.5 font-mono">{detail.customerLot || '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs font-medium text-muted">父批</dt>
                  <dd className="mt-0.5 font-mono">
                    {detail.parentLotId != null ? String(detail.parentLotId) : '—'}
                  </dd>
                </div>
                <div className="sm:col-span-2">
                  <dt className="text-xs font-medium text-muted">工艺路线</dt>
                  <dd className="mt-0.5 font-mono">
                    {detail.routeCode
                      ? `${detail.routeCode} · ${detail.routeName ?? ''}`
                      : '—（放行前须指定）'}
                  </dd>
                </div>
                {detail.remark ? (
                  <div className="sm:col-span-2">
                    <dt className="text-xs font-medium text-muted">备注</dt>
                    <dd className="mt-0.5">{detail.remark}</dd>
                  </div>
                ) : null}
              </dl>
            )}

            {canCarrierView ? (
              <section className="space-y-2 border-t border-border pt-4">
                <h3 className="text-sm font-semibold">载具</h3>
                {lotCarrier ? (
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-mono text-accent">{lotCarrier.carrierCode}</span>
                    {canCarrierBind ? (
                      <Button
                        variant="secondary"
                        loading={carrierBusy}
                        onClick={() => void doUnbindCarrier()}
                      >
                        <Link2Off className="size-4" aria-hidden />
                        解绑
                      </Button>
                    ) : null}
                  </div>
                ) : (
                  <div className="space-y-2">
                    <p className="text-sm text-muted">未绑定</p>
                    {canCarrierBind ? (
                      <div className="flex flex-wrap items-end gap-2">
                        <Field
                          label="载具编码 / ID"
                          name="lotCarrierRef"
                          className="font-mono"
                          value={carrierRefInput}
                          onChange={(e) => setCarrierRefInput(e.target.value)}
                          placeholder="FOUP-A-001"
                        />
                        <Button loading={carrierBusy} onClick={() => void doBindCarrier()}>
                          <Link2 className="size-4" aria-hidden />
                          绑定
                        </Button>
                      </div>
                    ) : null}
                  </div>
                )}
              </section>
            ) : null}

            <div>
              <h3 className="flex items-center gap-1.5 text-sm font-semibold">
                <GitBranch className="size-3.5 text-muted" aria-hidden />
                谱系
              </h3>
              <div className="mt-2">
                <GenealogyTree
                  root={genealogy}
                  highlightId={detail.id}
                  loading={genealogyLoading}
                  onSelectLot={openGenealogyLot}
                />
              </div>
            </div>

            {detail.steps && detail.steps.length > 0 ? (
              <div>
                <h3 className="text-sm font-semibold">版本步骤（只读快照）</h3>
                <div className="mt-2 overflow-x-auto rounded-md border border-border">
                  <table className="w-full min-w-[480px] text-left text-sm">
                    <thead className="bg-surface text-xs font-medium text-muted">
                      <tr className="h-9 border-b border-border">
                        <th className="px-3">序</th>
                        <th className="px-3">工序</th>
                        <th className="px-3">下一站</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.steps.map((s) => (
                        <tr key={`${s.stepId}-${s.sortNo}`} className="h-9 border-b border-border">
                          <td className="px-3 font-mono text-[13px]">{s.sortNo}</td>
                          <td className="px-3">
                            <span className="font-mono text-[13px]">{s.stepCode}</span>
                            <span className="ml-2 text-muted">{s.stepName}</span>
                          </td>
                          <td className="px-3 font-mono text-[13px] text-muted">
                            {s.nextSortNo ?? '结束'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : detail.status !== 'created' ? (
              <p className="text-sm text-muted">快照无步骤</p>
            ) : (
              <p className="text-sm text-muted">放行后显示绑定版本的工序序列。</p>
            )}
          </div>
        )}
      </Drawer>
      <ComplaintPackageDrawer
        open={pkgOpen}
        onClose={() => setPkgOpen(false)}
        anchorLotId={detail?.id}
        anchorLotNo={detail?.lotNo ?? ''}
        canBuild={canComplaintBuild}
      />
    </div>
  )
}
