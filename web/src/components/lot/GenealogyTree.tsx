import { useEffect, useMemo, useRef, useState } from 'react'
import gsap from 'gsap'
import { ChevronDown, ChevronUp } from 'lucide-react'
import type { MesLotGenealogyNode, MesLotStatus } from '../../api/lot'
import { MesLotStatusPill } from '../ui/StatusPill'
import { cn } from '../../lib/cn'
import { motionMs } from '../../lib/motion'

/** @deprecated 深追改为展开；保留类型兼容 */
export type GenealogyDirection = 'both' | 'up' | 'down'

function findPath(
  root: MesLotGenealogyNode,
  targetId: string | number,
  trail: MesLotGenealogyNode[] = [],
): MesLotGenealogyNode[] | null {
  const next = [...trail, root]
  if (String(root.lotId) === String(targetId)) return next
  for (const c of root.children ?? []) {
    const hit = findPath(c, targetId, next)
    if (hit) return hit
  }
  return null
}

function walk(node: MesLotGenealogyNode, fn: (n: MesLotGenealogyNode) => void) {
  fn(node)
  for (const c of node.children ?? []) walk(c, fn)
}

function edgeLabel(child: MesLotGenealogyNode): string {
  const n = child.qtyTransferred
  if (child.txnType === 'merge') return n != null ? `并入 ${n}` : '并入'
  if (child.txnType === 'split') return n != null ? `分出 ${n}` : '分出'
  return '关联'
}

function countFamily(root: MesLotGenealogyNode) {
  const seen = new Set<string>()
  let wip = 0
  let merged = 0
  let scrapped = 0
  walk(root, (n) => {
    const id = String(n.lotId)
    if (seen.has(id)) return
    seen.add(id)
    const s = n.status as MesLotStatus
    if (s === 'wait' || s === 'processing' || s === 'held' || s === 'released') wip += 1
    if (s === 'merged') merged += 1
    if (s === 'scrapped') scrapped += 1
  })
  return { total: seen.size, wip, merged, scrapped }
}

function countDescendants(node: MesLotGenealogyNode): number {
  let n = 0
  for (const c of node.children ?? []) {
    n += 1 + countDescendants(c)
  }
  return n
}

function LotChip({
  node,
  role,
  active,
  large,
  onSelect,
  tone,
}: {
  node: MesLotGenealogyNode
  role?: string
  active?: boolean
  large?: boolean
  onSelect?: (lotId: string | number) => void
  tone: 'admin' | 'field'
}) {
  const clickable = onSelect != null && !active
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!active || !ref.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const flash =
      tone === 'admin' ? 'oklch(0.58 0.12 230 / 0.16)' : 'oklch(0.58 0.12 230 / 0.28)'
    gsap.fromTo(
      ref.current,
      { backgroundColor: flash },
      { backgroundColor: 'transparent', duration: Math.max(d, 0.35), ease: 'power2.out' },
    )
  }, [active, node.lotId, tone])

  const shell =
    tone === 'admin'
      ? active
        ? 'border-primary/50 bg-primary/5'
        : 'border-border bg-bg hover:bg-surface'
      : active
        ? 'border-accent/50 bg-accent/15'
        : 'border-field-border bg-field-bg hover:bg-field-surface'

  return (
    <div
      ref={ref}
      className={cn(
        'mx-auto w-full max-w-[320px] rounded-md border px-3 transition-colors',
        large ? 'py-2.5' : 'py-2',
        shell,
        clickable && 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent',
      )}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      onClick={clickable ? () => onSelect(node.lotId) : undefined}
      onKeyDown={
        clickable
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                onSelect(node.lotId)
              }
            }
          : undefined
      }
    >
      <div className="flex flex-wrap items-center justify-center gap-2">
        {role ? (
          <span
            className={cn(
              'rounded-sm px-1.5 py-0.5 text-[11px] font-medium',
              active
                ? tone === 'admin'
                  ? 'bg-primary/10 text-primary'
                  : 'bg-accent/20 text-accent'
                : tone === 'admin'
                  ? 'bg-surface text-muted'
                  : 'bg-field-surface text-field-muted',
            )}
          >
            {role}
          </span>
        ) : null}
        <span
          className={cn(
            'font-mono font-medium',
            large ? 'text-sm' : 'text-[13px]',
            tone === 'admin' ? 'text-ink' : 'text-field-ink',
          )}
        >
          {node.lotNo}
        </span>
        {tone === 'admin' ? <MesLotStatusPill status={node.status} /> : null}
        <span className={cn('font-mono text-xs', tone === 'admin' ? 'text-muted' : 'text-field-muted')}>
          {node.qty}
        </span>
      </div>
    </div>
  )
}

