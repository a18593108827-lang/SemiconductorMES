import { useEffect, useRef, useState } from 'react'
import {
  buildComplaintPackageApi,
  containComplaintPackageApi,
  exportComplaintPackageApi,
  previewComplaintPackageApi,
  type ComplaintContainResultVO,
  type ComplaintDirection,
  type ComplaintPackageMemberVO,
  type ComplaintPackagePreviewVO,
} from '../../api/complaint'
import { listHoldReasonsApi, type MesHoldReason } from '../../api/hold'
import type { MesLotStatus } from '../../api/lot'
import { Button } from '../ui/Button'
import { useConfirm } from '../ui/ConfirmDialog'
import { Drawer } from '../ui/Drawer'
import { Field } from '../ui/Field'
import { mesLotStatusLabel } from '../ui/StatusPill'
import { useToast } from '../ui/Toast'
import { ApiError } from '../../lib/http'

const REL_LABEL: Record<string, string> = {
  ANCHOR: '锚点',
  ANCESTOR: '上游',
  DESCENDANT: '下游',
}

const DEFAULT_CONTAIN_REASON = 'CUSTOMER_COMPLAINT'

const PKG_STATUS_LABEL: Record<string, string> = {
  READY: '已生成',
  CONTAINING: '遏制中',
  CONTAINED: '已遏制',
  VOID: '已作废',
}

type Phase = 'form' | 'building' | 'built'

function memberStatusLabel(status: string | null) {
  if (!status) return '—'
  const m = mesLotStatusLabel[status as MesLotStatus]
  return m?.label ?? status
}

