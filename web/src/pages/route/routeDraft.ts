import type { MesRouteEdgeItem, MesRouteStepItem } from '../../api/route'

export type DraftRow = { key: string; stepId: string; pathKind: 'main' | 'off' }
export type DraftEdge = {
  key: string
  edgeType: 'rework' | 'branch' | 'skip_allow' | 'off_flow' | 'time_link' | 'normal_qtime'
  fromSortNo: number
  toSortNo: number
  maxReworkCount: number
  reasonCodes: string
  conditionCode: string
  maxQueueMin: number | null
  onViolate: string
}

export const STEP_TYPE_LABEL: Record<number, string> = {
  1: '加工',
  2: '量测',
  3: '其它',
}

export const VERSION_STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  active: '生效',
  archived: '归档',
}

export function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

export function toDraftRows(steps: MesRouteStepItem[]): DraftRow[] {
  return steps.map((s, i) => ({
    key: `e-${s.id}-${i}`,
    stepId: String(s.stepId),
    pathKind: s.sortNo >= 200 ? 'off' : 'main',
  }))
}

export function emptyQtimeFields() {
  return { maxQueueMin: null as number | null, onViolate: '' }
}

export function toDraftEdges(edges: MesRouteEdgeItem[] | undefined): DraftEdge[] {
  return (edges ?? [])
    .filter(
      (e) =>
        e.edgeType === 'rework' ||
        e.edgeType === 'branch' ||
        e.edgeType === 'skip_allow' ||
        e.edgeType === 'off_flow' ||
        e.edgeType === 'time_link' ||
        (e.edgeType === 'normal' && e.maxQueueMin != null && e.maxQueueMin >= 1),
    )
    .map((e, i) => ({
      key: `edge-${e.id ?? i}`,
      edgeType:
        e.edgeType === 'normal'
          ? ('normal_qtime' as const)
          : (e.edgeType as DraftEdge['edgeType']),
      fromSortNo: e.fromSortNo,
      toSortNo: e.toSortNo,
      maxReworkCount: e.maxReworkCount ?? 1,
      reasonCodes: e.reasonCodes ?? '',
      conditionCode: e.conditionCode ?? '',
      maxQueueMin: e.maxQueueMin ?? null,
      onViolate: e.onViolate ?? '',
    }))
}

export function toSaveSteps(rows: DraftRow[]) {
  const main = rows.filter((r) => r.pathKind === 'main')
  const off = rows.filter((r) => r.pathKind === 'off')
  const chain = (list: DraftRow[], base: number) =>
    list.map((r, i) => ({
      stepId: r.stepId,
      sortNo: base + (i + 1) * 10,
      nextSortNo: i < list.length - 1 ? base + (i + 2) * 10 : null,
    }))
  return [...chain(main, 0), ...chain(off, 200)]
}

export function displaySortNo(rows: DraftRow[], index: number) {
  const row = rows[index]
  if (row.pathKind === 'off') {
    const offIdx = rows.slice(0, index + 1).filter((r) => r.pathKind === 'off').length
    return 200 + offIdx * 10
  }
  const mainIdx = rows.slice(0, index + 1).filter((r) => r.pathKind === 'main').length
  return mainIdx * 10
}

export function toSaveEdges(edges: DraftEdge[]) {
  return edges.map((e, i) => {
    const qtime =
      e.maxQueueMin != null && e.maxQueueMin >= 1
        ? {
            maxQueueMin: e.maxQueueMin,
            onViolate: e.onViolate.trim() || null,
          }
        : {}
    if (e.edgeType === 'branch') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'branch',
        conditionCode: e.conditionCode.trim().toUpperCase() || null,
        sortNo: i,
        ...qtime,
      }
    }
    if (e.edgeType === 'skip_allow') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'skip_allow',
        reasonCodes: e.reasonCodes.trim() || null,
        sortNo: i,
        ...qtime,
      }
    }
    if (e.edgeType === 'off_flow') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'off_flow',
        maxReworkCount: e.maxReworkCount,
        reasonCodes: e.reasonCodes.trim() || null,
        sortNo: i,
        ...qtime,
      }
    }
    if (e.edgeType === 'time_link') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'time_link',
        maxQueueMin: e.maxQueueMin ?? 1,
        onViolate: e.onViolate.trim() || null,
        sortNo: i,
      }
    }
    if (e.edgeType === 'normal_qtime') {
      return {
        fromSortNo: e.fromSortNo,
        toSortNo: e.toSortNo,
        edgeType: 'normal',
        maxQueueMin: e.maxQueueMin ?? 1,
        onViolate: e.onViolate.trim() || null,
        sortNo: i,
      }
    }
    return {
      fromSortNo: e.fromSortNo,
      toSortNo: e.toSortNo,
      edgeType: 'rework',
      maxReworkCount: e.maxReworkCount,
      reasonCodes: e.reasonCodes.trim() || null,
      sortNo: i,
      ...qtime,
    }
  })
}
