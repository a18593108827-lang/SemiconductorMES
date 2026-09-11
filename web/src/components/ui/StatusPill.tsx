import { AlertTriangle, CircleAlert, CircleCheck, CircleDot, FilePlus2, Flame, GitMerge, Pause, Play, Rocket, Wrench } from 'lucide-react'
import { cn } from '../../lib/cn'
import type { MesEqpStatus } from '../../api/eqp'
import type { EqpStatus, LotStatus } from '../../data/mock'
import type { MesLotStatus } from '../../api/lot'

const lotMap: Record<
  LotStatus,
  { label: string; dot: string; icon: typeof CircleDot }
> = {
  Queued: { label: '排队', dot: 'bg-muted', icon: CircleDot },
  Running: { label: '加工中', dot: 'bg-success', icon: Play },
  Hold: { label: '锁批', dot: 'bg-warning', icon: Pause },
  Alarm: { label: '报警', dot: 'bg-danger', icon: AlertTriangle },
  Scrap: { label: '报废', dot: 'bg-danger', icon: CircleAlert },
}

const eqpMap: Record<EqpStatus, { label: string; dot: string; letter: string }> = {
  Idle: { label: '空闲', dot: 'bg-muted', letter: '空' },
  Running: { label: '运行', dot: 'bg-success', letter: '运' },
  Down: { label: '故障', dot: 'bg-danger', letter: '故' },
  PM: { label: '保养', dot: 'bg-warning', letter: '保' },
  Offline: { label: '离线', dot: 'bg-border', letter: '离' },
}

export function LotStatusPill({ status }: { status: LotStatus }) {
  const m = lotMap[status]
  const Icon = m.icon
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      <Icon className="size-3 text-muted" aria-hidden />
      {m.label}
    </span>
  )
}

export function EqpStatusPill({ status }: { status: EqpStatus }) {
  const m = eqpMap[status]
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      <span className="font-mono text-[11px] text-muted">{m.letter}</span>
      {m.label}
    </span>
  )
}

export function LiveDot({ paused }: { paused?: boolean }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-accent">
      <span
        className={cn('size-2 rounded-full bg-accent', !paused && 'live-dot')}
        aria-hidden
      />
      {paused ? '已暂停' : '实时'}
    </span>
  )
}

export function EnablePill({ enabled }: { enabled: boolean }) {
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', enabled ? 'bg-success' : 'bg-muted')} aria-hidden />
      {enabled ? '正常' : '禁用'}
    </span>
  )
}

export function RequestStatusPill({
  status,
}: {
  status: 'pending' | 'approved' | 'rejected' | 'cancelled'
}) {
  const map = {
    pending: { label: '待审批', dot: 'bg-warning' },
    approved: { label: '已通过', dot: 'bg-success' },
    rejected: { label: '已驳回', dot: 'bg-danger' },
    cancelled: { label: '已撤回', dot: 'bg-muted' },
  } as const
  const m = map[status]
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      {m.label}
    </span>
  )
}

export function RouteVersionPill({
  status,
}: {
  status: 'draft' | 'active' | 'archived'
}) {
  const map = {
    draft: { label: '草稿', dot: 'bg-warning' },
    active: { label: '生效', dot: 'bg-success' },
    archived: { label: '归档', dot: 'bg-muted' },
  } as const
  const m = map[status]
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      {m.label}
    </span>
  )
}

export function RecipeVersionPill({
  status,
}: {
  status: 'draft' | 'active' | 'obsolete'
}) {
  const map = {
    draft: { label: '草稿', dot: 'bg-warning' },
    active: { label: '生效', dot: 'bg-success' },
    obsolete: { label: '停用', dot: 'bg-muted' },
  } as const
  const m = map[status]
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      {m.label}
    </span>
  )
}

const mesLotMap: Record<
  MesLotStatus,
  { label: string; dot: string; icon: typeof CircleDot }
