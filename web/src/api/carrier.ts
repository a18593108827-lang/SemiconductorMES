import { request } from '../lib/http'
import type { PageResult } from './system'

export type MesCarrierStatus = 'AVAILABLE' | 'IN_USE' | 'QUARANTINE' | 'SCRAPPED'

export interface MesCarrierItem {
  id: number | string
  carrierCode: string
  carrierType: string
  capacity: number
  status: MesCarrierStatus
  cleanStatus: string | null
  locationType: string | null
  locationRef: string | null
  remark: string | null
  version: number
  createTime: string | null
  updateTime: string | null
  boundLotId?: number | string | null
  boundLotNo?: string | null
}

export interface MesCarrierBinding {
  lotId: number | string
  lotNo: string
  carrierId: number | string
  carrierCode: string
  bindTime: string | null
  bindBy: number | string | null
}

export function listCarriersApi(query: {
  keyword?: string
  status?: MesCarrierStatus | ''
  carrierType?: string
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status) params.set('status', query.status)
  if (query.carrierType?.trim()) params.set('carrierType', query.carrierType.trim())
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesCarrierItem>>(`/carrier?${params}`, { method: 'GET' })
}

export function getCarrierApi(id: number | string) {
  return request<MesCarrierItem>(`/carrier/${id}`, { method: 'GET' })
}

export function createCarrierApi(body: {
  carrierCode: string
  carrierType?: string
  capacity?: number
  cleanStatus?: string
  locationType?: string
  locationRef?: string
  remark?: string
}) {
  return request<MesCarrierItem>('/carrier', { method: 'POST', body })
}

export function updateCarrierApi(
  id: number | string,
  body: {
    capacity?: number
    cleanStatus?: string
    locationType?: string
    locationRef?: string
    remark?: string
  },
) {
  return request<MesCarrierItem>(`/carrier/${id}`, { method: 'PUT', body })
}

export function changeCarrierStatusApi(id: number | string, status: MesCarrierStatus, remark?: string) {
  return request<MesCarrierItem>(`/carrier/${id}/status`, {
    method: 'PUT',
    body: { status, remark: remark || undefined },
  })
}

export function bindCarrierApi(lotId: number | string, carrierRef: string) {
  return request<MesCarrierBinding>('/carrier/bind', {
    method: 'POST',
    body: { lotId, carrierRef },
  })
}

export function unbindCarrierApi(lotId: number | string) {
  return request<void>('/carrier/unbind', { method: 'POST', body: { lotId } })
}

export function unbindCarrierByIdApi(carrierId: number | string) {
  return request<void>(`/carrier/${carrierId}/unbind`, { method: 'POST' })
}

export function getLotCarrierApi(lotId: number | string) {
  return request<MesCarrierBinding | null>(`/lots/${lotId}/carrier`, { method: 'GET' })
}

export function bindLotCarrierApi(lotId: number | string, carrierRef: string) {
  return request<MesCarrierBinding>(`/lots/${lotId}/carrier`, {
    method: 'POST',
    body: { carrierRef },
  })
}

export function unbindLotCarrierApi(lotId: number | string) {
  return request<void>(`/lots/${lotId}/carrier`, { method: 'DELETE' })
}
