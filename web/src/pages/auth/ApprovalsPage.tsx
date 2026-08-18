import { useCallback, useEffect, useState } from 'react'
import {
  approvePermRequestApi,
  listDonePermRequestsApi,
  listTodoPermRequestsApi,
  rejectPermRequestApi,
  type PermRequestItem,
} from '../../api/system'
import { Button } from '../../components/ui/Button'
import { Drawer } from '../../components/ui/Drawer'
import { FlashRow } from '../../components/ui/FlashRow'
import { RequestStatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../components/ui/Toast'
import { ApiError } from '../../lib/http'
import { cn } from '../../lib/cn'

function fmtTime(v: string | null) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

export function ApprovalsPage() {
  const toast = useToast()
  const [tab, setTab] = useState<'pending' | 'done'>('pending')
  const [rows, setRows] = useState<PermRequestItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [current, setCurrent] = useState<PermRequestItem | null>(null)
  const [remark, setRemark] = useState('')
  const [flashId, setFlashId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [pendingCount, setPendingCount] = useState(0)
  const size = 20

  const load = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      if (tab === 'pending') {
        const data = await listTodoPermRequestsApi({ page, size })
        setRows(data.records)
        setTotal(data.total)
        setPendingCount(data.total)
      } else {
        const [done, todo] = await Promise.all([
          listDonePermRequestsApi({ page, size }),
          listTodoPermRequestsApi({ page: 1, size: 1 }),
        ])
        setRows(done.records)
        setTotal(done.total)
        setPendingCount(todo.total)
      }
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [tab, page, toast])

  useEffect(() => {
    void load()
  }, [load])

  async function decide(pass: boolean) {
    if (!current) return
    setSaving(true)
    try {
      if (pass) {
        await approvePermRequestApi(current.id, remark.trim() || undefined)
        toast.success('已通过')
      } else {
        await rejectPermRequestApi(current.id, remark.trim() || undefined)
        toast.success('已驳回')
      }
      setFlashId(String(current.id))
      setCurrent(null)
      setRemark('')
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '审批失败')
    } finally {
      setSaving(false)
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / size))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {(
          [
            { key: 'pending', label: '待我审批' },
            { key: 'done', label: '我已处理' },
          ] as const
        ).map((s) => (
          <button
            key={s.key}
            type="button"
            onClick={() => {
              setPage(1)
              setTab(s.key)
            }}
            className={cn(
              'cursor-pointer rounded-md border px-2.5 py-1 text-xs font-medium transition-colors duration-150',
              tab === s.key
                ? 'border-primary bg-primary text-white'
                : 'border-border bg-bg text-muted hover:bg-surface',
            )}
          >
            {s.label}
            {s.key === 'pending' ? (
              <span className="ml-1 font-mono">({pendingCount})</span>
            ) : null}
          </button>
        ))}
      </div>

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[800px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">申请人</th>
              <th className="px-3">目标角色</th>
              <th className="px-3">原因</th>
              <th className="px-3">状态</th>
              <th className="px-3">提交时间</th>
              <th className="px-3">审批人</th>
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
                    onClick={() => void load()}
                  >
                    重试
                  </button>
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={7} className="h-24 px-3 text-center text-muted">
                  {tab === 'pending' ? '暂无待审批申请。' : '暂无已处理记录。'}
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <FlashRow key={String(r.id)} active={flashId === String(r.id)}>
                  <td className="h-10 border-b border-border px-3">
                    <span className="font-mono text-[13px]">{r.applicantCode}</span>
                    <span className="ml-2 text-muted">{r.applicantName}</span>
                  </td>
                  <td className="border-b border-border px-3">
                    <span className="font-mono text-[13px]">{r.roleCode}</span>
                    <span className="ml-2 text-muted">{r.roleName}</span>
                  </td>
                  <td className="max-w-[260px] truncate border-b border-border px-3">{r.reason}</td>
                  <td className="border-b border-border px-3">
                    <RequestStatusPill status={r.status} />
                  </td>
                  <td className="border-b border-border px-3 font-mono text-xs text-muted">
                    {fmtTime(r.createTime)}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">
                    {r.approverCode || '—'}
                  </td>
                  <td className="border-b border-border px-3">
                    {r.status === 'pending' ? (
                      <button
                        type="button"
                        className="cursor-pointer text-accent hover:underline"
                        onClick={() => {
                          setCurrent(r)
                          setRemark('')
                        }}
                      >
                        审批
                      </button>
                    ) : (
                      <span className="text-muted">{r.approveOpinion || '—'}</span>
                    )}
                  </td>
                </FlashRow>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-end gap-2 text-sm text-muted">
        <span>
          共 {total} 条 · {page}/{totalPages}
        </span>
        <Button variant="secondary" disabled={page <= 1 || loading} onClick={() => setPage((p) => p - 1)}>
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
        open={!!current}
        title="审批申请"
        onClose={() => setCurrent(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCurrent(null)} disabled={saving}>
              取消
            </Button>
            <Button variant="danger" onClick={() => void decide(false)} loading={saving}>
              驳回
            </Button>
            <Button onClick={() => void decide(true)} loading={saving}>
              通过
            </Button>
          </>
        }
      >
        {current ? (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <div className="text-xs text-muted">申请人</div>
                <div className="mt-1 font-mono">
                  {current.applicantCode} / {current.applicantName}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted">目标角色</div>
                <div className="mt-1">
                  <span className="font-mono">{current.roleCode}</span> {current.roleName}
                </div>
              </div>
            </div>
            <div>
              <div className="text-xs text-muted">申请原因</div>
              <p className="mt-1 text-ink">{current.reason}</p>
            </div>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-medium text-muted">审批意见</span>
              <textarea
                className="min-h-24 rounded-md border border-border bg-bg px-3 py-2"
                value={remark}
                onChange={(e) => setRemark(e.target.value)}
                placeholder="可选"
                aria-label="审批意见"
              />
            </label>
            <p className="text-xs text-muted">通过后自动绑定角色并踢申请人重登；驳回不赋权。</p>
          </div>
        ) : null}
      </Drawer>
    </div>
  )
}
