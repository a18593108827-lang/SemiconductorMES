import { request } from '../lib/http'

export interface SpcChartItem {
  id: number | string
  paramId: number | string
  stepId: number | string
  eqpId: number | string | null
  chartType: string
  limitMode: string
  learningN: number
  ucl: number | string | null
  cl: number | string | null
  lcl: number | string | null
  runN: number
  enabled: number
  n: number | null
  version: number
}

export interface SpcSeriesPoint {
  itemId: number | string
  collectionId: number | string
  lotId: number | string | null
  lotNo: string | null
  time: string | null
  value: number | string | null
  itemResult: string | null
  evalOoc: boolean | null
}

export interface SpcEvalItem {
  id: number | string
  chartId: number | string
  collectionItemId: number | string
  ooc: number
  ruleCode: string
  uclSnap: number | string | null
  clSnap: number | string | null
  lclSnap: number | string | null
  createTime: string | null
}

export interface SpcSeries {
  chart: SpcChartItem
  specUsl: number | string | null
  specLsl: number | string | null
  cpk: number | string | null
  cp: number | string | null
  points: SpcSeriesPoint[]
  lastEval: SpcEvalItem | null
}

export interface SpcChartSaveBody {
  id?: number | string
  paramId: number | string
  stepId: number | string
  eqpId?: number | string | null
  limitMode?: string
  learningN?: number
  ucl?: number | string | null
  cl?: number | string | null
  lcl?: number | string | null
  runN?: number
  enabled?: number
}

export function listSpcChartsApi(query: {
  stepId?: number | string
  paramId?: number | string
} = {}) {
  const params = new URLSearchParams()
  if (query.stepId != null && query.stepId !== '') params.set('stepId', String(query.stepId))
  if (query.paramId != null && query.paramId !== '') params.set('paramId', String(query.paramId))
  const q = params.toString()
  return request<SpcChartItem[]>(`/spc/charts${q ? `?${q}` : ''}`, { method: 'GET' })
}

export function getSpcChartApi(id: number | string) {
  return request<SpcChartItem>(`/spc/charts/${id}`, { method: 'GET' })
}

export function getSpcSeriesApi(
  id: number | string,
  query: { from?: string; to?: string; limit?: number } = {},
) {
  const params = new URLSearchParams()
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  if (query.limit != null) params.set('limit', String(query.limit))
  const q = params.toString()
  return request<SpcSeries>(`/spc/charts/${id}/series${q ? `?${q}` : ''}`, { method: 'GET' })
}

export function saveSpcChartApi(body: SpcChartSaveBody) {
  return request<SpcChartItem>('/spc/charts', { method: 'POST', body })
}

export function setSpcLimitsApi(
  id: number | string,
  body: { ucl?: number | string | null; cl?: number | string | null; lcl?: number | string | null },
) {
  return request<void>(`/spc/charts/${id}/limits`, { method: 'PUT', body })
}

export function setSpcEnabledApi(id: number | string, enabled: 0 | 1) {
  return request<void>(`/spc/charts/${id}/enabled`, { method: 'PUT', body: { enabled } })
}
