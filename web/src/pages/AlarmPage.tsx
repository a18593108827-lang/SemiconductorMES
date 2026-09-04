import { useCallback, useEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import { AlertTriangle, CheckCircle2, Eye, RefreshCw, XCircle } from 'lucide-react'
import {
  ackAlarmApi,
  clearAlarmApi,
  getAlarmApi,
  listAlarmsApi,
  listCriticalAlarmsApi,
  type AlarmItem,
  type AlarmLevel,
  type AlarmStatus,
} from '../api/alarm'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { Drawer } from '../components/ui/Drawer'
import { FlashRow } from '../components/ui/FlashRow'
import { TableAction } from '../components/ui/TableAction'
import { useToast } from '../components/ui/Toast'
import { subscribeAlarmActive } from '../lib/alarmWs'
import { ApiError } from '../lib/http'
import { cn } from '../lib/cn'
import { motionMs } from '../lib/motion'

const STATUS_FILTERS: Array<{ key: AlarmStatus | 'all'; label: string }> = [
  { key: 'OPEN', label: '未确认' },
  { key: 'ACK', label: '已确认' },
  { key: 'CLEARED', label: '已关闭' },
  { key: 'all', label: '全部' },
]

const LEVEL_FILTERS: Array<{ key: AlarmLevel | 'all'; label: string }> = [
  { key: 'CRITICAL', label: '严重' },
  { key: 'WARNING', label: '警告' },
  { key: 'INFO', label: '提示' },
  { key: 'all', label: '全部级别' },
]

const LEVEL_LABEL: Record<AlarmLevel, string> = {
  CRITICAL: '严重',
  WARNING: '警告',
  INFO: '提示',
}

const STATUS_LABEL: Record<AlarmStatus, string> = {
  OPEN: '未确认',
  ACK: '已确认',
  CLEARED: '已关闭',
}

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

function LevelDot({ level }: { level: AlarmLevel }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className={cn(
          'size-1.5 rounded-full',
          level === 'CRITICAL' && 'bg-danger',
          level === 'WARNING' && 'bg-warning',
          level === 'INFO' && 'bg-accent',
        )}
      />
      {LEVEL_LABEL[level]}
    </span>
  )
}

