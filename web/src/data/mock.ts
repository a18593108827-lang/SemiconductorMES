export type LotStatus = 'Queued' | 'Running' | 'Hold' | 'Alarm' | 'Scrap'
export type EqpStatus = 'Idle' | 'Running' | 'Down' | 'PM' | 'Offline'

export interface Lot {
  id: string
  status: LotStatus
  step: string
  eqp: string
  qty: number
  product: string
  updatedAt: string
}

export interface Equipment {
  id: string
  name: string
  status: EqpStatus
  currentLot: string | null
  area: string
}

export interface HoldRecord {
  id: string
  lotId: string
  reason: string
  by: string
  at: string
  active: boolean
}

export interface AlarmRecord {
  id: string
  level: 'critical' | 'warning' | 'info'
  source: string
  message: string
  at: string
  ack: boolean
}

export interface HistoryEvent {
  id: string
  lotId: string
  type: string
  detail: string
  at: string
  by: string
}

export const lots: Lot[] = [
  { id: 'LOT-20260716-001', status: 'Running', step: 'ETCH-02', eqp: 'EQP-ETCH-A1', qty: 25, product: 'WAFER-N7', updatedAt: '2026-07-16 17:12:03' },
  { id: 'LOT-20260716-002', status: 'Hold', step: 'CMP-01', eqp: 'EQP-CMP-B2', qty: 25, product: 'WAFER-N7', updatedAt: '2026-07-16 16:58:11' },
  { id: 'LOT-20260716-003', status: 'Queued', step: 'PHOTO-01', eqp: '—', qty: 25, product: 'WAFER-N5', updatedAt: '2026-07-16 16:40:22' },
  { id: 'LOT-20260715-088', status: 'Alarm', step: 'DIFF-03', eqp: 'EQP-DIFF-C1', qty: 24, product: 'WAFER-N7', updatedAt: '2026-07-16 15:21:44' },
  { id: 'LOT-20260715-074', status: 'Running', step: 'METRO-01', eqp: 'EQP-MET-D3', qty: 25, product: 'WAFER-N5', updatedAt: '2026-07-16 17:05:19' },
  { id: 'LOT-20260714-051', status: 'Scrap', step: 'ETCH-01', eqp: 'EQP-ETCH-A2', qty: 3, product: 'WAFER-N7', updatedAt: '2026-07-15 09:18:00' },
]

export const equipment: Equipment[] = [
  { id: 'EQP-ETCH-A1', name: '刻蚀机 A1', status: 'Running', currentLot: 'LOT-20260716-001', area: '刻蚀' },
  { id: 'EQP-ETCH-A2', name: '刻蚀机 A2', status: 'Idle', currentLot: null, area: '刻蚀' },
  { id: 'EQP-CMP-B2', name: '抛光机 B2', status: 'Down', currentLot: 'LOT-20260716-002', area: 'CMP' },
  { id: 'EQP-DIFF-C1', name: '扩散炉 C1', status: 'Running', currentLot: 'LOT-20260715-088', area: '扩散' },
  { id: 'EQP-MET-D3', name: '量测机 D3', status: 'Running', currentLot: 'LOT-20260715-074', area: '量测' },
  { id: 'EQP-PHOTO-E1', name: '光刻机 E1', status: 'PM', currentLot: null, area: '光刻' },
  { id: 'EQP-PHOTO-E2', name: '光刻机 E2', status: 'Offline', currentLot: null, area: '光刻' },
]

export const holds: HoldRecord[] = [
  { id: 'H-1001', lotId: 'LOT-20260716-002', reason: 'Q-SPC超限', by: 'eng.li', at: '2026-07-16 16:58:11', active: true },
  { id: 'H-0998', lotId: 'LOT-20260714-012', reason: '设备异常待确认', by: 'op.wang', at: '2026-07-15 11:02:33', active: false },
]

export const alarms: AlarmRecord[] = [
  { id: 'A-5001', level: 'critical', source: 'EQP-DIFF-C1', message: '腔体压力超限', at: '2026-07-16 15:21:44', ack: false },
  { id: 'A-5002', level: 'warning', source: 'EQP-CMP-B2', message: '抛光垫寿命低于阈值', at: '2026-07-16 16:10:02', ack: false },
  { id: 'A-4990', level: 'info', source: '派工', message: '光刻排队积压超过 30 分钟', at: '2026-07-16 14:44:18', ack: true },
]

export const history: HistoryEvent[] = [
  { id: 'E-1', lotId: 'LOT-20260716-001', type: '进站', detail: 'ETCH-02 @ EQP-ETCH-A1', at: '2026-07-16 17:12:03', by: 'op.chen' },
  { id: 'E-2', lotId: 'LOT-20260716-002', type: '锁批', detail: 'Q-SPC超限', at: '2026-07-16 16:58:11', by: 'eng.li' },
  { id: 'E-3', lotId: 'LOT-20260716-001', type: '出站', detail: 'ETCH-01', at: '2026-07-16 16:50:00', by: 'op.chen' },
  { id: 'E-4', lotId: 'LOT-20260715-088', type: '报警', detail: '腔体压力超限', at: '2026-07-16 15:21:44', by: 'system' },
]

export const outputTrend = [
  { t: '08:00', qty: 42 },
  { t: '10:00', qty: 58 },
  { t: '12:00', qty: 51 },
  { t: '14:00', qty: 67 },
  { t: '16:00', qty: 73 },
  { t: '18:00', qty: 61 },
]

export const kpi = {
  wip: 186,
  hold: 12,
  alarm: 2,
  oee: 87.4,
}
