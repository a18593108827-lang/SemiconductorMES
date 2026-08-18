export function motionMs() {
  if (typeof window === 'undefined') return 200
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 200
}
