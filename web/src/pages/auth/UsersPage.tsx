import { useCallback, useEffect, useMemo, useState } from 'react'
import { Ban, CirclePlay, KeyRound, LogOut, Pencil } from 'lucide-react'
import { registerApi } from '../../api/auth'
import {
  assignUserRolesApi,
  kickUserApi,
  listRolesApi,
  listUsersApi,
  resetUserPasswordApi,
  updateUserApi,
  updateUserStatusApi,
  userRoleIdsApi,
  type SysRoleItem,
  type SysUserItem,
} from '../../api/system'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { useConfirm } from '../../components/ui/ConfirmDialog'
import { Drawer } from '../../components/ui/Drawer'
import { Field } from '../../components/ui/Field'
import { FlashRow } from '../../components/ui/FlashRow'
import { EnablePill } from '../../components/ui/StatusPill'
import { TableAction } from '../../components/ui/TableAction'
import { useToast } from '../../components/ui/Toast'
import { type AuthUser, type UserStatus } from '../../data/authMock'
import { ApiError } from '../../lib/http'
import { cn } from '../../lib/cn'

type Filter = 'All' | '1' | '0'

function toAuthUser(u: SysUserItem): AuthUser {
  return {
    id: String(u.id),
    userCode: u.userCode,
    userName: u.userName,
    status: u.status,
    roles: u.roles ?? [],
    mustChangePwd: u.mustChangePwd === 1,
    updatedAt: formatTime(u.updateTime),
  }
}

function formatTime(v: string | null) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

