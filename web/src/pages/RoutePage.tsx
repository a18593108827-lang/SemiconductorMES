import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import gsap from 'gsap'
import { GitBranch, Pencil, Plus, Wrench } from 'lucide-react'
import {
  createRouteApi,
  createStepApi,
  listRoutesApi,
  listStepsApi,
  updateStepApi,
  type MesRouteItem,
  type MesStepItem,
} from '../api/route'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { Drawer } from '../components/ui/Drawer'
import { Field } from '../components/ui/Field'
import { EnablePill } from '../components/ui/StatusPill'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'
import { STEP_TYPE_LABEL, fmtTime } from './route/routeDraft'

export function RoutePage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const rootRef = useRef<HTMLDivElement>(null)

  const [routes, setRoutes] = useState<MesRouteItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState({
    routeCode: '',
    routeName: '',
    productCode: '',
    remark: '',
  })
  const [createError, setCreateError] = useState('')
  const [savingCreate, setSavingCreate] = useState(false)

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

  const size = 20
  const canAdd = hasPermission('route:add')
  const canEdit = hasPermission('route:edit')
  const totalPages = Math.max(1, Math.ceil(total / size))

  const loadRoutes = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listRoutesApi({ keyword, page, size })
      setRoutes(data.records)
      setTotal(data.total)
    } catch (err) {
      setRoutes([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, page, toast])

  useEffect(() => {
    void loadRoutes()
  }, [loadRoutes])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.route-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [])

  async function submitCreate() {
    const routeCode = createForm.routeCode.trim()
    const routeName = createForm.routeName.trim()
    if (!routeCode || !routeName) {
      setCreateError('请填写路线编码和名称')
      return
    }
    setSavingCreate(true)
    setCreateError('')
    try {
      const { id } = await createRouteApi({
        routeCode,
        routeName,
        productCode: createForm.productCode.trim() || undefined,
        remark: createForm.remark.trim() || undefined,
      })
      setCreateOpen(false)
      setCreateForm({ routeCode: '', routeName: '', productCode: '', remark: '' })
      toast.success('路线已创建')
      navigate(`/app/route/${id}`)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setSavingCreate(false)
    }
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
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSavingStep(false)
    }
  }

  return (
    <div ref={rootRef} className="space-y-4">
      <header className="route-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">工艺路线</h1>
          <p className="mt-1 text-sm text-muted">维护工序与有序路线版本；生效版只读，变更请升版</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canEdit || canAdd ? (
            <Button variant="secondary" onClick={() => void openStepsLibrary()}>
              <Wrench className="size-4" aria-hidden />
              工序库
            </Button>
          ) : null}
          {canAdd ? (
            <Button
              onClick={() => {
                setCreateOpen(true)
                setCreateError('')
                setCreateForm({ routeCode: '', routeName: '', productCode: '', remark: '' })
              }}
            >
              <Plus className="size-4" aria-hidden />
              新建路线
            </Button>
          ) : null}
        </div>
      </header>

      <div className="route-block flex flex-wrap items-center gap-2">
        <input
          className="h-9 w-56 rounded-md border border-border bg-bg px-3 text-sm"
          placeholder="编码 / 名称 / 产品"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setPage(1)
              setKeyword(q.trim())
            }
          }}
          aria-label="搜索路线"
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
      </div>

      <div className="route-block overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">路线编码</th>
              <th className="px-3">名称</th>
              <th className="px-3">产品</th>
              <th className="px-3">生效版本</th>
              <th className="px-3">状态</th>
              <th className="px-3">更新</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="h-16 px-3 text-center text-muted">
                  加载中…
                </td>
              </tr>
            ) : loadFailed ? (
              <tr>
                <td colSpan={7} className="h-20 px-3 text-center text-muted">
                  加载失败
                  <button
                    type="button"
                    className="ml-2 cursor-pointer text-accent hover:underline"
                    onClick={() => void loadRoutes()}
                  >
                    重试
                  </button>
                </td>
              </tr>
            ) : routes.length === 0 ? (
              <tr>
                <td colSpan={7} className="h-24 px-3 text-center text-muted">
                  暂无路线。
                  {canAdd ? '点击「新建路线」开始配置。' : null}
                </td>
              </tr>
            ) : (
              routes.map((r) => (
                <tr key={String(r.id)}>
                  <td className="h-10 border-b border-border px-3 font-mono text-[13px] font-medium">
                    {r.routeCode}
                  </td>
                  <td className="border-b border-border px-3">{r.routeName}</td>
                  <td className="border-b border-border px-3 font-mono text-[13px] text-muted">
                    {r.productCode || '—'}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">
                    {r.activeVersionNo != null ? `v${r.activeVersionNo}` : '—'}
                  </td>
                  <td className="border-b border-border px-3">
                    <EnablePill enabled={r.status === 1} />
                  </td>
                  <td className="border-b border-border px-3 font-mono text-xs text-muted">
                    {fmtTime(r.updateTime)}
                  </td>
                  <td className="border-b border-border px-3">
                    <Link
                      to={`/app/route/${r.id}`}
                      className="inline-flex h-7 items-center gap-1 rounded-md border border-accent/30 px-2 text-xs font-medium text-accent transition-colors duration-150 hover:bg-accent/8"
                    >
                      <GitBranch className="size-3.5 shrink-0" aria-hidden />
                      维护
                    </Link>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-end gap-2 text-sm text-muted">
        <span>
          共 {total} 条 · {page}/{totalPages}
        </span>
        <Button
          variant="secondary"
          disabled={page <= 1 || loading}
          onClick={() => setPage((p) => Math.max(1, p - 1))}
        >
          上一页
        </Button>
        <Button
          variant="secondary"
          disabled={page >= totalPages || loading}
          onClick={() => setPage((p) => p + 1)}
        >
          下一页
        </Button>
      </div>

      <Drawer
        open={createOpen}
        title="新建路线"
        size="sm"
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)} disabled={savingCreate}>
              取消
            </Button>
            <Button onClick={() => void submitCreate()} loading={savingCreate}>
              创建
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {createError ? <p className="text-xs text-danger">{createError}</p> : null}
          <Field
            label="路线编码"
            name="routeCode"
            value={createForm.routeCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, routeCode: e.target.value }))}
          />
          <Field
            label="路线名称"
            name="routeName"
            value={createForm.routeName}
            onChange={(e) => setCreateForm((f) => ({ ...f, routeName: e.target.value }))}
          />
          <Field
            label="产品编码"
            name="productCode"
            value={createForm.productCode}
            onChange={(e) => setCreateForm((f) => ({ ...f, productCode: e.target.value }))}
            placeholder="可选"
          />
          <Field
            label="备注"
            name="remark"
            value={createForm.remark}
            onChange={(e) => setCreateForm((f) => ({ ...f, remark: e.target.value }))}
            placeholder="可选"
          />
          <p className="text-xs text-muted">创建后进入维护页配置步骤并发布。</p>
        </div>
      </Drawer>

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