export function ComplaintPackageDrawer({
  open,
  onClose,
  anchorLotId,
  anchorLotNo,
  canBuild,
  canContain,
}: {
  open: boolean
  onClose: () => void
  anchorLotId: number | string | null | undefined
  anchorLotNo: string
  canBuild: boolean
  canContain: boolean
}) {
  const toast = useToast()
  const confirm = useConfirm()
  const [direction, setDirection] = useState<ComplaintDirection>('both')
  const [depth, setDepth] = useState(5)
  const [reasonCode, setReasonCode] = useState('')
  const [remark, setRemark] = useState('')
  const [preview, setPreview] = useState<ComplaintPackagePreviewVO | null>(null)
  const [previewKey, setPreviewKey] = useState<string | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [phase, setPhase] = useState<Phase>('form')
  const [packageId, setPackageId] = useState<number | string | null>(null)
  const [packageNo, setPackageNo] = useState('')
  const [pkgStatus, setPkgStatus] = useState('')
  const [downloading, setDownloading] = useState<'json' | 'zip' | null>(null)
  const downloadingRef = useRef<'json' | 'zip' | null>(null)
  const downloadSeq = useRef(0)
  const [reasons, setReasons] = useState<MesHoldReason[]>([])
  const [containReason, setContainReason] = useState(DEFAULT_CONTAIN_REASON)
  const [containRemark, setContainRemark] = useState('')
  const [containing, setContaining] = useState(false)
  const [containResult, setContainResult] = useState<ComplaintContainResultVO | null>(null)

  useEffect(() => {
    if (!open) return
    setDirection('both')
    setDepth(5)
    setReasonCode('')
    setRemark('')
    setPreview(null)
    setPreviewKey(null)
    setPreviewing(false)
    setPhase('form')
    setPackageId(null)
    setPackageNo('')
    setPkgStatus('')
    downloadSeq.current += 1
    downloadingRef.current = null
    setDownloading(null)
    setContainReason(DEFAULT_CONTAIN_REASON)
    setContainRemark('')
    setContaining(false)
    setContainResult(null)
  }, [open, anchorLotId])

  useEffect(() => {
    if (!open || phase !== 'built' || !canContain) return
    let cancelled = false
    void listHoldReasonsApi(false)
      .then((rows) => {
        if (cancelled) return
        const list = rows ?? []
        setReasons(list)
        const codes = new Set(list.map((r) => r.reasonCode))
        setContainReason((cur) => (codes.has(cur) ? cur : codes.has(DEFAULT_CONTAIN_REASON) ? DEFAULT_CONTAIN_REASON : (list[0]?.reasonCode ?? '')))
      })
      .catch(() => {
        if (!cancelled) setReasons([])
      })
    return () => {
      cancelled = true
    }
  }, [open, phase, canContain])

  const currentKey = `${direction}|${depth}`
  const paramsMatch = previewKey === currentKey
  const canGenerate =
    canBuild && phase === 'form' && !!preview && paramsMatch && !previewing && anchorLotId != null

  const summary = preview?.summary
  const members = preview?.members ?? []
  const truncated = preview?.truncated === true
  const containCount = members.length
  const reasonName = reasons.find((r) => r.reasonCode === containReason)?.reasonName ?? containReason

  async function doPreview() {
    if (anchorLotId == null) return
    setPreviewing(true)
    try {
      const vo = await previewComplaintPackageApi({
        anchorLotId,
        direction,
        depth,
      })
      setPreview(vo)
      setPreviewKey(`${direction}|${depth}`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '预览失败')
    } finally {
      setPreviewing(false)
    }
  }

  async function doBuild() {
    if (anchorLotId == null || !canGenerate) return
    setPhase('building')
    try {
      const vo = await buildComplaintPackageApi({
        anchorLotId,
        direction,
        depth,
        reasonCode: reasonCode.trim() || undefined,
        remark: remark.trim() || undefined,
      })
      setPackageId(vo.packageId)
      setPackageNo(vo.packageNo)
      setPkgStatus(vo.status)
      setPhase('built')
    } catch (e) {
      setPhase('form')
      toast.error(e instanceof ApiError ? e.message : '生成失败')
    }
  }

  /** 下载证据：json 单文件 / zip 证据包（文件名以后端响应头为准） */
  async function doDownload(format: 'json' | 'zip') {
    if (packageId == null || downloadingRef.current != null) return
    const seq = downloadSeq.current
    downloadingRef.current = format
    setDownloading(format)
    try {
      await exportComplaintPackageApi(
        packageId,
        packageNo ? `${packageNo}.${format}` : undefined,
        format,
      )
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '下载失败')
    } finally {
      if (downloadSeq.current === seq) {
        downloadingRef.current = null
        setDownloading(null)
      }
    }
  }

  /** 二次确认后 contain；徽标用响应里重读的 status */
  async function doContain() {
    if (packageId == null || !canContain || containing || !containReason) return
    const ok = await confirm({
      title: '确认遏制',
      message: `将对 ${containCount} 个批次发起锁批，原因：${reasonName}`,
      confirmText: '遏制',
      danger: true,
    })
    if (!ok) return
    setContaining(true)
    try {
      const vo = await containComplaintPackageApi(packageId, {
        reasonCode: containReason,
        remark: containRemark.trim() || undefined,
      })
      setContainResult(vo)
      setPkgStatus(vo.status)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '遏制失败')
    } finally {
      setContaining(false)
    }
  }

  const footer =
    phase === 'built' ? (
      <>
        <Button variant="secondary" onClick={onClose}>
          关闭
        </Button>
        <Button
          variant="secondary"
          loading={downloading === 'json'}
          disabled={packageId == null || downloading != null}
          onClick={() => void doDownload('json')}
        >
          下载 JSON
        </Button>
        <Button loading={downloading === 'zip'} disabled={packageId == null || downloading != null} onClick={() => void doDownload('zip')}>
          下载 ZIP
        </Button>
      </>
    ) : (
      <>
        <Button variant="secondary" onClick={onClose}>
          关闭
        </Button>
        <Button variant="secondary" loading={previewing} disabled={phase === 'building'} onClick={() => void doPreview()}>
          预览影响面
        </Button>
        {canBuild ? (
          <Button loading={phase === 'building'} disabled={!canGenerate} onClick={() => void doBuild()}>
            生成追溯包
          </Button>
        ) : null}
      </>
    )

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={phase === 'built' ? '下载证据' : '生成追溯包'}
      size="lg"
      footer={footer}
    >
      <div className="space-y-4">
        <p className="font-mono text-sm text-ink">
          锚点 {anchorLotNo || '—'}
        </p>

        {phase === 'built' ? (
          <p className="text-sm text-ink">
            已生成 <span className="font-mono">{packageNo}</span>
            {pkgStatus ? ` · ${PKG_STATUS_LABEL[pkgStatus] ?? pkgStatus}` : ''}
            。证据以导出时刻为准。
          </p>
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5 text-sm">
                <span className="text-xs font-medium text-muted">方向</span>
                <select
                  className="h-9 rounded-md border border-border bg-bg px-3 text-sm text-ink"
                  value={direction}
                  disabled={phase === 'building'}
                  onChange={(e) => setDirection(e.target.value as ComplaintDirection)}
                >
                  <option value="both">双向</option>
                  <option value="up">上游</option>
                  <option value="down">下游</option>
                </select>
              </label>
              <Field
                label="深度"
                type="number"
                min={1}
                max={20}
                value={String(depth)}
                disabled={phase === 'building'}
                onChange={(e) => {
                  const n = Number(e.target.value)
                  if (!Number.isFinite(n)) return
                  setDepth(Math.min(20, Math.max(1, Math.trunc(n))))
                }}
              />
            </div>
            {canBuild ? (
              <div className="grid gap-3 sm:grid-cols-2">
                <Field
                  label="原因码"
                  name="reasonCode"
                  value={reasonCode}
                  maxLength={64}
                  disabled={phase === 'building'}
                  onChange={(e) => setReasonCode(e.target.value)}
                  placeholder="可选"
                />
                <Field
                  label="备注"
                  name="remark"
                  value={remark}
                  maxLength={512}
                  disabled={phase === 'building'}
                  onChange={(e) => setRemark(e.target.value)}
                  placeholder="可选"
                />
              </div>
            ) : null}
            {preview && !paramsMatch ? (
              <p className="rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-xs text-ink">
                方向或深度已改，请重新预览影响面后再生成。
              </p>
            ) : null}
          </>
        )}

        {truncated ? (
          <p className="rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-xs text-ink">
            影响面已截断。可缩小深度或更换锚点后重新预览。
          </p>
        ) : null}

        {summary ? (
          <div className="flex flex-wrap gap-3 text-xs text-muted">
            <span>成员 {preview?.memberCount ?? 0}</span>
            <span>锁批 {summary.activeHoldCount}</span>
            <span>告警 {summary.openAlarmCount}</span>
            <span>报废 {summary.scrapLotCount}</span>
          </div>
        ) : null}

        {members.length > 0 && phase !== 'built' ? (
          <div className="overflow-auto rounded-md border border-border">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface text-xs text-muted">
                <tr>
                  <th className="px-3 py-2 font-medium">批号</th>
                  <th className="px-3 py-2 font-medium">关系</th>
                  <th className="px-3 py-2 font-medium">深度</th>
                  <th className="px-3 py-2 font-medium">状态</th>
                </tr>
              </thead>
              <tbody>
                {members.map((m: ComplaintPackageMemberVO) => (
                  <tr key={String(m.lotId)} className="border-t border-border">
                    <td className="px-3 py-1.5 font-mono text-[13px]">{m.lotNo}</td>
                    <td className="px-3 py-1.5">{REL_LABEL[m.relation] ?? m.relation}</td>
                    <td className="px-3 py-1.5 font-mono tabular-nums">{m.depth}</td>
                    <td className="px-3 py-1.5">{memberStatusLabel(m.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {phase === 'built' && canContain ? (
          <div className="space-y-3 rounded-md border border-border p-3">
            <p className="text-sm font-medium text-ink">遏制</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-1.5 text-sm">
                <span className="text-xs font-medium text-muted">原因码</span>
                <select
                  className="h-9 rounded-md border border-border bg-bg px-3 text-sm text-ink"
                  value={containReason}
                  disabled={containing}
                  onChange={(e) => setContainReason(e.target.value)}
                >
                  {reasons.map((r) => (
                    <option key={r.reasonCode} value={r.reasonCode}>
                      {r.reasonName} ({r.reasonCode})
                    </option>
                  ))}
                </select>
              </label>
              <Field
                label="备注"
                name="containRemark"
                value={containRemark}
                maxLength={512}
                disabled={containing}
                onChange={(e) => setContainRemark(e.target.value)}
                placeholder="可选"
              />
            </div>
            <Button
              loading={containing}
              disabled={packageId == null || !containReason}
              onClick={() => void doContain()}
            >
              遏制
            </Button>
            {containResult ? (
              <div className="space-y-2 text-sm">
                <p className="text-xs text-muted">
                  成功 {containResult.succeededCount} · 跳过 {containResult.skippedCount} · 失败 {containResult.failedCount}
                </p>
                <ContainGroup title="成功" rows={containResult.succeeded} />
                <ContainGroup title="跳过" rows={containResult.skipped} />
                <ContainGroup title="失败" rows={containResult.failed} showMessage />
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </Drawer>
  )
}

/** 遏制三段分组；失败组展开 message */
function ContainGroup({
  title,
  rows,
  showMessage,
}: {
  title: string
  rows: ComplaintContainResultVO['succeeded']
  showMessage?: boolean
}) {
  if (!rows.length) return null
  return (
    <details open={showMessage && rows.some((r) => r.message)}>
      <summary className="cursor-pointer text-xs text-muted">
        {title} {rows.length}
      </summary>
      <ul className="mt-1 space-y-0.5 font-mono text-[13px] text-ink">
        {rows.map((r) => (
          <li key={String(r.lotId)}>
            {r.lotNo}
            {showMessage && r.message ? ` · ${r.message}` : ''}
          </li>
        ))}
      </ul>
    </details>
  )
}
