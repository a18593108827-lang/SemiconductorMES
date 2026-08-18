import { useCallback, useEffect, useMemo, useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import {
  createPermApi,
  deletePermApi,
  permTreeApi,
  updatePermApi,
  type SysPermTreeNode,
} from '../../api/system'
import { Button } from '../../components/ui/Button'
import { useConfirm } from '../../components/ui/ConfirmDialog'
import { Drawer } from '../../components/ui/Drawer'
import { Field } from '../../components/ui/Field'
import { EnablePill } from '../../components/ui/StatusPill'
import { useToast } from '../../components/ui/Toast'
import { permTypeLabel, type UserStatus } from '../../data/authMock'
import { ApiError } from '../../lib/http'
import { cn } from '../../lib/cn'

type TreeNode = {
  id: string
  parentId: string
  permType: 1 | 2 | 3
  permCode: string
  permName: string
  path?: string
  sortNo: number
  status: UserStatus
  children: TreeNode[]
}

function mapTree(nodes: SysPermTreeNode[]): TreeNode[] {
  return (nodes ?? []).map((n) => ({
    id: String(n.id),
    parentId: String(n.parentId ?? 0),
    permType: n.permType,
    permCode: n.permCode ?? '',
    permName: n.permName,
    path: n.path ?? undefined,
    sortNo: n.sortNo ?? 0,
    status: n.status,
    children: mapTree(n.children ?? []),
  }))
}

function flatten(nodes: TreeNode[]): TreeNode[] {
  const out: TreeNode[] = []
  nodes.forEach((n) => {
    out.push(n)
    out.push(...flatten(n.children))
  })
  return out
}

export function PermsPage() {
  const toast = useToast()
  const confirm = useConfirm()
  const [tree, setTree] = useState<TreeNode[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [selected, setSelected] = useState<TreeNode | null>(null)
  const [mode, setMode] = useState<'edit' | 'create'>('edit')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [form, setForm] = useState({
    parentId: '0',
    permType: 2 as 1 | 2 | 3,
    permCode: '',
    permName: '',
    path: '',
    sortNo: 100,
    status: 1 as UserStatus,
  })

  const branchIds = useMemo(() => collectBranchIds(tree), [tree])
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const rows = useMemo(() => flatten(tree), [tree])
  const flatParents = useMemo(() => rows.filter((p) => p.permType !== 3), [rows])
  const visible = useMemo(() => flattenVisible(tree, expanded), [tree, expanded])

  const load = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const data = await permTreeApi()
      const next = mapTree(data)
      setTree(next)
      setExpanded((prev) => (prev.size ? prev : new Set(next.map((n) => n.id))))
    } catch (err) {
      setTree([])
      setLoadFailed(true)
      toast.error(err instanceof ApiError ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    void load()
  }, [load])

  function toggle(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function openCreate(parentId = '0') {
    setMode('create')
    setFormError('')
    setForm({
      parentId,
      permType: parentId === '0' ? 1 : 2,
      permCode: '',
      permName: '',
      path: '',
      sortNo: 100,
      status: 1,
    })
    setSelected({
      id: '',
      parentId,
      permType: 2,
      permCode: '',
      permName: '',
      sortNo: 100,
      status: 1,
      children: [],
    })
  }

  function openEdit(p: TreeNode) {
    setMode('edit')
    setFormError('')
    setSelected(p)
    setForm({
      parentId: p.parentId,
      permType: p.permType,
      permCode: p.permCode,
      permName: p.permName,
      path: p.path ?? '',
      sortNo: p.sortNo,
      status: p.status,
    })
  }

  async function save() {
    if (!form.permName.trim()) {
      setFormError('请填写名称')
      return
    }
    setSaving(true)
    setFormError('')
    const body = {
      parentId: form.parentId === '0' ? 0 : form.parentId,
      permType: form.permType,
      permCode: form.permCode.trim() || undefined,
      permName: form.permName.trim(),
      path: form.path.trim() || undefined,
      sortNo: form.sortNo,
      status: form.status,
    }
    try {
      if (mode === 'create') {
        await createPermApi(body)
        if (form.parentId !== '0') {
          setExpanded((prev) => new Set(prev).add(form.parentId))
        }
        toast.success('权限已新增')
      } else if (selected?.id) {
        await updatePermApi(selected.id, body)
        toast.success('权限已保存')
      }
      setSelected(null)
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function remove(p: TreeNode) {
    const ok = await confirm({
      title: '删除权限',
      message: `确认删除权限「${p.permName}」？`,
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    try {
      await deletePermApi(p.id)
      toast.success('权限已删除')
      await load()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm text-muted">目录 / 菜单 / 按钮</p>
          <button
            type="button"
            className="cursor-pointer text-xs text-accent hover:underline"
            onClick={() => setExpanded(new Set(branchIds))}
          >
            全部展开
          </button>
          <button
            type="button"
            className="cursor-pointer text-xs text-muted hover:text-ink hover:underline"
            onClick={() => setExpanded(new Set())}
          >
            全部收起
          </button>
        </div>
        <Button onClick={() => openCreate()}>新增权限</Button>
      </div>

      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[800px] text-left text-sm">
          <thead className="sticky top-0 bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">名称</th>
              <th className="px-3">类型</th>
              <th className="px-3">权限码</th>
              <th className="px-3">路由</th>
              <th className="px-3">排序</th>
              <th className="px-3">状态</th>
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
            ) : visible.length === 0 ? (
              <tr>
                <td colSpan={7} className="h-16 px-3 text-center text-muted">
                  暂无数据
                </td>
              </tr>
            ) : (
              visible.map(({ node, depth }) => {
                const hasChildren = node.children.length > 0
                const isOpen = expanded.has(node.id)
                return (
                  <tr key={node.id} className="h-10 border-b border-border hover:bg-surface/80">
                    <td className="px-3">
                      <div className="flex items-center gap-1" style={{ paddingLeft: depth * 16 }}>
                        {hasChildren ? (
                          <button
                            type="button"
                            className="inline-flex size-6 cursor-pointer items-center justify-center rounded-sm text-muted hover:bg-border/50 hover:text-ink"
                            aria-label={isOpen ? '收起' : '展开'}
                            aria-expanded={isOpen}
                            onClick={() => toggle(node.id)}
                          >
                            {isOpen ? (
                              <ChevronDown className="size-4" />
                            ) : (
                              <ChevronRight className="size-4" />
                            )}
                          </button>
                        ) : (
                          <span className="inline-block size-6" aria-hidden />
                        )}
                        <span
                          className={cn(
                            'font-medium',
                            node.permType === 1 && 'text-ink',
                            node.permType === 3 && 'font-normal text-muted',
                          )}
                        >
                          {node.permName}
                        </span>
                        {hasChildren ? (
                          <span className="font-mono text-[11px] text-muted">{node.children.length}</span>
                        ) : null}
                      </div>
                    </td>
                    <td className="px-3 text-muted">{permTypeLabel[node.permType]}</td>
                    <td className="px-3 font-mono text-[13px]">{node.permCode || '—'}</td>
                    <td className="px-3 font-mono text-[12px] text-muted">{node.path || '—'}</td>
                    <td className="px-3 font-mono tabular-nums">{node.sortNo}</td>
                    <td className="px-3">
                      <EnablePill enabled={node.status === 1} />
                    </td>
                    <td className="px-3">
                      <div className="flex gap-2">
                        <button
                          type="button"
                          className="cursor-pointer text-accent hover:underline"
                          onClick={() => openEdit(node)}
                        >
                          编辑
                        </button>
                        {node.permType !== 3 ? (
                          <button
                            type="button"
                            className="cursor-pointer text-muted hover:text-ink hover:underline"
                            onClick={() => openCreate(node.id)}
                          >
                            子项
                          </button>
                        ) : null}
                        <button
                          type="button"
                          className="cursor-pointer text-muted hover:text-danger hover:underline"
                          onClick={() => void remove(node)}
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      <Drawer
        open={!!selected}
        title={mode === 'create' ? '新增权限' : '编辑权限'}
        onClose={() => setSelected(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setSelected(null)} disabled={saving}>
              取消
            </Button>
            <Button onClick={() => void save()} loading={saving} disabled={!form.permName.trim()}>
              保存
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {formError ? <p className="text-xs text-danger">{formError}</p> : null}
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">父级</span>
            <select
              className="h-9 rounded-md border border-border bg-bg px-3"
              value={form.parentId}
              onChange={(e) => setForm((f) => ({ ...f, parentId: e.target.value }))}
            >
              <option value="0">根</option>
              {flatParents
                .filter((p) => mode === 'create' || p.id !== selected?.id)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.permName}
                  </option>
                ))}
            </select>
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted">类型</span>
            <select
              className="h-9 rounded-md border border-border bg-bg px-3"
              value={form.permType}
              onChange={(e) =>
                setForm((f) => ({ ...f, permType: Number(e.target.value) as 1 | 2 | 3 }))
              }
            >
              <option value={1}>目录</option>
              <option value={2}>菜单</option>
              <option value={3}>按钮</option>
            </select>
          </label>
          <Field
            label="名称"
            name="permName"
            value={form.permName}
            onChange={(e) => setForm((f) => ({ ...f, permName: e.target.value }))}
          />
          <Field
            label="权限码"
            name="permCode"
            value={form.permCode}
            onChange={(e) => setForm((f) => ({ ...f, permCode: e.target.value }))}
            placeholder="module:action"
          />
          <Field
            label="路由"
            name="path"
            value={form.path}
            onChange={(e) => setForm((f) => ({ ...f, path: e.target.value }))}
            placeholder="/app/..."
          />
          <Field
            label="排序"
            name="sortNo"
            type="number"
            value={form.sortNo}
            onChange={(e) => setForm((f) => ({ ...f, sortNo: Number(e.target.value) }))}
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
      </Drawer>
    </div>
  )
}

function collectBranchIds(nodes: TreeNode[]): string[] {
  const ids: string[] = []
  nodes.forEach((n) => {
    if (n.children.length > 0) {
      ids.push(n.id)
      ids.push(...collectBranchIds(n.children))
    }
  })
  return ids
}

function flattenVisible(
  nodes: TreeNode[],
  expanded: Set<string>,
  depth = 0,
): Array<{ node: TreeNode; depth: number }> {
  const out: Array<{ node: TreeNode; depth: number }> = []
  nodes.forEach((n) => {
    out.push({ node: n, depth })
    if (n.children.length > 0 && expanded.has(n.id)) {
      out.push(...flattenVisible(n.children, expanded, depth + 1))
    }
  })
  return out
}