function Edge({ child, tone }: { child: MesLotGenealogyNode; tone: 'admin' | 'field' }) {
  return (
    <div
      className={cn(
        'flex flex-col items-center py-1',
        tone === 'admin' ? 'text-muted' : 'text-field-muted',
      )}
    >
      <span className={cn('h-3 w-px', tone === 'admin' ? 'bg-border' : 'bg-field-border')} aria-hidden />
      <span
        className={cn(
          'rounded-sm px-1.5 py-0.5 text-[11px] font-medium',
          tone === 'admin' ? 'bg-surface text-ink' : 'bg-field-surface text-field-ink',
        )}
      >
        {edgeLabel(child)}
      </span>
      <span className={cn('h-3 w-px', tone === 'admin' ? 'bg-border' : 'bg-field-border')} aria-hidden />
    </div>
  )
}

function ImpactStrip({
  stats,
  tone,
}: {
  stats: { total: number; wip: number; merged: number; scrapped: number }
  tone: 'admin' | 'field'
}) {
  const items = [
    { k: '家族', v: stats.total },
    { k: '在制', v: stats.wip },
    { k: '已合批', v: stats.merged },
    { k: '报废', v: stats.scrapped },
  ]
  return (
    <div
      className={cn(
        'flex flex-wrap gap-x-3 gap-y-1 rounded-md border px-3 py-2 text-xs',
        tone === 'admin' ? 'border-border bg-surface text-muted' : 'border-field-border bg-field-surface text-field-muted',
      )}
    >
      {items.map((it, i) => (
        <span key={it.k} className="inline-flex items-baseline gap-1">
          {i > 0 ? <span className="select-none opacity-40" aria-hidden>·</span> : null}
          <span>{it.k}</span>
          <span
            className={cn(
              'font-mono font-medium',
              tone === 'admin' ? 'text-ink' : 'text-field-ink',
            )}
          >
            {it.v}
          </span>
        </span>
      ))}
    </div>
  )
}

function ExpandBtn({
  open,
  label,
  onClick,
  tone,
}: {
  open: boolean
  label: string
  onClick: () => void
  tone: 'admin' | 'field'
}) {
  const Icon = open ? ChevronUp : ChevronDown
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'mx-auto flex h-7 items-center gap-1 rounded-sm px-2 text-xs font-medium',
        tone === 'admin' ? 'text-muted hover:bg-surface hover:text-ink' : 'text-field-muted hover:bg-field-surface hover:text-field-ink',
      )}
    >
      <Icon className="size-3.5" aria-hidden />
      {label}
    </button>
  )
}

function ChildBranch({
  child,
  expandDeep,
  onSelectLot,
  tone,
}: {
  child: MesLotGenealogyNode
  expandDeep: boolean
  onSelectLot?: (lotId: string | number) => void
  tone: 'admin' | 'field'
}) {
  const role = child.txnType === 'merge' ? '并入源' : '子批'
  const grand = child.children ?? []
  return (
    <div className="flex min-w-[140px] max-w-[200px] flex-1 flex-col items-center">
      <Edge child={child} tone={tone} />
      <LotChip node={child} role={role} onSelect={onSelectLot} tone={tone} />
      {expandDeep && grand.length > 0
        ? grand.map((g) => (
            <ChildBranch
              key={String(g.lotId)}
              child={g}
              expandDeep
              onSelectLot={onSelectLot}
              tone={tone}
            />
          ))
        : null}
    </div>
  )
}

