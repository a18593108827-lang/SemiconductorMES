import { useEffect, useRef } from 'react'
import gsap from 'gsap'
import { AlertTriangle, CircleAlert, Ruler } from 'lucide-react'
import type { TrackEdc } from '../../api/track'
import { cn } from '../../lib/cn'
import { motionMs } from '../../lib/motion'

export type EdcGateCopy = {
  key: string
  tone: 'danger' | 'warning' | 'muted'
  role: 'alert' | 'status'
  text: string
  Icon: typeof Ruler
}

export function edcGateCopy(edc: TrackEdc | null | undefined): EdcGateCopy | null {
  if (!edc?.required) return null
  if (!edc.clear) {
    if (edc.reasonCode === 'OOS') {
      return {
        key: 'OOS',
        tone: 'danger',
        role: 'alert',
        text: '量测超规，重采合格才能完工',
        Icon: AlertTriangle,
      }
    }
    return {
      key: 'NO_DATA',
      tone: 'warning',
      role: 'alert',
      text: '本站还没采，先填量测才能完工',
      Icon: Ruler,
    }
  }
  if (edc.reasonCode === 'GATE_DISABLED') {
    return {
      key: 'GATE_DISABLED',
      tone: 'muted',
      role: 'status',
      text: '量测应急放行，完工不卡',
      Icon: CircleAlert,
    }
  }
  return null
}

type Props = {
  edc: TrackEdc | null | undefined
}

export function EdcGateHint({ edc }: Props) {
  const rootRef = useRef<HTMLParagraphElement>(null)
  const copy = edcGateCopy(edc)

  useEffect(() => {
    const el = rootRef.current
    if (!el || !copy || motionMs() === 0) return
    const tw = gsap.fromTo(
      el,
      { autoAlpha: 0, y: 4 },
      { autoAlpha: 1, y: 0, duration: 0.2, ease: 'power2.out' },
    )
    return () => {
      tw.kill()
    }
  }, [copy?.key])

  if (!copy) return null
  const { Icon } = copy
  return (
    <p
      ref={rootRef}
      id="edc-gate-hint"
      className={cn(
        'mt-2 flex items-center gap-1.5 text-xs',
        copy.tone === 'danger' && 'text-danger',
        copy.tone === 'warning' && 'text-warning',
        copy.tone === 'muted' && 'text-field-muted',
      )}
      role={copy.role}
    >
      <Icon className="size-3.5 shrink-0" aria-hidden />
      {copy.text}
    </p>
  )
}