> = {
  created: { label: '已创建', dot: 'bg-muted', icon: FilePlus2 },
  released: { label: '已放行', dot: 'bg-success', icon: Rocket },
  wait: { label: '待加工', dot: 'bg-accent', icon: CircleDot },
  processing: { label: '加工中', dot: 'bg-success', icon: Play },
  held: { label: '锁批', dot: 'bg-warning', icon: Pause },
  completed: { label: '已完工', dot: 'bg-accent', icon: CircleCheck },
  scrapped: { label: '已报废', dot: 'bg-danger', icon: CircleAlert },
  merged: { label: '已合批', dot: 'bg-muted', icon: GitMerge },
}

/** Hot Lot：色点 + 文案 + 图标（三重编码） */
export function HotLotPill({ hot }: { hot?: number | boolean | null }) {
  const on = hot === true || hot === 1
  if (!on) return null
  return (
    <span
      className="inline-flex h-[22px] items-center gap-1 rounded-sm bg-warning/15 px-2 text-xs font-medium text-ink"
      title="特急批次"
    >
      <span className="size-1.5 rounded-full bg-warning" aria-hidden />
      <Flame className="size-3 text-warning" aria-hidden />
      Hot
    </span>
  )
}

/** MES 批次状态（created / released / …） */
export function MesLotStatusPill({ status }: { status: MesLotStatus }) {
  const m = mesLotMap[status] ?? mesLotMap.created
  const Icon = m.icon
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      <Icon className="size-3 text-muted" aria-hidden />
      {m.label}
    </span>
  )
}

const mesEqpMap: Record<
  MesEqpStatus,
  { label: string; dot: string; letter: string; icon: typeof CircleDot }
> = {
  idle: { label: '空闲', dot: 'bg-muted', letter: '空', icon: CircleDot },
  running: { label: '运行', dot: 'bg-success', letter: '运', icon: Play },
  down: { label: '故障', dot: 'bg-danger', letter: '故', icon: AlertTriangle },
  pm: { label: '保养', dot: 'bg-warning', letter: '保', icon: Wrench },
  eng: { label: '工程', dot: 'bg-accent', letter: '工', icon: CircleDot },
  offline: { label: '离线', dot: 'bg-border', letter: '离', icon: CircleAlert },
}

/** MES 设备业务态（idle / running / …） */
export function MesEqpStatusPill({ status }: { status: MesEqpStatus | string }) {
  const m = mesEqpMap[status as MesEqpStatus] ?? {
    label: status || '—',
    dot: 'bg-muted',
    letter: '?',
    icon: CircleDot,
  }
  const Icon = m.icon
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      <Icon className="size-3 text-muted" aria-hidden />
      <span className="font-mono text-[11px] text-muted">{m.letter}</span>
      {m.label}
    </span>
  )
}

export const lotStatusLabel = lotMap
export const mesLotStatusLabel = mesLotMap
export const eqpStatusLabel = eqpMap
export const mesEqpStatusLabel = mesEqpMap

export type MesCarrierStatus = 'AVAILABLE' | 'IN_USE' | 'QUARANTINE' | 'SCRAPPED'

const mesCarrierMap: Record<
  MesCarrierStatus,
  { label: string; dot: string; icon: typeof CircleDot }
> = {
  AVAILABLE: { label: '空闲', dot: 'bg-muted', icon: CircleDot },
  IN_USE: { label: '使用中', dot: 'bg-success', icon: Play },
  QUARANTINE: { label: '隔离', dot: 'bg-warning', icon: Pause },
  SCRAPPED: { label: '报废', dot: 'bg-danger', icon: CircleAlert },
}

export function MesCarrierStatusPill({ status }: { status: MesCarrierStatus | string }) {
  const m = mesCarrierMap[status as MesCarrierStatus] ?? {
    label: status || '—',
    dot: 'bg-muted',
    icon: CircleDot,
  }
  const Icon = m.icon
  return (
    <span className="inline-flex h-[22px] items-center gap-1.5 rounded-sm bg-surface px-2 text-xs font-medium text-ink">
      <span className={cn('size-1.5 rounded-full', m.dot)} aria-hidden />
      <Icon className="size-3 text-muted" aria-hidden />
      {m.label}
    </span>
  )
}

export const mesCarrierStatusLabel = mesCarrierMap
