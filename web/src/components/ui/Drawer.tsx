import { useEffect, useRef } from 'react'
import gsap from 'gsap'
import { X } from 'lucide-react'
import { cn } from '../../lib/cn'
import { motionMs } from '../../lib/motion'

interface DrawerProps {
  open: boolean
  title: string
  onClose: () => void
  children: React.ReactNode
  footer?: React.ReactNode
  width?: number
  tone?: 'admin' | 'field'
}

export function Drawer({
  open,
  title,
  onClose,
  children,
  footer,
  width = 440,
  tone = 'admin',
}: DrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const backdropRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!panelRef.current || !backdropRef.current) return
    const d = motionMs() / 1000
    if (open) {
      gsap.set(panelRef.current, { x: '100%' })
      gsap.to(backdropRef.current, { opacity: 1, duration: d, ease: 'power2.out' })
      gsap.to(panelRef.current, { x: 0, duration: d, ease: 'power2.out' })
    }
  }, [open])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      <div
        ref={backdropRef}
        className={cn('absolute inset-0 opacity-0', tone === 'field' ? 'bg-black/50' : 'bg-ink/30')}
        onClick={onClose}
        aria-hidden
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal
        aria-label={title}
        className={cn(
          'relative z-10 flex h-full flex-col border-l',
          tone === 'field'
            ? 'border-field-border bg-field-bg text-field-ink shadow-[0_8px_28px_oklch(0_0_0/0.45)]'
            : 'border-border bg-bg shadow-[0_8px_24px_oklch(0_0_0/0.12)]',
        )}
        style={{ width, maxWidth: '100vw' }}
      >
        <header
          className={cn(
            'flex h-14 shrink-0 items-center justify-between border-b px-4',
            tone === 'field' ? 'border-field-border' : 'border-border',
          )}
        >
          <h2 className={cn('text-xl font-semibold', tone === 'field' ? 'text-field-ink' : 'text-ink')}>
            {title}
          </h2>
          <button
            type="button"
            className={cn(
              'rounded-md p-2',
              tone === 'field'
                ? 'text-field-muted hover:bg-field-surface hover:text-field-ink'
                : 'text-muted hover:bg-surface hover:text-ink',
            )}
            onClick={onClose}
            aria-label="关闭"
          >
            <X className="size-5" />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">{children}</div>
        {footer ? (
          <footer
            className={cn(
              'flex shrink-0 items-center justify-end gap-2 border-t px-4 py-3',
              tone === 'field' ? 'border-field-border' : 'border-border',
            )}
          >
            {footer}
          </footer>
        ) : null}
      </div>
    </div>
  )
}