export function UsersPage() {
  const { user: me, hasPermission } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const [rows, setRows] = useState<AuthUser[]>([])
  const [roleOptions, setRoleOptions] = useState<SysRoleItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [filter, setFilter] = useState<Filter>('All')
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<AuthUser | null>(null)
  const [mode, setMode] = useState<'view' | 'edit' | 'create' | 'reset'>('view')
  const [flashId, setFlashId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [form, setForm] = useState({
    userName: '',
    userCode: '',
    password: '',
    status: 1 as UserStatus,
    roleIds: [] as string[],
    mustChangePwd: true,
  })

  const size = 20
  const roleNameMap = useMemo(
    () => Object.fromEntries(roleOptions.map((r) => [r.roleCode, r.roleName])),
    [roleOptions],
  )

  const loadRoles = useCallback(async () => {
    try {
      const data = await listRolesApi({ page: 1, size: 100, status: 1 })
      setRoleOptions(data.records)
    } catch {
      setRoleOptions([])
    }
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listUsersApi({
        keyword,
        status: filter === 'All' ? undefined : (Number(filter) as 0 | 1),
        page,
        size,
      })
      setRows(data.records.map(toAuthUser))
      setTotal(data.total)
    } catch (err) {
      setRows([])
      setTotal(0)
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [filter, keyword, page, toast])

  useEffect(() => {
    void loadRoles()
  }, [loadRoles])

  useEffect(() => {
    void load()
  }, [load])

  function openCreate() {
    setMode('create')
    setFormError('')
    setForm({ userName: '', userCode: '', password: '', status: 1, roleIds: [], mustChangePwd: true })
    setSelected({
      id: '',
      userName: '',
      userCode: '',
      status: 1,
      roles: [],
      mustChangePwd: false,
      updatedAt: '',
    })
  }

  async function openEdit(u: AuthUser) {
    setMode('edit')
    setFormError('')
    setSelected(u)
    setForm({
      userName: u.userName,
      userCode: u.userCode,
      password: '',
      status: u.status,
      roleIds: [],
      mustChangePwd: true,
    })
    try {
      const ids = await userRoleIdsApi(u.id)
      setForm((f) => ({ ...f, roleIds: ids.map(String) }))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载角色失败')
    }
  }

  function openReset(u: AuthUser) {
    setMode('reset')
    setFormError('')
    setSelected(u)
    setForm({
      userName: u.userName,
      userCode: u.userCode,
      password: '',
      status: u.status,
      roleIds: [],
      mustChangePwd: true,
    })
  }

  function toggleRole(id: string) {
    setForm((f) => ({
      ...f,
      roleIds: f.roleIds.includes(id) ? f.roleIds.filter((c) => c !== id) : [...f.roleIds, id],
    }))
  }

  async function save() {
    if (mode === 'create') {
      const userCode = form.userCode.trim()
      const userName = form.userName.trim()
      const password = form.password
      if (!userCode || !userName || !password) {
        setFormError('请填写用户编码、姓名和初始密码')
        return
      }
      setSaving(true)
      setFormError('')
      try {
        await registerApi({ userCode, userName, password })
        setSelected(null)
        toast.success('用户已新增')
        if (page === 1) await load()
        else setPage(1)
      } catch (err) {
        toast.error(err instanceof ApiError ? err.message : '新增失败')
      } finally {
        setSaving(false)
      }
      return
    }
    if (mode === 'reset') {
      if (!selected?.id) return
      if (!form.password || form.password.length < 6) {
        setFormError('临时密码至少6位')
        return
      }
      setSaving(true)
      setFormError('')
      try {
        await resetUserPasswordApi(selected.id, {
          password: form.password,
          mustChangePwd: form.mustChangePwd,
        })
        setFlashId(selected.id)
        setSelected(null)
        toast.success('密码已重置')
        await load()
      } catch (err) {
        toast.error(err instanceof ApiError ? err.message : '重置失败')
      } finally {
        setSaving(false)
      }
      return
    }
    if (!selected?.id) return
    const userName = form.userName.trim()
    if (!userName) {
      setFormError('请填写姓名')
      return
    }
    setSaving(true)
    setFormError('')
    try {
      await updateUserApi(selected.id, { userName })
      if (form.status !== selected.status) {
        await updateUserStatusApi(selected.id, form.status)
      }
      await assignUserRolesApi(selected.id, form.roleIds)
      setFlashId(selected.id)
      setSelected(null)
      toast.success('用户已保存')
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function toggleStatus(u: AuthUser) {
    const next = u.status === 1 ? 0 : 1
    try {
      await updateUserStatusApi(u.id, next)
      setFlashId(u.id)
      toast.success(next === 1 ? '已启用' : '已禁用')
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  async function kickUser(u: AuthUser) {
    const ok = await confirm({
      title: '强制下线',
      message: `确认将 ${u.userCode} 强制下线？账号仍可再次登录。`,
      confirmText: '踢下线',
      danger: true,
    })
    if (!ok) return
    try {
      await kickUserApi(u.id)
      setFlashId(u.id)
      toast.success('已强制下线')
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '踢人失败')
    }
  }

  function applySearch() {
    setPage(1)
    setKeyword(q.trim())
  }

  const totalPages = Math.max(1, Math.ceil(total / size))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          {(
            [
              { key: 'All', label: '全部' },
              { key: '1', label: '正常' },
              { key: '0', label: '禁用' },
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
          <input
            className="h-9 w-48 rounded-md border border-border bg-bg px-3 text-sm"
            placeholder="编码 / 姓名"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') applySearch()
            }}
            aria-label="搜索用户"
          />
          <Button variant="secondary" onClick={applySearch}>
            搜索
          </Button>
        </div>
        <Button onClick={openCreate}>新增用户</Button>
      </div>

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[920px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">用户编码</th>
              <th className="px-3">姓名</th>
              <th className="px-3">状态</th>
              <th className="px-3">角色</th>
              <th className="px-3">改密</th>
              <th className="px-3">更新时间</th>
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
                <td colSpan={7} className="h-16 px-3 text-center text-muted">
                  暂无数据
                </td>
              </tr>
            ) : (
              rows.map((u) => (
                <FlashRow key={u.id} active={flashId === u.id}>
                  <td className="h-10 border-b border-border px-3 font-mono text-[13px] font-medium">
                    {u.userCode}
                  </td>
                  <td className="border-b border-border px-3">{u.userName}</td>
                  <td className="border-b border-border px-3">
                    <EnablePill enabled={u.status === 1} />
                  </td>
                  <td className="border-b border-border px-3">
                    {u.roles.length ? u.roles.map((c) => roleNameMap[c] ?? c).join('、') : '—'}
                  </td>
                  <td className="border-b border-border px-3 text-muted">
                    {u.mustChangePwd ? '强制' : '—'}
                  </td>
                  <td className="border-b border-border px-3 font-mono text-xs text-muted">
                    {u.updatedAt}
                  </td>
                  <td className="border-b border-border px-3">
                    <div className="flex flex-nowrap items-center gap-1.5">
                      <TableAction
                        icon={Pencil}
                        label="编辑"
                        tone="accent"
                        onClick={() => void openEdit(u)}
                      />
                      <TableAction
                        icon={KeyRound}
                        label="重置密码"
                        onClick={() => openReset(u)}
                      />
                      {hasPermission('user:kick') ? (
                        <TableAction
                          icon={LogOut}
                          label="踢下线"
                          tone="danger"
                          disabled={String(me?.id) === String(u.id)}
                          title={
                            String(me?.id) === String(u.id)
                              ? '不能踢自己下线'
                              : '强制该用户下线'
                          }
                          onClick={() => void kickUser(u)}
                        />
                      ) : null}
                      <span className="mx-0.5 h-4 w-px shrink-0 bg-border" aria-hidden />
                      <TableAction
                        icon={u.status === 1 ? Ban : CirclePlay}
                        label={u.status === 1 ? '禁用' : '启用'}
                        tone={u.status === 1 ? 'warning' : 'default'}
                        onClick={() => void toggleStatus(u)}
                      />
                    </div>
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
        open={!!selected}
        title={mode === 'create' ? '新增用户' : mode === 'reset' ? '重置密码' : '编辑用户'}
        onClose={() => setSelected(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setSelected(null)} disabled={saving}>
              取消
            </Button>
            <Button
              onClick={() => void save()}
              loading={saving}
              disabled={
                mode === 'reset'
                  ? form.password.length < 6
                  : !form.userCode.trim() ||
                    !form.userName.trim() ||
                    (mode === 'create' && !form.password)
              }
            >
              保存
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {formError ? <p className="text-xs text-danger">{formError}</p> : null}
          {mode === 'reset' ? (
            <>
              <p className="text-sm text-muted">
                用户 <span className="font-mono text-ink">{form.userCode}</span> · {form.userName}
              </p>
              <Field
                label="临时密码"
                name="password"
                type="password"
                value={form.password}
                onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
              />
              <label className="flex cursor-pointer items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.mustChangePwd}
                  onChange={(e) => setForm((f) => ({ ...f, mustChangePwd: e.target.checked }))}
                />
                <span>强制下次登录修改密码</span>
              </label>
            </>
          ) : (
            <>
              <Field
                label="用户编码"
                name="userCode"
                value={form.userCode}
                disabled={mode === 'edit'}
                onChange={(e) => setForm((f) => ({ ...f, userCode: e.target.value }))}
              />
              <Field
                label="姓名"
                name="userName"
                value={form.userName}
                onChange={(e) => setForm((f) => ({ ...f, userName: e.target.value }))}
              />
              {mode === 'create' ? (
                <Field
                  label="初始密码"
                  name="password"
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                />
              ) : null}
              {mode === 'edit' ? (
                <>
                  <label className="flex flex-col gap-1.5 text-sm">
                    <span className="text-xs font-medium text-muted">状态</span>
                    <select
                      className="h-9 rounded-md border border-border bg-bg px-3"
                      value={form.status}
                      onChange={(e) =>
                        setForm((f) => ({ ...f, status: Number(e.target.value) as UserStatus }))
                      }
                    >
                      <option value={1}>正常</option>
                      <option value={0}>禁用</option>
                    </select>
                  </label>
                  <fieldset>
                    <legend className="mb-2 text-xs font-medium text-muted">角色</legend>
                    <div className="space-y-2">
                      {roleOptions.map((r) => {
                        const id = String(r.id)
                        return (
                          <label key={id} className="flex cursor-pointer items-center gap-2 text-sm">
                            <input
                              type="checkbox"
                              checked={form.roleIds.includes(id)}
                              onChange={() => toggleRole(id)}
                            />
                            <span className="font-mono text-[13px]">{r.roleCode}</span>
                            <span className="text-muted">{r.roleName}</span>
                          </label>
                        )
                      })}
                    </div>
                  </fieldset>
                </>
              ) : null}
            </>
          )}
        </div>
      </Drawer>
    </div>
  )
}
