import { downloadFile, request } from '../lib/http'

export type ComplaintDirection = 'up' | 'down' | 'both'

export interface ComplaintPackageSummaryVO {
  activeHoldCount: number
  openAlarmCount: number
  scrapLotCount: number
}

export interface ComplaintPackageMemberVO {
  lotId: number | string
  lotNo: string
  relation: string
  depth: number
  qty: number | null
  status: string | null
}

export interface ComplaintPackagePreviewVO {
  anchorLotId: number | string
  anchorLotNo: string
  members: ComplaintPackageMemberVO[]
  memberCount: number
  truncated: boolean
  summary: ComplaintPackageSummaryVO | null
}

export interface ComplaintPackageVO {
  packageId: number | string
  packageNo: string
  anchorLotId: number | string
  anchorLotNo: string
  direction: string
  depth: number
  memberCount: number
  truncated: boolean
  reasonCode: string | null
  remark: string | null
  status: string
  createBy: number | string | null
  createTime: string | null
}

export function getComplaintEnabledApi() {
  return request<boolean>('/complaint-packages/enabled', { method: 'GET' })
}

export function previewComplaintPackageApi(body: {
  anchorLotId: number | string
  direction?: ComplaintDirection
  depth?: number
}) {
  return request<ComplaintPackagePreviewVO>('/complaint-packages/preview', {
    method: 'POST',
    body,
  })
}

export function buildComplaintPackageApi(body: {
  anchorLotId: number | string
  direction?: ComplaintDirection
  depth?: number
  reasonCode?: string
  remark?: string
}) {
  return request<ComplaintPackageVO>('/complaint-packages', { method: 'POST', body })
}

/**
 * 下载追溯包附件。
 * format=json → 单文件 JSON；format=zip → `{packageNo}.zip`，内含 `{packageNo}.json` + `README.txt` 封面。
 * 文件名以响应头 `filename=` 为准（后端给全名），fallbackName 仅兜底。
 */
export function exportComplaintPackageApi(
  id: number | string,
  fileName?: string,
  format: 'json' | 'zip' = 'json',
) {
  return downloadFile(`/complaint-packages/${id}/export?format=${format}`, fileName)
}

export interface ComplaintContainLotVO {
  lotId: number | string
  lotNo: string
  code: string | null
  message: string | null
}

export interface ComplaintContainResultVO {
  succeeded: ComplaintContainLotVO[]
  skipped: ComplaintContainLotVO[]
  failed: ComplaintContainLotVO[]
  succeededCount: number
  skippedCount: number
  failedCount: number
  status: string
  containBy: number | string | null
  containTime: string | null
}

/** 批量遏制；超时 120s（同步 HTTP，成员多时偏长） */
export function containComplaintPackageApi(
  id: number | string,
  body: { lotIds?: string[]; reasonCode: string; remark?: string },
) {
  return request<ComplaintContainResultVO>(`/complaint-packages/${id}/contain`, {
    method: 'POST',
    body,
    signal: AbortSignal.timeout(120_000),
  })
}
