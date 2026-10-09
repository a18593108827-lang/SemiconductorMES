import { request } from '../lib/http'
import type { PageResult } from './system'

export type TestStage = 'CP' | 'FT' | 'OTHER'
export type TestSource = 'FILE' | 'API' | 'MANUAL'
export type BinType = 'HARD' | 'SOFT'
export type BinScope = 'GLOBAL' | 'PRODUCT' | 'PROGRAM' | 'PROGRAM_VERSION'

export interface TestBinLine {
  binType: BinType | string
  binCode: string
  binName?: string | null
  binQty: number
  isShippable?: number | null
  ratio?: number | string | null
}

export interface TestRecordItem {
  id: number | string
  recordNo: string
  lotId: number | string
  lotNo: string
  testStage: TestStage | string
  programName: string
  programVersion: string
  eqpId: number | string | null
  eqpCode: string | null
  testTime: string
  totalQty: number
  sourceType: TestSource | string
  sourceRef: string | null
  remark: string | null
  createTime: string | null
  bins?: TestBinLine[] | null
}

export interface TestBinDefItem {
  id: number | string
  binScope: BinScope | string
  productCode: string | null
  programName: string | null
  programVersion: string | null
  binType: BinType | string
  binCode: string
  binName: string
  failureMode: string | null
  isShippable: number
  status: number
  version: number
  remark: string | null
}

export function listTestRecordsApi(query: {
  lotId?: number | string
  lotNo?: string
  stage?: TestStage | ''
  programName?: string
  from?: string
  to?: string
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.lotId != null && query.lotId !== '') params.set('lotId', String(query.lotId))
  if (query.lotNo?.trim()) params.set('lotNo', query.lotNo.trim())
  if (query.stage) params.set('stage', query.stage)
  if (query.programName?.trim()) params.set('programName', query.programName.trim())
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<TestRecordItem>>(`/test/records?${params}`, { method: 'GET' })
}

export function getTestRecordApi(id: number | string) {
  return request<TestRecordItem>(`/test/records/${id}`, { method: 'GET' })
}

export function listTestRecordsByLotApi(lotId: number | string) {
  return request<TestRecordItem[]>(`/test/summary/by-lot/${lotId}`, { method: 'GET' })
}

export function createTestRecordApi(body: {
  lotId: number | string
  testStage: TestStage
  programName: string
  programVersion: string
  eqpId?: number | string
  eqpCode?: string
  testTime: string
  totalQty: number
  sourceType: TestSource
  sourceRef?: string
  remark?: string
  bins: Array<{ binType: BinType; binCode: string; binQty: number }>
}) {
  return request<TestRecordItem>('/test/records', { method: 'POST', body })
}

export function voidTestRecordApi(id: number | string, reason: string) {
  return request<void>(`/test/records/${id}/void`, { method: 'PUT', body: { reason } })
}

export function listTestBinsApi(query: {
  productCode?: string
  programName?: string
  programVersion?: string
  binType?: BinType | ''
} = {}) {
  const params = new URLSearchParams()
  if (query.productCode != null) params.set('productCode', query.productCode)
  if (query.programName != null) params.set('programName', query.programName)
  if (query.programVersion != null) params.set('programVersion', query.programVersion)
  if (query.binType) params.set('binType', query.binType)
  const q = params.toString()
  return request<TestBinDefItem[]>(`/test/bins${q ? `?${q}` : ''}`, { method: 'GET' })
}

export function createTestBinApi(body: {
  binScope: BinScope
  productCode?: string
  programName?: string
  programVersion?: string
  binType: BinType
  binCode: string
  binName: string
  failureMode?: string
  isShippable: number
  status: number
  remark?: string
}) {
  return request<TestBinDefItem>('/test/bins', { method: 'POST', body })
}

export function updateTestBinApi(
  id: number | string,
  body: {
    binScope: BinScope
    productCode?: string
    programName?: string
    programVersion?: string
    binType: BinType
    binCode: string
    binName: string
    failureMode?: string
    isShippable: number
    status: number
    version: number
    remark?: string
  },
) {
  return request<void>(`/test/bins/${id}`, { method: 'PUT', body })
}
