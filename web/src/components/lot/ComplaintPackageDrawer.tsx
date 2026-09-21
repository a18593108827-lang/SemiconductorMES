import { useEffect, useState } from 'react'
import {
  buildComplaintPackageApi,
  exportComplaintPackageApi,
  previewComplaintPackageApi,
  type ComplaintDirection,
  type ComplaintPackageMemberVO,
  type ComplaintPackagePreviewVO,
} from '../../api/complaint'
import type { MesLotStatus } from '../../api/lot'
import { Button } from '../ui/Button'
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
}: {
  open: boolean
  onClose: () => void
  anchorLotId: number | string | null | undefined
  anchorLotNo: string
  canBuild: boolean
}) {
  const toast = useToast()
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
  const [downloading, setDownloading] = useState(false)

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
    setDownloading(false)
  }, [open, anchorLotId])

  const currentKey = `${direction}|${depth}`
  const paramsMatch = previewKey === currentKey
  const canGenerate =
    canBuild && phase === 'form' && !!preview && paramsMatch && !previewing && anchorLotId != null

  const summary = preview?.summary
  const members = preview?.members ?? []

  const truncated = preview?.truncated === true

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
      setPhase('built')
    } catch (e) {
      setPhase('form')
      toast.error(e instanceof ApiError ? e.message : '生成失败')
    }
  }

  async function doDownload() {
    if (packageId == null) return
    setDownloading(true)
    try {
      await exportComplaintPackageApi(packageId, packageNo ? `${packageNo}.json` : undefined)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '下载失败')
    } finally {
      setDownloading(false)
    }
  }

  const footer =
    phase === 'built' ? (
      <>
        <Button variant="secondary" onClick={onClose}>
          关闭
        </Button>
        <Button loading={downloading} disabled={packageId == null} onClick={() => void doDownload()}>
          下载证据
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
            已生成 <span className="font-mono">{packageNo}</span>。证据以导出时刻为准。
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
      </div>
    </Drawer>
  )
}
