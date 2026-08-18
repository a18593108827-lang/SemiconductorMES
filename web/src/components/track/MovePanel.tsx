import { useEffect, useRef } from 'react'
import gsap from 'gsap'
import { ArrowRight, ArrowRightLeft } from 'lucide-react'
import type { MesLotStepItem } from '../../api/lot'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { motionMs } from '../../lib/motion'

type Props = {
  lotNo: string
  currentSortNo: number | null
  currentStep: MesLotStepItem | null
  nextSortNo: number | null
  nextStepName: string | null
  nextStep: MesLotStepItem | null
  remark: string
  loading?: boolean
  onRemarkChange: (v: string) => void
  onConfirm: () => void
  onCancel: () => void
}

/**
 * 现场台 Move 面板：没加工，只把批挪到工艺下一站。
 * 青蓝 accent —— 物流感；别跟中止琥珀、报废红搞混。
 */
export function MovePanel({
  lotNo,
  currentSortNo,
  currentStep,
  nextSortNo,
  nextStepName,
  nextStep,
  remark,
  loading,
  onRemarkChange,
  onConfirm,
  onCancel,
}: Props) {
  const rootRef = useRef<HTMLDivElement>(null)
  const arrowRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const el = rootRef.current
    if (!el) return
    const d = motionMs() / 1000
    if (d === 0) {
      gsap.set(el, { autoAlpha: 1, y: 0 })
      if (arrowRef.current) gsap.set(arrowRef.current, { x: 0, autoAlpha: 1 })
      return
    }
    const tl = gsap.timeline()
    tl.fromTo(
      el,
      { autoAlpha: 0, y: 6 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
    if (arrowRef.current) {
      tl.fromTo(
        arrowRef.current,
        { x: -6, autoAlpha: 0.35 },
        { x: 0, autoAlpha: 1, duration: d * 0.9, ease: 'power2.out' },
        '-=0.12',
      )
    }
    return () => {
      tl.kill()
    }
  }, [])

  const fromLabel =
    currentStep?.stepName ||
    (currentSortNo != null ? `站 ${currentSortNo}` : '当前站')
  const toLabel =
    nextStepName ||
    nextStep?.stepName ||
    (nextSortNo != null ? `站 ${nextSortNo}` : '下一站')

  return (
    <div
      ref={rootRef}
      className="mt-4 overflow-hidden rounded-md border border-accent/45 bg-field-bg"
      role="region"
      aria-label="独立移站"
    >
      <div className="flex items-start gap-3 border-b border-accent/25 bg-accent/10 px-4 py-3">
        <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-md border border-accent/35 bg-field-panel text-accent">
          <ArrowRightLeft className="size-4" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-field-ink">移站 · 不经加工</p>
          <p className="mt-0.5 text-xs text-field-muted">
            只换站号 · 仍是 wait · 不算完工 · 不能乱跳
          </p>
        </div>
      </div>

      <div className="space-y-3 p-4">
        <dl className="grid grid-cols-2 gap-2 rounded-md border border-field-border bg-field-panel/80 px-3 py-2 font-mono text-xs text-field-muted sm:grid-cols-3">
          <div>
            <dt className="text-[10px] text-field-muted/80">Lot</dt>
            <dd className="mt-0.5 truncate text-field-ink">{lotNo}</dd>
          </div>
          <div>
            <dt className="text-[10px] text-field-muted/80">当前</dt>
            <dd className="mt-0.5 text-field-ink">
              {currentSortNo != null ? `S${currentSortNo}` : '—'}
            </dd>
          </div>
          <div className="col-span-2 sm:col-span-1">
            <dt className="text-[10px] text-field-muted/80">目标</dt>
            <dd className="mt-0.5 text-accent">
              {nextSortNo != null ? `S${nextSortNo}` : '—'}
            </dd>
          </div>
        </dl>

        <div
          className="grid grid-cols-[1fr_auto_1fr] items-stretch gap-2 rounded-md border border-accent/30 bg-accent/[0.06] p-3"
          aria-label={`从 ${fromLabel} 移至 ${toLabel}`}
        >
          <div className="min-w-0 rounded-md border border-field-border bg-field-panel px-3 py-2.5">
            <p className="text-[10px] font-medium uppercase tracking-wide text-field-muted">
              本站
            </p>
            <p className="mt-1 font-mono text-xs text-accent">
              {currentSortNo != null ? `S${currentSortNo}` : '—'}
            </p>
            <p className="mt-0.5 truncate text-sm font-medium text-field-ink" title={fromLabel}>
              {fromLabel}
            </p>
          </div>

          <span
            ref={arrowRef}
            className="flex items-center justify-center self-center text-accent"
            aria-hidden
          >
            <ArrowRight className="size-5" strokeWidth={2.25} />
          </span>

          <div className="min-w-0 rounded-md border border-accent/40 bg-field-panel px-3 py-2.5 ring-1 ring-accent/20">
            <p className="text-[10px] font-medium uppercase tracking-wide text-accent/90">
              下一站
            </p>
            <p className="mt-1 font-mono text-xs text-accent">
              {nextSortNo != null ? `S${nextSortNo}` : '—'}
            </p>
            <p className="mt-0.5 truncate text-sm font-medium text-field-ink" title={toLabel}>
              {toLabel}
            </p>
          </div>
        </div>

        <Field
          label="备注"
          name="moveRemark"
          value={remark}
          onChange={(e) => onRemarkChange(e.target.value)}
          placeholder="可选，例如：物流进清洗区"
        />

        <p className="text-xs text-accent/90">
          移站不是完工：本站没干活。加工完离开请点「完工」。乱跳属 Special（未开放）。
        </p>

        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="md"
            variant="secondary"
            className="min-h-12 min-w-[7.5rem] cursor-pointer border-accent/55 bg-accent text-[oklch(0.14_0_0)] hover:bg-accent/90"
            loading={loading}
            disabled={nextSortNo == null}
            onClick={onConfirm}
          >
            <ArrowRightLeft className="size-3.5" aria-hidden />
            确认移站
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