export function AlarmPage() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const rootRef = useRef<HTMLDivElement>(null)

  const canView = hasPermission('alarm:view')
  const canAck = hasPermission('alarm:ack')
  const canClear = hasPermission('alarm:clear')

  const [statusFilter, setStatusFilter] = useState<AlarmStatus | 'all'>('OPEN')
  const [levelFilter, setLevelFilter] = useState<AlarmLevel | 'all'>('all')
  const [codeQ, setCodeQ] = useState('')
  const [code, setCode] = useState('')
  const [page, setPage] = useState(1)
  const size = 20

  const [rows, setRows] = useState<AlarmItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)
  const [criticals, setCriticals] = useState<AlarmItem[]>([])
  const [flashId, setFlashId] = useState<string | null>(null)

  const [detail, setDetail] = useState<AlarmItem | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [remark, setRemark] = useState('')
  const [acting, setActing] = useState(false)

  const totalPages = Math.max(1, Math.ceil(total / size))

  const loadList = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listAlarmsApi({
        status: statusFilter === 'all' ? '' : statusFilter,
        level: levelFilter === 'all' ? '' : levelFilter,
        code,
        page,
        size,
      })
      setRows(data.records)
      setTotal(data.total)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '告警加载失败')
    } finally {
      setLoading(false)
    }
  }, [statusFilter, levelFilter, code, page, toast])

  const loadCriticals = useCallback(async () => {
    try {
      setCriticals(await listCriticalAlarmsApi())
    } catch {
      setCriticals([])
    }
  }, [])

  useEffect(() => {
    if (!canView) return
    void loadList()
  }, [canView, loadList])

  useEffect(() => {
    if (!canView) return
    void loadCriticals()
  }, [canView, loadCriticals])

  useEffect(() => {
    if (!canView) return
    return subscribeAlarmActive((msg) => {
      setFlashId(String(msg.id))
      void loadList()
      void loadCriticals()
    })
  }, [canView, loadList, loadCriticals])

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.alarm-block', {
        y: 6,
        duration: d,
        stagger: 0.04,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [])

  const openDetail = async (row: AlarmItem) => {
    setDetail(row)
    setRemark('')
    setDetailLoading(true)
    try {
      setDetail(await getAlarmApi(row.id))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '详情加载失败')
    } finally {
      setDetailLoading(false)
    }
  }

  const doAck = async (id: number | string) => {
    setActing(true)
    try {
      const updated = await ackAlarmApi(id, remark.trim() || undefined)
      toast.success('已确认')
      setFlashId(String(id))
      setDetail(updated)
      setRemark('')
      await loadList()
      await loadCriticals()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '确认失败')
    } finally {
      setActing(false)
    }
  }

  const doClear = async (id: number | string) => {
    setActing(true)
    try {
      const updated = await clearAlarmApi(id, remark.trim() || undefined)
      toast.success('已关闭')
      setFlashId(String(id))
      setDetail(updated)
      setRemark('')
      await loadList()
      await loadCriticals()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '关闭失败')
    } finally {
      setActing(false)
    }
  }

  if (!canView) {
    return (
      <div className="space-y-2">
        <h1 className="text-xl font-semibold tracking-tight">报警管理</h1>
        <p className="text-sm text-muted">无权限查看告警（需要 alarm:view）</p>
      </div>
    )
  }

  const banner = criticals[0]

  return (
    <div ref={rootRef} className="space-y-4">
      {banner ? (
        <div className="alarm-block flex items-center gap-3 rounded-md border border-danger bg-danger/10 px-4 py-3 text-sm">
          <AlertTriangle className="size-5 shrink-0 text-danger" />
          <div className="min-w-0 flex-1">
            <div className="font-medium text-danger">
              严重 · {banner.code}
              {criticals.length > 1 ? ` · 另有 ${criticals.length - 1} 条` : ''}
            </div>
            <div className="truncate text-ink">{banner.message}</div>
          </div>
          {canAck && banner.status === 'OPEN' ? (
            <Button
              variant="danger"
              className="h-8 shrink-0"
              disabled={acting}
              onClick={() => void doAck(banner.id)}
            >
              确认
            </Button>
          ) : (
            <Button variant="secondary" className="h-8 shrink-0" onClick={() => void openDetail(banner)}>
              查看
            </Button>
          )}
        </div>
      ) : null}

      <header className="alarm-block flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">报警管理</h1>
          <p className="mt-1 text-sm text-muted">统一台：记录 · 确认 · 关闭 · 推送</p>
        </div>
        <Button
          variant="secondary"
          onClick={() => {
            void loadList()
            void loadCriticals()
          }}
        >
          <RefreshCw className="size-4" aria-hidden />
          刷新
        </Button>
      </header>

      <div className="alarm-block flex flex-wrap items-center gap-2">
        <input
          className="h-9 w-40 rounded-md border border-border bg-bg px-3 font-mono text-sm"
          placeholder="告警码"
          value={codeQ}
          onChange={(e) => setCodeQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setPage(1)
              setCode(codeQ.trim())
            }
          }}
        />
        <Button
          variant="secondary"
          onClick={() => {
            setPage(1)
            setCode(codeQ.trim())
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
          {LEVEL_FILTERS.map((s) => (
            <button
              key={s.key}
              type="button"
              onClick={() => {
                setLevelFilter(s.key)
                setPage(1)
              }}
              className={cn(
                'h-8 cursor-pointer rounded-md border px-2.5 text-xs font-medium transition-colors duration-150',
                levelFilter === s.key
                  ? 'border-primary bg-primary text-white'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="alarm-block overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[920px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">级别</th>
              <th className="px-3">告警码</th>
              <th className="px-3">状态</th>
              <th className="px-3">消息</th>
              <th className="px-3">对象</th>
              <th className="px-3">次数</th>
              <th className="px-3">最近触发</th>
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
                <td colSpan={8} className="px-3 py-10 text-center text-sm text-muted">
                  暂无告警
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <FlashRow key={String(row.id)} active={flashId === String(row.id)}>
                  <td className="h-10 px-3">
                    <LevelDot level={row.level} />
                  </td>
                  <td className="px-3 font-mono text-[13px] text-accent">{row.code}</td>
                  <td className="px-3 text-xs">{STATUS_LABEL[row.status]}</td>
                  <td className="max-w-[240px] truncate px-3" title={row.message}>
                    {row.message}
                  </td>
                  <td className="px-3 font-mono text-xs text-muted">
                    {row.entityType === 'NONE' ? '—' : `${row.entityType}#${row.entityId ?? ''}`}
                  </td>
                  <td className="px-3 font-mono text-[13px]">{row.raiseCount}</td>
                  <td className="px-3 font-mono text-[13px] text-muted">{fmtTime(row.lastRaiseAt)}</td>
                  <td className="px-3">
                    <div className="flex flex-wrap gap-1.5">
                      <TableAction icon={Eye} label="详情" onClick={() => void openDetail(row)} />
                      {row.status === 'OPEN' && canAck ? (
                        <TableAction
                          icon={CheckCircle2}
                          label="确认"
                          tone="accent"
                          onClick={() => {
                            setDetail(row)
                            setRemark('')
                          }}
                        />
                      ) : null}
                      {row.status !== 'CLEARED' && canClear ? (
                        <TableAction
                          icon={XCircle}
                          label="关闭"
                          tone="warning"
                          onClick={() => {
                            setDetail(row)
                            setRemark('')
                          }}
                        />
                      ) : null}
                    </div>
                  </td>
                </FlashRow>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="alarm-block flex items-center justify-between text-sm text-muted">
        <span>共 {total} 条</span>
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            className="h-8"
            disabled={page <= 1 || loading}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </Button>
          <span className="font-mono text-xs">
            {page} / {totalPages}
          </span>
          <Button
            variant="secondary"
            className="h-8"
            disabled={page >= totalPages || loading}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </div>
      </div>

      <Drawer
        open={!!detail}
        title={detail ? `告警 · ${detail.code}` : '告警详情'}
        onClose={() => {
          setDetail(null)
          setRemark('')
        }}
        footer={
          detail && detail.status !== 'CLEARED' ? (
            <div className="flex flex-wrap justify-end gap-2">
              {detail.status === 'OPEN' && canAck ? (
                <Button disabled={acting || detailLoading} onClick={() => void doAck(detail.id)}>
                  确认
                </Button>
              ) : null}
              {canClear ? (
                <Button
                  variant="secondary"
                  disabled={acting || detailLoading}
                  onClick={() => void doClear(detail.id)}
                >
                  关闭
                </Button>
              ) : null}
            </div>
          ) : undefined
        }
      >
        {detail ? (
          <div className="space-y-4 text-sm">
            {detailLoading ? <p className="text-muted">加载中…</p> : null}
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">级别</span>
                <LevelDot level={detail.level} />
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">状态</span>
                <span>{STATUS_LABEL[detail.status]}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">次数</span>
                <span className="font-mono">{detail.raiseCount}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">对象</span>
                <span className="font-mono text-xs">
                  {detail.entityType === 'NONE'
                    ? '—'
                    : `${detail.entityType}#${detail.entityId ?? ''}`}
                </span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">首次触发</span>
                <span className="font-mono text-[13px]">{fmtTime(detail.firstRaiseAt)}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">最近触发</span>
                <span className="font-mono text-[13px]">{fmtTime(detail.lastRaiseAt)}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">确认时间</span>
                <span className="font-mono text-[13px]">{fmtTime(detail.ackAt)}</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">关闭时间</span>
                <span className="font-mono text-[13px]">{fmtTime(detail.clearAt)}</span>
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-xs font-medium text-muted">消息</span>
              <p className="whitespace-pre-wrap text-ink">{detail.message}</p>
            </div>
            {detail.ackRemark ? (
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">确认备注</span>
                <p>{detail.ackRemark}</p>
              </div>
            ) : null}
            {detail.clearRemark ? (
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">关闭备注</span>
                <p>{detail.clearRemark}</p>
              </div>
            ) : null}
            {detail.status !== 'CLEARED' && (canAck || canClear) ? (
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-medium text-muted">操作备注</span>
                <textarea
                  className="min-h-[72px] w-full rounded-md border border-border bg-bg px-3 py-2 text-sm"
                  value={remark}
                  onChange={(e) => setRemark(e.target.value)}
                  placeholder="可选"
                />
              </label>
            ) : null}
            {detail.payloadJson ? (
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-muted">上下文</span>
                <pre className="max-h-40 overflow-auto rounded-md border border-border bg-surface p-2 font-mono text-[11px] text-muted">
                  {detail.payloadJson}
                </pre>
              </div>
            ) : null}
          </div>
        ) : null}
      </Drawer>
    </div>
  )
}