function GenealogyDiagram({
  root,
  highlightId,
  onSelectLot,
  tone,
}: {
  root: MesLotGenealogyNode
  highlightId?: string | number
  onSelectLot?: (lotId: string | number) => void
  tone: 'admin' | 'field'
}) {
  const [expandUp, setExpandUp] = useState(false)
  const [expandDown, setExpandDown] = useState(false)

  const { self, parent, olderAncestors, children, deepUp, deepDown, stats } = useMemo(() => {
    const id = highlightId ?? root.lotId
    const path = findPath(root, id) ?? [root]
    const selfNode = path[path.length - 1]!
    const parentNode = path.length >= 2 ? path[path.length - 2]! : null
    const older = path.slice(0, -2)
    const kids = selfNode.children ?? []
    return {
      self: selfNode,
      parent: parentNode,
      olderAncestors: older,
      children: kids,
      deepUp: older.length,
      deepDown: kids.reduce((s, c) => s + countDescendants(c), 0),
      stats: countFamily(root),
    }
  }, [root, highlightId])

  const empty = !parent && children.length === 0

  if (empty) {
    return (
      <p className={cn('text-sm', tone === 'admin' ? 'text-muted' : 'text-field-muted')}>
        这批还没有分批或合批记录。需要拆批 / 合批时，请去现场台操作。
      </p>
    )
  }

  return (
    <div className="space-y-3">
      <ImpactStrip stats={stats} tone={tone} />

      <div className="flex flex-col items-center">
        {deepUp > 0 ? (
          <ExpandBtn
            open={expandUp}
            label={expandUp ? '收起更早批次' : `展开祖先（${deepUp}）`}
            onClick={() => setExpandUp((v) => !v)}
            tone={tone}
          />
        ) : null}

        {expandUp && olderAncestors.length > 0
          ? olderAncestors.map((anc, i) => {
              const next = i === olderAncestors.length - 1 ? parent! : olderAncestors[i + 1]!
              return (
                <div key={String(anc.lotId)} className="flex w-full flex-col items-center">
                  <LotChip node={anc} role="更早" onSelect={onSelectLot} tone={tone} />
                  <Edge child={next} tone={tone} />
                </div>
              )
            })
          : null}

        {parent ? (
          <>
            <LotChip
              node={parent}
              role={self.txnType === 'merge' ? '合批主批' : '父批'}
              onSelect={onSelectLot}
              tone={tone}
            />
            <Edge child={self} tone={tone} />
          </>
        ) : null}

        <LotChip node={self} role="当前" active large onSelect={onSelectLot} tone={tone} />

        {children.length > 0 ? (
          <div className="flex w-full flex-wrap justify-center gap-2 pt-0">
            {children.map((c) => (
              <ChildBranch
                key={String(c.lotId)}
                child={c}
                expandDeep={expandDown}
                onSelectLot={onSelectLot}
                tone={tone}
              />
            ))}
          </div>
        ) : null}

        {deepDown > 0 ? (
          <div className="mt-1">
            <ExpandBtn
              open={expandDown}
              label={expandDown ? '收起更深子孙' : `展开子孙（${deepDown}）`}
              onClick={() => setExpandDown((v) => !v)}
              tone={tone}
            />
          </div>
        ) : null}
      </div>
    </div>
  )
}

function Skeleton({ tone }: { tone: 'admin' | 'field' }) {
  const bar = tone === 'admin' ? 'bg-surface border-border' : 'bg-field-surface border-field-border'
  return (
    <div className="flex flex-col items-center gap-2" aria-busy aria-label="谱系加载中">
      <div className={cn('h-8 w-full animate-pulse rounded-md border', bar)} />
      <div className={cn('h-12 w-48 animate-pulse rounded-md border', bar)} />
      <div className={cn('h-3 w-px', tone === 'admin' ? 'bg-border' : 'bg-field-border')} />
      <div className={cn('h-14 w-56 animate-pulse rounded-md border', bar)} />
    </div>
  )
}

/** 谱系简图：当前居中 · 直系默认 · 深链折叠 · 影响面 */
export function GenealogyTree({
  root,
  highlightId,
  loading = false,
  onSelectLot,
}: {
  root: MesLotGenealogyNode | null
  highlightId?: string | number
  emptyText?: string
  loading?: boolean
  direction?: GenealogyDirection
  onDirectionChange?: (d: GenealogyDirection) => void
  onSelectLot?: (lotId: string | number) => void
}) {
  if (loading) return <Skeleton tone="admin" />
  if (!root) {
    return <p className="text-sm text-muted">这批还没有分批或合批记录。需要拆批 / 合批时，请去现场台操作。</p>
  }
  return (
    <GenealogyDiagram
      root={root}
      highlightId={highlightId}
      onSelectLot={onSelectLot}
      tone="admin"
    />
  )
}

/** 现场台深色谱系 */
export function GenealogyTreeField({
  root,
  highlightId,
  loading = false,
  onSelectLot,
}: {
  root: MesLotGenealogyNode | null
  highlightId?: string | number
  loading?: boolean
  onSelectLot?: (lotId: string | number) => void
}) {
  if (loading) return <Skeleton tone="field" />
  if (!root) return <p className="text-sm text-field-muted">暂无分合批记录</p>
  return (
    <GenealogyDiagram
      root={root}
      highlightId={highlightId}
      onSelectLot={onSelectLot}
      tone="field"
    />
  )
}
