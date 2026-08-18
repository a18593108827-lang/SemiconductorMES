import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import { Button } from './Button'

type ConfirmOptions = {
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

type ConfirmApi = (options: ConfirmOptions) => Promise<boolean>

const ConfirmContext = createContext<ConfirmApi | null>(null)

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false)
  const [opts, setOpts] = useState<ConfirmOptions | null>(null)
  const resolver = useRef<((v: boolean) => void) | null>(null)

  const close = useCallback((value: boolean) => {
    resolver.current?.(value)
    resolver.current = null
    setOpen(false)
    setOpts(null)
  }, [])

  const confirm = useCallback<ConfirmApi>((options) => {
    return new Promise<boolean>((resolve) => {
      resolver.current?.(false)
      resolver.current = resolve
      setOpts(options)
      setOpen(true)
    })
  }, [])

  const api = useMemo(() => confirm, [confirm])

  return (
    <ConfirmContext.Provider value={api}>
      {children}
      {open && opts ? (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-ink/30" onClick={() => close(false)} aria-hidden />
          <div
            role="dialog"
            aria-modal
            aria-labelledby="confirm-title"
            className="relative z-10 w-full max-w-sm rounded-md border border-border bg-bg p-4 shadow-[0_12px_32px_oklch(0_0_0/0.14)]"
          >
            <h2 id="confirm-title" className="text-base font-semibold text-ink">
              {opts.title}
            </h2>
            <p className="mt-2 text-sm text-muted">{opts.message}</p>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => close(false)}>
                {opts.cancelText ?? '取消'}
              </Button>
              <Button
                variant={opts.danger ? 'danger' : 'primary'}
                onClick={() => close(true)}
              >
                {opts.confirmText ?? '确认'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </ConfirmContext.Provider>
  )
}

export function useConfirm() {
  const ctx = useContext(ConfirmContext)
  if (!ctx) throw new Error('useConfirm must be used within ConfirmProvider')
  return ctx
}
