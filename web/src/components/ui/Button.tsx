import { cn } from '../../lib/cn'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'md' | 'field'

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

const variants: Record<Variant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover border border-transparent',
  secondary: 'bg-surface text-ink border border-border hover:bg-border/40',
  ghost: 'bg-transparent text-ink border border-transparent hover:bg-surface',
  danger: 'bg-danger text-white border border-transparent hover:opacity-90',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  className,
  disabled,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-45',
        size === 'md' ? 'h-9 min-w-[88px] px-3.5 text-sm' : 'h-12 min-w-[88px] px-5 text-base font-semibold',
        variants[variant],
        className,
      )}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? '处理中…' : children}
    </button>
  )
}
