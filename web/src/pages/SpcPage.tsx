import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import gsap from 'gsap'
import { Activity, Pencil, Plus, Power, Search } from 'lucide-react'
import { listEdcParamsApi, type MesEdcParamItem } from '../api/edc'
import { listEqpOptionsApi, type MesEqpOption } from '../api/eqp'
import { listStepsApi, type MesStepItem } from '../api/route'
import {
  getSpcSeriesApi,
  listSpcChartsApi,
  saveSpcChartApi,
  setSpcEnabledApi,
  setSpcLimitsApi,
  type SpcChartItem,
  type SpcChartSaveBody,
  type SpcSeries,
} from '../api/spc'
import { useAuth } from '../auth/AuthContext'
import { SpcTrendCharts } from '../components/spc/SpcTrendCharts'
import { Button } from '../components/ui/Button'
import { useConfirm } from '../components/ui/ConfirmDialog'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

type EnabledFilter = 'all' | 0 | 1

type ChartForm = {
  id?: string
  paramId: string
  stepId: string
  eqpId: string
  limitMode: 'LEARNING' | 'MANUAL'
  learningN: string
  runN: string
  ucl: string
  cl: string
  lcl: string
  enabled: 0 | 1
}

const emptyForm = (): ChartForm => ({
  paramId: '',
  stepId: '',
  eqpId: '',
  limitMode: 'LEARNING',
  learningN: '25',
  runN: '7',
  ucl: '',
  cl: '',
  lcl: '',
  enabled: 1,
})

