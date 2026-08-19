import { request } from '../lib/http'
import type { PageResult } from './system'

export type HistorySeverity = 'danger' | 'warning' | 'info'

export interface HistoryTxItem {
  id: number | string
  lotId: number | string
  lotNo: string
  txType: string
  fromStatus: string | null
  toStatus: string | null
  fromSortNo: number | null
  toSortNo: number | null
  stepId: number | string | null
  stepName: string | null
  eqpId: number | string | null
  eqpCode: string | null
  eqpName: string | null
  recipeId: number | string | null
  recipeVersionId: number | string | null
  recipeVersionNo: number | null
  routeVersionId: number | string | null
  remark: string | null
  extJson?: string | null
  ext?: Record<string, unknown> | null
  severity?: HistorySeverity | null
  operUserId: number | string | null
  operUserName: string | null
  createTime: string | null
}

export function queryHistoryApi(query: {
  lotId?: number | string
  eqpId?: number | string
  txType?: string
  fromTime?: string
  toTime?: string
  page?: number
  size?: number
}) {
  const params = new URLSearchParams()
  if (query.lotId != null && query.lotId !== '') params.set('lotId', String(query.lotId))
  if (query.eqpId != null && query.eqpId !== '') params.set('eqpId', String(query.eqpId))
  if (query.txType?.trim()) params.set('txType', query.txType.trim())
  if (query.fromTime?.trim()) params.set('fromTime', query.fromTime.trim())
  if (query.toTime?.trim()) params.set('toTime', query.toTime.trim())
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 50))
  return request<PageResult<HistoryTxItem>>(`/history?${params}`, { method: 'GET' })
}

export function getHistoryTxApi(txId: number | string) {
  return request<HistoryTxItem>(`/history/${txId}`, { method: 'GET' })
}
