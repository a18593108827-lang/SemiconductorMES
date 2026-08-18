import { useCallback, useEffect, useRef, useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import {
  assignRolePermissionsApi,
  createRoleApi,
  deleteRoleApi,
  listRolesApi,
  permTreeApi,
  rolePermissionIdsApi,
  updateRoleApi,
  type SysPermTreeNode,
  type SysRoleItem,
} from '../../api/system'
import { Button } from '../../components/ui/Button'
import { useConfirm } from '../../components/ui/ConfirmDialog'
import { Drawer } from '../../components/ui/Drawer'
import { Field } from '../../components/ui/Field'
import { FlashRow } from '../../components/ui/FlashRow'
import { EnablePill } from '../../components/ui/StatusPill'
import { useToast } from '../../components/ui/Toast'
import { permTypeLabel, type AuthRole, type UserStatus } from '../../data/authMock'
import { ApiError } from '../../lib/http'

function toAuthRole(r: SysRoleItem): AuthRole {
  return {
    id: String(r.id),
    roleCode: r.roleCode,
    roleName: r.roleName,
    remark: r.remark ?? '',
    status: r.status,
    permIds: [],
    userCount: Number(r.userCount ?? 0),
  }
}

function filterEnabledTree(nodes: SysPermTreeNode[]): SysPermTreeNode[] {
  return (nodes ?? [])
    .filter((n) => n.status === 1)
    .map((n) => ({ ...n, children: filterEnabledTree(n.children ?? []) }))
}

function collectDescendantIds(node: SysPermTreeNode): string[] {
  const ids: string[] = []
  for (const c of node.children ?? []) {
    ids.push(String(c.id), ...collectDescendantIds(c))
  }
  return ids
}

function findNode(nodes: SysPermTreeNode[], id: string): SysPermTreeNode | null {
  for (const n of nodes) {
    if (String(n.id) === id) return n
    const hit = findNode(n.children ?? [], id)
    if (hit) return hit
  }
  return null
}

function findParent(nodes: SysPermTreeNode[], childId: string): SysPermTreeNode | null {
  for (const n of nodes) {
    if ((n.children ?? []).some((c) => String(c.id) === childId)) return n
    const hit = findParent(n.children ?? [], childId)
    if (hit) return hit
  }
  return null
}

function allDescendantIds(node: SysPermTreeNode): string[] {
  return collectDescendantIds(node)
}

/** 勾选/取消节点并联动子孙与祖先 */
function cascadeTogglePermIds(
  tree: SysPermTreeNode[],
  checked: string[],
  targetId: string,
): string[] {
  const node = findNode(tree, targetId)
  if (!node) return checked
  const set = new Set(checked)
  const selfAndKids = [targetId, ...allDescendantIds(node)]
  const turningOn = !set.has(targetId)

  if (turningOn) {
    selfAndKids.forEach((id) => set.add(id))
  } else {
    selfAndKids.forEach((id) => set.delete(id))
  }

  let parent = findParent(tree, targetId)
  while (parent) {
    const pid = String(parent.id)
    const childIds = (parent.children ?? []).map((c) => String(c.id))
    const allOn = childIds.length > 0 && childIds.every((cid) => set.has(cid))
    if (allOn) set.add(pid)
    else set.delete(pid)
    parent = findParent(tree, pid)
  }

  return Array.from(set)
}

function nodeCheckState(
  node: SysPermTreeNode,
  checked: string[],
): 'checked' | 'unchecked' | 'indeterminate' {
  const id = String(node.id)
  const descs = allDescendantIds(node)
  if (descs.length === 0) {
    return checked.includes(id) ? 'checked' : 'unchecked'
  }
  const allOn = checked.includes(id) && descs.every((d) => checked.includes(d))
  const anyOn = checked.includes(id) || descs.some((d) => checked.includes(d))
  if (allOn) return 'checked'
  if (!anyOn) return 'unchecked'
  return 'indeterminate'
}

export function RolesPage() {
  const toast = useToast()
  const confirm = useConfirm()
  const [rows, setRows] = useState<AuthRole[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [selected, setSelected] = useState<AuthRole | null>(null)
  const [mode, setMode] = useState<'edit' | 'create' | 'perms'>('edit')
  const [flashId, setFlashId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [permLoading, setPermLoading] = useState(false)
  const [formError, setFormError] = useState('')
  const [permTree, setPermTree] = useState<SysPermTreeNode[]>([])
  const [form, setForm] = useState({
    roleCode: '',
    roleName: '',
    remark: '',
    status: 1 as UserStatus,
    permIds: [] as string[],
  })

  const load = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await listRolesApi({ page: 1, size: 100 })
      setRows(data.records.map(toAuthRole))
    } catch (err) {
      setRows([])
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    void load()
  }, [load])

  function openCreate() {
    setMode('create')
    setFormError('')
    setForm({ roleCode: '', roleName: '', remark: '', status: 1, permIds: [] })
    setSelected({
      id: '',
      roleCode: '',
      roleName: '',
      remark: '',
      status: 1,
      permIds: [],
      userCount: 0,
    })
  }

  function openEdit(r: AuthRole) {
    setMode('edit')
    setFormError('')
    setSelected(r)
    setForm({
      roleCode: r.roleCode,
      roleName: r.roleName,
      remark: r.remark,
      status: r.status,
      permIds: [...r.permIds],
    })
  }

  async function openPerms(r: AuthRole) {
    setMode('perms')
    setFormError('')
    setSelected(r)
    setForm({
      roleCode: r.roleCode,
      roleName: r.roleName,
      remark: r.remark,
      status: r.status,
      permIds: [],
    })
    setPermLoading(true)
    try {
      const [tree, ids] = await Promise.all([permTreeApi(), rolePermissionIdsApi(r.id)])
      setPermTree(filterEnabledTree(tree))
      setForm((f) => ({ ...f, permIds: ids.map(String) }))
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '加载权限失败')
      setPermTree([])
    } finally {
      setPermLoading(false)
    }
  }

  function togglePerm(id: string) {
    setForm((f) => ({
      ...f,
      permIds: cascadeTogglePermIds(permTree, f.permIds, id),
    }))
  }

  async function save() {
    setSaving(true)
    setFormError('')
    try {
      if (mode === 'perms') {
        if (!selected?.id) return
        await assignRolePermissionsApi(selected.id, form.permIds)
        setFlashId(selected.id)
        setSelected(null)
        toast.success('权限已保存')
        return
      }
      const roleCode = form.roleCode.trim()
      const roleName = form.roleName.trim()
      const remark = form.remark.trim()
      if (!roleCode || !roleName) {
        setFormError('请填写角色编码和名称')
        return
      }
      if (mode === 'create') {
        await createRoleApi({ roleCode, roleName, remark, status: form.status })
        toast.success('角色已新增')
      } else if (selected) {
        await updateRoleApi(selected.id, { roleName, remark, status: form.status })
        toast.success('角色已保存')
      }
      setFlashId(selected?.id || '')
      setSelected(null)
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function remove(r: AuthRole) {
    const ok = await confirm({
      title: '删除角色',
      message: `确认删除角色 ${r.roleCode}？`,
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    try {
      await deleteRoleApi(r.id)
      toast.success('角色已删除')
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={openCreate}>新增角色</Button>
      </div>
      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">编码</th>
              <th className="px-3">名称</th>
              <th className="px-3">状态</th>
              <th className="px-3">用户数</th>
              <th className="px-3">备注</th>
              <th className="px-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="h-16 px-3 text-center text-muted">
                  加载中…
                </td>
              </tr>
            ) : loadFailed ? (
              <tr>
                <td colSpan={6} className="h-20 px-3 text-center text-muted">
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
                <td colSpan={6} className="h-16 px-3 text-center text-muted">
                  暂无数据
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <FlashRow key={r.id} active={flashId === r.id}>
                  <td className="h-10 border-b border-border px-3 font-mono text-[13px] font-medium">
                    {r.roleCode}
                  </td>
                  <td className="border-b border-border px-3">{r.roleName}</td>
                  <td className="border-b border-border px-3">
                    <EnablePill enabled={r.status === 1} />
                  </td>
                  <td className="border-b border-border px-3 font-mono tabular-nums">{r.userCount}</td>
                  <td className="border-b border-border px-3 text-muted">{r.remark || '—'}</td>
                  <td className="border-b border-border px-3">
                    <div className="flex gap-2">
                      <button
                        type="button"
                        className="cursor-pointer text-accent hover:underline"
                        onClick={() => openEdit(r)}
                      >
                        编辑
                      </button>
                      <button
                        type="button"
                        className="cursor-pointer text-accent hover:underline"
                        onClick={() => void openPerms(r)}
                      >
                        分配权限
                      </button>
                      <button
                        type="button"
                        className="cursor-pointer text-muted hover:text-danger hover:underline"
                        onClick={() => void remove(r)}
                      >
                        删除
                      </button>
                    </div>
                  </td>
                </FlashRow>
              ))
            )}
          </tbody>
        </table>
      </div>

      <Drawer
        open={!!selected}
        title={mode === 'create' ? '新增角色' : mode === 'perms' ? '分配权限' : '编辑角色'}
        onClose={() => setSelected(null)}
        width={mode === 'perms' ? 480 : 440}
        footer={
          <>
            <Button variant="secondary" onClick={() => setSelected(null)} disabled={saving}>
              取消
            </Button>
            <Button
              onClick={() => void save()}
              loading={saving}
              disabled={
                permLoading ||
                (mode !== 'perms' && (!form.roleCode.trim() || !form.roleName.trim()))
              }
            >
              保存
            </Button>
          </>
        }
      >
        {mode === 'perms' ? (
          <div className="space-y-1">
            <p className="mb-3 text-sm text-muted">
              角色 <span className="font-mono text-ink">{form.roleCode}</span>
              · 勾选目录/菜单将联动子项；部分勾选时父级为半选
            </p>
            {formError ? <p className="mb-2 text-xs text-danger">{formError}</p> : null}
            {permLoading ? (
              <p className="text-sm text-muted">加载权限树…</p>
            ) : permTree.length === 0 ? (
              <p className="text-sm text-muted">暂无权限数据</p>
            ) : (
              permTree.map((node) => (
                <PermNode
                  key={node.id}
                  node={node}
                  checked={form.permIds}
                  onToggle={togglePerm}
                  depth={0}
                />
              ))
            )}
          </div>
        ) : (
          <div className="space-y-4">
            <Field
              label="角色编码"
              name="roleCode"
              value={form.roleCode}
              disabled={mode === 'edit'}
              onChange={(e) => setForm((f) => ({ ...f, roleCode: e.target.value }))}
            />
            <Field
              label="角色名称"
              name="roleName"
              value={form.roleName}
              onChange={(e) => setForm((f) => ({ ...f, roleName: e.target.value }))}
              error={formError || undefined}
            />
            <Field
              label="备注"
              name="remark"
              value={form.remark}
              onChange={(e) => setForm((f) => ({ ...f, remark: e.target.value }))}
            />
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
          </div>
        )}
      </Drawer>
    </div>
  )
}

function PermNode({
  node,
  checked,
  onToggle,
  depth,
}: {
  node: SysPermTreeNode
  checked: string[]
  onToggle: (id: string) => void
  depth: number
}) {
  const [open, setOpen] = useState(depth < 1)
  const id = String(node.id)
  const hasChildren = (node.children?.length ?? 0) > 0
  const state = nodeCheckState(node, checked)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.indeterminate = state === 'indeterminate'
    }
  }, [state])

  return (
    <div>
      <div
        className="flex items-center gap-1 rounded-md px-2 py-1.5 text-sm hover:bg-surface"
        style={{ paddingLeft: 8 + depth * 16 }}
      >
        {hasChildren ? (
          <button
            type="button"
            className="inline-flex size-6 shrink-0 cursor-pointer items-center justify-center rounded-sm text-muted hover:bg-border/50 hover:text-ink"
            aria-label={open ? '收起' : '展开'}
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
          >
            {open ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
          </button>
        ) : (
          <span className="inline-block size-6 shrink-0" aria-hidden />
        )}
        <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2">
          <input
            ref={inputRef}
            type="checkbox"
            checked={state === 'checked'}
            onChange={() => onToggle(id)}
          />
          <span className="shrink-0 text-xs text-muted">{permTypeLabel[node.permType]}</span>
          <span className="truncate">{node.permName}</span>
          {node.permCode ? (
            <span className="truncate font-mono text-[12px] text-muted">{node.permCode}</span>
          ) : null}
        </label>
      </div>
      {hasChildren && open
        ? node.children.map((c) => (
            <PermNode key={c.id} node={c} checked={checked} onToggle={onToggle} depth={depth + 1} />
          ))
        : null}
    </div>
  )
}
