import { request } from '../lib/http'
import type { MesFutureHoldItem } from './hold'
import type { MesLotStatus, MesLotStepItem } from './lot'

export interface TrackReworkOption {
  toSortNo: number
  toStepCode: string | null
  toStepName: string | null
  maxReworkCount: number
  remainCount: number
  reasonCodes: string[]
}

export interface TrackBranchOption {
  conditionCode: string
  toSortNo: number
  toStepCode: string | null
  toStepName: string | null
}

export interface TrackSkipOption {
  toSortNo: number
  toStepCode: string | null
  toStepName: string | null
  skippedSortNos: number[]
  reasonCodes: string[]
}

export interface TrackOffFlowOption {
  toSortNo: number
  toStepCode: string | null
  toStepName: string | null
  maxOffFlowCount: number
  remainCount: number
  reasonCodes: string[]
}

export interface TrackQueueTime {
  fromSortNo: number
  toSortNo: number
  startedAt: string
  maxQueueMin: number
  elapsedMin: number
  remainMin: number
  onViolate: string
  violated: boolean
}

export interface TrackProcessTime {
  startedAt: string
  minProcessMin: number | null
  maxProcessMin: number | null
  elapsedMin: number
  remainToMinMin: number | null
  remainToMaxMin: number | null
  /** 未到下限时为 false；超上限仍为 true（可完工） */
  canTrackOutByTime: boolean
  exceededMax?: boolean
  willHoldOnOut?: boolean
}

export interface TrackEdc {
  required: boolean
  clear: boolean
  reasonCode: string
  message: string | null
}

export interface TrackContext {
  lotId: number | string
  lotNo: string
  status: MesLotStatus
  routeId: number | string | null
  routeVersionId: number | string | null
  routeVersionNo: number | null
  currentSortNo: number | null
  currentStepId: number | string | null
  currentEqpId: number | string | null
  currentStep: MesLotStepItem | null
  nextStep: MesLotStepItem | null
  canTrackIn: boolean
  canTrackOut: boolean
  canRework?: boolean
  canSkip?: boolean
  canEnterOffFlow?: boolean
  canResumeOffFlow?: boolean
  canSplit?: boolean
  /** 是否可作合批主批 */
  canMerge?: boolean
  /** 是否可报废 */
  canScrap?: boolean
  /** 是否可数量调整 */
  canBonus?: boolean
  /** 是否可加工中止（回本站 wait） */
  canAbort?: boolean
  /** 是否可独立移站（wait → 下一站 wait） */
  canMove?: boolean
  /** 默认下一站序号 */
  nextSortNo?: number | null
  /** 默认下一站名称 */
  nextStepName?: string | null
  offFlow?: boolean
  offFlowAnchorSortNo?: number | null
  reworkCount?: number
  reworkOptions?: TrackReworkOption[]
  branchOptions?: TrackBranchOption[]
  skipOptions?: TrackSkipOption[]
  offFlowOptions?: TrackOffFlowOption[]
  completed: boolean
  /** 未生效预约锁批 */
  pendingFutureHolds?: MesFutureHoldItem[]
  /** Queue Time 开窗；无则 null */
  queueTime?: TrackQueueTime | null
  /** Process Time 站内加工；无则 null */
  processTime?: TrackProcessTime | null
  /** 量测门禁；非 processing 为 null */
  edc?: TrackEdc | null
  /** 当前载具 ID */
  carrierId?: number | string | null
  /** 当前载具编码 */
  carrierCode?: string | null
  /** TrackIn 是否强制已绑 */
  carrierRequired?: boolean | null
  /** TrackIn 是否强制扫码比对 */
  carrierScanRequired?: boolean | null
}

export interface TrackTxnResult {
  lotId: number | string
  lotNo: string
  txType: string
  status: MesLotStatus
  currentSortNo: number | null
  currentStepId: number | string | null
  currentEqpId: number | string | null
  routeVersionId: number | string | null
  completed: boolean
  offFlow?: boolean
  reworkCount?: number | null
  maxReworkCount?: number | null
  offFlowCount?: number | null
  maxOffFlowCount?: number | null
}

export interface MesTxLogItem {
  id: number | string
  lotId: number | string
  lotNo: string
  txType: string
  fromStatus: string | null
  toStatus: string | null
  fromSortNo: number | null
  toSortNo: number | null
  stepId: number | string | null
  stepName: string | null
  eqpId: number | string | null
  routeVersionId: number | string | null
  remark: string | null
  extJson?: string | null
  operUserId: number | string | null
  operUserName: string | null
  createTime: string | null
}

export function getTrackContextApi(lotId: number | string) {
  return request<TrackContext>(`/track/lots/${lotId}/context`, { method: 'GET' })
}

export function trackInApi(
  lotId: number | string,
  eqpId?: number | string,
  carrierCode?: string,
) {
  return request<TrackTxnResult>('/track/track-in', {
    method: 'POST',
    body: {
      lotId,
      eqpId: eqpId != null && eqpId !== '' ? eqpId : undefined,
      carrierCode: carrierCode != null && carrierCode !== '' ? carrierCode : undefined,
    },
  })
}

export function trackOutApi(lotId: number | string, resultCode?: string) {
  return request<TrackTxnResult>('/track/track-out', {
    method: 'POST',
    body: {
      lotId,
      resultCode: resultCode?.trim() ? resultCode.trim() : undefined,
    },
  })
}

export function trackReworkApi(body: {
  lotId: number | string
  toSortNo: number
  reasonCode?: string
  remark?: string
}) {
  return request<TrackTxnResult>('/track/rework', {
    method: 'POST',
    body,
  })
}

export function trackSkipApi(body: {
  lotId: number | string
  toSortNo: number
  reasonCode?: string
  remark?: string
}) {
  return request<TrackTxnResult>('/track/skip', {
    method: 'POST',
    body,
  })
}

