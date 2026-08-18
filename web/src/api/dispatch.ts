import { request } from '../lib/http'
import type { MesEqpStatus } from './eqp'

export interface DispatchCandidateItem {
  eqpId: number | string
  eqpCode: string
  eqpName: string
  eqpType: string | null
  status: MesEqpStatus | string
  area: string | null
  score: number | null
  reason: string | null
  loadCount: number | null
}

export interface DispatchCandidates {
  lotId: number | string
  lotNo: string
  lotStatus: string
  eqpType: string | null
  recommendedEqpId: number | string | null
  held: boolean | null
  message: string | null
  candidates: DispatchCandidateItem[]
}

export type DispatchReserveStatus = 'active' | 'released' | 'expired' | 'consumed'

export interface DispatchReserveItem {
  id: number | string
  lotId: number | string
  lotNo: string | null
  eqpId: number | string
  eqpCode: string | null
  eqpName: string | null
  status: DispatchReserveStatus | string
  expireTime: string | null
  reserveUserId: number | string | null
  consumeTxId: number | string | null
  remark: string | null
  createTime: string | null
  updateTime: string | null
}

export function getDispatchCandidatesApi(lotId: number | string) {
  return request<DispatchCandidates>(`/dispatch/candidates?lotId=${lotId}`, { method: 'GET' })
}

export function createDispatchReserveApi(body: {
  lotId: number | string
  eqpId: number | string
  remark?: string
}) {
  return request<DispatchReserveItem>('/dispatch/reserve', { method: 'POST', body })
}

export function releaseDispatchReserveApi(id: number | string, remark?: string) {
  return request<DispatchReserveItem>(`/dispatch/reserves/${id}/release`, {
    method: 'POST',
    body: remark ? { remark } : {},
  })
}

export function listDispatchReservesApi(query: {
  lotId?: number | string
  eqpId?: number | string
  status?: string
}) {
  const params = new URLSearchParams()
  if (query.lotId != null && query.lotId !== '') params.set('lotId', String(query.lotId))
  if (query.eqpId != null && query.eqpId !== '') params.set('eqpId', String(query.eqpId))
  if (query.status?.trim()) params.set('status', query.status.trim())
  return request<DispatchReserveItem[]>(`/dispatch/reserves?${params}`, { method: 'GET' })
}
