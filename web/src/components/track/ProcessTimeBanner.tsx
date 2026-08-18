import { useEffect, useMemo, useRef, useState } from 'react'
import gsap from 'gsap'
import { Gauge } from 'lucide-react'
import type { TrackProcessTime } from '../../api/track'
import { cn } from '../../lib/cn'
import { motionMs } from '../../lib/motion'

export function parseProcessStartedAt(v: string) {
  const t = new Date(v.includes('T') ? v : v.replace(' ', 'T')).getTime()
  return Number.isNaN(t) ? null : t
}

export function liveCanTrackOutByTime(pt: TrackProcessTime | null | undefined, now = Date.now()) {
  if (!pt || pt.minProcessMin == null) return true
  const started = parseProcessStartedAt(pt.startedAt)
  if (started == null) return pt.canTrackOutByTime !== false
  return now - started >= pt.minProcessMin * 60_000
}

export function liveExceededMax(pt: TrackProcessTime | null | undefined, now = Date.now()) {
  if (!pt?.maxProcessMin) return Boolean(pt?.exceededMax)
  const started = parseProcessStartedAt(pt.startedAt)
  if (started == null) return Boolean(pt.exceededMax)
  return now - started > pt.maxProcessMin * 60_000
}

export function useLiveProcessGate(pt: TrackProcessTime | null | undefined) {
  const [now, setNow] = useState(() => Date.now())
  const ticking = pt != null && (pt.minProcessMin != null || pt.maxProcessMin != null)
  useEffect(() => {
    if (!ticking) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [ticking, pt?.startedAt])
  return {
    canTrackOutByTime: liveCanTrackOutByTime(pt, now),
    exceededMax: liveExceededMax(pt, now),
  }
}

function formatClock(ms: number) {
  const totalSec = Math.max(0, Math.floor(ms / 1000))
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  if (m >= 60) {
    const h = Math.floor(m / 60)
    const mm = m % 60
    return `${h}:${String(mm).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return `${m}:${String(s).padStart(2, '0')}`
}

type Props = {
  processTime: TrackProcessTime
}

/** 现场台 Process Time：站内加工计时，未到下限 / 超上限强调 */
export function ProcessTimeBanner({ processTime }: Props) {
  const rootRef = useRef<HTMLDivElement>(null)
  const startedMs = useMemo(() => parseProcessStartedAt(processTime.startedAt), [processTime.startedAt])
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (startedMs == null) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [startedMs, processTime.startedAt])

  const elapsedMs = startedMs != null ? Math.max(0, now - startedMs) : 0
  const minMs =
    processTime.minProcessMin != null ? processTime.minProcessMin * 60_000 : null
  const maxMs =
    processTime.maxProcessMin != null ? processTime.maxProcessMin * 60_000 : null

  const tooShort = minMs != null && elapsedMs < minMs
  const tooLong = maxMs != null && elapsedMs > maxMs
  const nearMax =
    !tooLong && maxMs != null && maxMs - elapsedMs <= 5 * 60_000 && elapsedMs >= (minMs ?? 0)
  const canOut = !tooShort

  const progress = useMemo(() => {
    const span = maxMs ?? minMs
    if (span == null || span <= 0) return 0
    return Math.min(1.05, Math.max(0, elapsedMs / span))
  }, [elapsedMs, maxMs, minMs])

  const minMarkPct =
    maxMs != null && minMs != null && maxMs > 0
      ? Math.min(100, Math.round((minMs / maxMs) * 100))
      : null

  useEffect(() => {
    const el = rootRef.current
    if (!el || motionMs() === 0) return
    if (!tooShort && !tooLong && !nearMax) {
      gsap.killTweensOf(el)
      gsap.set(el, { opacity: 1 })
      return
    }
    const tw = gsap.fromTo(
      el,
      { opacity: 1 },
      {
        opacity: 0.72,
        duration: tooLong || tooShort ? 0.55 : 0.9,
        yoyo: true,
        repeat: -1,
        ease: 'sine.inOut',
      },
    )
    return () => {
      tw.kill()
      gsap.set(el, { opacity: 1 })
    }
  }, [tooShort, tooLong, nearMax])

  const tone = tooShort
    ? 'danger'
    : tooLong
      ? 'warning'
      : nearMax
        ? 'warning'
        : canOut
          ? 'success'
          : 'neutral'
  const statusText = tooShort
    ? `未到下限 · 还需 ${formatClock(minMs! - elapsedMs)} · 禁止完工`
    : tooLong
      ? '已超上限 · 可完工，出站后自动锁批'
      : nearMax
        ? `接近上限 · 剩余 ${formatClock(maxMs! - elapsedMs)}`
        : '可完工'

  return (
    <div
      ref={rootRef}
      className={cn(
        'rounded-md border px-3 py-2',
        tone === 'danger' && 'border-danger/40 bg-danger/10',
        tone === 'warning' && 'border-warning/40 bg-warning/10',
        tone === 'success' && 'border-success/40 bg-success/10',
        tone === 'neutral' && 'border-field-border bg-field-bg',
      )}
      role="status"
      aria-live="polite"
    >
      <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 text-xs font-medium text-field-ink">
          <Gauge
            className={cn(
              'size-3.5',
              tone === 'danger' && 'text-danger',
              tone === 'warning' && 'text-warning',
              tone === 'success' && 'text-success',
              tone === 'neutral' && 'text-field-muted',
            )}
            aria-hidden
          />
          Process Time · 站内加工
        </div>
        <span
          className={cn(
            'font-mono text-sm font-semibold tabular-nums',
            tone === 'danger' && 'text-danger',
            tone === 'warning' && 'text-warning',
            tone === 'success' && 'text-success',
            tone === 'neutral' && 'text-field-ink',
          )}
        >
          {formatClock(elapsedMs)}
        </span>
      </div>

      <div className="relative h-1.5 overflow-hidden rounded-full bg-field-border/80">
        <div
          className={cn(
            'h-full rounded-full transition-[width] duration-300 ease-out',
            tone === 'danger' && 'bg-danger',
            tone === 'warning' && 'bg-warning',
            tone === 'success' && 'bg-success',
            tone === 'neutral' && 'bg-accent',
          )}
          style={{ width: `${Math.round(Math.min(1, progress) * 100)}%` }}
        />
        {minMarkPct != null ? (
          <span
            className="absolute top-0 bottom-0 w-px bg-field-ink/50"
            style={{ left: `${minMarkPct}%` }}
            title={`下限 ${processTime.minProcessMin} 分`}
            aria-hidden
          />
        ) : null}
      </div>

      <p className="mt-1.5 text-[11px] text-field-muted">
        {processTime.minProcessMin != null ? `下限 ${processTime.minProcessMin} 分` : '无下限'}
        {' · '}
        {processTime.maxProcessMin != null ? `上限 ${processTime.maxProcessMin} 分` : '无上限'}
        {' · '}
        <span
          className={cn(
            tone === 'danger' && 'text-danger',
            tone === 'warning' && 'text-warning',
            tone === 'success' && 'text-success',
          )}
        >
          {statusText}
        </span>
      </p>
    </div>
  )
}
