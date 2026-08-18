import { request } from '../lib/http'
import type { PageResult } from './system'

export type HoldStatus = 'active' | 'released'

export type FutureHoldStatus = 'pending' | 'activated' | 'cancelled'
export type FutureHoldTiming = 'PRE' | 'POST'

export interface MesHoldReason {
  id: number | string
  reasonCode: string
  reasonName: string
  category: string | null
  status: number
  remark: string | null
}

export interface MesHoldItem {
  id: number | string
  lotId: number | string
  lotNo: string
  reasonId: number | string
  reasonCode: string
  reasonName: string | null
  status: HoldStatus
  prevStatus: string | null
  remark: string | null
  releaseRemark: string | null
  holdUserId: number | string | null
  holdUserName: string | null
  holdTime: string | null
  releaseUserId: number | string | null
  releaseUserName: string | null
  releaseTime: string | null
  createTime: string | null
}

export interface MesFutureHoldItem {
  id: number | string
  lotId: number | string
  lotNo: string
  routeVersionId: number | string
  targetSortNo: number
  timing: FutureHoldTiming
  reasonId: number | string
  reasonCode: string
  reasonName: string | null
  status: FutureHoldStatus
  holdId: number | string | null
  remark: string | null
  ownerUserId: number | string | null
  ownerUserName: string | null
  createUserId: number | string | null
  createUserName: string | null
  createTime: string | null
  activateTime: string | null
  cancelUserId: number | string | null
  cancelUserName: string | null
  cancelTime: string | null
  cancelRemark: string | null
}

export function listHoldReasonsApi(all = false) {
  const q = all ? '?all=true' : ''
  return request<MesHoldReason[]>(`/holds/reasons${q}`, { method: 'GET' })
}

export function updateHoldReasonStatusApi(id: number | string, status: 0 | 1) {
  return request<void>(`/holds/reasons/${id}/status`, {
    method: 'PUT',
    body: { status },
  })
}

export function listHoldsApi(query: {
  keyword?: string
  status?: HoldStatus | 'all' | ''
  reasonCode?: string
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status) params.set('status', query.status)
  if (query.reasonCode?.trim()) params.set('reasonCode', query.reasonCode.trim())
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesHoldItem>>(`/holds?${params}`, { method: 'GET' })
}

export function getHoldApi(id: number | string) {
  return request<MesHoldItem>(`/holds/${id}`, { method: 'GET' })
}

export function listLotHoldsApi(lotId: number | string) {
  return request<MesHoldItem[]>(`/lots/${lotId}/holds`, { method: 'GET' })
}

export function createHoldApi(body: {
  lotId: number | string
  reasonCode: string
  remark?: string
}) {
  return request<MesHoldItem>('/holds', {
    method: 'POST',
    body: {
      lotId: body.lotId,
      reasonCode: body.reasonCode,
      remark: body.remark || undefined,
    },
  })
}

export function releaseHoldApi(id: number | string, remark?: string) {
  return request<MesHoldItem>(`/holds/${id}/release`, {
    method: 'POST',
    body: { remark: remark || undefined },
  })
}

export function listFutureHoldsApi(query: {
  keyword?: string
  status?: FutureHoldStatus | 'all' | ''
  reasonCode?: string
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status) params.set('status', query.status)
  if (query.reasonCode?.trim()) params.set('reasonCode', query.reasonCode.trim())
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesFutureHoldItem>>(`/holds/future?${params}`, { method: 'GET' })
}

export function getFutureHoldApi(id: number | string) {
  return request<MesFutureHoldItem>(`/holds/future/${id}`, { method: 'GET' })
}

export function listLotFutureHoldsApi(lotId: number | string) {
  return request<MesFutureHoldItem[]>(`/lots/${lotId}/future-holds`, { method: 'GET' })
}

export function createFutureHoldApi(body: {
  lotId: number | string
  targetSortNo: number
  timing?: FutureHoldTiming
  reasonCode: string
  remark?: string
}) {
  return request<MesFutureHoldItem>('/holds/future', {
    method: 'POST',
    body: {
      lotId: body.lotId,
      targetSortNo: body.targetSortNo,
      timing: body.timing || undefined,
      reasonCode: body.reasonCode,
      remark: body.remark || undefined,
    },
  })
}

export function cancelFutureHoldApi(id: number | string, remark?: string) {
  return request<MesFutureHoldItem>(`/holds/future/${id}/cancel`, {
    method: 'POST',
    body: { remark: remark || undefined },
  })
}