function fmtNum(v: number | string | null | undefined) {
  if (v == null || v === '') return '—'
  return String(v)
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function isNoneEqp(id: number | string | null | undefined) {
  return id == null || id === '' || Number(id) === 0
}

function parseOptionalNum(s: string): number | null {
  const t = s.trim()
  if (!t) return null
  const n = Number(t)
  return Number.isFinite(n) ? n : null
}

const selectCls =
  'h-9 w-full rounded-md border border-border bg-bg px-3 text-sm text-ink focus:border-accent disabled:cursor-not-allowed disabled:opacity-60'

export function SpcPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const rootRef = useRef<HTMLDivElement>(null)

  const canView = hasPermission('spc:view')
  const canEdit = hasPermission('spc:edit')

  const [steps, setSteps] = useState<MesStepItem[]>([])
  const [params, setParams] = useState<MesEdcParamItem[]>([])
  const [eqps, setEqps] = useState<MesEqpOption[]>([])

  const [filterStepId, setFilterStepId] = useState('')
  const [filterParamId, setFilterParamId] = useState('')
  const [filterEnabled, setFilterEnabled] = useState<EnabledFilter>('all')

  const [charts, setCharts] = useState<SpcChartItem[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const [series, setSeries] = useState<SpcSeries | null>(null)
  const [seriesLoading, setSeriesLoading] = useState(false)
  const [highlightIdx, setHighlightIdx] = useState<number | null>(null)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form, setForm] = useState<ChartForm>(emptyForm)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState(false)

  const stepMap = useMemo(() => new Map(steps.map((s) => [String(s.id), s])), [steps])
  const paramMap = useMemo(() => new Map(params.map((p) => [String(p.id), p])), [params])
  const eqpMap = useMemo(() => new Map(eqps.map((e) => [String(e.id), e])), [eqps])

  const visibleCharts = useMemo(() => {
    if (filterEnabled === 'all') return charts
    return charts.filter((c) => Number(c.enabled) === filterEnabled)
  }, [charts, filterEnabled])

  const selected = useMemo(
    () => charts.find((c) => String(c.id) === selectedId) ?? null,
    [charts, selectedId],
  )

  function labelStep(id: number | string | null | undefined) {
    if (id == null) return '—'
    const s = stepMap.get(String(id))
    return s ? `${s.stepCode} ${s.stepName}` : String(id)
  }

  function labelParam(id: number | string | null | undefined) {
    if (id == null) return '—'
    const p = paramMap.get(String(id))
    return p ? `${p.paramCode} ${p.paramName}` : String(id)
  }

  function labelEqp(id: number | string | null | undefined) {
    if (isNoneEqp(id)) return '全机台'
    const e = eqpMap.get(String(id))
    return e ? `${e.eqpCode} ${e.eqpName}` : String(id)
  }

  const loadOptions = useCallback(async () => {
    try {
      const [stepPage, paramPage, eqpList] = await Promise.all([
        listStepsApi({ status: 1, page: 1, size: 200 }),
        listEdcParamsApi({ enabled: 1, page: 1, size: 200 }),
        listEqpOptionsApi(),
      ])
      setSteps(stepPage.records ?? [])
      setParams(paramPage.records ?? [])
      setEqps(eqpList ?? [])
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '选项加载失败')
    }
  }, [toast])

  const loadCharts = useCallback(async () => {
    setListLoading(true)
    try {
      const rows = await listSpcChartsApi({
        stepId: filterStepId || undefined,
        paramId: filterParamId || undefined,
      })
      setCharts(rows ?? [])
    } catch (err) {
      setCharts([])
      toast.error(err instanceof ApiError ? err.message : '图列表加载失败')
    } finally {
      setListLoading(false)
    }
  }, [filterParamId, filterStepId, toast])

  const loadSeries = useCallback(
    async (id: string) => {
      setSeriesLoading(true)
      setHighlightIdx(null)
      try {
        const data = await getSpcSeriesApi(id, { limit: 100 })
        setSeries(data)
      } catch (err) {
        setSeries(null)
        toast.error(err instanceof ApiError ? err.message : '趋势加载失败')
      } finally {
        setSeriesLoading(false)
      }
    },
    [toast],
  )

  useEffect(() => {
    if (!canView) return
    void loadOptions()
  }, [canView, loadOptions])

  useEffect(() => {
    if (!canView) return
    void loadCharts()
  }, [canView, loadCharts])

  useEffect(() => {
    if (!selectedId) {
      setSeries(null)
      return
    }
    if (!visibleCharts.some((c) => String(c.id) === selectedId)) {
      setSelectedId(null)
      setSeries(null)
      return
    }
    void loadSeries(selectedId)
  }, [selectedId, visibleCharts, loadSeries])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.spc-block', {
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
    setForm(emptyForm())
    setFormError('')
    setDrawerOpen(true)
  }

  function openEdit(row: SpcChartItem) {
    setForm({
      id: String(row.id),
      paramId: String(row.paramId),
      stepId: String(row.stepId),
      eqpId: isNoneEqp(row.eqpId) ? '' : String(row.eqpId),
      limitMode: row.limitMode === 'MANUAL' ? 'MANUAL' : 'LEARNING',
      learningN: String(row.learningN ?? 25),
      runN: String(row.runN ?? 7),
      ucl: row.ucl == null ? '' : String(row.ucl),
      cl: row.cl == null ? '' : String(row.cl),
      lcl: row.lcl == null ? '' : String(row.lcl),
      enabled: Number(row.enabled) === 1 ? 1 : 0,
    })
    setFormError('')
    setDrawerOpen(true)
  }

  async function submitForm() {
    if (!form.paramId) {
      setFormError('请选择特性')
      return
    }
    if (!form.stepId) {
      setFormError('请选择站点')
      return
    }
    if (form.limitMode === 'MANUAL') {
      if (parseOptionalNum(form.ucl) == null || parseOptionalNum(form.cl) == null || parseOptionalNum(form.lcl) == null) {
        setFormError('MANUAL 需填 UCL / CL / LCL')
        return
      }
    }
    setSaving(true)
    setFormError('')
    try {
      const body: SpcChartSaveBody = {
        id: form.id,
        paramId: form.paramId,
        stepId: form.stepId,
        eqpId: form.eqpId ? form.eqpId : null,
        limitMode: form.limitMode,
        learningN: Number(form.learningN) || 25,
        runN: Number(form.runN),
        ucl: parseOptionalNum(form.ucl),
        cl: parseOptionalNum(form.cl),
        lcl: parseOptionalNum(form.lcl),
        enabled: form.enabled,
      }
      const saved = await saveSpcChartApi(body)
      if (form.id && (form.ucl || form.cl || form.lcl)) {
        await setSpcLimitsApi(saved.id, {
          ucl: parseOptionalNum(form.ucl),
          cl: parseOptionalNum(form.cl),
          lcl: parseOptionalNum(form.lcl),
        })
      }
      toast.success(form.id ? '已保存' : '已新建')
      setDrawerOpen(false)
      await loadCharts()
      setSelectedId(String(saved.id))
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function toggleEnabled(row: SpcChartItem) {
    if (!canEdit) return
    const next = Number(row.enabled) === 1 ? 0 : 1
    const ok = await confirm({
      title: next === 1 ? '启用此图？' : '停用此图？',
      message: next === 1 ? '启用后采集会继续判异。' : '停用后不再判异，图和限保留。',
    })
    if (!ok) return
    setToggling(true)
    try {
      await setSpcEnabledApi(row.id, next as 0 | 1)
      toast.success(next === 1 ? '已启用' : '已停用')
      await loadCharts()
      if (selectedId === String(row.id)) await loadSeries(String(row.id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '启停失败')
    } finally {
      setToggling(false)
    }
  }

  if (!canView) {
    return (
      <div className="rounded-md border border-border bg-surface px-4 py-8 text-center text-sm text-muted">
        无 spc:view 权限
      </div>
    )
  }

  const chart = series?.chart ?? selected
  const lastEval = series?.lastEval
  const showCpk = series?.cpk != null && series.cpk !== ''

  return (
    <div ref={rootRef} className="flex h-[calc(100vh-7.5rem)] min-h-[520px] flex-col gap-3">
      <header className="spc-block flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Activity className="size-5 text-accent" aria-hidden />
          <h1 className="text-lg font-semibold text-ink">趋势（SPC）</h1>
        </div>
        {canEdit ? (
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            新建图
          </Button>
        ) : null}
      </header>

      <div className="spc-block grid min-h-0 flex-1 grid-cols-1 gap-3 lg:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="flex min-h-0 flex-col rounded-md border border-border bg-bg">
          <div className="space-y-2 border-b border-border p-3">
            <label className="flex flex-col gap-1 text-xs text-muted">
              站点
              <select className={selectCls} value={filterStepId} onChange={(e) => setFilterStepId(e.target.value)}>
                <option value="">全部</option>
                {steps.map((s) => (
                  <option key={String(s.id)} value={String(s.id)}>
                    {s.stepCode} {s.stepName}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-xs text-muted">
              特性
              <select className={selectCls} value={filterParamId} onChange={(e) => setFilterParamId(e.target.value)}>
                <option value="">全部</option>
                {params.map((p) => (
                  <option key={String(p.id)} value={String(p.id)}>
                    {p.paramCode} {p.paramName}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-xs text-muted">
              启停
              <select
                className={selectCls}
                value={String(filterEnabled)}
                onChange={(e) => {
                  const v = e.target.value
                  setFilterEnabled(v === 'all' ? 'all' : (Number(v) as 0 | 1))
                }}
              >
                <option value="all">全部</option>
                <option value="1">启用</option>
                <option value="0">停用</option>
              </select>
            </label>
            <Button variant="secondary" className="w-full" onClick={() => void loadCharts()}>
              <Search className="size-4" />
              刷新
            </Button>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto">
            {listLoading ? (
              <p className="px-3 py-6 text-center text-sm text-muted">加载中…</p>
            ) : visibleCharts.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-muted">暂无图</p>
            ) : (
              <ul className="divide-y divide-border">
                {visibleCharts.map((row) => {
                  const active = String(row.id) === selectedId
                  return (
                    <li key={String(row.id)}>
                      <button
                        type="button"
                        onClick={() => setSelectedId(String(row.id))}
                        className={cn(
                          'w-full px-3 py-2.5 text-left transition-colors',
                          active ? 'bg-accent/10' : 'hover:bg-surface',
                        )}
                      >
                        <div className="truncate text-sm font-medium text-ink">{labelParam(row.paramId)}</div>
                        <div className="mt-0.5 truncate text-xs text-muted">
                          {labelStep(row.stepId)} · {labelEqp(row.eqpId)}
                        </div>
                        <div className="mt-1 flex items-center gap-2 text-[11px] text-muted">
                          <span
                            className={cn(
                              'inline-flex items-center gap-1 rounded-sm bg-surface px-1.5 py-0.5',
                              Number(row.enabled) === 1 ? 'text-ink' : 'text-muted',
                            )}
                          >
                            <span
                              className={cn(
                                'size-1.5 rounded-full',
                                Number(row.enabled) === 1 ? 'bg-success' : 'bg-border',
                              )}
                            />
                            {Number(row.enabled) === 1 ? '启用' : '停用'}
                          </span>
                          <span>{row.limitMode}</span>
                          <span>n={row.n ?? 0}</span>
                        </div>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </aside>

        <main className="flex min-h-0 flex-col gap-3 overflow-y-auto">
          {!selectedId || !chart ? (
            <div className="flex flex-1 items-center justify-center rounded-md border border-dashed border-border text-sm text-muted">
              从左侧选一张图
            </div>
          ) : (
            <>
              <section className="rounded-md border border-border bg-bg p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 space-y-1">
                    <h2 className="truncate text-base font-semibold text-ink">{labelParam(chart.paramId)}</h2>
                    <p className="text-sm text-muted">
                      {labelStep(chart.stepId)} · {labelEqp(chart.eqpId)} · I-MR · {chart.limitMode}
                    </p>
                    <p className="text-xs text-muted">
                      UCL {fmtNum(chart.ucl)} · CL {fmtNum(chart.cl)} · LCL {fmtNum(chart.lcl)}
                      {showCpk ? ` · Cpk ${fmtNum(series?.cpk)}` : ''}
                      {series?.cp != null && series.cp !== '' ? ` · Cp ${fmtNum(series.cp)}` : ''}
                    </p>
                    {lastEval ? (
                      <p className="text-xs text-danger">
                        最近 OOC：{lastEval.ruleCode} · {fmtTime(lastEval.createTime)}
                      </p>
                    ) : (
                      <p className="text-xs text-muted">最近无 OOC</p>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {canEdit ? (
                      <>
                        <Button variant="secondary" onClick={() => openEdit(chart)} disabled={toggling}>
                          <Pencil className="size-4" />
                          维护
                        </Button>
                        <Button variant="secondary" onClick={() => void toggleEnabled(chart)} loading={toggling}>
                          <Power className="size-4" />
                          {Number(chart.enabled) === 1 ? '停用' : '启用'}
                        </Button>
                      </>
                    ) : null}
                  </div>
                </div>
              </section>

              {seriesLoading ? (
                <div className="rounded-md border border-border px-4 py-16 text-center text-sm text-muted">加载趋势…</div>
              ) : series ? (
                <>
                  <SpcTrendCharts series={series} highlightIdx={highlightIdx} onSelectIdx={setHighlightIdx} />
                  <section className="rounded-md border border-border">
                    <header className="border-b border-border px-4 py-2.5 text-sm font-medium">点表</header>
                    <div className="max-h-56 overflow-auto">
                      <table className="w-full text-left text-sm">
                        <thead className="sticky top-0 bg-surface text-xs text-muted">
                          <tr>
                            <th className="px-3 py-2 font-medium">#</th>
                            <th className="px-3 py-2 font-medium">时间</th>
                            <th className="px-3 py-2 font-medium">Lot</th>
                            <th className="px-3 py-2 font-medium">值</th>
                            <th className="px-3 py-2 font-medium">EDC</th>
                            <th className="px-3 py-2 font-medium">OOC</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(series.points ?? []).map((p, i) => {
                            const idx = i + 1
                            const active = highlightIdx === idx
                            return (
                              <tr
                                key={String(p.itemId)}
                                className={cn(
                                  'cursor-pointer border-t border-border',
                                  active ? 'bg-accent/10' : 'hover:bg-surface',
                                )}
                                onClick={() => setHighlightIdx(idx)}
                              >
                                <td className="px-3 py-1.5">{idx}</td>
                                <td className="px-3 py-1.5 whitespace-nowrap">{fmtTime(p.time)}</td>
                                <td className="px-3 py-1.5">{p.lotNo ?? '—'}</td>
                                <td className="px-3 py-1.5">{fmtNum(p.value)}</td>
                                <td className="px-3 py-1.5">
                                  {p.itemResult === 'OOS' ? (
                                    <span className="text-warning">OOS</span>
                                  ) : (
                                    p.itemResult ?? '—'
                                  )}
                                </td>
                                <td className="px-3 py-1.5">
                                  {p.evalOoc ? <span className="font-medium text-danger">是</span> : '—'}
                                </td>
                              </tr>
                            )
                          })}
                        </tbody>
                      </table>
                      {(series.points ?? []).length === 0 ? (
                        <p className="px-3 py-6 text-center text-sm text-muted">暂无点</p>
                      ) : null}
                    </div>
                  </section>
                </>
              ) : (
                <div className="rounded-md border border-border px-4 py-16 text-center text-sm text-muted">无趋势数据</div>
              )}
            </>
          )}
        </main>
      </div>

      <Drawer
        open={drawerOpen}
        title={form.id ? '维护图' : '新建图'}
        onClose={() => setDrawerOpen(false)}
        width={480}
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDrawerOpen(false)}>
              取消
            </Button>
            <Button loading={saving} onClick={() => void submitForm()}>
              保存
            </Button>
          </div>
        }
      >
        <div className="space-y-3">
          {formError ? <p className="text-sm text-danger">{formError}</p> : null}
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">特性</span>
            <select
              className={selectCls}
              value={form.paramId}
              disabled={!!form.id}
              onChange={(e) => setForm((f) => ({ ...f, paramId: e.target.value }))}
            >
              <option value="">请选择</option>
              {params.map((p) => (
                <option key={String(p.id)} value={String(p.id)}>
                  {p.paramCode} {p.paramName}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">站点</span>
            <select
              className={selectCls}
              value={form.stepId}
              disabled={!!form.id}
              onChange={(e) => setForm((f) => ({ ...f, stepId: e.target.value }))}
            >
              <option value="">请选择</option>
              {steps.map((s) => (
                <option key={String(s.id)} value={String(s.id)}>
                  {s.stepCode} {s.stepName}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">机台（空=全机台）</span>
            <select
              className={selectCls}
              value={form.eqpId}
              disabled={!!form.id}
              onChange={(e) => setForm((f) => ({ ...f, eqpId: e.target.value }))}
            >
              <option value="">全机台</option>
              {eqps.map((e) => (
                <option key={String(e.id)} value={String(e.id)}>
                  {e.eqpCode} {e.eqpName}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">限模式</span>
            <select
              className={selectCls}
              value={form.limitMode}
              onChange={(e) =>
                setForm((f) => ({ ...f, limitMode: e.target.value === 'MANUAL' ? 'MANUAL' : 'LEARNING' }))
              }
            >
              <option value="LEARNING">LEARNING（攒点算限）</option>
              <option value="MANUAL">MANUAL（手填限）</option>
            </select>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <Field
              label="learningN"
              value={form.learningN}
              onChange={(e) => setForm((f) => ({ ...f, learningN: e.target.value }))}
            />
            <Field
              label="runN（0=关）"
              value={form.runN}
              onChange={(e) => setForm((f) => ({ ...f, runN: e.target.value }))}
            />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <Field label="UCL" value={form.ucl} onChange={(e) => setForm((f) => ({ ...f, ucl: e.target.value }))} />
            <Field label="CL" value={form.cl} onChange={(e) => setForm((f) => ({ ...f, cl: e.target.value }))} />
            <Field label="LCL" value={form.lcl} onChange={(e) => setForm((f) => ({ ...f, lcl: e.target.value }))} />
          </div>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">启停</span>
            <select
              className={selectCls}
              value={String(form.enabled)}
              onChange={(e) => setForm((f) => ({ ...f, enabled: Number(e.target.value) === 1 ? 1 : 0 }))}
            >
              <option value="1">启用</option>
              <option value="0">停用</option>
            </select>
          </label>
        </div>
      </Drawer>
    </div>
  )
}
