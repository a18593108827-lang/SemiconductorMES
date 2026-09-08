import { request } from '../lib/http'
import type { MesEqpStatus } from './eqp'

export interface DashboardKpi {
  wipCount: number
  holdActiveCount: number
  alarmOpenCount: number
  eqpDownCount: number
  eqpTotal: number
  eqpRunningCount: number
}

export interface DashboardEqp {
  id: number | string
  eqpCode: string
  name: string
  status: MesEqpStatus | string
  currentLotNo: string | null
}

export interface DashboardAlarm {
  id: number | string
  level: 'CRITICAL' | 'WARNING' | 'INFO' | string
  source: string
  message: string
  raisedAt: string | null
  status: 'OPEN' | 'ACK' | string
}

export interface DashboardTrendPoint {
  day: string
  trackOutCount: number
}

export interface DashboardOverview {
  generatedAt: string
  partial: boolean
  errors: string[]
  kpi: DashboardKpi
  equipment: DashboardEqp[]
  alarms: DashboardAlarm[]
  outputTrend: DashboardTrendPoint[]
}

export function fetchDashboardOverviewApi(query: {
  trendDays?: number
  alarmLimit?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.trendDays != null) params.set('trendDays', String(query.trendDays))
  if (query.alarmLimit != null) params.set('alarmLimit', String(query.alarmLimit))
  const q = params.toString()
  return request<DashboardOverview>(`/dashboard/overview${q ? `?${q}` : ''}`, { method: 'GET' })
}
