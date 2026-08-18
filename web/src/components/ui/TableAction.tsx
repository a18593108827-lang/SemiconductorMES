import type { LucideIcon } from 'lucide-react'
import { cn } from '../../lib/cn'

type Tone = 'default' | 'accent' | 'danger' | 'warning'

const toneClass: Record<Tone, string> = {
  default: 'border-border text-ink hover:bg-surface',
  accent: 'border-accent/30 text-accent hover:bg-accent/8',
  danger: 'border-danger/30 text-danger hover:bg-danger/8',
  warning: 'border-warning/40 text-warning hover:bg-warning/10',
}

export function TableAction({
  icon: Icon,
  label,
  tone = 'default',
  disabled,
  title,
  onClick,
}: {
  icon: LucideIcon
  label: string
  tone?: Tone
  disabled?: boolean
  title?: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      title={title ?? label}
      aria-label={label}
      onClick={onClick}
      className={cn(
        'inline-flex h-7 items-center gap-1 rounded-md border px-2 text-xs font-medium transition-colors duration-150',
        toneClass[tone],
        disabled && 'cursor-not-allowed opacity-40 hover:bg-transparent',
      )}
    >
      <Icon className="size-3.5 shrink-0" aria-hidden />
      <span className="whitespace-nowrap">{label}</span>
    </button>
  )
}
