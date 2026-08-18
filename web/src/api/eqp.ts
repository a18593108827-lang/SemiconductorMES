import { request } from '../lib/http'
import type { PageResult } from './system'

export type MesEqpStatus = 'idle' | 'running' | 'down' | 'pm' | 'eng' | 'offline'

export interface MesEqpItem {
  id: number | string
  eqpCode: string
  eqpName: string
  eqpType: string | null
  area: string | null
  status: MesEqpStatus
  enabled: number
  remark: string | null
  version: number
  createTime: string | null
  updateTime: string | null
}

export interface MesEqpOption {
  id: number | string
  eqpCode: string
  eqpName: string
  eqpType: string | null
  area: string | null
  status: MesEqpStatus
}

export function listEqpsApi(query: {
  keyword?: string
  status?: MesEqpStatus | ''
  eqpType?: string
  enabled?: 0 | 1 | ''
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status) params.set('status', query.status)
  if (query.eqpType?.trim()) params.set('eqpType', query.eqpType.trim())
  if (query.enabled === 0 || query.enabled === 1) params.set('enabled', String(query.enabled))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesEqpItem>>(`/equipments?${params}`, { method: 'GET' })
}

export function listEqpOptionsApi(eqpType?: string) {
  const params = new URLSearchParams()
  if (eqpType?.trim()) params.set('eqpType', eqpType.trim())
  const q = params.toString()
  return request<MesEqpOption[]>(`/equipments/options${q ? `?${q}` : ''}`, { method: 'GET' })
}

export function getEqpApi(id: number | string) {
  return request<MesEqpItem>(`/equipments/${id}`, { method: 'GET' })
}

export function createEqpApi(body: {
  eqpCode: string
  eqpName: string
  eqpType?: string
  area?: string
  remark?: string
}) {
  return request<MesEqpItem>('/equipments', { method: 'POST', body })
}

export function updateEqpApi(
  id: number | string,
  body: {
    eqpName: string
    eqpType?: string
    area?: string
    remark?: string
  },
) {
  return request<void>(`/equipments/${id}`, { method: 'PUT', body })
}

export function updateEqpEnabledApi(id: number | string, enabled: 0 | 1) {
  return request<void>(`/equipments/${id}/enabled`, {
    method: 'PUT',
    body: { enabled },
  })
}

export function updateEqpStatusApi(id: number | string, status: MesEqpStatus) {
  return request<void>(`/equipments/${id}/status`, {
    method: 'PUT',
    body: { status },
  })
}
