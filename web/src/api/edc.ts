import { request } from '../lib/http'
import type { PageResult } from './system'

export interface MesEdcParamItem {
  id: number | string
  paramCode: string
  paramName: string
  unit: string | null
  valueType: string
  enabled: number
  remark: string | null
  version: number
  createTime: string | null
  updateTime: string | null
}

export type EdcSpecStatus = 'draft' | 'active' | 'obsolete'

export interface MesEdcSpecItem {
  id: number | string
  paramId: number | string
  paramCode: string | null
  paramName: string | null
  productCode: string
  versionNo: number
  status: EdcSpecStatus
  usl: number | string | null
  lsl: number | string | null
  target: number | string | null
  remark: string | null
  publishedAt: string | null
  version: number
  createTime: string | null
  updateTime: string | null
}

export function listEdcParamsApi(query: {
  keyword?: string
  enabled?: 0 | 1 | ''
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.enabled === 0 || query.enabled === 1) params.set('enabled', String(query.enabled))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesEdcParamItem>>(`/edc/params?${params}`, { method: 'GET' })
}

export function getEdcParamApi(id: number | string) {
  return request<MesEdcParamItem>(`/edc/params/${id}`, { method: 'GET' })
}

export function createEdcParamApi(body: {
  paramCode: string
  paramName: string
  unit?: string
  remark?: string
}) {
  return request<MesEdcParamItem>('/edc/params', { method: 'POST', body })
}

export function updateEdcParamApi(
  id: number | string,
  body: {
    paramName: string
    unit?: string
    remark?: string
  },
) {
  return request<void>(`/edc/params/${id}`, { method: 'PUT', body })
}

export function updateEdcParamEnabledApi(id: number | string, enabled: 0 | 1) {
  return request<void>(`/edc/params/${id}/enabled`, { method: 'PUT', body: { enabled } })
}

export function listEdcSpecsApi(query: {
  paramId?: number | string
  productCode?: string
  status?: EdcSpecStatus | ''
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.paramId != null && query.paramId !== '') params.set('paramId', String(query.paramId))
  if (query.productCode != null) params.set('productCode', query.productCode)
  if (query.status) params.set('status', query.status)
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesEdcSpecItem>>(`/edc/specs?${params}`, { method: 'GET' })
}

export function getEdcSpecApi(id: number | string) {
  return request<MesEdcSpecItem>(`/edc/specs/${id}`, { method: 'GET' })
}

export function createEdcSpecApi(body: {
  paramId: number | string
  productCode?: string
  usl?: string
  lsl?: string
  target?: string
  remark?: string
}) {
  return request<MesEdcSpecItem>('/edc/specs', { method: 'POST', body })
}

export function updateEdcSpecApi(
  id: number | string,
  body: {
    usl?: string
    lsl?: string
    target?: string
    remark?: string
  },
) {
  return request<void>(`/edc/specs/${id}`, { method: 'PUT', body })
}

export function publishEdcSpecApi(id: number | string) {
  return request<void>(`/edc/specs/${id}/publish`, { method: 'POST' })
}

export interface MesEdcPlanItem {
  id?: number | string
  planId?: number | string
  paramId: number | string
  paramCode?: string | null
  paramName?: string | null
  unit?: string | null
  specId?: number | string | null
  specVersionNo?: number | null
  specStatus?: EdcSpecStatus | null
  sortNo?: number
  mandatory?: number
}

export interface MesEdcPlan {
  id: number | string
  stepId: number | string
  stepCode: string | null
  stepName: string | null
  required: number
  enabled: number
  remark: string | null
  version: number
  itemCount: number
  items: MesEdcPlanItem[]
  createTime: string | null
  updateTime: string | null
}

export function listEdcPlansApi(query: {
  stepId?: number | string
  required?: 0 | 1 | ''
  enabled?: 0 | 1 | ''
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.stepId != null && query.stepId !== '') params.set('stepId', String(query.stepId))
  if (query.required === 0 || query.required === 1) params.set('required', String(query.required))
  if (query.enabled === 0 || query.enabled === 1) params.set('enabled', String(query.enabled))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesEdcPlan>>(`/edc/plans?${params}`, { method: 'GET' })
}

export function getEdcPlanApi(id: number | string) {
  return request<MesEdcPlan>(`/edc/plans/${id}`, { method: 'GET' })
}

export function createEdcPlanApi(body: {
  stepId: number | string
  required?: 0 | 1
  remark?: string
}) {
  return request<MesEdcPlan>('/edc/plans', { method: 'POST', body })
}

export function updateEdcPlanApi(
  id: number | string,
  body: {
    required: 0 | 1
    remark?: string
  },
) {
  return request<void>(`/edc/plans/${id}`, { method: 'PUT', body })
}

export function updateEdcPlanEnabledApi(id: number | string, enabled: 0 | 1) {
  return request<void>(`/edc/plans/${id}/enabled`, { method: 'PUT', body: { enabled } })
}

export function replaceEdcPlanItemsApi(
  id: number | string,
  items: { paramId: number | string; specId?: number | string; sortNo?: number; mandatory?: 0 | 1 }[],
) {
  return request<void>(`/edc/plans/${id}/items`, { method: 'PUT', body: { items } })
}

export interface MesEdcCollectionItem {
  id?: number | string
  collectionId?: number | string
  paramId: number | string
  paramCode?: string | null
  paramName?: string | null
  unit?: string | null
  specId?: number | string | null
  specVersionNo?: number | null
  uslSnap?: number | string | null
  lslSnap?: number | string | null
  valueNum?: number | string | null
  itemResult?: 'PASS' | 'OOS' | string | null
}

export interface MesEdcCollection {
  id: number | string
  lotId: number | string
  lotNo: string | null
  routeVersionId: number | string | null
  sortNo: number | null
  stepId: number | string
  stepCode: string | null
  stepName: string | null
  trackInTxId: number | string
  planId: number | string
  result: 'PASS' | 'FAIL' | string
  source: string | null
  eqpId?: number | string | null
  remark: string | null
  collectedBy?: number | string | null
  collectedAt: string | null
  itemCount: number
  items: MesEdcCollectionItem[]
  createTime: string | null
}

export function getLatestEdcCollectionApi(lotId: number | string, trackInTxId: number | string) {
  const params = new URLSearchParams()
  params.set('lotId', String(lotId))
  params.set('trackInTxId', String(trackInTxId))
  return request<MesEdcCollection | null>(`/edc/collections/latest?${params}`, { method: 'GET' })
}

export function submitEdcCollectionApi(body: {
  lotId: number | string
  trackInTxId: number | string
  eqpId?: number | string
  remark?: string
  items: { paramId: number | string; value: string }[]
}) {
  return request<MesEdcCollection>('/edc/collections', { method: 'POST', body })
}
