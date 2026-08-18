import { useCallback, useEffect, useState } from 'react'
import {
  cancelPermRequestApi,
  createPermRequestApi,
  listApplyableRolesApi,
  listMyPermRequestsApi,
  type ApplyableRoleItem,
  type PermRequestItem,
  type PermRequestStatus,
} from '../../api/system'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { useConfirm } from '../../components/ui/ConfirmDialog'
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

export function MyRequestsPage() {
  const { user, hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const [rows, setRows] = useState<PermRequestItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [filter, setFilter] = useState<PermRequestStatus | 'All'>('All')
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [formError, setFormError] = useState('')
  const [open, setOpen] = useState(false)
  const [flashId, setFlashId] = useState<string | null>(null)
  const [roles, setRoles] = useState<ApplyableRoleItem[]>([])
  const [form, setForm] = useState({ roleId: '', reason: '' })
  const [saving, setSaving] = useState(false)
  const size = 20

  const load = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listMyPermRequestsApi({
        status: filter === 'All' ? undefined : filter,
        page,
        size,
      })
      setRows(data.records)
      setTotal(data.total)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [filter, page, toast])

  useEffect(() => {
    void load()
  }, [load])

  async function openCreate() {
    setForm({ roleId: '', reason: '' })
    setFormError('')
    try {
      const list = await listApplyableRolesApi()
      setRoles(list)
      setForm({ roleId: list[0] ? String(list[0].id) : '', reason: '' })
      setOpen(true)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载角色失败')
    }
  }

  async function submit() {
    if (!form.roleId || !form.reason.trim()) {
      setFormError('请选择角色并填写申请原因')
      return
    }
    setSaving(true)
    setFormError('')
    try {
      await createPermRequestApi({ roleId: form.roleId, reason: form.reason.trim() })
      setOpen(false)
      toast.success('申请已提交')
      setPage(1)
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '提交失败')
    } finally {
      setSaving(false)
    }
  }

  async function cancel(r: PermRequestItem) {
    if (r.status !== 'pending') return
    const ok = await confirm({
      title: '撤回申请',
      message: '确认撤回该申请？',
      confirmText: '撤回',
      danger: true,
    })
    if (!ok) return
    try {
      await cancelPermRequestApi(r.id)
      setFlashId(String(r.id))
      toast.success('申请已撤回')
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '撤回失败')
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / size))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {(
            [
              { key: 'All', label: '全部' },
              { key: 'pending', label: '待审批' },
              { key: 'approved', label: '已通过' },
              { key: 'rejected', label: '已驳回' },
              { key: 'cancelled', label: '已撤回' },
            ] as const
          ).map((s) => (
            <button
              key={s.key}
              type="button"
              onClick={() => {
                setPage(1)
                setFilter(s.key)
              }}
              className={cn(
                'cursor-pointer rounded-md border px-2.5 py-1 text-xs font-medium transition-colors duration-150',
                filter === s.key
                  ? 'border-primary bg-primary text-white'
                  : 'border-border bg-bg text-muted hover:bg-surface',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
        {hasPermission('perm:apply') ? <Button onClick={() => void openCreate()}>发起申请</Button> : null}
      </div>

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">单号</th>
              <th className="px-3">申请角色</th>
              <th className="px-3">原因</th>
              <th className="px-3">状态</th>
              <th className="px-3">提交时间</th>
              <th className="px-3">审批人</th>
              <th className="px-3">审批意见</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} className="h-16 px-3 text-center text-muted">
                  加载中…
                </td>
              </tr>
            ) : loadFailed ? (
              <tr>
                <td colSpan={8} className="h-20 px-3 text-center text-muted">
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
                <td colSpan={8} className="h-24 px-3 text-center text-muted">
                  暂无申请。需要额外角色时点击「发起申请」。
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <FlashRow key={String(r.id)} active={flashId === String(r.id)}>
                  <td className="h-10 border-b border-border px-3 font-mono text-xs">{r.requestNo}</td>
                  <td className="border-b border-border px-3">
                    <span className="font-mono text-[13px]">{r.roleCode}</span>
                    <span className="ml-2 text-muted">{r.roleName}</span>
                  </td>
                  <td className="max-w-[240px] truncate border-b border-border px-3">{r.reason}</td>
                  <td className="border-b border-border px-3">
                    <RequestStatusPill status={r.status} />
                  </td>
                  <td className="border-b border-border px-3 font-mono text-xs text-muted">
                    {fmtTime(r.createTime)}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-[13px]">
                    {r.approverCode || '—'}
                  </td>
                  <td className="border-b border-border px-3 text-muted">{r.approveOpinion || '—'}</td>
                  <td className="border-b border-border px-3">
                    {r.status === 'pending' && hasPermission('perm:apply') ? (
                      <button
                        type="button"
                        className="cursor-pointer text-muted hover:text-ink hover:underline"
                        onClick={() => void cancel(r)}
                      >
                        撤回
                      </button>
                    ) : (
                      '—'
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
        open={open}
        title="发起权限申请"
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>
              取消
            </Button>
            <Button onClick={() => void submit()} loading={saving} disabled={!form.roleId || !form.reason.trim()}>
              提交申请
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {formError ? <p className="text-xs text-danger">{formError}</p> : null}
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">目标角色</span>
            <select
              className="h-9 rounded-md border border-border bg-bg px-3"
              value={form.roleId}
              onChange={(e) => setForm((f) => ({ ...f, roleId: e.target.value }))}
            >
              {roles.map((r) => (
                <option key={String(r.id)} value={String(r.id)}>
                  {r.roleName}（{r.roleCode}）
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">申请原因</span>
            <textarea
              className="min-h-24 rounded-md border border-border bg-bg px-3 py-2 text-sm"
              value={form.reason}
              onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))}
              aria-label="申请原因"
            />
          </label>
          <div className="text-xs text-muted">
            申请人{' '}
            <span className="font-mono text-ink">
              {user?.userCode}
              {user?.userName ? ` / ${user.userName}` : ''}
            </span>
          </div>
        </div>
      </Drawer>
    </div>
  )
}
