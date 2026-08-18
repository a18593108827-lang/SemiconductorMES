import { request } from '../lib/http'
import type { PageResult } from './system'

export type MesLotStatus =
  | 'created'
  | 'released'
  | 'wait'
  | 'processing'
  | 'held'
  | 'completed'
  | 'scrapped'
  | 'merged'

export interface MesLotStepItem {
  stepId: number | string
  stepCode: string
  stepName: string
  sortNo: number
  nextSortNo: number | null
  eqpType?: string | null
  stepType?: number | null
}

export interface MesLotItem {
  id: number | string
  lotNo: string
  productCode: string | null
  qty: number
  scrapQty?: number | null
  priority: number
  hotFlag?: number | null
  customerLot: string | null
  parentLotId?: number | string | null
  mergedToLotId?: number | string | null
  routeId: number | string | null
  routeCode: string | null
  routeName: string | null
  routeVersionId: number | string | null
  routeVersionNo: number | null
  currentSortNo: number | null
  currentStepId: number | string | null
  currentEqpId: number | string | null
  status: MesLotStatus
  remark: string | null
  version: number
  steps?: MesLotStepItem[] | null
  createTime: string | null
  updateTime: string | null
}

/** 谱系树节点 */
export interface MesLotGenealogyNode {
  lotId: number | string
  lotNo: string
  qty: number
  status: MesLotStatus
  txnType: string | null
  txnTime: string | null
  qtyTransferred?: number | null
  txId?: number | string | null
  reasonCode?: string | null
  children?: MesLotGenealogyNode[] | null
}

export function getLotGenealogyApi(
  id: number | string,
  query: { direction?: 'up' | 'down' | 'both'; depth?: number } = {},
) {
  const params = new URLSearchParams()
  if (query.direction) params.set('direction', query.direction)
  if (query.depth != null) params.set('depth', String(query.depth))
  const q = params.toString()
  return request<MesLotGenealogyNode>(`/lots/${id}/genealogy${q ? `?${q}` : ''}`, {
    method: 'GET',
  })
}

export function listLotsApi(query: {
  keyword?: string
  status?: MesLotStatus | ''
  hotFlag?: 0 | 1
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status) params.set('status', query.status)
  if (query.hotFlag === 0 || query.hotFlag === 1) params.set('hotFlag', String(query.hotFlag))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesLotItem>>(`/lots?${params}`, { method: 'GET' })
}

export function getLotApi(id: number | string) {
  return request<MesLotItem>(`/lots/${id}`, { method: 'GET' })
}

export function createLotApi(body: {
  lotNo?: string
  productCode?: string
  qty: number
  priority?: number
  hotFlag?: number
  customerLot?: string
  routeId?: number | string
  remark?: string
}) {
  return request<{ id: number | string; lotNo: string }>('/lots', {
    method: 'POST',
    body: {
      lotNo: body.lotNo || undefined,
      productCode: body.productCode || undefined,
      qty: body.qty,
      priority: body.priority,
      hotFlag: body.hotFlag,
      customerLot: body.customerLot || undefined,
      routeId: body.routeId || undefined,
      remark: body.remark || undefined,
    },
  })
}

export function updateLotApi(
  id: number | string,
  body: {
    productCode?: string
    qty: number
    priority: number
    hotFlag?: number
    customerLot?: string
    routeId?: number | string | null
    remark?: string
  },
) {
  return request<void>(`/lots/${id}`, {
    method: 'PUT',
    body: {
      productCode: body.productCode || undefined,
      qty: body.qty,
      priority: body.priority,
      hotFlag: body.hotFlag,
      customerLot: body.customerLot || undefined,
      routeId: body.routeId != null && body.routeId !== '' ? body.routeId : null,
      remark: body.remark || undefined,
    },
  })
}

export function releaseLotApi(id: number | string) {
  return request<void>(`/lots/${id}/release`, { method: 'POST' })
}
