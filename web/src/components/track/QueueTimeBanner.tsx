import { useEffect, useMemo, useRef, useState } from 'react'
import gsap from 'gsap'
import { Timer } from 'lucide-react'
import type { TrackQueueTime } from '../../api/track'
import { cn } from '../../lib/cn'
import { motionMs } from '../../lib/motion'

function parseStartedAt(v: string) {
  const t = new Date(v.includes('T') ? v : v.replace(' ', 'T')).getTime()
  return Number.isNaN(t) ? null : t
}

function formatRemain(ms: number) {
  if (ms <= 0) return '0:00'
  const totalSec = Math.floor(ms / 1000)
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
  queueTime: TrackQueueTime
}

/** 现场台 Queue Time：本地倒计时，临近/超时强调 */
export function QueueTimeBanner({ queueTime }: Props) {
  const rootRef = useRef<HTMLDivElement>(null)
  const startedMs = useMemo(() => parseStartedAt(queueTime.startedAt), [queueTime.startedAt])
  const deadlineMs = startedMs != null ? startedMs + queueTime.maxQueueMin * 60_000 : null

  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (deadlineMs == null) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [deadlineMs, queueTime.startedAt, queueTime.maxQueueMin])

  const remainMs = deadlineMs != null ? deadlineMs - now : 0
  const violated = remainMs <= 0
  const urgent = !violated && remainMs <= 5 * 60_000
  const progress = useMemo(() => {
    if (deadlineMs == null || startedMs == null) return 0
    const total = deadlineMs - startedMs
    if (total <= 0) return 1
    return Math.min(1, Math.max(0, (now - startedMs) / total))
  }, [deadlineMs, startedMs, now])

  useEffect(() => {
    const el = rootRef.current
    if (!el || motionMs() === 0) return
    if (!urgent && !violated) {
      gsap.killTweensOf(el)
      gsap.set(el, { opacity: 1 })
      return
    }
    const tw = gsap.fromTo(
      el,
      { opacity: 1 },
      {
        opacity: 0.72,
        duration: violated ? 0.55 : 0.9,
        yoyo: true,
        repeat: -1,
        ease: 'sine.inOut',
      },
    )
    return () => {
      tw.kill()
      gsap.set(el, { opacity: 1 })
    }
  }, [urgent, violated])

  const policyLabel =
    queueTime.onViolate === 'ALARM'
      ? '超时告警'
      : queueTime.onViolate === 'HOLD_ALARM'
        ? '超时锁批+告警'
        : '超时锁批'

  return (
    <div
      ref={rootRef}
      className={cn(
        'rounded-md border px-3 py-2',
        violated
          ? 'border-danger/40 bg-danger/10'
          : urgent
            ? 'border-warning/40 bg-warning/10'
            : 'border-accent/30 bg-accent/10',
      )}
      role="status"
      aria-live="polite"
    >
      <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 text-xs font-medium text-field-ink">
          <Timer
            className={cn(
              'size-3.5',
              violated ? 'text-danger' : urgent ? 'text-warning' : 'text-accent',
            )}
            aria-hidden
          />
          Queue Time · 站 {queueTime.fromSortNo} → {queueTime.toSortNo}
        </div>
        <span
          className={cn(
            'font-mono text-sm font-semibold tabular-nums',
            violated ? 'text-danger' : urgent ? 'text-warning' : 'text-field-ink',
          )}
        >
          {violated ? '已超时' : formatRemain(remainMs)}
        </span>
      </div>
      <div className="h-1 overflow-hidden rounded-full bg-field-border/80">
        <div
          className={cn(
            'h-full rounded-full transition-[width] duration-300 ease-out',
            violated ? 'bg-danger' : urgent ? 'bg-warning' : 'bg-accent',
          )}
          style={{ width: `${Math.round(progress * 100)}%` }}
        />
      </div>
      <p className="mt-1.5 text-[11px] text-field-muted">
        上限 {queueTime.maxQueueMin} 分钟 · {policyLabel}
        {violated ? ' · 目标站禁止开工直至处理' : null}
      </p>
    </div>
  )
}
