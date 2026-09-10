import { request } from '../lib/http'

export interface ReportDayPoint {
  day: string
  trackOutCount: number
}

export interface ReportStepPoint {
  stepId: number | string | null
  stepCode?: string | null
  stepName?: string | null
  trackOutCount: number
}

export interface ReportMove {
  generatedAt: string
  partial: boolean
  errors: string[]
  from: string
  to: string
  byDay: ReportDayPoint[]
  byStep: ReportStepPoint[]
  totalTrackOut: number
}

export interface ReportHoldReason {
  reasonCode: string | null
  reasonName: string | null
  holdCount: number
  activeCount: number
  avgDurationMinutes: number | null
}

export interface ReportHold {
  generatedAt: string
  partial: boolean
  errors: string[]
  from: string
  to: string
  byReason: ReportHoldReason[]
  totalHold: number
}

export function fetchReportMoveApi(from: string, to: string) {
  const params = new URLSearchParams({ from, to })
  return request<ReportMove>(`/report/move?${params}`, { method: 'GET' })
}

export function fetchReportHoldApi(from: string, to: string) {
  const params = new URLSearchParams({ from, to })
  return request<ReportHold>(`/report/hold?${params}`, { method: 'GET' })
}
