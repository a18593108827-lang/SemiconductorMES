import { request } from '../lib/http'
import type { PageResult } from './system'

export type AlarmStatus = 'OPEN' | 'ACK' | 'CLEARED'
export type AlarmLevel = 'CRITICAL' | 'WARNING' | 'INFO'
export type AlarmEntityType = 'LOT' | 'EQP' | 'CHART' | 'NONE'
export type AlarmOnRaise = 'NONE' | 'HOLD_LOT'

export interface AlarmItem {
  id: number | string
  code: string
  level: AlarmLevel
  status: AlarmStatus
  message: string
  entityType: AlarmEntityType
  entityId: number | string | null
  dedupeKey: string | null
  payloadJson: string | null
  raiseCount: number
  firstRaiseAt: string | null
  lastRaiseAt: string | null
  ackBy: number | string | null
  ackAt: string | null
  ackRemark: string | null
  clearBy: number | string | null
  clearAt: string | null
  clearRemark: string | null
  /** 详情：码表策略 */
  onRaise?: AlarmOnRaise | string | null
  holdReasonCode?: string | null
  /** 详情：挂 Lot 时是否已有生效锁批 */
  lotHoldActive?: boolean | null
}

export interface AlarmCodeItem {
  code: string
  name: string
  level: AlarmLevel
  onRaise: AlarmOnRaise | string
  holdReasonCode: string | null
  enabled: number
  remark: string | null
}

export interface AlarmCodeUpdateBody {
  name: string
  level: AlarmLevel
  onRaise: AlarmOnRaise
  holdReasonCode?: string | null
  enabled: 0 | 1
  remark?: string | null
}

export interface AlarmActiveMessage {
  action: 'OPEN' | 'BUMP' | 'ACK' | 'CLEARED' | string
  id: number | string
  code: string
  level: AlarmLevel
  status: AlarmStatus
  message: string
  entityType: AlarmEntityType
  entityId: number | string | null
  raiseCount: number
}

export function listAlarmsApi(query: {
  status?: AlarmStatus | ''
  level?: AlarmLevel | ''
  code?: string
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.status) params.set('status', query.status)
  if (query.level) params.set('level', query.level)
  if (query.code?.trim()) params.set('code', query.code.trim())
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<AlarmItem>>(`/alarm?${params}`, { method: 'GET' })
}

export function listCriticalAlarmsApi() {
  return request<AlarmItem[]>('/alarm/critical', { method: 'GET' })
}

export function getAlarmApi(id: number | string) {
  return request<AlarmItem>(`/alarm/${id}`, { method: 'GET' })
}

export function ackAlarmApi(id: number | string, remark?: string) {
  return request<AlarmItem>(`/alarm/${id}/ack`, {
    method: 'POST',
    body: remark !== undefined ? { remark } : {},
  })
}

export function clearAlarmApi(id: number | string, remark?: string) {
  return request<AlarmItem>(`/alarm/${id}/clear`, {
    method: 'POST',
    body: remark !== undefined ? { remark } : {},
  })
}

export function listAlarmCodesApi() {
  return request<AlarmCodeItem[]>('/alarm/codes', { method: 'GET' })
}

export function updateAlarmCodeApi(code: string, body: AlarmCodeUpdateBody) {
  return request<AlarmCodeItem>(`/alarm/codes/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body,
  })
}
