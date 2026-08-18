import { useEffect, useRef } from 'react'
import gsap from 'gsap'
import { Ban, Gauge } from 'lucide-react'
import type { TrackAbortReason, TrackProcessTime } from '../../api/track'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { cn } from '../../lib/cn'
import { motionMs } from '../../lib/motion'

type Props = {
  lotNo: string
  currentSortNo: number | null
  currentEqpId: number | string | null
  processTime?: TrackProcessTime | null
  reasons: TrackAbortReason[]
  reason: string
  remark: string
  loading?: boolean
  onReasonChange: (code: string) => void
  onRemarkChange: (v: string) => void
  onConfirm: () => void
  onCancel: () => void
}

/**
 * 现场台 Abort 面板：加工中出事，合法回本站等待。
 * 琥珀色（警告）——不是报废红；确认后站别/数量不变。
 */
export function AbortPanel({
  lotNo,
  currentSortNo,
  currentEqpId,
  processTime,
  reasons,
  reason,
  remark,
  loading,
  onReasonChange,
  onRemarkChange,
  onConfirm,
  onCancel,
}: Props) {
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = rootRef.current
    if (!el) return
    const d = motionMs() / 1000
    if (d === 0) {
      gsap.set(el, { autoAlpha: 1, y: 0 })
      return
    }
    gsap.fromTo(
      el,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [])

  const otherNeedsRemark = reason === 'OTHER'

  return (
    <div
      ref={rootRef}
      className="mt-4 overflow-hidden rounded-md border border-warning/45 bg-field-bg"
      role="region"
      aria-label="加工中止"
    >
      <div className="flex items-start gap-3 border-b border-warning/25 bg-warning/10 px-4 py-3">
        <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-md border border-warning/35 bg-field-panel text-warning">
          <Ban className="size-4" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-field-ink">加工中止 · 回本站等待</p>
          <p className="mt-0.5 text-xs text-field-muted">
            站别与数量不变 · 机台释放 · 秒表清零 · 可再次开工
          </p>
        </div>
      </div>

      <div className="space-y-3 p-4">
        <dl className="grid grid-cols-2 gap-2 rounded-md border border-field-border bg-field-panel/80 px-3 py-2 font-mono text-xs text-field-muted sm:grid-cols-4">
          <div>
            <dt className="text-[10px] text-field-muted/80">Lot</dt>
            <dd className="mt-0.5 truncate text-field-ink">{lotNo}</dd>
          </div>
          <div>
            <dt className="text-[10px] text-field-muted/80">当前站</dt>
            <dd className="mt-0.5 text-field-ink">
              {currentSortNo != null ? `S${currentSortNo}` : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-[10px] text-field-muted/80">设备</dt>
            <dd className="mt-0.5 truncate text-field-ink">
              {currentEqpId != null ? String(currentEqpId) : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-[10px] text-field-muted/80">加工计时</dt>
            <dd
              className={cn(
                'mt-0.5 flex items-center gap-1',
                processTime ? 'text-warning' : 'text-field-ink',
              )}
            >
              {processTime ? (
                <>
                  <Gauge className="size-3 shrink-0" aria-hidden />
                  将清零
                </>
              ) : (
                '无'
              )}
            </dd>
          </div>
        </dl>

        <div>
          <label
            htmlFor="abortReason"
            className="mb-1 block text-xs font-medium text-field-muted"
          >
            中止原因
          </label>
          <select
            id="abortReason"
            className="h-11 w-full cursor-pointer rounded-md border border-field-border bg-field-panel px-3 font-mono text-sm text-field-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            value={reason}
            onChange={(e) => onReasonChange(e.target.value)}
          >
            {reasons.length === 0 ? (
              <option value="">加载中…</option>
            ) : (
              reasons.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.code} · {r.label}
                </option>
              ))
            )}
          </select>
        </div>

        <Field
          label={otherNeedsRemark ? '备注（必填）' : '备注'}
          name="abortRemark"
          value={remark}
          onChange={(e) => onRemarkChange(e.target.value)}
          placeholder={otherNeedsRemark ? '请说明其他原因' : '可选'}
        />

        <p className="text-xs text-warning/90">
          中止不是报废、也不是跳站。确认后仍停在本站 wait，可重新选机开工。
        </p>

        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="md"
            variant="secondary"
            className="min-h-12 min-w-[7.5rem] cursor-pointer border-warning/60 bg-warning text-[oklch(0.18_0_0)] hover:bg-warning/90"
            loading={loading}
            disabled={!reason}
            onClick={onConfirm}
          >
            <Ban className="size-3.5" aria-hidden />
            确认中止
          </Button>
          <Button
            type="button"
            size="md"
            variant="secondary"
            className="min-h-12 cursor-pointer"
            onClick={onCancel}
          >
            取消
          </Button>
        </div>
      </div>
    </div>
  )
}
