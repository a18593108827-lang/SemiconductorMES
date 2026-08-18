import { request } from '../lib/http'
import type { MesLotStatus } from './lot'
import type { PageResult } from './system'

export type WipStatus = Extract<MesLotStatus, 'wait' | 'processing' | 'held'>

export interface MesWipItem {
  lotId: number | string
  lotNo: string
  productCode: string | null
  qty: number
  priority: number
  hotFlag?: number | null
  customerLot: string | null
  status: WipStatus
  currentSortNo: number | null
  currentStepId: number | string | null
  currentStepCode: string | null
  currentStepName: string | null
  currentEqpId: number | string | null
  routeId: number | string | null
  routeCode: string | null
  routeName: string | null
  routeVersionId: number | string | null
  routeVersionNo: number | null
  updateTime: string | null
}

export interface MesWipStepSummary {
  sortNo: number | null
  stepId: number | string | null
  stepCode: string | null
  stepName: string | null
  waitCount: number
  processingCount: number
  heldCount: number
  total: number
}

export function listWipApi(query: {
  keyword?: string
  status?: WipStatus | ''
  productCode?: string
  currentSortNo?: number | null
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status) params.set('status', query.status)
  if (query.productCode?.trim()) params.set('productCode', query.productCode.trim())
  if (query.currentSortNo != null) params.set('currentSortNo', String(query.currentSortNo))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesWipItem>>(`/wip?${params}`, { method: 'GET' })
}

export function wipSummaryByStepApi() {
  return request<MesWipStepSummary[]>('/wip/summary/by-step', { method: 'GET' })
}