export function trackOffFlowApi(body: {
  lotId: number | string
  toSortNo: number
  reasonCode?: string
  remark?: string
}) {
  return request<TrackTxnResult>('/track/off-flow', {
    method: 'POST',
    body,
  })
}

export function trackOffFlowResumeApi(body: { lotId: number | string; remark?: string }) {
  return request<TrackTxnResult>('/track/off-flow/resume', {
    method: 'POST',
    body,
  })
}

export function trackReleaseApi(lotId: number | string) {
  return request('/track/release', {
    method: 'POST',
    body: { lotId },
  })
}

export interface TrackSplitChild {
  qty: number
  lotNo?: string
}

export interface TrackSplitResult {
  parent: { lotId: number | string; lotNo: string; qty: number }
  children: { lotId: number | string; lotNo: string; qty: number }[]
  txId: number | string
}

/** 分批：父保留余量，子继承快照与当前站 */
export function trackSplitApi(body: {
  parentLotId: number | string
  children: TrackSplitChild[]
  reasonCode?: string
  remark?: string
}) {
  return request<TrackSplitResult>('/track/split', {
    method: 'POST',
    body: {
      parentLotId: body.parentLotId,
      children: body.children,
      reasonCode: body.reasonCode?.trim() || undefined,
      remark: body.remark?.trim() || undefined,
    },
  })
}

export interface TrackMergeCandidate {
  lotId: number | string
  lotNo: string
  qty: number
  productCode?: string | null
  routeVersionId?: number | string | null
  currentSortNo?: number | null
  currentStepId?: number | string | null
  status?: MesLotStatus
}

export interface TrackMergeResult {
  main: { lotId: number | string; lotNo: string; qty: number }
  merged: { lotId: number | string; lotNo: string; qtyMerged: number }[]
  txId: number | string
}

/** 可合入主批的候选 */
export function getMergeCandidatesApi(mainLotId: number | string) {
  return request<TrackMergeCandidate[]>(
    `/track/merge/candidates?mainLotId=${encodeURIComponent(String(mainLotId))}`,
    { method: 'GET' },
  )
}

/** 合批：主 qty 累加，源 → merged */
export function trackMergeApi(body: {
  mainLotId: number | string
  sourceLotIds: (number | string)[]
  reasonCode?: string
  remark?: string
}) {
  return request<TrackMergeResult>('/track/merge', {
    method: 'POST',
    body: {
      mainLotId: body.mainLotId,
      sourceLotIds: body.sourceLotIds,
      reasonCode: body.reasonCode?.trim() || undefined,
      remark: body.remark?.trim() || undefined,
    },
  })
}

export interface TrackScrapReason {
  code: string
  label: string
}

export interface TrackScrapResult {
  lotId: number | string
  lotNo: string
  mode: 'partial' | 'full' | string
  qty: number
  scrapQty: number
  status: MesLotStatus
  txId: number | string
}

/** Scrap 原因码白名单 */
export function getScrapReasonCodesApi() {
  return request<TrackScrapReason[]>('/track/scrap/reason-codes', { method: 'GET' })
}

/** 报废：部分减 qty；全批 → scrapped */
export function trackScrapApi(body: {
  lotId: number | string
  scrapQty: number
  reasonCode: string
  remark?: string
}) {
  return request<TrackScrapResult>('/track/scrap', {
    method: 'POST',
    body: {
      lotId: body.lotId,
      scrapQty: body.scrapQty,
      reasonCode: body.reasonCode.trim(),
      remark: body.remark?.trim() || undefined,
    },
  })
}

export interface TrackBonusReason {
  code: string
  label: string
}

export interface TrackBonusResult {
  lotId: number | string
  lotNo: string
  delta: number
  qty: number
  scrapQty: number
  status: MesLotStatus
  txId: number | string
}

/** Bonus 原因码白名单（与 Scrap 隔离） */
export function getBonusReasonCodesApi() {
  return request<TrackBonusReason[]>('/track/bonus/reason-codes', { method: 'GET' })
}

/** 数量调整：±delta；不改 scrap_qty/status */
export function trackBonusApi(body: {
  lotId: number | string
  delta: number
  reasonCode: string
  remark?: string
}) {
  return request<TrackBonusResult>('/track/bonus', {
    method: 'POST',
    body: {
      lotId: body.lotId,
      delta: body.delta,
      reasonCode: body.reasonCode.trim(),
      remark: body.remark?.trim() || undefined,
    },
  })
}

export interface TrackAbortReason {
  code: string
  label: string
}

/** Abort 原因码白名单 */
export function getAbortReasonCodesApi() {
  return request<TrackAbortReason[]>('/track/abort/reason-codes', { method: 'GET' })
}

/** 加工中止：processing → 本站 wait；站别/数量不动 */
export function trackAbortApi(body: {
  lotId: number | string
  reasonCode: string
  remark?: string
}) {
  return request<TrackTxnResult>('/track/abort', {
    method: 'POST',
    body: {
      lotId: body.lotId,
      reasonCode: body.reasonCode.trim(),
      remark: body.remark?.trim() || undefined,
    },
  })
}

/** 独立移站：wait → 下一站 wait；不经加工 */
export function trackMoveApi(body: {
  lotId: number | string
  toSortNo?: number
  remark?: string
}) {
  return request<TrackTxnResult>('/track/move', {
    method: 'POST',
    body: {
      lotId: body.lotId,
      toSortNo: body.toSortNo,
      remark: body.remark?.trim() || undefined,
    },
  })
}

export function getLotHistoryApi(lotId: number | string) {
  return request<MesTxLogItem[]>(`/lots/${lotId}/history`, { method: 'GET' })
}
