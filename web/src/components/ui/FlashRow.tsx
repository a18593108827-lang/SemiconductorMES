import { useEffect, useRef } from 'react'
import gsap from 'gsap'
import { motionMs } from '../../lib/motion'

export function FlashRow({ active, children }: { active: boolean; children: React.ReactNode }) {
  const ref = useRef<HTMLTableRowElement>(null)

  useEffect(() => {
    if (!active || !ref.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      ref.current,
      { backgroundColor: 'oklch(0.58 0.12 230 / 0.18)' },
      { backgroundColor: 'transparent', duration: 0.6, ease: 'power2.out' },
    )
  }, [active])

  return <tr ref={ref}>{children}</tr>
}
