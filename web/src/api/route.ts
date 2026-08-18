import { request } from '../lib/http'
import type { PageResult } from './system'

export interface MesStepItem {
  id: number | string
  stepCode: string
  stepName: string
  stepType: 1 | 2 | 3
  eqpType: string | null
  allowSkip?: number | null
  maxQueueMin?: number | null
  minProcessMin?: number | null
  maxProcessMin?: number | null
  status: 0 | 1
  remark: string | null
  createTime: string | null
  updateTime: string | null
}

export interface MesRouteItem {
  id: number | string
  routeCode: string
  routeName: string
  productCode: string | null
  status: 0 | 1
  remark: string | null
  activeVersionId: number | string | null
  activeVersionNo: number | null
  createTime: string | null
  updateTime: string | null
}

export interface MesRouteVersionItem {
  id: number | string
  routeId: number | string
  versionNo: number
  status: 'draft' | 'active' | 'archived'
  publishedAt: string | null
  publishedBy: number | string | null
  remark: string | null
  createTime: string | null
  updateTime: string | null
}

export interface MesRouteStepItem {
  id: number | string
  stepId: number | string
  stepCode: string
  stepName: string
  stepType: number
  eqpType?: string | null
  allowSkip?: number | null
  maxQueueMin?: number | null
  minProcessMin?: number | null
  maxProcessMin?: number | null
  sortNo: number
  nextSortNo: number | null
}

export interface MesRouteEdgeItem {
  id?: number | string
  fromSortNo: number
  toSortNo: number
  edgeType: string
  maxReworkCount: number | null
  reasonCodes: string | null
  conditionCode?: string | null
  maxQueueMin?: number | null
  minQueueMin?: number | null
  onViolate?: string | null
  sortNo?: number | null
}

export interface MesRouteVersionDetail {
  id: number | string
  routeId: number | string
  routeCode: string
  routeName: string
  versionNo: number
  status: 'draft' | 'active' | 'archived'
  publishedAt: string | null
  publishedBy: number | string | null
  remark: string | null
  steps: MesRouteStepItem[]
  edges?: MesRouteEdgeItem[]
}

export function listStepsApi(query: {
  keyword?: string
  status?: 0 | 1
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status !== undefined) params.set('status', String(query.status))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 100))
  return request<PageResult<MesStepItem>>(`/routes/steps?${params}`, { method: 'GET' })
}

export function createStepApi(body: {
  stepCode: string
  stepName: string
  stepType: number
  eqpType?: string
  allowSkip?: number | null
  maxQueueMin?: number | null
  minProcessMin?: number | null
  maxProcessMin?: number | null
  remark?: string
}) {
  return request<null>('/routes/steps', { method: 'POST', body })
}

export function updateStepApi(
  id: number | string,
  body: {
    stepName: string
    stepType: number
    status: number
    eqpType?: string
    allowSkip?: number | null
    maxQueueMin?: number | null
    minProcessMin?: number | null
    maxProcessMin?: number | null
    remark?: string
  },
) {
  return request<null>(`/routes/steps/${id}`, { method: 'PUT', body })
}

export function listRoutesApi(query: {
  keyword?: string
  status?: 0 | 1
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status !== undefined) params.set('status', String(query.status))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesRouteItem>>(`/routes?${params}`, { method: 'GET' })
}

export function createRouteApi(body: {
  routeCode: string
  routeName: string
  productCode?: string
  remark?: string
}) {
  return request<{ id: number | string }>('/routes', { method: 'POST', body })
}

export function getRouteApi(id: number | string) {
  return request<MesRouteItem>(`/routes/${id}`, { method: 'GET' })
}

export function listRouteVersionsApi(routeId: number | string) {
  return request<MesRouteVersionItem[]>(`/routes/${routeId}/versions`, { method: 'GET' })
}

export function getRouteVersionApi(versionId: number | string) {
  return request<MesRouteVersionDetail>(`/routes/versions/${versionId}`, { method: 'GET' })
}

export function saveRouteDraftStepsApi(
  versionId: number | string,
  steps: Array<{ stepId: number | string; sortNo: number; nextSortNo: number | null }>,
  edges?: Array<{
    fromSortNo: number
    toSortNo: number
    edgeType: string
    maxReworkCount?: number | null
    reasonCodes?: string | null
    conditionCode?: string | null
    maxQueueMin?: number | null
    minQueueMin?: number | null
    onViolate?: string | null
    sortNo?: number
  }>,
) {
  return request<null>(`/routes/versions/${versionId}/steps`, {
    method: 'PUT',
    body: { steps, edges },
  })
}

export function publishRouteVersionApi(versionId: number | string) {
  return request<null>(`/routes/versions/${versionId}/publish`, { method: 'POST' })
}

export function upgradeRouteVersionApi(routeId: number | string, fromVersionId: number | string) {
  return request<{ versionId: number | string }>(`/routes/${routeId}/versions`, {
    method: 'POST',
    body: { fromVersionId },
  })
}
