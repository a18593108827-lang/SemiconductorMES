import { useCallback, useEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import { Ban, ClipboardList, Eye, Plus, Search } from 'lucide-react'
import { listLotsApi } from '../api/lot'
import {
  createTestBinApi,
  createTestRecordApi,
  getTestRecordApi,
  listTestBinsApi,
  listTestRecordsApi,
  updateTestBinApi,
  voidTestRecordApi,
  type BinScope,
  type BinType,
  type TestBinDefItem,
  type TestRecordItem,
  type TestSource,
  type TestStage,
} from '../api/test'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { FlashRow } from '../components/ui/FlashRow'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

type TabKey = 'records' | 'bins'

const STAGE_LABEL: Record<string, string> = { CP: '晶圆测试', FT: '成品测试', OTHER: '其他' }
const SOURCE_LABEL: Record<string, string> = { MANUAL: '手工', FILE: '文件', API: '接口' }
const SCOPE_LABEL: Record<string, string> = {
  GLOBAL: '全局',
  PRODUCT: '产品',
  PROGRAM: '程序',
  PROGRAM_VERSION: '程序版本',
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function nowLocal() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

function toApiTime(local: string) {
  return local.length === 16 ? `${local}:00` : local
}

type BinRow = { binType: BinType; binCode: string; binQty: string }

function newRecordForm() {
  return {
    lotNo: '',
    testStage: 'FT' as TestStage,
    programName: '',
    programVersion: '',
    eqpCode: '',
    testTime: nowLocal(),
    totalQty: '',
    sourceType: 'MANUAL' as TestSource,
    sourceRef: '',
    remark: '',
    bins: [
      { binType: 'HARD' as BinType, binCode: '', binQty: '' },
      { binType: 'HARD' as BinType, binCode: '', binQty: '' },
    ] as BinRow[],
  }
}

type BinForm = {
  binScope: BinScope
  productCode: string
  programName: string
  programVersion: string
  binType: BinType
  binCode: string
  binName: string
  failureMode: string
  isShippable: 0 | 1
  status: 0 | 1
  remark: string
}

const emptyBin: BinForm = {
  binScope: 'GLOBAL',
  productCode: '',
  programName: '',
  programVersion: '',
  binType: 'HARD',
  binCode: '',
  binName: '',
  failureMode: '',
  isShippable: 1,
  status: 1,
  remark: '',
}

/** 只合计将提交的硬档（有档号）；空档号行不计入，与 submitCreate 的 filter 口径一致（R7-F3） */
function hardSum(bins: BinRow[]) {
  return bins.reduce((s, b) => {
    if (b.binType !== 'HARD' || !b.binCode.trim()) return s
    return s + (Number(b.binQty) || 0)
  }, 0)
}

export function TestPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)
  const [tab, setTab] = useState<TabKey>('records')

  const canView = hasPermission('test:view')
  const canCreate = hasPermission('test:create')
  const canVoid = hasPermission('test:void')
  const canEditBin = hasPermission('test:edit-bin')

  const [rows, setRows] = useState<TestRecordItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [lotNo, setLotNo] = useState('')
  const [lotNoQ, setLotNoQ] = useState('')
  const [programName, setProgramName] = useState('')
  const [programQ, setProgramQ] = useState('')
  const [stage, setStage] = useState<TestStage | ''>('')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)
  const size = 20
  const totalPages = Math.max(1, Math.ceil(total / size))

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(newRecordForm)
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)
  const createGate = useRef(false)
  const createSeq = useRef(0)

  const [detail, setDetail] = useState<TestRecordItem | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [voidReason, setVoidReason] = useState('')

  const [bins, setBins] = useState<TestBinDefItem[]>([])
  const [binLoading, setBinLoading] = useState(false)
  const [binFailed, setBinFailed] = useState(false)
  const [binTypeFilter, setBinTypeFilter] = useState<BinType | ''>('')
  const [binOpen, setBinOpen] = useState(false)
  const [binEditing, setBinEditing] = useState<TestBinDefItem | null>(null)
  const [binForm, setBinForm] = useState<BinForm>(emptyBin)
  const [binError, setBinError] = useState('')
  const [savingBin, setSavingBin] = useState(false)
  const binGate = useRef(false)

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listTestRecordsApi({
        lotNo: lotNoQ,
        programName: programQ,
        stage,
        page,
        size,
      })
      setRows(data.records ?? [])
      setTotal(data.total ?? 0)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '测试记录加载失败')
    } finally {
      setLoading(false)
    }
  }, [lotNoQ, programQ, stage, page, toast])

  const loadBins = useCallback(async () => {
    setBinLoading(true)
    setBinFailed(false)
    try {
      setBins(await listTestBinsApi({ binType: binTypeFilter }))
    } catch (err) {
      setBins([])
      setBinFailed(true)
      toast.error(err instanceof ApiError ? err.message : 'Bin 字典加载失败')
    } finally {
      setBinLoading(false)
    }
  }, [binTypeFilter, toast])

  useEffect(() => {
    if (!canView || tab !== 'records') return
    void loadList()
  }, [canView, tab, loadList])

  useEffect(() => {
    if (!canView || tab !== 'bins') return
    void loadBins()
  }, [canView, tab, loadBins])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.test-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [tab])

  function applySearch() {
    setPage(1)
    setLotNoQ(lotNo.trim())
    setProgramQ(programName.trim())
  }

  async function openDetail(row: TestRecordItem) {
    setDetail(row)
    setVoidReason('')
    setDetailLoading(true)
    try {
      setDetail(await getTestRecordApi(row.id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载详情失败')
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitCreate() {
    if (createGate.current) return
    if (!createForm.lotNo.trim()) {
      setCreateError('批次号不能为空')
      return
    }
    if (!createForm.programName.trim() || !createForm.programVersion.trim()) {
      setCreateError('程序名和版本不能为空')
      return
    }
    const totalQty = Number(createForm.totalQty)
    if (!Number.isFinite(totalQty) || totalQty <= 0) {
      setCreateError('测试颗数必须大于 0')
      return
    }
    const lines = createForm.bins.filter((b) => b.binCode.trim())
    if (lines.length === 0) {
      setCreateError('请至少登记一档')
      return
    }
    if (hardSum(createForm.bins) !== totalQty) {
      setCreateError('硬档颗数之和必须等于总量')
      return
    }
    createGate.current = true
    const seq = ++createSeq.current
    setSavingCreate(true)
    setCreateError('')
    try {
      const lots = await listLotsApi({ keyword: createForm.lotNo.trim(), page: 1, size: 20 })
      const lot = (lots.records ?? []).find((l) => l.lotNo === createForm.lotNo.trim())
      if (!lot) {
        setCreateError('找不到该批次号')
        return
      }
      const created = await createTestRecordApi({
        lotId: lot.id,
        testStage: createForm.testStage,
        programName: createForm.programName.trim(),
        programVersion: createForm.programVersion.trim(),
        eqpCode: createForm.eqpCode.trim() || undefined,
        testTime: toApiTime(createForm.testTime),
        totalQty,
        sourceType: createForm.sourceType,
        sourceRef: createForm.sourceRef.trim() || undefined,
        remark: createForm.remark.trim() || undefined,
        bins: lines.map((b) => ({
          binType: b.binType,
          binCode: b.binCode.trim(),
          binQty: Number(b.binQty) || 0,
        })),
      })
      toast.success(`已登记 ${created.recordNo}`)
      setFlashId(String(created.id))
      setCreateOpen(false)
      setPage(1)
      await loadList()
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : '登记失败')
    } finally {
      if (createSeq.current === seq) {
        createGate.current = false
        setSavingCreate(false)
      }
    }
  }

  async function doVoid() {
    if (!detail || !canVoid) return
    if (!voidReason.trim()) {
      toast.error('作废原因不能为空')
      return
    }
    const ok = await confirm({
      title: '作废测试记录',
      message: `确认作废 ${detail.recordNo}？记录号不复用。`,
      confirmText: '作废',
      danger: true,
    })
    if (!ok) return
    try {
      await voidTestRecordApi(detail.id, voidReason.trim())
      toast.success('已作废')
      setDetail(null)
      await loadList()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '作废失败')
    }
  }

  function openBinCreate() {
    setBinEditing(null)
    setBinForm(emptyBin)
    setBinError('')
    setBinOpen(true)
  }

  function openBinEdit(row: TestBinDefItem) {
    setBinEditing(row)
    setBinForm({
      binScope: (row.binScope as BinScope) || 'GLOBAL',
      productCode: row.productCode ?? '',
      programName: row.programName ?? '',
      programVersion: row.programVersion ?? '',
      binType: (row.binType as BinType) || 'HARD',
      binCode: row.binCode,
      binName: row.binName,
      failureMode: row.failureMode ?? '',
      isShippable: row.isShippable === 1 ? 1 : 0,
      status: row.status === 0 ? 0 : 1,
      remark: row.remark ?? '',
    })
    setBinError('')
    setBinOpen(true)
  }

  async function submitBin() {
    if (binGate.current) return
    if (!binForm.binCode.trim() || !binForm.binName.trim()) {
      setBinError('档号和名称不能为空')
      return
    }
    binGate.current = true
    setSavingBin(true)
    setBinError('')
    const body = {
      binScope: binForm.binScope,
      productCode: binForm.binScope === 'GLOBAL' ? '' : binForm.productCode.trim(),
      programName:
        binForm.binScope === 'PROGRAM' || binForm.binScope === 'PROGRAM_VERSION'
          ? binForm.programName.trim()
          : '',
      programVersion: binForm.binScope === 'PROGRAM_VERSION' ? binForm.programVersion.trim() : '',
      binType: binForm.binType,
      binCode: binForm.binCode.trim(),
      binName: binForm.binName.trim(),
      failureMode: binForm.failureMode.trim() || undefined,
      isShippable: binForm.isShippable,
      status: binForm.status,
      remark: binForm.remark.trim() || undefined,
    }
    try {
      if (binEditing) {
        await updateTestBinApi(binEditing.id, { ...body, version: binEditing.version })
        toast.success('已保存 Bin')
      } else {
        const created = await createTestBinApi(body)
        toast.success(`已新增 ${created.binCode}`)
        setFlashId(String(created.id))
      }
      setBinOpen(false)
      await loadBins()
    } catch (err) {
      setBinError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      binGate.current = false
      setSavingBin(false)
    }
  }

  const qty = Number(createForm.totalQty) || 0
  const sum = hardSum(createForm.bins)
  const sumOk = qty > 0 && sum === qty

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">测试数据</h1>
        <p className="text-sm text-muted">无权限查看（需要 test:view）</p>
      </div>
    )
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="test-block flex flex-wrap items-end justify-between gap-3">
        <div className="flex items-start gap-3">
          <div
            className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-accent"
            aria-hidden
          >
            <ClipboardList className="size-5" strokeWidth={1.75} />
          </div>
          <div>
            <h1 className="text-xl font-semibold tracking-tight">测试数据</h1>
            <p className="mt-1 text-sm text-muted">测试记录 · Bin 字典</p>
          </div>
        </div>
        {tab === 'records' && canCreate ? (
          <Button
            onClick={() => {
              setCreateForm(newRecordForm())
              setCreateError('')
              setCreateOpen(true)
            }}
          >
            <Plus className="size-4" aria-hidden />
            登记记录
          </Button>
        ) : null}
        {tab === 'bins' && canEditBin ? (
          <Button onClick={openBinCreate}>
            <Plus className="size-4" aria-hidden />
            新增 Bin
          </Button>
        ) : null}
      </header>

      <div className="test-block flex gap-1 border-b border-border" role="tablist" aria-label="测试数据分区">
        {(
          [
            { key: 'records' as const, label: '测试记录' },
            { key: 'bins' as const, label: 'Bin 字典' },
          ] as const
        ).map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              'relative h-10 cursor-pointer px-3 text-sm font-medium transition-colors duration-150',
              tab === t.key ? 'text-ink' : 'text-muted hover:text-ink',
            )}
          >
            {t.label}
            {tab === t.key ? (
              <span className="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-primary" aria-hidden />
            ) : null}
          </button>
        ))}
      </div>

      {tab === 'records' ? (
        <>
          <div className="test-block flex flex-wrap items-center gap-2">
            <input
              className="h-9 w-40 rounded-md border border-border bg-bg px-3 font-mono text-sm"
              placeholder="批次号"
              value={lotNo}
              onChange={(e) => setLotNo(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && applySearch()}
              aria-label="批次号"
            />
            <input
              className="h-9 w-40 rounded-md border border-border bg-bg px-3 text-sm"
              placeholder="程序名"
              value={programName}
              onChange={(e) => setProgramName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && applySearch()}
              aria-label="程序名"
            />
            <Button variant="secondary" onClick={applySearch}>
              <Search className="size-4" aria-hidden />
              搜索
            </Button>
            <div className="flex flex-wrap gap-1.5 sm:ml-2">
              {(['' as const, 'CP' as const, 'FT' as const, 'OTHER' as const] as const).map((s) => (
                <button
                  key={s || 'all'}
                  type="button"
                  onClick={() => {
                    setStage(s)
                    setPage(1)
                  }}
                  className={cn(
                    'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
                    stage === s
                      ? 'border-accent bg-accent/10 text-accent'
                      : 'border-border bg-bg text-muted hover:bg-surface',
                  )}
                >
                  {s ? STAGE_LABEL[s] : '全部'}
                </button>
              ))}
            </div>
          </div>

          <div className="test-block overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[960px] text-left text-sm">
              <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
                <tr className="h-10 border-b border-border">
                  <th className="px-3">记录号</th>
                  <th className="px-3">批次</th>
                  <th className="px-3">阶段</th>
                  <th className="px-3">程序</th>
                  <th className="px-3">设备</th>
                  <th className="px-3">测试时间</th>
                  <th className="px-3">颗数</th>
                  <th className="px-3">来源</th>
                  <th className="px-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  Array.from({ length: 6 }).map((_, i) => (
                    <tr key={i} className="h-10 border-b border-border/60">
                      {Array.from({ length: 9 }).map((__, j) => (
                        <td key={j} className="px-3">
                          <div className="h-3.5 w-[72%] max-w-[140px] animate-pulse rounded-sm bg-surface" />
                        </td>
                      ))}
                    </tr>
                  ))
                ) : loadFailed ? (
                  <tr>
                    <td colSpan={9} className="px-3 py-10 text-center">
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
                    <td colSpan={9} className="px-3 py-12 text-center">
                      <div className="mx-auto flex max-w-sm flex-col items-center gap-2">
                        <div className="flex size-11 items-center justify-center rounded-md border border-border bg-surface text-muted">
                          <ClipboardList className="size-5" aria-hidden />
                        </div>
                        <p className="text-sm text-ink">还没有测试记录</p>
                        <p className="text-xs text-muted">登记一次测试结果与各档颗数，硬档合计须等于总量</p>
                        {canCreate ? (
                          <Button
                            className="mt-2"
                            onClick={() => {
                              setCreateForm(newRecordForm())
                              setCreateOpen(true)
                            }}
                          >
                            <Plus className="size-4" aria-hidden />
                            登记记录
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ) : (
                  rows.map((row) => (
                    <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                      <td className="h-10 px-3 font-mono text-[13px] text-accent">{row.recordNo}</td>
                      <td className="px-3 font-mono text-[13px]">{row.lotNo}</td>
                      <td className="px-3">{STAGE_LABEL[row.testStage] ?? row.testStage}</td>
                      <td className="px-3">
                        <span className="font-mono text-[13px]">{row.programName}</span>
                        <span className="ml-1 text-xs text-muted">v{row.programVersion}</span>
                      </td>
                      <td className="px-3 font-mono text-[13px] text-muted">{row.eqpCode || '—'}</td>
                      <td className="px-3 font-mono text-[12px] text-muted">{fmtTime(row.testTime)}</td>
                      <td className="px-3 font-mono text-[13px]">{row.totalQty}</td>
                      <td className="px-3 text-xs">{SOURCE_LABEL[row.sourceType] ?? row.sourceType}</td>
                      <td className="px-3">
                        <TableAction icon={Eye} label="详情" onClick={() => void openDetail(row)} />
                      </td>
                    </FlashRow>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="test-block flex items-center justify-between text-sm text-muted">
            <span>
              共 {total} 条 · 第 {page}/{totalPages} 页
            </span>
            <div className="flex gap-2">
              <Button variant="secondary" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
                上一页
              </Button>
              <Button variant="secondary" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>
                下一页
              </Button>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="test-block flex flex-wrap gap-1.5">
            {(['' as const, 'HARD' as const, 'SOFT' as const] as const).map((s) => (
              <button
                key={s || 'all'}
                type="button"
                onClick={() => setBinTypeFilter(s)}
                className={cn(
                  'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium',
                  binTypeFilter === s
                    ? 'border-accent bg-accent/10 text-accent'
                    : 'border-border bg-bg text-muted hover:bg-surface',
                )}
              >
                {s === 'HARD' ? '硬档' : s === 'SOFT' ? '软档' : '全部'}
              </button>
            ))}
          </div>
          <div className="test-block overflow-x-auto rounded-md border border-border">
            <table className="w-full min-w-[880px] text-left text-sm">
              <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
                <tr className="h-10 border-b border-border">
                  <th className="px-3">范围</th>
                  <th className="px-3">档号</th>
                  <th className="px-3">名称</th>
                  <th className="px-3">类型</th>
                  <th className="px-3">可出货</th>
                  <th className="px-3">状态</th>
                  <th className="px-3">操作</th>
                </tr>
              </thead>
              <tbody>
                {binLoading ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <tr key={i} className="h-10 border-b border-border/60">
                      {Array.from({ length: 7 }).map((__, j) => (
                        <td key={j} className="px-3">
                          <div className="h-3.5 w-[60%] animate-pulse rounded-sm bg-surface" />
                        </td>
                      ))}
                    </tr>
                  ))
                ) : binFailed ? (
                  <tr>
                    <td colSpan={7} className="px-3 py-10 text-center text-sm text-muted">
                      加载失败{' '}
                      <button type="button" className="text-accent hover:underline" onClick={() => void loadBins()}>
                        重试
                      </button>
                    </td>
                  </tr>
                ) : bins.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-3 py-12 text-center text-sm text-muted">
                      还没有 Bin 定义
                    </td>
                  </tr>
                ) : (
                  bins.map((row) => (
                    <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                      <td className="h-10 px-3 text-xs">{SCOPE_LABEL[row.binScope] ?? row.binScope}</td>
                      <td className="px-3 font-mono text-[13px] text-accent">{row.binCode}</td>
                      <td className="px-3">{row.binName}</td>
                      <td className="px-3 text-xs">{row.binType === 'HARD' ? '硬档' : '软档'}</td>
                      <td className="px-3 text-xs">{row.isShippable === 1 ? '可出货' : '不可出货'}</td>
                      <td className="px-3 text-xs">{row.status === 1 ? '启用' : '停用'}</td>
                      <td className="px-3">
                        {canEditBin ? (
                          <TableAction icon={Eye} label="维护" onClick={() => openBinEdit(row)} />
                        ) : null}
                      </td>
                    </FlashRow>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </>
      )}

      <Drawer
        open={createOpen}
        onClose={() => !savingCreate && setCreateOpen(false)}
        title="登记测试记录"
        size="lg"
        footer={
          <>
            <Button variant="secondary" disabled={savingCreate} onClick={() => setCreateOpen(false)}>
              取消
            </Button>
            <Button disabled={savingCreate} onClick={() => void submitCreate()}>
              {savingCreate ? '提交中…' : '提交'}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field
              label="批次号"
              name="lotNo"
              className="font-mono"
              value={createForm.lotNo}
              onChange={(e) => setCreateForm((f) => ({ ...f, lotNo: e.target.value }))}
            />
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted">阶段</span>
              <select
                className="h-9 rounded-md border border-border bg-bg px-3 text-sm"
                value={createForm.testStage}
                onChange={(e) => setCreateForm((f) => ({ ...f, testStage: e.target.value as TestStage }))}
              >
                <option value="CP">晶圆测试</option>
                <option value="FT">成品测试</option>
                <option value="OTHER">其他</option>
              </select>
            </label>
            <Field
              label="程序名"
              name="programName"
              value={createForm.programName}
              onChange={(e) => setCreateForm((f) => ({ ...f, programName: e.target.value }))}
            />
            <Field
              label="程序版本"
              name="programVersion"
              className="font-mono"
              value={createForm.programVersion}
              onChange={(e) => setCreateForm((f) => ({ ...f, programVersion: e.target.value }))}
            />
            <Field
              label="设备编码（可空）"
              name="eqpCode"
              className="font-mono"
              value={createForm.eqpCode}
              onChange={(e) => setCreateForm((f) => ({ ...f, eqpCode: e.target.value }))}
            />
            <Field
              label="测试完成时间"
              name="testTime"
              type="datetime-local"
              value={createForm.testTime}
              onChange={(e) => setCreateForm((f) => ({ ...f, testTime: e.target.value }))}
            />
            <Field
              label="测试颗数"
              name="totalQty"
              type="number"
              min={1}
              className="font-mono"
              value={createForm.totalQty}
              onChange={(e) => setCreateForm((f) => ({ ...f, totalQty: e.target.value }))}
            />
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted">来源</span>
              <select
                className="h-9 rounded-md border border-border bg-bg px-3 text-sm"
                value={createForm.sourceType}
                onChange={(e) => setCreateForm((f) => ({ ...f, sourceType: e.target.value as TestSource }))}
              >
                <option value="MANUAL">手工</option>
                <option value="FILE">文件</option>
                <option value="API">接口</option>
              </select>
            </label>
          </div>
          <Field
            label="备注"
            name="remark"
            value={createForm.remark}
            onChange={(e) => setCreateForm((f) => ({ ...f, remark: e.target.value }))}
          />
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted">分档</span>
              <span className={cn('font-mono text-xs', sumOk ? 'text-success' : 'text-danger')}>
                硬档合计 {sum} / 总量 {qty || '—'}
              </span>
            </div>
            {createForm.bins.map((line, i) => (
              <div key={i} className="grid grid-cols-[88px_1fr_88px] gap-2">
                <select
                  className="h-9 rounded-md border border-border bg-bg px-2 text-sm"
                  value={line.binType}
                  onChange={(e) =>
                    setCreateForm((f) => {
                      const bins = [...f.bins]
                      bins[i] = { ...bins[i], binType: e.target.value as BinType }
                      return { ...f, bins }
                    })
                  }
                >
                  <option value="HARD">硬档</option>
                  <option value="SOFT">软档</option>
                </select>
                <input
                  className="h-9 rounded-md border border-border bg-bg px-3 font-mono text-sm"
                  placeholder="档号"
                  value={line.binCode}
                  onChange={(e) =>
                    setCreateForm((f) => {
                      const bins = [...f.bins]
                      bins[i] = { ...bins[i], binCode: e.target.value }
                      return { ...f, bins }
                    })
                  }
                />
                <input
                  className="h-9 rounded-md border border-border bg-bg px-3 font-mono text-sm"
                  type="number"
                  min={0}
                  placeholder="颗数"
                  value={line.binQty}
                  onChange={(e) =>
                    setCreateForm((f) => {
                      const bins = [...f.bins]
                      bins[i] = { ...bins[i], binQty: e.target.value }
                      return { ...f, bins }
                    })
                  }
                />
              </div>
            ))}
            <Button
              variant="secondary"
              onClick={() =>
                setCreateForm((f) => ({
                  ...f,
                  bins: [...f.bins, { binType: 'HARD', binCode: '', binQty: '' }],
                }))
              }
            >
              加一档
            </Button>
          </div>
          {createError ? <p className="text-sm text-danger">{createError}</p> : null}
        </div>
      </Drawer>

      <Drawer
        open={!!detail}
        onClose={() => setDetail(null)}
        title={detail ? `记录 · ${detail.recordNo}` : '测试记录'}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setDetail(null)}>
              关闭
            </Button>
            {canVoid ? (
              <Button variant="danger" onClick={() => void doVoid()}>
                <Ban className="size-4" aria-hidden />
                作废
              </Button>
            ) : null}
          </>
        }
      >
        {detailLoading || !detail ? (
          <p className="text-sm text-muted">加载中…</p>
        ) : (
          <div className="space-y-4">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs text-muted">批次</dt>
                <dd className="mt-0.5 font-mono">{detail.lotNo}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">阶段</dt>
                <dd className="mt-0.5">{STAGE_LABEL[detail.testStage] ?? detail.testStage}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">程序</dt>
                <dd className="mt-0.5 font-mono">
                  {detail.programName} v{detail.programVersion}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted">颗数</dt>
                <dd className="mt-0.5 font-mono">{detail.totalQty}</dd>
              </div>
            </dl>
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full text-left text-sm">
                <thead className="bg-surface text-xs text-muted">
                  <tr className="h-9 border-b border-border">
                    <th className="px-3">档</th>
                    <th className="px-3">名称</th>
                    <th className="px-3">颗数</th>
                    <th className="px-3">占比</th>
                    <th className="px-3">出货</th>
                  </tr>
                </thead>
                <tbody>
                  {(detail.bins ?? []).map((b, i) => {
                    const pct = Math.round(Number(b.ratio ?? 0) * 1000) / 10
                    return (
                      <tr key={`${b.binType}-${b.binCode}-${i}`} className="h-9 border-b border-border">
                        <td className="px-3 font-mono text-[13px]">
                          {b.binType === 'HARD' ? 'H' : 'S'} {b.binCode}
                        </td>
                        <td className="px-3">{b.binName || '—'}</td>
                        <td className="px-3 font-mono text-[13px]">{b.binQty}</td>
                        <td className="px-3">
                          <div className="flex items-center gap-2">
                            <div className="h-1.5 w-20 overflow-hidden rounded-sm bg-surface">
                              <div
                                className={cn('h-full', b.isShippable === 1 ? 'bg-success' : 'bg-muted')}
                                style={{ width: `${Math.min(100, pct)}%` }}
                              />
                            </div>
                            <span className="font-mono text-[12px] text-muted">{pct}%</span>
                          </div>
                        </td>
                        <td className="px-3 text-xs">{b.isShippable === 1 ? '可' : '否'}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            {canVoid ? (
              <Field
                label="作废原因"
                name="voidReason"
                value={voidReason}
                onChange={(e) => setVoidReason(e.target.value)}
              />
            ) : null}
          </div>
        )}
      </Drawer>

      <Drawer
        open={binOpen}
        onClose={() => !savingBin && setBinOpen(false)}
        title={binEditing ? '维护 Bin' : '新增 Bin'}
        size="md"
        footer={
          <>
            <Button variant="secondary" disabled={savingBin} onClick={() => setBinOpen(false)}>
              取消
            </Button>
            <Button disabled={savingBin} onClick={() => void submitBin()}>
              {savingBin ? '保存中…' : '保存'}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">适用范围</span>
            <select
              className="h-9 rounded-md border border-border bg-bg px-3 text-sm"
              value={binForm.binScope}
              onChange={(e) => setBinForm((f) => ({ ...f, binScope: e.target.value as BinScope }))}
            >
              <option value="GLOBAL">全局</option>
              <option value="PRODUCT">产品</option>
              <option value="PROGRAM">程序</option>
              <option value="PROGRAM_VERSION">程序版本</option>
            </select>
          </label>
          {binForm.binScope !== 'GLOBAL' ? (
            <Field
              label="产品编码"
              name="productCode"
              className="font-mono"
              value={binForm.productCode}
              onChange={(e) => setBinForm((f) => ({ ...f, productCode: e.target.value }))}
            />
          ) : null}
          {binForm.binScope === 'PROGRAM' || binForm.binScope === 'PROGRAM_VERSION' ? (
            <Field
              label="程序名"
              name="binProgram"
              value={binForm.programName}
              onChange={(e) => setBinForm((f) => ({ ...f, programName: e.target.value }))}
            />
          ) : null}
          {binForm.binScope === 'PROGRAM_VERSION' ? (
            <Field
              label="程序版本"
              name="binVer"
              className="font-mono"
              value={binForm.programVersion}
              onChange={(e) => setBinForm((f) => ({ ...f, programVersion: e.target.value }))}
            />
          ) : null}
          <div className="grid gap-3 sm:grid-cols-2">
            <Field
              label="档号"
              name="binCode"
              className="font-mono"
              value={binForm.binCode}
              onChange={(e) => setBinForm((f) => ({ ...f, binCode: e.target.value }))}
            />
            <Field
              label="名称"
              name="binName"
              value={binForm.binName}
              onChange={(e) => setBinForm((f) => ({ ...f, binName: e.target.value }))}
            />
          </div>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">类型</span>
            <select
              className="h-9 rounded-md border border-border bg-bg px-3 text-sm"
              value={binForm.binType}
              onChange={(e) => setBinForm((f) => ({ ...f, binType: e.target.value as BinType }))}
            >
              <option value="HARD">硬档</option>
              <option value="SOFT">软档</option>
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-[oklch(0.62_0.14_145)]"
              checked={binForm.isShippable === 1}
              onChange={(e) => setBinForm((f) => ({ ...f, isShippable: e.target.checked ? 1 : 0 }))}
            />
            可出货
          </label>
          {binEditing ? (
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="size-4"
                checked={binForm.status === 1}
                onChange={(e) => setBinForm((f) => ({ ...f, status: e.target.checked ? 1 : 0 }))}
              />
              启用
            </label>
          ) : null}
          {binError ? <p className="text-sm text-danger">{binError}</p> : null}
        </div>
      </Drawer>
    </div>
  )
}
