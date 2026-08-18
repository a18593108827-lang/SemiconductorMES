import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { CheckCircle2, CircleAlert, X } from 'lucide-react'
import { cn } from '../../lib/cn'

type ToastTone = 'success' | 'error'

type ToastItem = {
  id: number
  tone: ToastTone
  message: string
}

type ToastApi = {
  success: (message: string) => void
  error: (message: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

let seq = 0

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const dismiss = useCallback((id: number) => {
    setItems((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = ++seq
      setItems((prev) => [...prev.slice(-4), { id, tone, message }])
      window.setTimeout(() => dismiss(id), 3200)
    },
    [dismiss],
  )

  const api = useMemo<ToastApi>(
    () => ({
      success: (message) => push('success', message),
      error: (message) => push('error', message),
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="pointer-events-none fixed right-4 top-4 z-[80] flex w-[min(360px,calc(100vw-2rem))] flex-col gap-2"
        aria-live="polite"
      >
        {items.map((t) => (
          <div
            key={t.id}
            role="status"
            className={cn(
              'pointer-events-auto flex items-start gap-2 rounded-md border px-3 py-2.5 text-sm shadow-[0_8px_24px_oklch(0_0_0/0.1)]',
              t.tone === 'success'
                ? 'border-success/30 bg-bg text-ink'
                : 'border-danger/30 bg-bg text-ink',
            )}
          >
            {t.tone === 'success' ? (
              <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" />
            ) : (
              <CircleAlert className="mt-0.5 size-4 shrink-0 text-danger" />
            )}
            <p className="min-w-0 flex-1 leading-snug">{t.message}</p>
            <button
              type="button"
              className="shrink-0 rounded-sm p-0.5 text-muted hover:bg-surface hover:text-ink"
              aria-label="关闭"
              onClick={() => dismiss(t.id)}
            >
              <X className="size-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within ToastProvider')
  return ctx
}
